package jealoustone.elytravario.hud;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.flight.FlightRecorder;
import jealoustone.elytravario.flight.Sample;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

/**
 * A pitch ladder drawn over the world view, with a flight path marker showing where the
 * player is actually going.
 *
 * <p>Unlike the readout panel this is a <em>conformal</em> instrument: every mark is placed
 * by projecting a direction through the same camera the world was drawn with, so a rung
 * labelled {@code -20} lies exactly along the ground that is twenty degrees above the
 * horizon. The ladder and the terrain move together, which is what makes it readable at a
 * glance rather than by being studied.
 *
 * <h2>Reading it without looking at it</h2>
 *
 * <p>The rungs are tiered by length and weight so that an angle can be recovered from the
 * pattern alone, in peripheral vision, without resolving any digits: stubs every two
 * degrees near the centre, short dashed rungs every ten, solid rungs every twenty, and the
 * datum lines — the horizon and plus or minus forty — longest and brightest. Labels are
 * deliberately faint, and only the twenties carry one.
 *
 * <p>Nothing distinguishes above the horizon from below it, because the sky, the ground and
 * the labelled datum line already do.
 *
 * <h2>The projection</h2>
 *
 * <p>Minecraft's perspective matrix is built from a vertical field of view and the viewport
 * height, so the pixel scale is the same on both screen axes and no aspect ratio is needed:
 * a direction {@code t} units of tangent off the camera axis lands {@code t * scale} pixels
 * away from the centre, where {@code scale = halfHeight / tan(fov / 2)}. The GUI's
 * orthographic projection covers the whole framebuffer, so the half-height may be taken in
 * scaled GUI pixels and the centre of the GUI is the centre of the view.
 *
 * <p>The rung case reduces to vanilla's own {@code GameRenderer.projectHorizonToScreen},
 * which is {@code tan(cameraPitch) / tan(fov / 2)} — the same expression with the rung pitch
 * set to zero. Everything is referenced to {@link Camera}, not to the player, so the ladder
 * stays glued to the world in third person and in the mirrored front view too.
 *
 * <p>Marks land on fractional pixels, and rounding each to a whole one makes the ladder
 * climb the screen in visible steps — worst on the labels, whose glyphs jump as a block.
 * Each mark is therefore drawn on a pose translated by its own fractional part, which pushes
 * the quantisation down to the physical pixel the GUI scale is drawn at.
 *
 * <h2>Why the ladder is yaw-locked</h2>
 *
 * <p>A rung is the set of directions at one pitch, which is a circle on the view sphere, and
 * a circle projects to a conic — so a rung is only truly straight where it crosses the
 * centre of the screen. Drawing straight horizontal rungs symmetric about the centre is
 * therefore exact in the middle and bows away from the truth towards the ends, by an amount
 * that grows with how far off-centre the rung reaches. At the default rung length the error
 * is well under a pixel.
 *
 * <p>The flight path marker has no such problem. It is a single direction rather than a
 * curve, and a point projects to a point, so it is placed exactly on both axes — including
 * horizontally, where its offset from the crosshair is the sideslip that the chart's cyan
 * cursor also measures.
 */
public final class PitchLadderElement implements HudElement {
	/** Dash geometry for the ten-degree rungs, in pixels. */
	private static final int DASH = 4;
	private static final int DASH_GAP = 3;

	/** Gap between a rung's outer end and its label. */
	private static final int LABEL_GAP = 4;

	/** Half the font's line height, to centre a label on the rung it belongs to. */
	private static final int LABEL_RISE = 4;

	/**
	 * Speed below which the flight path marker is not drawn, in blocks/tick. A direction of
	 * travel needs travel; near standstill the marker would spin on rounding noise.
	 */
	private static final double MIN_SPEED = 0.02;

	/** Cosine floor for "in front of the camera", guarding the divide by the depth term. */
	private static final double MIN_DEPTH = 1.0e-3;

	/** Kept clear of the screen edge so a pegged marker's wings stay visible. */
	private static final int MARKER_MARGIN = 12;

	/**
	 * A rung a quarter turn off the camera axis is edge-on, and beyond that it is behind the
	 * viewer; the tangent would place both on screen as ghosts.
	 */
	private static final double MAX_ELEVATION = 89.5;

	private final FlightRecorder recorder;

	public PitchLadderElement(FlightRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled || !VarioConfig.showLadder) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return;
		}

		Sample sample = recorder.latest();

		if (sample == null || (VarioConfig.onlyWhileGliding && !sample.gliding())) {
			return;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();

		// Before the first frame the camera holds neither a rotation nor a field of view, so
		// there is no projection to place marks with.
		if (!camera.isInitialized()) {
			return;
		}

		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		double halfHeight = graphics.guiHeight() / 2.0;
		double scale = halfHeight / Math.tan(Math.toRadians(camera.getFov() / 2.0));
		int bandUp = (int) Math.round(halfHeight * VarioConfig.ladderBandFractionUp);
		int bandDown = (int) Math.round(halfHeight * VarioConfig.ladderBandFractionDown);
		float cameraPitch = camera.xRot();

		// Fine ticks first, so a coarse rung always paints over one where the two land
		// together at the very edge of the fine range.
		drawFineTicks(graphics, cameraPitch, centerX, centerY, scale, bandUp, bandDown);
		drawRungs(graphics, minecraft.font, cameraPitch, centerX, centerY, scale, bandUp, bandDown);

		if (VarioConfig.showFlightPath) {
			drawFlightPath(graphics, camera, centerX, centerY, scale, bandUp, bandDown);
		}
	}

	/**
	 * The tiered rungs, labelled in raw Minecraft pitch so that they agree in sign with the
	 * panel's {@code PITCH} row, with F3 and with elytrasim: negative is above the horizon.
	 */
	private void drawRungs(GuiGraphicsExtractor graphics, Font font, float cameraPitch,
			int centerX, int centerY, double scale, int bandUp, int bandDown) {
		int step = Math.max(1, VarioConfig.ladderStepDegrees);

		for (int pitch = -90; pitch <= 90; pitch += step) {
			double offset = project(cameraPitch, pitch, scale);
			double edge = edgeFade(offset, bandUp, bandDown);

			if (edge <= 0.0) {
				continue;
			}

			boolean prime = pitch == 0 || Math.abs(pitch) == 40;
			boolean major = pitch % 20 == 0;

			int length = prime ? VarioConfig.ladderPrimeLength
					: major ? VarioConfig.ladderMajorLength
					: VarioConfig.ladderMinorLength;

			if (pitch == 0) {
				length += VarioConfig.ladderHorizonExtra;
			}

			int color = prime ? VarioConfig.ladderPrimeColor
					: major ? VarioConfig.ladderMajorColor
					: VarioConfig.ladderMinorColor;

			int inner = VarioConfig.ladderCenterGap;
			int outer = inner + length;

			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			int y = subpixel(pose, centerY - offset);

			rung(graphics, centerX, y, inner, outer, fade(color, edge), !prime && !major);

			// Only the twenties are labelled. The rungs between them are unambiguous from
			// their own tier, and a digit on every one is the clutter this ladder avoids.
			if (major) {
				String label = Integer.toString(pitch);
				int labelY = y - LABEL_RISE;
				int labelColor = fade(VarioConfig.ladderLabelColor, edge);
				graphics.text(font, label, centerX - outer - LABEL_GAP - font.width(label),
						labelY, labelColor, true);
				graphics.text(font, label, centerX + outer + LABEL_GAP,
						labelY, labelColor, true);
			}

			pose.popMatrix();
		}
	}

	/**
	 * Stubs at a finer step, covering only the span the camera is pointing at and fading to
	 * nothing at the edge of it. Their pitches are absolute multiples of the fine step rather
	 * than offsets from the camera, so they are real angles that the view slides across
	 * instead of a scale that follows the head around.
	 */
	private void drawFineTicks(GuiGraphicsExtractor graphics, float cameraPitch,
			int centerX, int centerY, double scale, int bandUp, int bandDown) {
		int step = Math.max(1, VarioConfig.ladderFineStepDegrees);
		int coarse = Math.max(1, VarioConfig.ladderStepDegrees);
		double range = VarioConfig.ladderFineRangeDegrees;

		if (range <= 0.0) {
			return;
		}

		int lowest = Math.max(-90, (int) Math.ceil((cameraPitch - range) / step) * step);
		int highest = Math.min(90, (int) Math.floor((cameraPitch + range) / step) * step);

		for (int pitch = lowest; pitch <= highest; pitch += step) {
			if (pitch % coarse == 0) {
				continue;
			}

			double offset = project(cameraPitch, pitch, scale);

			// Two independent fades multiply: one for leaving the span the camera is looking
			// at, one for approaching the edge of the ladder.
			double near = 1.0 - Math.abs(pitch - cameraPitch) / range;
			double edge = edgeFade(offset, bandUp, bandDown);

			if (near <= 0.0 || edge <= 0.0) {
				continue;
			}

			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			int y = subpixel(pose, centerY - offset);

			rung(graphics, centerX, y, VarioConfig.ladderCenterGap,
					VarioConfig.ladderCenterGap + VarioConfig.ladderFineLength,
					fade(VarioConfig.ladderFineColor, near * edge), false);

			pose.popMatrix();
		}
	}

	/**
	 * Where a mark at {@code pitch} lands relative to the centre of the view, in pixels,
	 * positive upwards. {@code NaN} for marks that are edge-on or behind the camera.
	 */
	private static double project(float cameraPitch, double pitch, double scale) {
		double elevation = cameraPitch - pitch;

		if (Math.abs(elevation) >= MAX_ELEVATION) {
			return Double.NaN;
		}

		return Math.tan(Math.toRadians(elevation)) * scale;
	}

	/**
	 * How strongly to draw a mark at {@code offset} pixels above centre: full inside the
	 * band, tapering to nothing at its edge, zero beyond it and for marks the projection
	 * could not place.
	 *
	 * <p>Without the taper a mark leaves by blinking off, which in a HUD that is mostly
	 * watched peripherally reads as a flicker at the top of the vision rather than as
	 * something departing.
	 */
	private static double edgeFade(double offset, int bandUp, int bandDown) {
		if (!Double.isFinite(offset)) {
			return 0.0;
		}

		int limit = offset >= 0.0 ? bandUp : bandDown;

		if (limit <= 0) {
			return 0.0;
		}

		double taper = VarioConfig.ladderFadeFraction;
		double remaining = 1.0 - Math.abs(offset) / limit;

		if (remaining <= 0.0) {
			return 0.0;
		}

		return taper <= 0.0 ? 1.0 : Math.min(1.0, remaining / taper);
	}

	/**
	 * Translates the pose by the fractional part of {@code exactY} and returns the whole part
	 * to draw at, so that a mark ends up where it belongs rather than snapped to the nearest
	 * scaled pixel. Caller is responsible for the surrounding push and pop.
	 */
	private static int subpixel(Matrix3x2fStack pose, double exactY) {
		int whole = (int) Math.floor(exactY);
		pose.translate(0.0f, (float) (exactY - whole));
		return whole;
	}

	/**
	 * One rung, drawn as a mirrored pair so the two halves stay in step: distances are
	 * measured out from the centre and applied to both sides, which keeps the dashes lined
	 * up across the gap instead of drifting apart.
	 */
	private void rung(GuiGraphicsExtractor graphics, int centerX, int y, int inner, int outer,
			int color, boolean dashed) {
		if (!dashed) {
			graphics.fill(centerX + inner, y, centerX + outer, y + 1, color);
			graphics.fill(centerX - outer, y, centerX - inner, y + 1, color);
			return;
		}

		for (int d = inner; d < outer; d += DASH + DASH_GAP) {
			int end = Math.min(d + DASH, outer);
			graphics.fill(centerX + d, y, centerX + end, y + 1, color);
			graphics.fill(centerX - end, y, centerX - d, y + 1, color);
		}
	}

	/**
	 * The flight path marker: where the player is going, as against the crosshair's where
	 * they are looking. The vertical gap between the two is the angle of attack, and the
	 * horizontal gap is sideslip.
	 *
	 * <p>Projected against the camera's own basis rather than from pitch and yaw, which
	 * makes it exact on both axes and correct in every camera mode.
	 */
	private void drawFlightPath(GuiGraphicsExtractor graphics, Camera camera,
			int centerX, int centerY, double scale, int bandUp, int bandDown) {
		Vec3 velocity = recorder.smoothedVelocity(VarioConfig.flightPathWindow);
		double speed = velocity.length();

		if (speed < MIN_SPEED) {
			return;
		}

		Vec3 direction = velocity.scale(1.0 / speed);
		Vector3fc forward = camera.forwardVector();
		Vector3fc up = camera.upVector();
		Vector3fc left = camera.leftVector();

		double depth = dot(direction, forward);

		// Travelling behind the camera: there is no forward projection of the marker, and
		// pinning it to an edge would only say something false about which edge.
		if (depth < MIN_DEPTH) {
			return;
		}

		double offsetY = dot(direction, up) / depth * scale;
		double offsetX = -dot(direction, left) / depth * scale;

		// Vertically the marker is held inside the ladder, so it always has rungs to be read
		// against. Horizontally there is no ladder to stay within, only the screen.
		int reach = Math.max(0, centerX - MARKER_MARGIN);

		// Pegged at an edge the marker is a limit rather than a reading, so it is demoted to
		// grey: still there to say which way the flight path went, no longer claiming where.
		boolean pegged = Math.abs(offsetX) > reach || offsetY > bandUp || offsetY < -bandDown;
		int color = pegged ? VarioConfig.flightPathPeggedColor : VarioConfig.cursorForwardColor;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();

		int y = subpixel(pose, centerY - Mth.clamp(offsetY, -bandDown, bandUp));
		double exactX = centerX + Mth.clamp(offsetX, -reach, reach);
		int x = (int) Math.floor(exactX);
		pose.translate((float) (exactX - x), 0.0f);

		graphics.outline(x - 3, y - 3, 7, 7, color);
		graphics.fill(x - 10, y, x - 4, y + 1, color);
		graphics.fill(x + 5, y, x + 11, y + 1, color);
		graphics.fill(x, y - 8, x + 1, y - 3, color);

		pose.popMatrix();
	}

	/** Scales a colour's alpha, leaving its RGB alone. */
	private static int fade(int color, double factor) {
		int alpha = (int) Math.round(((color >>> 24) & 0xFF) * Mth.clamp(factor, 0.0, 1.0));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static double dot(Vec3 a, Vector3fc b) {
		return a.x * b.x() + a.y * b.y() + a.z * b.z();
	}
}

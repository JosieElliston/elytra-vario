package jealoustone.elytravario.hud;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.flight.FlightRecorder;
import jealoustone.elytravario.flight.OptimalPitch;
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
 * A pitch ladder drawn over the world view, with a set of bugs showing where each of the pump
 * cycle's rules says to point, and a flight path marker showing where the player is actually
 * going.
 *
 * <p>Unlike the readout panel this is a <em>conformal</em> instrument: every mark is placed
 * by projecting a direction through the same camera the world was drawn with, so a rung
 * labeled {@code -20} lies exactly along the ground that is twenty degrees above the
 * horizon. The ladder and the terrain move together, which is what makes it readable at a
 * glance rather than by being studied.
 *
 * <h2>Reading it without looking at it</h2>
 *
 * <p>The rungs are tiered by length and weight so that an angle can be recovered from the
 * pattern alone, in peripheral vision, without resolving any digits: faint stubs every two
 * degrees near the center, short rungs every ten, longer ones every twenty, and the datum
 * lines — the horizon and plus or minus forty — longest and brightest. Every tier is solid;
 * length and strength say everything a dash pattern would have. Labels are deliberately
 * faint, and only the twenties carry one.
 *
 * <p>Nothing distinguishes above the horizon from below it, because the sky, the ground and
 * the labeled datum line already do.
 *
 * <h2>The marks that are not a scale</h2>
 *
 * <p>The rungs say where you are pointing. The bugs say where you should be, and they are the
 * only advisory things the ladder carries, so they are the only things on it that are not
 * gray. They ride in the center gap, the one radius no rung or label ever reaches, which is
 * what lets them be added to a ladder that was deliberately decluttered without taking
 * anything back.
 *
 * <p>Reading one is a single gesture: the gap between the crosshair and the bug is the
 * correction, and when there is none the two wedges close around the crosshair.
 *
 * <p>There are four of them, because an optimised pump cycle turns out to be piecewise
 * myopic — each phase of it follows a simple rule of the state, and the hard part is knowing
 * when to switch rules rather than what each rule is. Three of the bugs are those rules, and
 * the fourth is the reference the dive's rule is read against:
 *
 * <ul>
 * <li>the <b>hold</b> bug, the pitch that leaves the flight path angle where it is, which is
 *     the dive;</li>
 * <li>the <b>lookahead</b> bug, the constant pitch that gains the most energy over the next
 *     twenty ticks, which is the climb;</li>
 * <li>the <b>optimal pitch</b> bug, the same over one tick, which is the greedy reading the
 *     ladder shipped with — <em>off by default</em>, since it is a diagnostic rather than a
 *     rule and is wrong through both of the phases above;</li>
 * <li>the <b>velocity</b> bug, where you are actually going, which is not advice and so is
 *     the one of the four that is gray — also <em>off by default</em>.</li>
 * </ul>
 *
 * <p>They share one band, since the center gap is the only place any of them can go, and are
 * told apart by color and by height — ranked by how much each rule is actually flown, so that
 * a pile of agreeing bugs nests into chevrons rather than merging into one mark. The ranking
 * still reads when every color has been spent: pegged at the edge of the band they all take
 * the same gray, and only the heights are left.
 *
 * <p>Nothing here tells you which rule the phase you are in calls for. That switch is the
 * open part of the problem, and a display that guessed at it would be inventing the answer
 * rather than showing the evidence.
 *
 * <h2>The projection</h2>
 *
 * <p>Minecraft's perspective matrix is built from a vertical field of view and the viewport
 * height, so the pixel scale is the same on both screen axes and no aspect ratio is needed:
 * a direction {@code t} units of tangent off the camera axis lands {@code t * scale} pixels
 * away from the center, where {@code scale = halfHeight / tan(fov / 2)}. The GUI's
 * orthographic projection covers the whole framebuffer, so the half-height may be taken in
 * scaled GUI pixels and the center of the GUI is the center of the view.
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
 * center of the screen. Drawing straight horizontal rungs symmetric about the center is
 * therefore exact in the middle and bows away from the truth towards the ends, by an amount
 * that grows with how far off-center the rung reaches. At the default rung length the error
 * is well under a pixel.
 *
 * <p>The flight path marker has no such problem. It is a single direction rather than a
 * curve, and a point projects to a point, so it is placed exactly on both axes — including
 * horizontally, where its offset from the crosshair is the sideslip that the chart's cyan
 * cursor also measures.
 */
public final class PitchLadderElement implements HudElement {
	/** Gap between a rung's outer end and its label. */
	private static final int LABEL_GAP = 4;

	/** Half the font's line height, to center a label on the rung it belongs to. */
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

		drawBugs(graphics, sample, cameraPitch, centerX, centerY, scale, bandUp, bandDown);

		if (VarioConfig.showFlightPath) {
			drawFlightPath(graphics, camera, centerX, centerY, scale, bandUp, bandDown);
		}
	}

	/**
	 * The four bugs, drawn tallest first.
	 *
	 * <p>The order is the whole trick to keeping them separable. They occupy one band and
	 * their apexes land on the same row whenever the rules agree, so a taller wedge drawn
	 * first keeps its shoulders visible past every shorter one painted over it, and a pile of
	 * agreeing bugs reads as nested chevrons instead of as one mark of indeterminate color.
	 * Reversing this would hide the rules that are flown under the ones that are not, exactly
	 * when they agree, which is when the pile is worth reading as a pile.
	 *
	 * <p>Each is skipped when its rule has nothing to say. The two energy searches return null
	 * whenever the player is not gliding, and the hold returns {@code NaN} both there and at
	 * the states where no pitch holds the flight path angle at all — so the bugs appear with
	 * the wing and leave with it, and the dive's bug also leaves when the dive is past saving.
	 */
	private void drawBugs(GuiGraphicsExtractor graphics, Sample sample, float cameraPitch,
			int centerX, int centerY, double scale, int bandUp, int bandDown) {
		// In descending order of rise, which is what makes an overlap nest. Retuning the rises
		// in VarioConfig means reordering these calls to match; nothing checks it.
		if (VarioConfig.showHoldPitch) {
			drawBug(graphics, cameraPitch, recorder.flightPathHold(VarioConfig.flightPathWindow),
					VarioConfig.ladderHoldRise, VarioConfig.holdPitchColor,
					centerX, centerY, scale, bandUp, bandDown);
		}

		if (VarioConfig.showLookaheadPitch) {
			OptimalPitch lookahead = recorder.optimalPitch(VarioConfig.lookaheadTicks);

			if (lookahead != null) {
				drawBug(graphics, cameraPitch, lookahead.pitch(), VarioConfig.ladderLookaheadRise,
						VarioConfig.lookaheadPitchColor, centerX, centerY, scale, bandUp, bandDown);
			}
		}

		if (VarioConfig.showOptimalPitch) {
			OptimalPitch optimal = recorder.optimalPitch();

			if (optimal != null) {
				drawBug(graphics, cameraPitch, optimal.pitch(), VarioConfig.ladderBugRise,
						VarioConfig.optimalPitchColor, centerX, centerY, scale, bandUp, bandDown);
			}
		}

		// Gated on gliding like the other three, even though a direction of travel exists
		// without a wing: it is here to be read against the hold bug, and on its own it is
		// what the flight path marker already says better.
		if (VarioConfig.showVelocityPitch && sample.gliding()) {
			drawBug(graphics, cameraPitch,
					(float) recorder.flightPathPitch(VarioConfig.flightPathWindow),
					VarioConfig.ladderVelocityRise, VarioConfig.velocityPitchColor,
					centerX, centerY, scale, bandUp, bandDown);
		}
	}

	/**
	 * One bug: a mirrored pair of wedges marking a pitch, {@code rise} pixels tall at the
	 * base and tapering to an apex on the row that is the reading. A rise of zero is a single
	 * row rather than a wedge.
	 *
	 * <p>It is placed by the same projection as the rungs, so it lies against the world like
	 * they do, and while it is on the ladder it carries the same edge fade they do and
	 * nothing else. Strength deliberately does <em>not</em> track how much the correction is
	 * worth: dimming it when the margin is small would hide it exactly while it is being
	 * followed, and dimming it when the margin is large would hide it exactly when there is
	 * a long way to go.
	 *
	 * <p>The wedges live inside the center gap and point inwards, which is the only radius
	 * that never meets a rung or a label; see {@code VarioConfig.ladderBugGap}. Drawn as a
	 * stack of rows rather than as a polygon, since the HUD's primitives are rectangles.
	 *
	 * <p>A {@code NaN} pitch draws nothing. That is a real answer from two of the four rules
	 * rather than a defensive check — it is how they say the state they are describing has no
	 * such pitch — so it is tested for here and not left to fall out of the arithmetic.
	 *
	 * <p><b>It pegs at the edge of the band rather than leaving.</b> A whole regime of flight
	 * has its answer off the bottom of the ladder — in a slow descent the best pitch is
	 * eighty-something degrees nose-down, far below anything the band reaches. Held at the
	 * limit it takes the flight path marker's pegged gray, which already means the same thing
	 * there: a direction to go, not a place to be.
	 *
	 * <p>The peg is a cheap courtesy rather than a necessity, and the code should not be read
	 * as claiming otherwise. Pitch clamps at ±90, so the stops need no aiming and a cue there
	 * can only name a direction the situation already implies. The bug does its real work at
	 * interior angles, where it is a target and there is no other. This is kept because it is
	 * unobtrusive, not because anything depends on it.
	 */
	private void drawBug(GuiGraphicsExtractor graphics, float cameraPitch, float pitch,
			int riseSetting, int bugColor, int centerX, int centerY, double scale, int bandUp,
			int bandDown) {
		if (Float.isNaN(pitch)) {
			return;
		}

		double elevation = cameraPitch - pitch;
		double offset;
		boolean pegged;

		// Past a quarter turn the tangent has wrapped and would place the mark on the wrong
		// side, so the direction is taken from the elevation's sign rather than from it.
		if (elevation >= MAX_ELEVATION) {
			offset = bandUp;
			pegged = true;
		} else if (elevation <= -MAX_ELEVATION) {
			offset = -bandDown;
			pegged = true;
		} else {
			offset = Math.tan(Math.toRadians(elevation)) * scale;
			pegged = offset > bandUp || offset < -bandDown;
			offset = Mth.clamp(offset, -bandDown, bandUp);
		}

		// Pegged it is a limit rather than a reading, so it is drawn at full strength: the
		// band taper exists to let marks leave gracefully, and this one is not leaving.
		double edge = pegged ? 1.0 : edgeFade(offset, bandUp, bandDown);

		if (edge <= 0.0) {
			return;
		}

		int base = Math.max(1, VarioConfig.ladderCenterGap - VarioConfig.ladderBugGap);
		int apex = Math.max(0, base - VarioConfig.ladderBugLength);
		int rise = Math.max(0, riseSetting);
		int color = fade(pegged ? VarioConfig.flightPathPeggedColor : bugColor, edge);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		int y = subpixel(pose, centerY - offset);

		for (int row = -rise; row <= rise; row++) {
			// The taper: the wedge's inner edge retreats towards the base as the row moves
			// away from the marked pitch, leaving the apex on the row that is the reading.
			int inner = rise == 0 ? apex
					: apex + (int) Math.round((base - apex) * (double) Math.abs(row) / rise);

			if (inner >= base) {
				continue;
			}

			graphics.fill(centerX - base, y + row, centerX - inner, y + row + 1, color);
			graphics.fill(centerX + inner, y + row, centerX + base, y + row + 1, color);
		}

		pose.popMatrix();
	}

	/**
	 * The tiered rungs, labeled in raw Minecraft pitch so that they agree in sign with the
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

			rung(graphics, centerX, y, inner, outer, fade(color, edge));

			// Only the twenties are labeled. The rungs between them are unambiguous from
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
					fade(VarioConfig.ladderFineColor, near * edge));

			pose.popMatrix();
		}
	}

	/**
	 * Where a mark at {@code pitch} lands relative to the center of the view, in pixels,
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
	 * How strongly to draw a mark at {@code offset} pixels above center: full inside the
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
	 * One rung, drawn as a mirrored pair about the center of the screen.
	 *
	 * <p>Every tier is solid. Length and strength already separate them, and a dash pattern
	 * on top of that was a third channel saying what the first two had said.
	 */
	private void rung(GuiGraphicsExtractor graphics, int centerX, int y, int inner, int outer,
			int color) {
		graphics.fill(centerX + inner, y, centerX + outer, y + 1, color);
		graphics.fill(centerX - outer, y, centerX - inner, y + 1, color);
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
		// gray: still there to say which way the flight path went, no longer claiming where.
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

	/** Scales a color's alpha, leaving its RGB alone. */
	private static int fade(int color, double factor) {
		int alpha = (int) Math.round(((color >>> 24) & 0xFF) * Mth.clamp(factor, 0.0, 1.0));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static double dot(Vec3 a, Vector3fc b) {
		return a.x * b.x() + a.y * b.y() + a.z * b.z();
	}
}

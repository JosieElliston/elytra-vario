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

import org.joml.Vector3fc;

/**
 * A pitch ladder drawn over the world view, with a flight path marker showing where the
 * player is actually going.
 *
 * <p>Unlike the readout panel this is a <em>conformal</em> instrument: every mark is placed
 * by projecting a direction through the same camera the world was drawn with, so a rung
 * labelled {@code -10} lies exactly along the ground that is ten degrees above the horizon.
 * That is what makes it readable at a glance — the ladder and the terrain move together.
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
	/** Dash geometry for the below-horizon rungs, in pixels. */
	private static final int DASH = 5;
	private static final int DASH_GAP = 4;

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
		int band = (int) Math.round(halfHeight * VarioConfig.ladderBandFraction);

		drawLadder(graphics, minecraft.font, camera.xRot(), centerX, centerY, scale, band);

		if (VarioConfig.showFlightPath) {
			drawFlightPath(graphics, camera, centerX, centerY, scale, band);
		}
	}

	/**
	 * Rungs every {@link VarioConfig#ladderStepDegrees} degrees, labelled in raw Minecraft
	 * pitch so that they agree in sign with the panel's {@code PITCH} row, with F3, and with
	 * elytrasim: <em>negative is above the horizon</em>. Since that reads backwards for a
	 * spatial instrument, direction is carried by the drawing instead — rungs above the
	 * horizon are solid and those below it are dashed, as on a real ladder.
	 */
	private void drawLadder(GuiGraphicsExtractor graphics, Font font, float cameraPitch,
			int centerX, int centerY, double scale, int band) {
		int step = Math.max(1, VarioConfig.ladderStepDegrees);

		for (int pitch = -90; pitch <= 90; pitch += step) {
			double elevation = cameraPitch - pitch;

			// A rung a quarter turn off the camera axis is edge-on, and beyond that it is
			// behind the viewer; the tangent would place both on screen as ghosts.
			if (Math.abs(elevation) >= 89.5) {
				continue;
			}

			double offset = Math.tan(Math.toRadians(elevation)) * scale;

			if (Math.abs(offset) > band) {
				continue;
			}

			int y = centerY - (int) Math.round(offset);
			boolean horizon = pitch == 0;
			int outer = VarioConfig.ladderCenterGap
					+ (horizon ? VarioConfig.ladderHorizonLength : VarioConfig.ladderRungLength);

			rung(graphics, centerX, y, VarioConfig.ladderCenterGap, outer,
					horizon ? VarioConfig.horizonColor : VarioConfig.rungColor, pitch > 0);

			String label = Integer.toString(pitch);
			int labelY = y - LABEL_RISE;

			graphics.text(font, label,
					centerX - outer - LABEL_GAP - font.width(label), labelY,
					VarioConfig.ladderLabelColor, true);
			graphics.text(font, label,
					centerX + outer + LABEL_GAP, labelY,
					VarioConfig.ladderLabelColor, true);
		}
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
			int centerX, int centerY, double scale, int band) {
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

		int x = centerX + (int) Math.round(Mth.clamp(offsetX, -reach, reach));
		int y = centerY - (int) Math.round(Mth.clamp(offsetY, -band, band));

		// Pegged at an edge the marker is a limit rather than a reading, so it is demoted to
		// grey: still there to say which way the flight path went, no longer claiming where.
		boolean pegged = Math.abs(offsetX) > reach || Math.abs(offsetY) > band;
		int color = pegged ? VarioConfig.flightPathPeggedColor : VarioConfig.cursorForwardColor;

		graphics.outline(x - 3, y - 3, 7, 7, color);
		graphics.fill(x - 10, y, x - 4, y + 1, color);
		graphics.fill(x + 5, y, x + 11, y + 1, color);
		graphics.fill(x, y - 8, x + 1, y - 3, color);
	}

	private static double dot(Vec3 a, Vector3fc b) {
		return a.x * b.x() + a.y * b.y() + a.z * b.z();
	}
}

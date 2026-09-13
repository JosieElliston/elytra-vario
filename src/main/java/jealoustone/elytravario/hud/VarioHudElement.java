package jealoustone.elytravario.hud;

import java.util.Locale;

import static jealoustone.elytravario.hud.HudChrome.AXIS;
import static jealoustone.elytravario.hud.HudChrome.BORDER;
import static jealoustone.elytravario.hud.HudChrome.GRID;
import static jealoustone.elytravario.hud.HudChrome.LABEL;
import static jealoustone.elytravario.hud.HudChrome.MUTED;
import static jealoustone.elytravario.hud.HudChrome.PANEL_BG;
import static jealoustone.elytravario.hud.HudChrome.VALUE;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.flight.EnergyField;
import jealoustone.elytravario.flight.FlightRecorder;
import jealoustone.elytravario.flight.Sample;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2fStack;

/**
 * Draws the four readout panels and the velocity-space chart.
 *
 * <p>Which rows each panel carries, how wide and how tall it is, and whether it is drawn at all
 * are {@link StatsPanel}'s; this draws them.
 *
 * <p>In 26.2 the HUD is built by extracting a render state rather than by issuing draw calls
 * directly, hence {@code extractRenderState} rather than a {@code render} method — but the
 * available primitives (text, fill, scissor) are the same ones the old context had.
 *
 * <p>Speeds are stored in blocks/tick, the units vanilla physics uses, and converted to
 * blocks/second only for display.
 */
public final class VarioHudElement implements HudElement {
	/** Ticks per second, the factor between internal blocks/tick and displayed blocks/second. */
	private static final double TPS = 20.0;

	private static final int LINE = StatsPanel.LINE;
	private static final int PAD = StatsPanel.PAD;

	private static final double ACCELERATION_ARROW_SECONDS = 1.0;

	/**
	 * Column templates for the rows that carry two figures. Each figure is right-aligned
	 * inside a column reserved from these, so gaining or losing a digit cannot shove the
	 * figure beside it sideways — and since every value has a fixed number of decimals and a
	 * fixed suffix, right-alignment also pins the decimal point. The only motion left is a
	 * leading digit appearing, which is the least a changing number can do.
	 *
	 * <p>Measured through the font rather than written as pixel counts, so they stay correct
	 * if the font ever changes. A value wider than its template is not clipped, it just
	 * encroaches on the column to its left.
	 */
	private static final String DELTA_COLUMN = "-000.0 b";
	private static final String ABSOLUTE_COLUMN = "-0000.0";

	/**
	 * While set, the chart stretches whatever field it last built instead of building one for
	 * the size it is now.
	 *
	 * <p>The settings screen holds this for the length of a drag on the chart's resize grips.
	 * A rebuild costs about 30ms at the default size and grows with the area, and a drag asks
	 * for a new size every pixel it moves, so rebuilding as it went would turn the drag into a
	 * slideshow of exact pictures nobody has time to look at. Only the scale changes under a
	 * drag, never the domain, so the stretched field describes the same velocities as the chart
	 * it is drawn on and is wrong in nothing but resolution. The exact one arrives on release.
	 */
	public static boolean stretchEnergyField;

	private final FlightRecorder recorder;

	public VarioHudElement(FlightRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		// No hide-GUI check needed: Gui.extractRenderState skips the whole Hud pass when the
		// GUI is hidden, so this element is never reached in that case.
		if (minecraft.player == null) {
			return;
		}

		Sample sample = recorder.latest();

		if (sample == null) {
			return;
		}

		boolean chart = VarioInstrument.CHART.visible(sample.gliding());
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();

		HudPosition position = HudPosition.clamp(VarioConfig.chartX, VarioConfig.chartY,
				chartWidth(), chartHeight(), screenWidth, screenHeight);

		// In declaration order, which is the order the panels stack in by default. Nothing here
		// depends on it: each panel is placed on its own and any two may overlap, in which case
		// the later one wins, exactly as the module editor's hit test says it will.
		for (StatsPanel panel : StatsPanel.values()) {
			if (!panel.visible(sample.gliding())) continue;
			int boxWidth = panel.width();
			int boxHeight = panel.height();
			HudPosition at = HudPosition.clamp(panel.x(), panel.y(),
					boxWidth, boxHeight, screenWidth, screenHeight);
			graphics.pose().pushMatrix();
			graphics.pose().translate(at.x(), at.y());
			// Each dimension is scaled onto exactly the box the settings name rather than by one
			// shared factor. The two factors differ only by the pixel the layout width was
			// rounded to — under half a percent apart even at the narrowest a panel goes — so
			// this is a rounding remainder rather than a stretch, and it keeps the drawn border
			// on the same box the position editor puts its grips around.
			graphics.pose().scale((float) boxWidth / panel.layoutWidth(),
					(float) boxHeight / panel.layoutHeight());
			drawPanel(graphics, minecraft.font, panel, sample, 0, 0);
			graphics.pose().popMatrix();
			// The contents scale with the panel, but its frame is always one screen pixel,
			// like the velocity graph and bar speedometer frames. Drawing the outline inside
			// the transform made larger stats panels grow a visibly heavier border.
			if (panel.border()) graphics.outline(at.x(), at.y(), boxWidth, boxHeight, BORDER);
		}
		if (chart) {
			drawChart(graphics, minecraft.font, sample, position.x(), position.y());
		}
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Font font, StatsPanel panel,
			Sample sample, int x, int y) {
		int width = panel.layoutWidth();
		int height = panel.layoutHeight();
		double opacity = panel.opacity();
		int background = ((int) Math.round(opacity * 255) << 24) | (PANEL_BG & 0xFFFFFF);
		if (opacity > 0) graphics.fill(x, y, x + width, y + height, background);

		int row = y + PAD;
		Sample previous = recorder.ago(1);

		// Each arm draws exactly the rows StatsPanel counts for that panel, in the order it
		// names them: a row drawn here and not counted there would run off the bottom of a
		// panel sized for the rows that were counted.
		switch (panel) {
			case OTHER -> {
				double glide = sample.glideRatio();
				if (VarioConfig.showPitch) row = row(graphics, font, panel, x, row,
						"PITCH", fmt("%.1f°", sample.pitch()), VALUE);
				if (VarioConfig.showGlideRatio) row = row(graphics, font, panel, x, row, "GLIDE",
						Double.isFinite(glide) ? fmt("%.2f : 1", glide) : "--", VALUE);
			}
			case SPEED -> {
				// Vertical speed is colored on displayed blocks/second, with a small neutral
				// deadband; the two magnitudes beside it have no sign to color.
				if (VarioConfig.showVerticalSpeed) row = row(graphics, font, panel, x, row,
						"SPEED Y", signedSpeed(sample.vy()), rateColor(sample.vy() * TPS));
				if (VarioConfig.showHorizontalSpeed) row = row(graphics, font, panel, x, row,
						"SPEED XZ", speed(sample.horizontalSpeed()), VALUE);
				if (VarioConfig.showTotalSpeed) row = row(graphics, font, panel, x, row,
						"SPEED XYZ", speed(sample.speed()), VALUE);
			}
			case ACCEL -> {
				if (VarioConfig.showVerticalAcceleration) row = accelerationRow(graphics, font,
						panel, x, row, "ACCEL Y", verticalAcceleration(sample, previous));
				if (VarioConfig.showHorizontalAcceleration) row = accelerationRow(graphics, font,
						panel, x, row, "ACCEL XZ", horizontalAcceleration(sample, previous));
				if (VarioConfig.showTotalAcceleration) row = accelerationRow(graphics, font,
						panel, x, row, "ACCEL XYZ", totalAcceleration(sample, previous));
			}
			case ENERGY -> {
				if (VarioConfig.showKineticEnergy) row = row(graphics, font, panel, x, row,
						"KE", fmt("%.1f b", sample.kineticHeight()), VALUE);
				if (VarioConfig.showPotentialEnergy) row = sinceApexRow(graphics, font, panel,
						x, row, "PE", sample.potentialHeight(), recorder.peakPotentialHeight());
				if (VarioConfig.showTotalEnergy) row = sinceApexRow(graphics, font, panel,
						x, row, "TE", sample.totalHeight(), recorder.peakTotalHeight());
				double gain = recorder.lastCycleGain();
				if (VarioConfig.showCycleGain) row = row(graphics, font, panel, x, row, "GAIN",
						Double.isFinite(gain) ? fmt("%+.1f b", gain) : "--", rateColor(gain));
			}
		}
	}

	private int row(GuiGraphicsExtractor graphics, Font font, StatsPanel panel, int x, int y,
			String label, String value, int color) {
		graphics.text(font, label, x + PAD, y, LABEL, true);
		graphics.text(font, value, x + panel.layoutWidth() - PAD - font.width(value), y, color, true);
		return y + LINE;
	}

	private int accelerationRow(GuiGraphicsExtractor graphics, Font font, StatsPanel panel,
			int x, int y, String label, double acceleration) {
		String value = Double.isFinite(acceleration)
				? fmt("%+.2f b/s²", acceleration) : "--";
		return row(graphics, font, panel, x, y, label, value,
				Double.isFinite(acceleration) ? rateColor(acceleration) : VALUE);
	}

	private static double horizontalAcceleration(Sample sample, Sample previous) {
		if (previous == null) return Double.NaN;
		return (sample.horizontalSpeed() - previous.horizontalSpeed()) * TPS * TPS;
	}

	private static double totalAcceleration(Sample sample, Sample previous) {
		if (previous == null) return Double.NaN;
		return (sample.speed() - previous.speed()) * TPS * TPS;
	}

	private static double verticalAcceleration(Sample sample, Sample previous) {
		return previous == null ? Double.NaN : (sample.vy() - previous.vy()) * TPS * TPS;
	}

	private void drawChart(GuiGraphicsExtractor graphics, Font font, Sample sample, int x, int y) {
		int width = chartWidth();
		int height = chartHeight();

		graphics.fill(x, y, x + width, y + height, PANEL_BG);

		// Before the outline, not after it. The field is opaque and covers the whole interior
		// including the edge pixels the border sits on, so drawing it second would erase the
		// frame. Everything after the border is translucent or a point, and tints it instead.
		if (VarioConfig.showEnergyField && sample.gravity() > 0.0) {
			drawEnergyField(graphics, sample, x, y, width, height);
		}

		graphics.outline(x, y, width, height, BORDER);

		// Gridlines every half block/tick, with the zero axes picked out more brightly.
		// Drawn with fill rather than the line helpers, whose bounds are inclusive on one
		// end and exclusive on the other and so leave the grid a pixel short.
		if (VarioConfig.showGrid) for (double v = Math.ceil(VarioConfig.chartMinVxz * 2.0) / 2.0; v <= VarioConfig.chartMaxVxz; v += 0.5) {
			int px = chartX(x, v);
			graphics.fill(px, y, px + 1, y + height, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		if (VarioConfig.showGrid) for (double v = Math.ceil(VarioConfig.chartMinVy * 2.0) / 2.0; v <= VarioConfig.chartMaxVy; v += 0.5) {
			int py = chartY(y, v);
			graphics.fill(x, py, x + width, py + 1, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		// Trail, oldest first so the newest samples paint over the older ones.
		int trail = VarioConfig.showTrail ? Math.min(recorder.size(), VarioConfig.chartTrailTicks) : 0;

		for (int i = trail - 1; i >= 1; i--) {
			Sample past = recorder.ago(i);

			if (past == null) {
				continue;
			}

			int alpha = 20 + (trail - i) * 190 / trail;
			int px = chartX(x, past.horizontalSpeed());
			int py = chartY(y, past.vy());
			graphics.fill(px, py, px + 1, py + 1, ((alpha * (VarioConfig.trailColor >>> 24) / 255) << 24) | (VarioConfig.trailColor & 0xFFFFFF));
		}

		// Acceleration is the cursor's tick-to-tick movement in velocity space. Extending that
		// rate for one second makes it legible on axes displayed in blocks/second. Arrows go
		// down first so the cursor remains the exact current point when they overlap.
		Sample previous = recorder.ago(1);
		if (previous != null) {
			double verticalChange = sample.vy() - previous.vy();
			if (VarioConfig.showForwardAccelerationArrow) {
				drawAccelerationArrow(graphics, x, y, sample.forwardSpeed(), sample.vy(),
						sample.forwardSpeed() - previous.forwardSpeed(), verticalChange,
						VarioConfig.forwardAccelerationArrowColor);
			}
			if (VarioConfig.showHorizontalAccelerationArrow) {
				drawAccelerationArrow(graphics, x, y, sample.horizontalSpeed(), sample.vy(),
						sample.horizontalSpeed() - previous.horizontalSpeed(), verticalChange,
						VarioConfig.horizontalAccelerationArrowColor);
			}
		}

		// Both cursors share a row, since vertical speed is the same either way; only the
		// horizontal coordinate differs. Whichever is drawn second wins where they overlap,
		// which is most of the time in straight flight, when the two speeds are equal.
		int py = chartY(y, sample.vy());
		if (VarioConfig.showForwardCursor) drawCursor(graphics, chartX(x, sample.forwardSpeed()), py, VarioConfig.cursorForwardColor);
		if (VarioConfig.showHorizontalCursor) drawCursor(graphics, chartX(x, sample.horizontalSpeed()), py, VarioConfig.cursorXzColor);

		if (VarioConfig.showAxisLabels) drawAxisLabels(graphics, font, x, y, width, height);
	}

	/**
	 * Fills the chart with the best energy change available at each velocity it can show; see
	 * {@link EnergyField} for what that means and what it costs, and
	 * {@link EnergyFieldTexture} for why it arrives as a texture rather than as rectangles.
	 *
	 * <p>Everything else on the chart is drawn afterwards, so the grid, the trail and both
	 * cursors sit over the field rather than under it. The grid is translucent and picks up
	 * the colour beneath it, which is the point: it is a reference, not a border.
	 */
	private void drawEnergyField(GuiGraphicsExtractor graphics, Sample sample, int x, int y,
			int width, int height) {
		EnergyField field = stretchEnergyField ? EnergyField.cached() : null;
		if (field == null) {
			field = EnergyField.of(width, height, VarioConfig.chartMinVxz,
					VarioConfig.chartMaxVy, chartScale(), sample.gravity());
		}
		EnergyFieldTexture.blit(graphics, field, x, y, width, height);
	}

	/**
	 * A height measured from the last apex rather than from the world's origin, with the raw
	 * figure dimmed to its left.
	 *
	 * <p>Potential and total energy are the two readings whose absolute value says nothing:
	 * both are anchored to sea level, which is an arbitrary datum that changes meaning between
	 * worlds and tells you nothing about the cycle you are flying. Measured from the apex they
	 * become the question actually being asked — how far below the top of the last cycle am I,
	 * and how much of that is recoverable.
	 *
	 * <p>Kinetic energy keeps its own row shape because it does not have this problem: speed
	 * is speed, and its absolute value is already the reading.
	 *
	 * <p>The raw figure stays, dimmed, because it is what matches F3 and a map.
	 */
	private int sinceApexRow(GuiGraphicsExtractor graphics, Font font, StatsPanel panel,
			int x, int y, String label, double current, double peak) {
		if (VarioConfig.energyReference == 0) {
			return row(graphics, font, panel, x, y, label, fmt("%.1f b", current), VALUE);
		}
		if (VarioConfig.energyReference == 1) {
			return row(graphics, font, panel, x, y, label,
					Double.isFinite(peak) ? fmt("%+.1f b", current - peak) : "--",
					Double.isFinite(peak) ? rateColor(current - peak) : VALUE);
		}
		graphics.text(font, label, x + PAD, y, LABEL, true);

		boolean known = Double.isFinite(peak);
		double change = current - peak;
		String delta = known ? fmt("%+.1f b", change) : "--";
		String absolute = fmt("%.1f", current);

		int deltaRight = x + panel.layoutWidth() - PAD;
		int absoluteRight = deltaRight - font.width(DELTA_COLUMN) - PAD;

		// Coloured like the rate readouts, and for the same reason: below the last apex is
		// energy still owed, above it is a cycle that has already paid for itself. The
		// deadband is the one rateColor applies, so a reading sitting on the apex is white
		// rather than flickering between the two.
		graphics.text(font, delta, deltaRight - font.width(delta), y,
				known ? rateColor(change) : VALUE, true);
		graphics.text(font, absolute, absoluteRight - font.width(absolute), y, MUTED, true);

		return y + LINE;
	}

	private void drawCursor(GuiGraphicsExtractor graphics, int px, int py, int color) {
		graphics.fill(px - 2, py, px + 3, py + 1, color);
		graphics.fill(px, py - 2, px + 1, py + 3, color);
	}

	private static void drawAccelerationArrow(GuiGraphicsExtractor graphics, int chartOriginX,
			int chartOriginY, double vx, double vy, double deltaVx, double deltaVy, int color) {
		int startX = chartX(chartOriginX, vx);
		int startY = chartY(chartOriginY, vy);
		double secondsInTicks = ACCELERATION_ARROW_SECONDS * TPS;
		double dx = deltaVx * secondsInTicks * chartScale();
		double dy = -deltaVy * secondsInTicks * chartScale();
		double length = Math.hypot(dx, dy);
		if (length < 1.0) return;

		double fraction = clippedFraction(startX, startY, dx, dy, chartOriginX,
				chartOriginY, chartOriginX + chartWidth() - 1, chartOriginY + chartHeight() - 1);
		int endX = (int) Math.round(startX + dx * fraction);
		int endY = (int) Math.round(startY + dy * fraction);
		drawLine(graphics, startX, startY, endX, endY, color);

		double ux = dx / length;
		double uy = dy / length;
		double head = Math.min(3.0, Math.hypot(endX - startX, endY - startY));
		drawLine(graphics, endX, endY,
				Math.clamp((int) Math.round(endX - ux * head - uy * head * 0.5),
						chartOriginX, chartOriginX + chartWidth() - 1),
				Math.clamp((int) Math.round(endY - uy * head + ux * head * 0.5),
						chartOriginY, chartOriginY + chartHeight() - 1), color);
		drawLine(graphics, endX, endY,
				Math.clamp((int) Math.round(endX - ux * head + uy * head * 0.5),
						chartOriginX, chartOriginX + chartWidth() - 1),
				Math.clamp((int) Math.round(endY - uy * head - ux * head * 0.5),
						chartOriginY, chartOriginY + chartHeight() - 1), color);
	}

	/** Clips a segment starting inside a rectangle and returns the visible fraction of it. */
	static double clippedFraction(double x, double y, double dx, double dy,
			double minX, double minY, double maxX, double maxY) {
		double fraction = 1.0;
		if (dx > 0.0) fraction = Math.min(fraction, (maxX - x) / dx);
		if (dx < 0.0) fraction = Math.min(fraction, (minX - x) / dx);
		if (dy > 0.0) fraction = Math.min(fraction, (maxY - y) / dy);
		if (dy < 0.0) fraction = Math.min(fraction, (minY - y) / dy);
		return Math.max(0.0, fraction);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
			int color) {
		int dx = Math.abs(x1 - x0);
		int sx = x0 < x1 ? 1 : -1;
		int dy = -Math.abs(y1 - y0);
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
			if (x0 == x1 && y0 == y1) return;
			int twiceError = error * 2;
			if (twiceError >= dy) {
				error += dy;
				x0 += sx;
			}
			if (twiceError <= dx) {
				error += dx;
				y0 += sy;
			}
		}
	}

	/** Axis extremes in blocks/second, at half scale so they do not swamp the chart. */
	private void drawAxisLabels(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);

		String maxVxz = fmt("%.0f", VarioConfig.chartMaxVxz * TPS);
		graphics.text(font, maxVxz, (x + width) * 2 - font.width(maxVxz) - 4, (y + height) * 2 - 12, MUTED, false);
		graphics.text(font, fmt("%+.0f", VarioConfig.chartMaxVy * TPS), x * 2 + 4, y * 2 + 4, MUTED, false);
		graphics.text(font, fmt("%+.0f", VarioConfig.chartMinVy * TPS), x * 2 + 4, (y + height) * 2 - 24, MUTED, false);

		String minVxz = fmt("%.0f", VarioConfig.chartMinVxz * TPS);
		graphics.text(font, minVxz, x * 2 + 4, (y + height) * 2 - 12, MUTED, false);

		// Label zero only when it is inside the domain and clear of the endpoint labels.
		String origin = "0";
		int zero = chartX(x, 0.0) * 2;
		if (VarioConfig.chartMinVxz < 0 && VarioConfig.chartMaxVxz > 0
				&& zero > x * 2 + font.width(minVxz) + 12
				&& zero < (x + width) * 2 - font.width(maxVxz) - 12) {
			graphics.text(font, origin, zero - font.width(origin) / 2, (y + height) * 2 - 12, MUTED, false);
		}

		pose.popMatrix();
	}

	/**
	 * Both dimensions come from one pixels-per-block/tick factor, so a pixel is worth the
	 * same change in speed horizontally and vertically whatever the domain is.
	 */
	public static int chartWidth() {
		return VarioConfig.chartSize;
	}

	public static int chartHeight() {
		return (int) Math.round((VarioConfig.chartMaxVy - VarioConfig.chartMinVy) * chartScale());
	}

	private static double chartScale() {
		return VarioConfig.chartSize / (VarioConfig.chartMaxVxz - VarioConfig.chartMinVxz);
	}

	private static int chartX(int originX, double vxz) {
		double px = (vxz - VarioConfig.chartMinVxz) * chartScale();
		return originX + (int) Math.round(Mth.clamp(px, 0.0, chartWidth() - 1.0));
	}

	private static int chartY(int originY, double vy) {
		double py = (VarioConfig.chartMaxVy - vy) * chartScale();
		return originY + (int) Math.round(Mth.clamp(py, 0.0, chartHeight() - 1.0));
	}

	private static int rateColor(double rate) {
		if (rate > 0.05) {
			return VarioConfig.positiveColor;
		}

		return rate < -0.05 ? VarioConfig.negativeColor : VALUE;
	}

	/** Always formats with {@link Locale#ROOT}, so decimal separators do not follow the system locale. */
	private static String fmt(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}

	private static String speed(double blocksPerTick) {
		return fmt("%.2f b/s", blocksPerTick * TPS);
	}

	private static String signedSpeed(double blocksPerTick) {
		return fmt("%+.2f b/s", blocksPerTick * TPS);
	}
}

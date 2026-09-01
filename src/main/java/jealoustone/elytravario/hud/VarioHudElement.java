package jealoustone.elytravario.hud;

import java.util.Locale;

import jealoustone.elytravario.VarioConfig;
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
 * Draws the readout panel and the velocity-space chart.
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

	private static final int PANEL_BG = 0xB0101014;
	private static final int BORDER = 0xFF3A3F45;
	private static final int LABEL = 0xFF9AA0A6;
	private static final int VALUE = 0xFFFFFFFF;
	private static final int MUTED = 0xFF6A7076;
	private static final int RISING = 0xFF66DD77;
	private static final int SINKING = 0xFFE2685F;

	private static final int GRID = 0x26FFFFFF;
	private static final int AXIS = 0x66FFFFFF;
	private static final int TRAIL = 0x33CCAA;

	private static final int LINE = 10;
	private static final int PAD = 4;

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

		if (sample == null || (VarioConfig.onlyWhileGliding && !sample.gliding())) {
			return;
		}

		int bottom = drawPanel(graphics, minecraft.font, sample, VarioConfig.originX, VarioConfig.originY);

		if (VarioConfig.showChart) {
			drawChart(graphics, minecraft.font, sample, VarioConfig.originX, bottom + PAD);
		}
	}

	/** Returns the y coordinate just past the bottom of the panel. */
	private int drawPanel(GuiGraphicsExtractor graphics, Font font, Sample sample, int x, int y) {
		int width = VarioConfig.panelWidth;
		// Ten readouts plus the separator line between the speed and energy groups.
		int height = 11 * LINE + PAD * 2;

		graphics.fill(x, y, x + width, y + height, PANEL_BG);
		graphics.outline(x, y, width, height, BORDER);

		int row = y + PAD;
		double energyRate = recorder.energyRate(VarioConfig.varioWindow);
		double glide = sample.glideRatio();

		row = row(graphics, font, x, row, "PITCH", fmt("%.1f°", sample.pitch()), VALUE);
		row = row(graphics, font, x, row, "SPEED XZ", speed(sample.horizontalSpeed()), VALUE);
		row = row(graphics, font, x, row, "SPEED XYZ", speed(sample.speed()), VALUE);
		// Coloured on the displayed blocks/second value, so the deadband matches TE RATE's.
		row = row(graphics, font, x, row, "SPEED Y", signedSpeed(sample.vy()), rateColor(sample.vy() * TPS));
		row = row(graphics, font, x, row, "GLIDE",
				Double.isFinite(glide) ? fmt("%.2f : 1", glide) : "--", VALUE);

		graphics.fill(x + PAD, row + LINE / 2 - 1, x + width - PAD, row + LINE / 2, BORDER);
		row += LINE;

		row = peakRow(graphics, font, x, row, "KE", sample.kineticHeight(), recorder.peakKineticHeight());
		row = peakRow(graphics, font, x, row, "PE", sample.potentialHeight(), recorder.peakPotentialHeight());
		row = peakRow(graphics, font, x, row, "TE", sample.totalHeight(), recorder.peakTotalHeight());
		row = row(graphics, font, x, row, "TE RATE", fmt("%+.2f b/s", energyRate), rateColor(energyRate));

		double gain = recorder.lastCycleGain();
		row = row(graphics, font, x, row, "GAIN",
				Double.isFinite(gain) ? fmt("%+.1f b", gain) : "--", rateColor(gain));

		return y + height;
	}

	private int row(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, String value, int color) {
		graphics.text(font, label, x + PAD, y, LABEL, true);
		graphics.text(font, value, x + VarioConfig.panelWidth - PAD - font.width(value), y, color, true);
		return y + LINE;
	}

	private void drawChart(GuiGraphicsExtractor graphics, Font font, Sample sample, int x, int y) {
		int width = chartWidth();
		int height = chartHeight();

		graphics.fill(x, y, x + width, y + height, PANEL_BG);
		graphics.outline(x, y, width, height, BORDER);

		// Gridlines every half block/tick, with the zero axes picked out more brightly.
		// Drawn with fill rather than the line helpers, whose bounds are inclusive on one
		// end and exclusive on the other and so leave the grid a pixel short.
		for (double v = Math.ceil(VarioConfig.chartMinVxz * 2.0) / 2.0; v <= VarioConfig.chartMaxVxz; v += 0.5) {
			int px = chartX(x, v);
			graphics.fill(px, y, px + 1, y + height, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		for (double v = Math.ceil(VarioConfig.chartMinVy * 2.0) / 2.0; v <= VarioConfig.chartMaxVy; v += 0.5) {
			int py = chartY(y, v);
			graphics.fill(x, py, x + width, py + 1, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		// Trail, oldest first so the newest samples paint over the older ones.
		int trail = Math.min(recorder.size(), VarioConfig.chartTrailTicks);

		for (int i = trail - 1; i >= 1; i--) {
			Sample past = recorder.ago(i);

			if (past == null) {
				continue;
			}

			int alpha = 20 + (int) ((1.0f - (float) i / trail) * 190.0f);
			int px = chartX(x, past.horizontalSpeed());
			int py = chartY(y, past.vy());
			graphics.fill(px, py, px + 1, py + 1, (alpha << 24) | TRAIL);
		}

		// Both cursors share a row, since vertical speed is the same either way; only the
		// horizontal coordinate differs. Whichever is drawn second wins where they overlap,
		// which is most of the time in straight flight, when the two speeds are equal.
		int py = chartY(y, sample.vy());
		drawCursor(graphics, chartX(x, sample.forwardSpeed()), py, VarioConfig.cursorForwardColor);
		drawCursor(graphics, chartX(x, sample.horizontalSpeed()), py, VarioConfig.cursorXzColor);

		drawAxisLabels(graphics, font, x, y, width, height);
	}

	/** A reading with the value held from the last crest dimmed alongside it. */
	private int peakRow(GuiGraphicsExtractor graphics, Font font, int x, int y, String label,
			double current, double peak) {
		graphics.text(font, label, x + PAD, y, LABEL, true);

		String peakText = Double.isFinite(peak) ? fmt("(%.1f)", peak) : "";
		String currentText = fmt("%.1f b", current);
		int right = x + VarioConfig.panelWidth - PAD;
		int peakWidth = font.width(peakText);

		graphics.text(font, peakText, right - peakWidth, y, MUTED, true);
		graphics.text(font, currentText, right - peakWidth - PAD - font.width(currentText), y, VALUE, true);

		return y + LINE;
	}

	private void drawCursor(GuiGraphicsExtractor graphics, int px, int py, int color) {
		graphics.fill(px - 2, py, px + 3, py + 1, color);
		graphics.fill(px, py - 2, px + 1, py + 3, color);
	}

	/** Axis extremes in blocks/second, at half scale so they do not swamp the chart. */
	private void drawAxisLabels(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);

		String maxVxz = fmt("%.0f", VarioConfig.chartMaxVxz * TPS);
		graphics.text(font, maxVxz, (x + width) * 2 - font.width(maxVxz) - 4, (y + height) * 2 - 12, MUTED, false);
		graphics.text(font, fmt("%+.0f", VarioConfig.chartMaxVy * TPS), x * 2 + 4, y * 2 + 4, MUTED, false);
		graphics.text(font, fmt("%+.0f", VarioConfig.chartMinVy * TPS), x * 2 + 4, (y + height) * 2 - 12, MUTED, false);

		// The horizontal origin no longer sits on the chart's edge, so name it.
		String origin = "0";
		graphics.text(font, origin, chartX(x, 0.0) * 2 - font.width(origin) / 2, (y + height) * 2 - 12, MUTED, false);

		pose.popMatrix();
	}

	/**
	 * Both dimensions come from one pixels-per-block/tick factor, so a pixel is worth the
	 * same change in speed horizontally and vertically whatever the domain is.
	 */
	private static int chartWidth() {
		return (int) Math.round((VarioConfig.chartMaxVxz - VarioConfig.chartMinVxz) * VarioConfig.chartScale);
	}

	private static int chartHeight() {
		return (int) Math.round((VarioConfig.chartMaxVy - VarioConfig.chartMinVy) * VarioConfig.chartScale);
	}

	private static int chartX(int originX, double vxz) {
		double px = (vxz - VarioConfig.chartMinVxz) * VarioConfig.chartScale;
		return originX + (int) Math.round(Mth.clamp(px, 0.0, chartWidth() - 1.0));
	}

	private static int chartY(int originY, double vy) {
		double py = (VarioConfig.chartMaxVy - vy) * VarioConfig.chartScale;
		return originY + (int) Math.round(Mth.clamp(py, 0.0, chartHeight() - 1.0));
	}

	private static int rateColor(double rate) {
		if (rate > 0.05) {
			return RISING;
		}

		return rate < -0.05 ? SINKING : VALUE;
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

package jealoustone.elytravario.hud;

import java.util.Locale;

import jealoustone.elytravario.VarioConfig;
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

	/** The heatmap's ramp, expanded from the three configured colours; see {@link #palette()}. */
	private static int[] palette;
	private static int paletteZero;
	private static int paletteGain;
	private static int paletteLoss;

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
		// Counted rather than fixed, since the angle of attack row is optional. The extra
		// line is the separator between the speed and energy groups.
		int readouts = VarioConfig.showAngleOfAttack ? 11 : 10;
		int height = (readouts + 1) * LINE + PAD * 2;

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

		if (VarioConfig.showAngleOfAttack) {
			// How far the nose sits above the flight path, which is the vertical gap between
			// the crosshair and the pitch ladder's flight path marker, read as a number.
			double aoa = sample.angleOfAttack();
			row = row(graphics, font, x, row, "AOA",
					Double.isFinite(aoa) ? fmt("%+.1f\u00b0", aoa) : "--", VALUE);
		}

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

	/**
	 * Fills the chart with the best energy change available at each velocity it can show; see
	 * {@link EnergyField} for what that means and why it is affordable.
	 *
	 * <p>Drawn as horizontal runs of equal colour rather than pixel by pixel, which turns
	 * twelve thousand fills a frame into about twenty-six hundred. It is still the most
	 * expensive thing on the HUD by an order of magnitude, and if it ever costs enough to
	 * notice the answer is to upload the field as a texture and blit it once, not to draw less
	 * of it.
	 *
	 * <p>Everything else on the chart is drawn afterwards, so the grid, the trail and both
	 * cursors sit over the field rather than under it. The grid is translucent and picks up
	 * the colour beneath it, which is the point: it is a reference, not a border.
	 */
	private void drawEnergyField(GuiGraphicsExtractor graphics, Sample sample, int x, int y,
			int width, int height) {
		EnergyField field = EnergyField.of(width, height, VarioConfig.chartMinVxz,
				VarioConfig.chartMaxVy, VarioConfig.chartScale, sample.gravity(),
				VarioConfig.chartFieldScale);

		int[] palette = palette();
		int[] runs = field.runs();

		for (int i = 0; i < runs.length; i += 4) {
			int row = runs[i];
			graphics.fill(x + runs[i + 1], y + row, x + runs[i + 2] + 1, y + row + 1,
					palette[runs[i + 3] + EnergyField.LEVELS]);
		}
	}

	/**
	 * The heatmap's colour for each level, rebuilt only when the configured colours change.
	 *
	 * <p>Interpolated in sRGB rather than in linear light. Linear light is the correct way to
	 * mix two lights, but this is not mixing light: it is laying out a scale, and on a ramp
	 * from near-black to a saturated colour the linear version spends most of its length near
	 * the dark end and arrives at the mid-tones desaturated. Plain sRGB keeps the hue and
	 * spaces the steps about as evenly as the eye reads them.
	 */
	private static int[] palette() {
		if (palette != null && paletteZero == VarioConfig.chartFieldZeroColor
				&& paletteGain == VarioConfig.chartFieldGainColor
				&& paletteLoss == VarioConfig.chartFieldLossColor) {
			return palette;
		}

		int[] built = new int[EnergyField.LEVELS * 2 + 1];

		for (int level = -EnergyField.LEVELS; level <= EnergyField.LEVELS; level++) {
			int end = level < 0 ? VarioConfig.chartFieldLossColor : VarioConfig.chartFieldGainColor;
			built[level + EnergyField.LEVELS] = mix(VarioConfig.chartFieldZeroColor, end,
					(float) Math.abs(level) / EnergyField.LEVELS);
		}

		palette = built;
		paletteZero = VarioConfig.chartFieldZeroColor;
		paletteGain = VarioConfig.chartFieldGainColor;
		paletteLoss = VarioConfig.chartFieldLossColor;
		return built;
	}

	/** Channel-wise interpolation between two ARGB colours, alpha included. */
	private static int mix(int from, int to, float t) {
		int argb = 0;

		for (int shift = 0; shift < 32; shift += 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			argb |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
		}

		return argb;
	}

	/** A reading with the value held from the last apex dimmed alongside it. */
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

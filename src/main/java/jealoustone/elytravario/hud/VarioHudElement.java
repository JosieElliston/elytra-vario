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
 * available primitives (text, fill, lines, scissor) are the same ones the old context had.
 */
public final class VarioHudElement implements HudElement {
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
	private static final int CURRENT = 0xFFFFD633;

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
		// Nine readouts plus the separator line between the speed and energy groups.
		int rows = 10;
		int height = rows * LINE + PAD * 2;

		graphics.fill(x, y, x + width, y + height, PANEL_BG);
		graphics.outline(x, y, width, height, BORDER);

		int row = y + PAD;
		double vario = recorder.energyRate(VarioConfig.varioWindow);
		double glide = sample.glideRatio();

		row = row(graphics, font, x, row, "PITCH", fmt("%.1f°", sample.pitch()), VALUE);
		row = row(graphics, font, x, row, "SPD XZ", speed(sample.horizontalSpeed()), VALUE);
		row = row(graphics, font, x, row, "SPD XYZ", speed(sample.speed()), VALUE);
		row = row(graphics, font, x, row, "GLIDE",
				Double.isFinite(glide) ? fmt("%.2f : 1", glide) : "--", VALUE);

		graphics.horizontalLine(x + PAD, x + width - PAD - 1, row + LINE / 2 - 1, BORDER);
		row += LINE;

		row = row(graphics, font, x, row, "KE", fmt("%.1f b", sample.kineticHeight()), VALUE);
		row = row(graphics, font, x, row, "PE", fmt("%.1f b", sample.potentialHeight()), VALUE);
		row = row(graphics, font, x, row, "TE", fmt("%.1f b", sample.totalHeight()), VALUE);
		row = row(graphics, font, x, row, "CLIMB", fmt("%+.2f b/s", sample.vy() * 20.0), rateColor(sample.vy()));
		row = row(graphics, font, x, row, "VARIO", fmt("%+.2f b/s", vario), rateColor(vario));

		if (!sample.gliding()) {
			graphics.text(font, "not gliding", x + PAD, y + height + 2, MUTED, true);
		}

		return y + height + (sample.gliding() ? 0 : LINE);
	}

	private int row(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, String value, int color) {
		graphics.text(font, label, x + PAD, y, LABEL, true);
		graphics.text(font, value, x + VarioConfig.panelWidth - PAD - font.width(value), y, color, true);
		return y + LINE;
	}

	private void drawChart(GuiGraphicsExtractor graphics, Font font, Sample sample, int x, int y) {
		int size = VarioConfig.chartSize;

		graphics.fill(x, y, x + size, y + size, PANEL_BG);
		graphics.outline(x, y, size, size, BORDER);

		// Gridlines every half block/tick, with the zero axes picked out more brightly.
		// Drawn with fill rather than the line helpers, whose bounds are inclusive on one
		// end and exclusive on the other and so leave the grid a pixel short.
		for (double v = Math.ceil(VarioConfig.chartMinVxz * 2.0) / 2.0; v <= VarioConfig.chartMaxVxz; v += 0.5) {
			int px = chartX(x, size, v);
			graphics.fill(px, y, px + 1, y + size, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		for (double v = Math.ceil(VarioConfig.chartMinVy * 2.0) / 2.0; v <= VarioConfig.chartMaxVy; v += 0.5) {
			int py = chartY(y, size, v);
			graphics.fill(x, py, x + size, py + 1, Math.abs(v) < 1.0e-9 ? AXIS : GRID);
		}

		// Trail, oldest first so the newest samples paint over the older ones.
		int trail = Math.min(recorder.size(), VarioConfig.chartTrailTicks);

		for (int i = trail - 1; i >= 1; i--) {
			Sample past = recorder.ago(i);

			if (past == null) {
				continue;
			}

			int alpha = 20 + (int) ((1.0f - (float) i / trail) * 190.0f);
			int px = chartX(x, size, past.horizontalSpeed());
			int py = chartY(y, size, past.vy());
			graphics.fill(px, py, px + 1, py + 1, (alpha << 24) | TRAIL);
		}

		int px = chartX(x, size, sample.horizontalSpeed());
		int py = chartY(y, size, sample.vy());
		graphics.fill(px - 2, py, px + 3, py + 1, CURRENT);
		graphics.fill(px, py - 2, px + 1, py + 3, CURRENT);

		drawAxisLabels(graphics, font, x, y, size);
	}

	/** Axis extremes, drawn at half scale so they do not swamp a 100px chart. */
	private void drawAxisLabels(GuiGraphicsExtractor graphics, Font font, int x, int y, int size) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);

		String maxVxz = fmt("%.1f", VarioConfig.chartMaxVxz);
		graphics.text(font, maxVxz, (x + size) * 2 - font.width(maxVxz) - 4, (y + size) * 2 - 12, MUTED, false);
		graphics.text(font, fmt("%.1f", VarioConfig.chartMaxVy), x * 2 + 4, y * 2 + 4, MUTED, false);
		graphics.text(font, fmt("%.1f", VarioConfig.chartMinVy), x * 2 + 4, (y + size) * 2 - 12, MUTED, false);

		pose.popMatrix();
	}

	private static int chartX(int originX, int size, double vxz) {
		double t = (vxz - VarioConfig.chartMinVxz) / (VarioConfig.chartMaxVxz - VarioConfig.chartMinVxz);
		return originX + (int) Math.round(Mth.clamp(t, 0.0, 1.0) * (size - 1));
	}

	private static int chartY(int originY, int size, double vy) {
		double t = (VarioConfig.chartMaxVy - vy) / (VarioConfig.chartMaxVy - VarioConfig.chartMinVy);
		return originY + (int) Math.round(Mth.clamp(t, 0.0, 1.0) * (size - 1));
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
		return fmt("%.3f b/t  %.1f b/s", blocksPerTick, blocksPerTick * 20.0);
	}
}

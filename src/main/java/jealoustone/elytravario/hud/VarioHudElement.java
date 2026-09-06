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

	private static final int LINE = 10;
	private static final int PAD = 4;

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

		boolean stats = VarioInstrument.STATS.visible(sample.gliding()) && panelRows() > 0;
		boolean chart = VarioInstrument.CHART.visible(sample.gliding());
		boolean attached = chart && HudPosition.attaches(VarioConfig.chartAnchor);
		int panelHeight = (int) Math.ceil(panelHeight() * VarioConfig.panelScale);
		int panelWidth = (int) Math.ceil(VarioConfig.panelWidth * VarioConfig.panelScale);
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();

		HudPosition panel;
		HudPosition position;
		if (attached) {
			// A hidden panel becomes a zero-sized box with no gap, which leaves the chart
			// standing exactly where the pair would have started rather than moving it.
			HudLayout layout = HudLayout.attached(VarioConfig.statsAnchor, VarioConfig.originX,
					VarioConfig.originY, VarioConfig.chartAnchor,
					stats ? panelWidth : 0, stats ? panelHeight : 0, chartWidth(), chartHeight(),
					stats ? PAD : 0, screenWidth, screenHeight);
			panel = layout.panel();
			position = new HudPosition(layout.chart().x() + VarioConfig.chartX,
					layout.chart().y() + VarioConfig.chartY);
		} else {
			panel = HudPosition.resolve(VarioConfig.statsAnchor, VarioConfig.originX,
					VarioConfig.originY, panelWidth, panelHeight, screenWidth, screenHeight);
			position = HudPosition.resolve(VarioConfig.chartAnchor, VarioConfig.chartX,
					VarioConfig.chartY, chartWidth(), chartHeight(), screenWidth, screenHeight);
		}

		if (stats) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(panel.x(), panel.y());
			graphics.pose().scale((float) VarioConfig.panelScale, (float) VarioConfig.panelScale);
			drawPanel(graphics, minecraft.font, sample, 0, 0);
			graphics.pose().popMatrix();
		}
		if (chart) {
			drawChart(graphics, minecraft.font, sample, position.x(), position.y());
		}
	}

	private static int speedRows() {
		return (VarioConfig.showPitch ? 1 : 0) + (VarioConfig.showHorizontalSpeed ? 1 : 0)
				+ (VarioConfig.showTotalSpeed ? 1 : 0) + (VarioConfig.showVerticalSpeed ? 1 : 0)
				+ (VarioConfig.showGlideRatio ? 1 : 0) + (VarioConfig.showAngleOfAttack ? 1 : 0);
	}

	private static int energyRows() {
		return (VarioConfig.showKineticEnergy ? 1 : 0) + (VarioConfig.showPotentialEnergy ? 1 : 0)
				+ (VarioConfig.showTotalEnergy ? 1 : 0) + (VarioConfig.showCycleGain ? 1 : 0);
	}

	private static int panelRows() { return speedRows() + energyRows(); }

	private static int panelHeight() {
		return (panelRows() + (speedRows() > 0 && energyRows() > 0 ? 1 : 0)) * LINE + PAD * 2;
	}

	/** Returns the y coordinate just past the bottom of the panel. */
	private int drawPanel(GuiGraphicsExtractor graphics, Font font, Sample sample, int x, int y) {
		int width = VarioConfig.panelWidth;
		int height = panelHeight();
		int background = ((int) Math.round(VarioConfig.panelOpacity * 255) << 24) | (PANEL_BG & 0xFFFFFF);
		if (VarioConfig.panelOpacity > 0) graphics.fill(x, y, x + width, y + height, background);
		if (VarioConfig.showPanelBorder) graphics.outline(x, y, width, height, BORDER);

		int row = y + PAD;
		double glide = sample.glideRatio();

		if (VarioConfig.showPitch) row = row(graphics, font, x, row, "PITCH", fmt("%.1f°", sample.pitch()), VALUE);
		if (VarioConfig.showHorizontalSpeed) row = row(graphics, font, x, row, "SPEED XZ", speed(sample.horizontalSpeed()), VALUE);
		if (VarioConfig.showTotalSpeed) row = row(graphics, font, x, row, "SPEED XYZ", speed(sample.speed()), VALUE);
		// Color on displayed blocks/second, with a small neutral deadband.
		if (VarioConfig.showVerticalSpeed) row = row(graphics, font, x, row, "SPEED Y", signedSpeed(sample.vy()), rateColor(sample.vy() * TPS));
		if (VarioConfig.showGlideRatio) row = row(graphics, font, x, row, "GLIDE",
				Double.isFinite(glide) ? fmt("%.2f : 1", glide) : "--", VALUE);

		if (VarioConfig.showAngleOfAttack) {
			// How far the nose sits above the flight path, which is the vertical gap between
			// the crosshair and the pitch ladder's flight path marker, read as a number.
			double aoa = sample.angleOfAttack();
			row = row(graphics, font, x, row, "AOA",
					Double.isFinite(aoa) ? fmt("%+.1f\u00b0", aoa) : "--", VALUE);
		}

		if (speedRows() > 0 && energyRows() > 0) {
			graphics.fill(x + PAD, row + LINE / 2 - 1, x + width - PAD, row + LINE / 2, BORDER);
			row += LINE;
		}

		if (VarioConfig.showKineticEnergy) row = row(graphics, font, x, row, "KE", fmt("%.1f b", sample.kineticHeight()), VALUE);
		if (VarioConfig.showPotentialEnergy) row = sinceApexRow(graphics, font, x, row, "PE", sample.potentialHeight(),
				recorder.peakPotentialHeight());
		if (VarioConfig.showTotalEnergy) row = sinceApexRow(graphics, font, x, row, "TE", sample.totalHeight(),
				recorder.peakTotalHeight());

		double gain = recorder.lastCycleGain();
		if (VarioConfig.showCycleGain) row = row(graphics, font, x, row, "GAIN",
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

			int alpha = 20 + (int) ((1.0f - (float) i / trail) * 190.0f);
			int px = chartX(x, past.horizontalSpeed());
			int py = chartY(y, past.vy());
			graphics.fill(px, py, px + 1, py + 1, ((alpha * (VarioConfig.trailColor >>> 24) / 255) << 24) | (VarioConfig.trailColor & 0xFFFFFF));
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
		EnergyFieldTexture.blit(graphics, EnergyField.of(width, height, VarioConfig.chartMinVxz,
				VarioConfig.chartMaxVy, VarioConfig.chartScale, sample.gravity()), x, y);
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
	private int sinceApexRow(GuiGraphicsExtractor graphics, Font font, int x, int y, String label,
			double current, double peak) {
		if (VarioConfig.energyReference == 0) {
			return row(graphics, font, x, y, label, fmt("%.1f b", current), VALUE);
		}
		if (VarioConfig.energyReference == 1) {
			return row(graphics, font, x, y, label,
					Double.isFinite(peak) ? fmt("%+.1f b", current - peak) : "--",
					Double.isFinite(peak) ? rateColor(current - peak) : VALUE);
		}
		graphics.text(font, label, x + PAD, y, LABEL, true);

		boolean known = Double.isFinite(peak);
		double change = current - peak;
		String delta = known ? fmt("%+.1f b", change) : "--";
		String absolute = fmt("%.1f", current);

		int deltaRight = x + VarioConfig.panelWidth - PAD;
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

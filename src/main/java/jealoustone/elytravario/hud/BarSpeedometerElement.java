package jealoustone.elytravario.hud;

import java.util.ArrayList;
import java.util.List;

import static jealoustone.elytravario.hud.HudChrome.BORDER;
import static jealoustone.elytravario.hud.HudChrome.LABEL;
import static jealoustone.elytravario.hud.HudChrome.PANEL_BG;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.flight.FlightRecorder;
import jealoustone.elytravario.flight.Sample;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Three vertical bars for vertical, horizontal, and total speed, with acceleration arrows. */
public final class BarSpeedometerElement implements HudElement {
	private static final double TPS = 20.0;
	private static final double ACCELERATION_ARROW_SECONDS = 1.0;
	private static final int MAJOR = 0xFFC6CCD2;
	private static final int ACCELERATION_ARROW = 0xFFFFFFFF;

	/** One shown bar: what it is called, what it reads, and the speeds it is marked against. */
	private record Bar(String label, double speed, double previousSpeed, int color,
			ReferenceSpeeds reference) { }

	private final FlightRecorder recorder;

	public BarSpeedometerElement(FlightRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		Sample sample = recorder.latest();
		if (sample == null || !VarioInstrument.BAR_SPEEDOMETER.visible(sample.gliding())) return;

		BarSpeedometerChart chart = BarSpeedometerChart.of(minecraft.font);
		HudPosition position = HudPosition.clamp(VarioConfig.barSpeedoX, VarioConfig.barSpeedoY,
				chart.width(), chart.height(), graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, minecraft.font, chart, sample, recorder.ago(1),
				position.x(), position.y());
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, BarSpeedometerChart chart,
			Sample sample, Sample previous, int x, int y) {
		drawFace(graphics, chart, x, y);
		drawScale(graphics, font, chart, x, y);

		List<Bar> bars = bars(sample, previous);
		for (int i = 0; i < bars.size(); i++) {
			drawBar(graphics, font, chart, x, y, i, bars.get(i));
		}

		if (VarioConfig.showBarSpeedoBorder) {
			graphics.outline(x, y, chart.width(), chart.height(), BORDER);
		}
	}

	/**
	 * The bars to draw, in their fixed order, hidden ones left out entirely. Their positions
	 * count off the list rather than the component, so switching one off closes its gap instead
	 * of leaving a hole in the panel.
	 */
	private static List<Bar> bars(Sample sample, Sample previous) {
		List<Bar> bars = new ArrayList<>(BarSpeedometerChart.BAR_COUNT);
		if (VarioConfig.showBarSpeedoVertical) {
			bars.add(new Bar("Y", Math.abs(sample.vy()),
					previous == null ? Double.NaN : Math.abs(previous.vy()),
					VarioConfig.barSpeedoVerticalColor, ReferenceSpeeds.VERTICAL));
		}
		if (VarioConfig.showBarSpeedoHorizontal) {
			bars.add(new Bar("XZ", sample.horizontalSpeed(),
					previous == null ? Double.NaN : previous.horizontalSpeed(),
					VarioConfig.barSpeedoHorizontalColor, ReferenceSpeeds.HORIZONTAL));
		}
		if (VarioConfig.showBarSpeedoTotal) {
			bars.add(new Bar("XYZ", sample.speed(),
					previous == null ? Double.NaN : previous.speed(),
					VarioConfig.barSpeedoTotalColor, ReferenceSpeeds.TOTAL));
		}
		return bars;
	}

	private static void drawFace(GuiGraphicsExtractor graphics, BarSpeedometerChart chart, int x, int y) {
		if (VarioConfig.barSpeedoOpacity <= 0) return;
		int background = ((int) Math.round(VarioConfig.barSpeedoOpacity * 255) << 24)
				| (PANEL_BG & 0xFFFFFF);
		graphics.fill(x, y, x + chart.width(), y + chart.height(), background);
	}

	private static void drawScale(GuiGraphicsExtractor graphics, Font font, BarSpeedometerChart chart,
			int x, int y) {
		for (double speed : chart.steps()) {
			int py = y + chart.speedY(speed);
			graphics.fill(x + chart.plotX() - BarSpeedometerChart.TICK_LENGTH, py,
					x + chart.plotX() + chart.plotWidth(), py + 1, MAJOR);
			if (!chart.scaleLabeled()) continue;
			String text = BarSpeedometerChart.scaleLabel(speed);
			graphics.text(font, text, x + chart.scaleLabelRight() - font.width(text),
					py - chart.textHeight() / 2, LABEL, true);
		}
	}

	private static void drawBar(GuiGraphicsExtractor graphics, Font font, BarSpeedometerChart chart,
			int x, int y, int index, Bar bar) {
		int bx = x + chart.barX(index);
		int top = y + chart.speedY(bar.speed());
		int bottom = y + chart.baselineY();
		int barColor = chart.pegged(bar.speed()) ? VarioConfig.barSpeedoPeggedColor : bar.color();
		if (top < bottom) {
			graphics.fill(bx, top, bx + BarSpeedometerChart.BAR_WIDTH, bottom, barColor);
		}

		if (VarioConfig.showBarSpeedoMaxHorizontalSpeedMarkers) {
			marker(graphics, chart, bx, y, bar.reference().maxHorizontalSpeed(),
					ReferenceSpeeds.MAX_HORIZONTAL_SPEED_COLOR);
		}
		if (VarioConfig.showBarSpeedoTerminalVelocityMarkers) {
			marker(graphics, chart, bx, y, bar.reference().terminal(),
					ReferenceSpeeds.TERMINAL_COLOR);
		}
		if (VarioConfig.showBarSpeedoAcceleration && Double.isFinite(bar.previousSpeed())) {
			accelerationArrow(graphics, chart, bx, y, bar.speed(), bar.previousSpeed());
		}

		int labelX = bx + (BarSpeedometerChart.BAR_WIDTH - font.width(bar.label())) / 2;
		graphics.text(font, bar.label(), labelX, y + chart.categoryY(), LABEL, true);
	}

	/** Projects the tick-to-tick speed change for one second on the speed scale. */
	private static void accelerationArrow(GuiGraphicsExtractor graphics, BarSpeedometerChart chart,
			int barX, int y, double speed, double previousSpeed) {
		double projected = speed + (speed - previousSpeed) * TPS * ACCELERATION_ARROW_SECONDS;
		int startY = y + chart.speedY(speed);
		int endY = y + chart.speedY(projected);

		int arrowX = barX + BarSpeedometerChart.BAR_WIDTH / 2;
		if (startY != endY) {
			drawLine(graphics, arrowX, startY, arrowX, endY, ACCELERATION_ARROW);
		}

		// Shrink the head's vertical depth with the visible projection. At zero acceleration
		// this leaves a horizontal five-pixel mark instead of making the indicator disappear;
		// on either side of zero it opens smoothly into an upward or downward arrowhead.
		double visiblePixels = Math.abs(chart.fraction(projected) - chart.fraction(speed))
				* chart.plotHeight();
		double headDepth = Math.min(2.0, visiblePixels);
		int directionY = -Double.compare(projected, speed);
		int headBaseY = (int) Math.round(endY - directionY * headDepth);
		drawLine(graphics, arrowX, endY, arrowX - 2, headBaseY, ACCELERATION_ARROW);
		drawLine(graphics, arrowX, endY, arrowX + 2, headBaseY, ACCELERATION_ARROW);
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

	/** A one-pixel reference line, extending one pixel beyond either side of its bar. */
	private static void marker(GuiGraphicsExtractor graphics, BarSpeedometerChart chart, int barX,
			int y, double speed, int color) {
		int py = y + chart.speedY(speed);
		graphics.fill(barX - BarSpeedometerChart.MARKER_BLEED, py,
				barX + BarSpeedometerChart.BAR_WIDTH + BarSpeedometerChart.MARKER_BLEED, py + 1, color);
	}
}

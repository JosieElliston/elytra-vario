package jealoustone.elytravario.hud;

import java.util.Locale;

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
import net.minecraft.client.gui.GuiGraphics;

/** Three vertical bars for vertical, horizontal, and total speed, with acceleration arrows. */
public final class SpeedometerElement implements HudElement {
	private static final double TPS = 20.0;
	private static final double ACCELERATION_ARROW_SECONDS = 1.0;
	private static final int MAJOR = 0xFFC6CCD2;
	private static final int ACCELERATION_ARROW = 0xFFFFFFFF;
	private static final int SOFT_MAX_MARKER = 0xFFFFFFFF;
	private static final int TERMINAL_MARKER = 0xFF9AA0A6;
	private static final int TICK_LENGTH = 3;
	private static final int LABEL_GAP = 2;
	private static final int MAX_TICKS = 1024;

	// Steady flight at +53.366 degrees, in blocks/tick. Vertical speed is a magnitude.
	private static final double SOFT_MAX_XZ = 3.38879;
	private static final double SOFT_MAX_Y = 1.00954;
	private static final double SOFT_MAX_XYZ = Math.hypot(SOFT_MAX_XZ, SOFT_MAX_Y);

	// Straight-down steady-state speed: (v - gravity) * vertical drag = v.
	private static final double TERMINAL_Y = 3.920003814700903;
	private static final double TERMINAL_XZ = 0.0;
	private static final double TERMINAL_XYZ = TERMINAL_Y;

	private final FlightRecorder recorder;

	public SpeedometerElement(FlightRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		Sample sample = recorder.latest();
		if (sample == null || !VarioInstrument.SPEEDOMETER.visible(sample.gliding())) return;

		SpeedometerChart chart = new SpeedometerChart(VarioConfig.speedoHeight,
				VarioConfig.speedoMaxSpeed);
		HudPosition position = HudPosition.clamp(VarioConfig.speedoX, VarioConfig.speedoY,
				chart.width(), chart.height(), graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, minecraft.font, chart, sample, recorder.ago(1),
				position.x(), position.y());
	}

	private static void draw(GuiGraphics graphics, Font font, SpeedometerChart chart,
			Sample sample, Sample previous, int x, int y) {
		drawFace(graphics, chart, x, y);
		drawScale(graphics, font, chart, x, y);

		drawBar(graphics, font, chart, x, y, 0, "Y", Math.abs(sample.vy()),
				previous == null ? Double.NaN : Math.abs(previous.vy()),
				VarioConfig.showSpeedoVertical, VarioConfig.speedoVerticalColor,
				SOFT_MAX_Y, TERMINAL_Y);
		drawBar(graphics, font, chart, x, y, 1, "XZ", sample.horizontalSpeed(),
				previous == null ? Double.NaN : previous.horizontalSpeed(),
				VarioConfig.showSpeedoHorizontal, VarioConfig.speedoHorizontalColor,
				SOFT_MAX_XZ, TERMINAL_XZ);
		drawBar(graphics, font, chart, x, y, 2, "XYZ", sample.speed(),
				previous == null ? Double.NaN : previous.speed(),
				VarioConfig.showSpeedoTotal, VarioConfig.speedoTotalColor,
				SOFT_MAX_XYZ, TERMINAL_XYZ);

		if (VarioConfig.showSpeedoBorder) {
			graphics.fill(x, y, x + chart.width(), y + 1, BORDER);
			graphics.fill(x, y + chart.height() - 1, x + chart.width(), y + chart.height(), BORDER);
			graphics.fill(x, y, x + 1, y + chart.height(), BORDER);
			graphics.fill(x + chart.width() - 1, y, x + chart.width(), y + chart.height(), BORDER);
		}
	}

	private static void drawFace(GuiGraphics graphics, SpeedometerChart chart, int x, int y) {
		if (VarioConfig.speedoOpacity <= 0) return;
		int background = ((int) Math.round(VarioConfig.speedoOpacity * 255) << 24)
				| (PANEL_BG & 0xFFFFFF);
		graphics.fill(x, y, x + chart.width(), y + chart.height(), background);
	}

	private static void drawScale(GuiGraphics graphics, Font font, SpeedometerChart chart,
			int x, int y) {
		boolean labels = VarioConfig.showSpeedoLabels
				&& chart.plotHeight() * chart.fraction(VarioConfig.speedoMajorStep)
				>= font.lineHeight + LABEL_GAP;
		for (double speed : steps(chart, VarioConfig.speedoMajorStep)) {
			int py = y + chart.speedY(speed);
			graphics.fill(x + chart.plotX() - TICK_LENGTH, py,
					x + chart.plotX() + chart.plotWidth(), py + 1, MAJOR);
			if (labels) {
				String text = fmt("%.0f", speed * TPS);
				int labelY = Math.max(py - font.lineHeight / 2,
						y + SpeedometerChart.TEXT_MARGIN);
				graphics.drawString(font, text, x + chart.plotX() - TICK_LENGTH - LABEL_GAP
						- font.width(text), labelY, LABEL, true);
			}
		}
	}

	private static void drawBar(GuiGraphics graphics, Font font, SpeedometerChart chart,
			int x, int y, int index, String label, double speed, double previousSpeed,
			boolean shown, int color,
			double softMax, double terminal) {
		if (!shown) return;
		int bx = x + chart.barX(index);
		int top = y + chart.speedY(speed);
		int bottom = y + chart.baselineY();
		int barColor = chart.pegged(speed) ? VarioConfig.speedoPeggedColor : color;
		if (top < bottom) {
			graphics.fill(bx, top, bx + SpeedometerChart.BAR_WIDTH, bottom, barColor);
		}

		if (VarioConfig.showSpeedoSoftMaxMarker) {
			marker(graphics, chart, bx, y, softMax, SOFT_MAX_MARKER);
		}
		if (VarioConfig.showSpeedoTerminalMarker) {
			marker(graphics, chart, bx, y, terminal, TERMINAL_MARKER);
		}
		if (VarioConfig.showSpeedoAcceleration && Double.isFinite(previousSpeed)) {
			accelerationArrow(graphics, chart, bx, y, speed, previousSpeed);
		}

		int labelX = bx + (SpeedometerChart.BAR_WIDTH - font.width(label)) / 2;
		graphics.drawString(font, label, labelX, y + chart.baselineY() + 3, LABEL, true);
	}

	/** Projects the tick-to-tick speed change for one second on the speed scale. */
	private static void accelerationArrow(GuiGraphics graphics, SpeedometerChart chart,
			int barX, int y, double speed, double previousSpeed) {
		double projected = speed + (speed - previousSpeed) * TPS * ACCELERATION_ARROW_SECONDS;
		int startY = y + chart.speedY(speed);
		int endY = y + chart.speedY(projected);

		int arrowX = barX + SpeedometerChart.BAR_WIDTH / 2;
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

	private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
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
	private static void marker(GuiGraphics graphics, SpeedometerChart chart, int barX,
			int y, double speed, int color) {
		int py = y + chart.speedY(speed);
		graphics.fill(barX - 1, py, barX + SpeedometerChart.BAR_WIDTH + 1, py + 1, color);
	}

	private static double[] steps(SpeedometerChart chart, double step) {
		if (!(step > 0.0) || !(chart.maxSpeed() > 0.0)) return new double[] { 0.0 };
		int count = Math.min(MAX_TICKS, (int) Math.floor(chart.maxSpeed() / step + 1.0e-9));
		double[] speeds = new double[count + 1];
		for (int i = 0; i <= count; i++) speeds[i] = i * step;
		return speeds;
	}

	private static String fmt(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}
}

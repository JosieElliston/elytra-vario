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
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.joml.Matrix3x2fStack;

/** A semicircular speedometer with separate total, horizontal, and vertical-speed needles. */
public final class DialSpeedometerElement implements HudElement {
	private static final double TPS = 20.0;
	private static final double ACCELERATION_ARROW_SECONDS = 1.0;
	private static final int MAJOR = 0xFFC6CCD2;
	private static final int MINOR = 0xA0B4BAC0;
	private static final int MAJOR_LENGTH = 7;
	private static final int MINOR_LENGTH = 4;
	private static final int LABEL_GAP = 3;
	private static final int LABEL_RISE = 4;
	private static final double TOTAL_LENGTH = 0.90;
	private static final double HORIZONTAL_LENGTH = 0.72;
	private static final double VERTICAL_LENGTH = 0.54;
	private static final int NEEDLE_WEIGHT = 2;
	private static final int HUB = 1;
	private static final int MAX_TICKS = 1024;

	private final FlightRecorder recorder;

	public DialSpeedometerElement(FlightRecorder recorder) { this.recorder = recorder; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		Sample sample = recorder.latest();
		if (sample == null || !VarioInstrument.DIAL_SPEEDOMETER.visible(sample.gliding())) return;

		DialSpeedometer dial = new DialSpeedometer(VarioConfig.dialSpeedoRadius,
				VarioConfig.dialSpeedoMaxSpeed);
		HudPosition position = HudPosition.clamp(VarioConfig.dialSpeedoX, VarioConfig.dialSpeedoY,
				dial.width(), dial.height(), graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, minecraft.font, dial, sample, recorder.ago(1),
				position.x(), position.y());
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, DialSpeedometer dial,
			Sample sample, Sample previous, int x, int y) {
		int hubX = x + dial.hubX();
		int hubY = y + dial.hubY();
		drawFace(graphics, dial, hubX, hubY);
		drawScale(graphics, font, dial, hubX, hubY);
		drawNeedles(graphics, dial, sample, previous, hubX, hubY);
	}

	private static void drawFace(GuiGraphicsExtractor graphics, DialSpeedometer dial,
			int hubX, int hubY) {
		int rim = dial.rim();
		if (VarioConfig.dialSpeedoOpacity > 0) {
			int background = ((int) Math.round(VarioConfig.dialSpeedoOpacity * 255) << 24)
					| (PANEL_BG & 0xFFFFFF);
			halfDisc(graphics, hubX, hubY, rim, background);
		}
		if (VarioConfig.showDialSpeedoBorder) {
			arc(graphics, hubX, hubY, rim, BORDER);
			graphics.fill(hubX - rim, hubY, hubX + rim + 1, hubY + 1, BORDER);
		}
	}

	private static void halfDisc(GuiGraphicsExtractor graphics, int hubX, int hubY, int radius,
			int color) {
		for (int dy = 0; dy <= radius; dy++) {
			int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			graphics.fill(hubX - half, hubY - dy, hubX + half + 1, hubY - dy + 1, color);
		}
	}

	private static void drawScale(GuiGraphicsExtractor graphics, Font font, DialSpeedometer dial,
			int hubX, int hubY) {
		arc(graphics, hubX, hubY, dial.radius(), MAJOR);
		for (double speed : steps(dial, VarioConfig.dialSpeedoMinorStep)) {
			tick(graphics, dial, hubX, hubY, speed, MINOR_LENGTH, 1, MINOR);
		}
		boolean labels = VarioConfig.showDialSpeedoLabels && labelsFit(dial, font);
		for (double speed : steps(dial, VarioConfig.dialSpeedoMajorStep)) {
			tick(graphics, dial, hubX, hubY, speed, MAJOR_LENGTH, 2, MAJOR);
			if (labels) label(graphics, font, dial, hubX, hubY, speed);
		}
	}

	private static double[] steps(DialSpeedometer dial, double step) {
		if (!(step > 0.0) || !(dial.maxSpeed() > 0.0)) return new double[] { 0.0 };
		int count = Math.min(MAX_TICKS, (int) Math.floor(dial.maxSpeed() / step + 1.0e-9));
		double[] speeds = new double[count + 1];
		for (int i = 0; i <= count; i++) speeds[i] = i * step;
		return speeds;
	}

	private static void arc(GuiGraphicsExtractor graphics, int hubX, int hubY, int radius,
			int color) {
		int steps = Math.max(1, (int) Math.ceil(Math.PI * radius * 2.0));
		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		for (int i = 0; i <= steps; i++) {
			double angle = Math.PI * (1.0 + (double) i / steps);
			int px = hubX + (int) Math.round(Math.cos(angle) * radius);
			int py = hubY + (int) Math.round(Math.sin(angle) * radius);
			if (px == lastX && py == lastY) continue;
			graphics.fill(px, py, px + 1, py + 1, color);
			lastX = px;
			lastY = py;
		}
	}

	private static void tick(GuiGraphicsExtractor graphics, DialSpeedometer dial, int hubX,
			int hubY, double speed, int length, int weight, int color) {
		radial(graphics, hubX, hubY, dial.angle(speed), dial.radius() - length,
				dial.radius(), weight, color);
	}

	private static void needle(GuiGraphicsExtractor graphics, DialSpeedometer dial, int hubX,
			int hubY, double speed, double length, int color) {
		radial(graphics, hubX, hubY, dial.angle(speed), 0,
				(int) Math.round(dial.radius() * length), NEEDLE_WEIGHT,
				needleColor(dial, speed, color));
	}

	private static int needleColor(DialSpeedometer dial, double speed, int color) {
		return dial.pegged(speed) ? VarioConfig.dialSpeedoPeggedColor : color;
	}

	private static void radial(GuiGraphicsExtractor graphics, int hubX, int hubY, double angle,
			int from, int to, int weight, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(hubX + 0.5f, hubY + 0.5f);
		pose.rotate((float) angle);
		pose.translate(0.0f, weight / -2.0f);
		graphics.fill(from, 0, to, weight, color);
		pose.popMatrix();
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, DialSpeedometer dial,
			int hubX, int hubY, double speed) {
		double angle = dial.angle(speed);
		int inset = labelInset(dial);
		String text = String.format(Locale.ROOT, "%.0f", speed * TPS);
		int x = hubX + (int) Math.round(Math.cos(angle) * inset) - font.width(text) / 2;
		int y = hubY + (int) Math.round(Math.sin(angle) * inset) - LABEL_RISE;
		graphics.text(font, text, x, Math.min(y, hubY - 2 * LABEL_RISE - 1), LABEL, true);
	}

	private static void drawNeedles(GuiGraphicsExtractor graphics, DialSpeedometer dial,
			Sample sample, Sample previous, int hubX, int hubY) {
		if (VarioConfig.showDialSpeedoTotal) {
			drawNeedle(graphics, dial, hubX, hubY, sample.speed(),
					previous == null ? Double.NaN : previous.speed(), TOTAL_LENGTH,
					VarioConfig.dialSpeedoTotalColor);
		}
		if (VarioConfig.showDialSpeedoHorizontal) {
			drawNeedle(graphics, dial, hubX, hubY, sample.horizontalSpeed(),
					previous == null ? Double.NaN : previous.horizontalSpeed(), HORIZONTAL_LENGTH,
					VarioConfig.dialSpeedoHorizontalColor);
		}
		if (VarioConfig.showDialSpeedoVertical) {
			drawNeedle(graphics, dial, hubX, hubY, Math.abs(sample.vy()),
					previous == null ? Double.NaN : Math.abs(previous.vy()), VERTICAL_LENGTH,
					VarioConfig.dialSpeedoVerticalColor);
		}
		graphics.fill(hubX - HUB, hubY - 2 * HUB, hubX + HUB + 1, hubY + 1, MAJOR);
	}

	private static void drawNeedle(GuiGraphicsExtractor graphics, DialSpeedometer dial,
			int hubX, int hubY, double speed, double previousSpeed, double length, int color) {
		needle(graphics, dial, hubX, hubY, speed, length, color);
		if (VarioConfig.showDialSpeedoAcceleration && Double.isFinite(previousSpeed)) {
			accelerationArrow(graphics, dial, hubX, hubY, speed, previousSpeed,
					(int) Math.round(dial.radius() * length), needleColor(dial, speed, color));
		}
	}

	/** Projects one second of measured acceleration along the circle at the needle's tip. */
	private static void accelerationArrow(GuiGraphicsExtractor graphics, DialSpeedometer dial,
			int hubX, int hubY, double speed, double previousSpeed, int radius, int color) {
		double projected = speed + (speed - previousSpeed) * TPS * ACCELERATION_ARROW_SECONDS;
		double startAngle = dial.angle(speed);
		double endAngle = dial.angle(projected);
		double sweep = endAngle - startAngle;
		double centerX = hubX + 0.5;
		double centerY = hubY + 0.5;
		int steps = Math.max(1, (int) Math.ceil(Math.abs(sweep) * radius));
		for (int i = 0; i < steps && sweep != 0.0; i++) {
			double from = startAngle + sweep * i / steps;
			double to = startAngle + sweep * (i + 1) / steps;
			smoothLine(graphics,
					centerX + Math.cos(from) * radius, centerY + Math.sin(from) * radius,
					centerX + Math.cos(to) * radius, centerY + Math.sin(to) * radius,
					1.0, color);
		}

		// As the arc shrinks to zero, the head becomes a stable radial five-pixel mark.
		double visiblePixels = Math.abs(sweep) * radius;
		double headDepth = Math.min(3.0, visiblePixels);
		double direction = Math.signum(sweep);
		double endX = centerX + Math.cos(endAngle) * radius;
		double endY = centerY + Math.sin(endAngle) * radius;
		double tangentX = -Math.sin(endAngle) * direction;
		double tangentY = Math.cos(endAngle) * direction;
		double radialX = Math.cos(endAngle) * 2.0;
		double radialY = Math.sin(endAngle) * 2.0;
		double baseX = endX - tangentX * headDepth;
		double baseY = endY - tangentY * headDepth;
		if (sweep == 0.0) {
			smoothLine(graphics, endX + radialX, endY + radialY,
					endX - radialX, endY - radialY, 1.0, color);
		} else {
			smoothLine(graphics, endX, endY, baseX + radialX, baseY + radialY, 1.0, color);
			smoothLine(graphics, endX, endY, baseX - radialX, baseY - radialY, 1.0, color);
		}
	}

	/** A subpixel-positioned rotated rectangle, matching the needle rendering. */
	private static void smoothLine(GuiGraphicsExtractor graphics, double x0, double y0,
			double x1, double y1, double weight, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double length = Math.hypot(dx, dy);
		if (!(length > 0.0)) return;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x0, (float) y0);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.translate(0.0f, (float) (-weight / 2.0));
		pose.scale((float) length, (float) weight);
		graphics.fill(0, 0, 1, 1, color);
		pose.popMatrix();
	}

	private static int labelInset(DialSpeedometer dial) {
		return dial.radius() - MAJOR_LENGTH - LABEL_GAP - LABEL_RISE;
	}

	private static boolean labelsFit(DialSpeedometer dial, Font font) {
		int inset = labelInset(dial);
		if (inset < LABEL_RISE) return false;
		double pitch = inset * Math.PI * dial.fraction(VarioConfig.dialSpeedoMajorStep);
		return pitch >= font.width(String.format(Locale.ROOT, "%.0f", dial.maxSpeed() * TPS))
				+ LABEL_GAP;
	}
}

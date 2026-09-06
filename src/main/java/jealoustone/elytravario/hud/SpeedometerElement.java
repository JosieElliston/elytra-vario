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

import org.joml.Matrix3x2fStack;

/**
 * A semicircular speedometer: one arc from zero to {@link VarioConfig#speedoMaxSpeed}, and a
 * needle on it for each of total, horizontal and vertical speed.
 *
 * <p>Every figure here is already printed on the readout panel, and that is deliberate. Digits
 * have to be read; an angle can be caught without looking straight at it, and three angles
 * about one hub turn the relationship between the three speeds into a shape — a wide fan is
 * speed going into climb or sink, a closed one is flight that is nearly level.
 *
 * <p>A separate element rather than another block on the readout panel because it sits
 * somewhere else on screen and has its own toggle, which is the same reason the pitch ladder
 * is its own element.
 *
 * <h2>Vertical speed has no sign on this dial</h2>
 *
 * <p>A scale that begins at zero cannot show one, so the blue needle reads {@code |vy|} and
 * nothing here says which way. Nothing needs to: the direction of travel is the most obvious
 * fact in the view out of the window, and the readout panel's {@code SPEED Y} row prints the
 * sign for when a figure is wanted. A mark on the dial repeating it would be one more thing
 * to look at for something already known.
 *
 * <h2>Drawing curves out of rectangles</h2>
 *
 * <p>The HUD's only primitive is a rectangle, but it is submitted with the current pose, and
 * that pose may carry a rotation — the four corners are each transformed on the way to the
 * vertex buffer. So every radial mark, needles and ticks alike, is one rectangle drawn along
 * the x axis of a pose rotated to its own angle, which is exact at any angle and costs one
 * draw call.
 *
 * <p>The two curves cannot be. An arc — the scale, and the rim around it — is walked at half a
 * pixel of arc length with each distinct pixel filled once, which is gapless by construction,
 * since consecutive samples can never be more than a pixel apart, and costs one fill per pixel
 * the curve covers rather than one per sample. The half disc behind everything is filled a row
 * at a time. Both deduplicate rather than overdraw, because both are drawn in a translucent
 * colour and a pixel covered twice would blend twice and show as a seam.
 */
public final class SpeedometerElement implements HudElement {
	/** Ticks per second, the factor between internal blocks/tick and displayed blocks/second. */
	private static final double TPS = 20.0;

	/** The scale's two tiers, sharing the pitch ladder's grays so the instruments match. */
	private static final int MAJOR = 0xFFC6CCD2;
	private static final int MINOR = 0xA0B4BAC0;

	/** Tick lengths inward from the arc, and their thicknesses, in GUI pixels. */
	private static final int MAJOR_LENGTH = 7;
	private static final int MINOR_LENGTH = 4;
	private static final int MAJOR_WEIGHT = 2;
	private static final int MINOR_WEIGHT = 1;

	/** Clearance between a labelled tick's inner end and the label. */
	private static final int LABEL_GAP = 3;

	/** Half the font's line height, to center a label on the tick it belongs to. */
	private static final int LABEL_RISE = 4;


	/**
	 * Needle lengths as a fraction of the radius, longest first, and their thickness.
	 *
	 * <p>The three needles turn about one hub, so two of them agreeing means one lying exactly
	 * on the other — and total and horizontal speed agree whenever flight is level, which is
	 * most of a glide. Distinct lengths are what make that overlap read as a needle with a
	 * longer one behind it instead of as one mark of indeterminate colour, and they are the
	 * only channel left when colour has stopped saying anything: at the stop, where every
	 * pegged needle takes the same grey, and for the red and green pair, which is the one pair
	 * a colour-blind eye cannot separate.
	 *
	 * <p>Total speed takes the longest because it is the largest of the three by construction,
	 * so the tips run outwards in the same order they run clockwise. The reading is the angle,
	 * so length costs nothing.
	 *
	 * <p><b>{@code drawNeedles} must draw them in descending order of length</b>, which is what
	 * leaves a shorter needle visible on top of a longer one rather than buried under it.
	 */
	private static final double TOTAL_LENGTH = 0.90;
	private static final double HORIZONTAL_LENGTH = 0.72;
	private static final double VERTICAL_LENGTH = 0.54;
	private static final int NEEDLE_WEIGHT = 2;

	/**
	 * Half-width of the hub, drawn over the needles' roots to tidy where they meet. Its bottom
	 * row is the hub's own, so the whole of it stays inside the half disc.
	 */
	private static final int HUB = 1;

	/**
	 * Guards the tick loops against a step so small it would draw for a very long time. Far
	 * above what any accepted setting reaches: the smallest step configuration allows is one
	 * block/second against a scale of at most four hundred.
	 */
	private static final int MAX_TICKS = 1024;

	private final FlightRecorder recorder;

	public SpeedometerElement(FlightRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!VarioConfig.enabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return;
		}

		Sample sample = recorder.latest();

		if (sample == null) {
			return;
		}

		if (!VarioInstrument.SPEEDOMETER.visible(sample.gliding())) {
			return;
		}

		SpeedometerDial dial = new SpeedometerDial(VarioConfig.speedoRadius, VarioConfig.speedoMaxSpeed);
		HudPosition position = HudPosition.resolve(VarioConfig.speedoAnchor, VarioConfig.speedoX,
				VarioConfig.speedoY, dial.width(), dial.height(),
				graphics.guiWidth(), graphics.guiHeight());

		draw(graphics, minecraft.font, dial, sample, position.x(), position.y());
	}

	private static void draw(GuiGraphics graphics, Font font, SpeedometerDial dial,
			Sample sample, int x, int y) {
		int hubX = x + dial.hubX();
		int hubY = y + dial.hubY();

		drawFace(graphics, dial, hubX, hubY);
		drawScale(graphics, font, dial, hubX, hubY);
		drawNeedles(graphics, dial, sample, hubX, hubY);
	}

	/**
	 * The background and the rim: a half disc, not a rectangle.
	 *
	 * <p>The instrument is round and its corners hold nothing, so a rectangle behind it would
	 * be four wedges of dimmed world paying for no reading. That matters more here than on the
	 * other panels because this one is large and because it is the only one whose content does
	 * not fill its bounds.
	 */
	private static void drawFace(GuiGraphics graphics, SpeedometerDial dial,
			int hubX, int hubY) {
		int rim = dial.rim();

		if (VarioConfig.speedoOpacity > 0) {
			int background = ((int) Math.round(VarioConfig.speedoOpacity * 255) << 24)
					| (PANEL_BG & 0xFFFFFF);
			halfDisc(graphics, hubX, hubY, rim, background);
		}

		if (VarioConfig.showSpeedoBorder) {
			arc(graphics, hubX, hubY, rim, BORDER);
			graphics.fill(hubX - rim, hubY, hubX + rim + 1, hubY + 1, BORDER);
		}
	}

	/**
	 * The upper half of a disc, filled one row at a time.
	 *
	 * <p>A row at a time rather than a pixel at a time because the fill is translucent: a pixel
	 * covered twice would blend twice and show as a seam, and whole rows cannot overlap.
	 */
	private static void halfDisc(GuiGraphics graphics, int hubX, int hubY, int radius,
			int color) {
		for (int dy = 0; dy <= radius; dy++) {
			int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			graphics.fill(hubX - half, hubY - dy, hubX + half + 1, hubY - dy + 1, color);
		}
	}

	/**
	 * The arc and its ticks.
	 *
	 * <p>Minor ticks are drawn before major ones, so that where the two steps coincide the
	 * major paints over the minor rather than the other way round. That is cheaper than
	 * testing for a coincidence in floating point and cannot get the test wrong.
	 */
	private static void drawScale(GuiGraphics graphics, Font font, SpeedometerDial dial,
			int hubX, int hubY) {
		arc(graphics, hubX, hubY, dial.radius(), MAJOR);

		// The flat side of the half circle is deliberately not drawn. A half turn puts both
		// stops on that diameter, so a line along it lies under every needle reading near
		// zero or near full scale — which is where vertical speed spends the apex and where a
		// pegged needle always is. It would close the shape at the cost of the two readings
		// hardest to see, and the ticks at either end already mark where the scale stops.

		for (double speed : steps(dial, VarioConfig.speedoMinorStep)) {
			tick(graphics, dial, hubX, hubY, speed, MINOR_LENGTH, MINOR_WEIGHT, MINOR);
		}

		boolean labels = VarioConfig.showSpeedoLabels && labelsFit(dial, font);

		for (double speed : steps(dial, VarioConfig.speedoMajorStep)) {
			tick(graphics, dial, hubX, hubY, speed, MAJOR_LENGTH, MAJOR_WEIGHT, MAJOR);

			if (labels) {
				label(graphics, font, dial, hubX, hubY, speed);
			}
		}
	}

	/** The speeds a tick lands on, from zero to full scale inclusive. */
	private static double[] steps(SpeedometerDial dial, double step) {
		if (!(step > 0.0) || !(dial.maxSpeed() > 0.0)) {
			return new double[] { 0.0 };
		}

		int count = Math.min(MAX_TICKS, (int) Math.floor(dial.maxSpeed() / step + 1.0e-9));
		double[] speeds = new double[count + 1];

		for (int i = 0; i <= count; i++) {
			speeds[i] = i * step;
		}

		return speeds;
	}

	/**
	 * A half circle, walked at half a pixel of arc length so that consecutive samples land on
	 * the same pixel or on a touching one, and filled once per distinct pixel.
	 *
	 * <p>Deduplicating is what makes this safe for a translucent colour as well as cheap: the
	 * rim is drawn in one, and a pixel visited twice would blend twice.
	 */
	private static void arc(GuiGraphics graphics, int hubX, int hubY, int radius,
			int color) {
		int steps = Math.max(1, (int) Math.ceil(Math.PI * radius * 2.0));
		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;

		for (int i = 0; i <= steps; i++) {
			double angle = Math.PI * (1.0 + (double) i / steps);
			int px = hubX + (int) Math.round(Math.cos(angle) * radius);
			int py = hubY + (int) Math.round(Math.sin(angle) * radius);

			if (px == lastX && py == lastY) {
				continue;
			}

			graphics.fill(px, py, px + 1, py + 1, color);
			lastX = px;
			lastY = py;
		}
	}

	/** One tick: a radial bar ending on the arc and reaching {@code length} pixels inwards. */
	private static void tick(GuiGraphics graphics, SpeedometerDial dial, int hubX, int hubY,
			double speed, int length, int weight, int color) {
		radial(graphics, hubX, hubY, dial.angle(speed), dial.radius() - length, dial.radius(),
				weight, color);
	}

	/**
	 * One needle, from the hub outwards.
	 *
	 * <p>Strength does not track how large the reading is, for the reason the pitch ladder
	 * gives about its bugs: a needle that faded near either end of the scale would be hardest
	 * to see exactly where it had the most to say. The only thing colour reports is whether
	 * the scale ran out.
	 */
	private static void needle(GuiGraphics graphics, SpeedometerDial dial, int hubX, int hubY,
			double speed, double length, int color) {
		radial(graphics, hubX, hubY, dial.angle(speed), 0,
				(int) Math.round(dial.radius() * length), NEEDLE_WEIGHT,
				dial.pegged(speed) ? VarioConfig.speedoPeggedColor : color);
	}

	/**
	 * A bar lying along one radius of the dial, between two distances from the hub.
	 *
	 * <p>Drawn as a rectangle along the x axis of a pose rotated to the angle, which the GUI
	 * transforms corner by corner, so it is a true rotated bar rather than a staircase. The
	 * half-pixel translation puts the hub at the center of its pixel rather than at its corner,
	 * which is what makes an odd-weight bar land on whole pixels where the dial is horizontal
	 * or vertical.
	 */
	private static void radial(GuiGraphics graphics, int hubX, int hubY, double angle,
			int from, int to, int weight, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(hubX + 0.5f, hubY + 0.5f);
		pose.rotate((float) angle);
		pose.translate(0.0f, weight / -2.0f);
		graphics.fill(from, 0, to, weight, color);
		pose.popMatrix();
	}

	/**
	 * A labelled tick's value in blocks/second, set inside the arc on the tick's own radius.
	 *
	 * <p>Drawn in the panel's label gray rather than in its muted one, which is what the chart
	 * uses for the same job. The chart's axis annotations sit on an opaque heatmap; these sit
	 * on a background thin enough to see the world through, and the muted gray disappears into
	 * a bright sky. Faint, still — the tick pattern is what the scale is read from, and the
	 * digits are for when one is looked at directly.
	 */
	private static void label(GuiGraphics graphics, Font font, SpeedometerDial dial,
			int hubX, int hubY, double speed) {
		double angle = dial.angle(speed);
		int inset = labelInset(dial);
		String text = fmt("%.0f", speed * TPS);
		int x = hubX + (int) Math.round(Math.cos(angle) * inset) - font.width(text) / 2;
		int y = hubY + (int) Math.round(Math.sin(angle) * inset) - LABEL_RISE;

		// The ticks at either end lie along the diameter, so a label centered on one of them
		// would hang half of itself outside the half disc. Those two are lifted to sit on that
		// line instead, which is the only room left inside the dial.
		graphics.drawString(font, text, x, Math.min(y, hubY - 2 * LABEL_RISE - 1), LABEL, true);
	}

	/**
	 * The three needles, longest first so that a shorter one lying under a longer one stays
	 * visible, and the hub over their roots.
	 *
	 * <p>Vertical speed is a magnitude here, and nothing on the dial carries its sign.
	 */
	private static void drawNeedles(GuiGraphics graphics, SpeedometerDial dial, Sample sample,
			int hubX, int hubY) {
		// In descending order of length, which is what makes an overlap read as a pile.
		// Retuning the lengths above means reordering these; nothing checks it.
		if (VarioConfig.showSpeedoTotal) {
			needle(graphics, dial, hubX, hubY, sample.speed(), TOTAL_LENGTH,
					VarioConfig.speedoTotalColor);
		}

		if (VarioConfig.showSpeedoHorizontal) {
			needle(graphics, dial, hubX, hubY, sample.horizontalSpeed(), HORIZONTAL_LENGTH,
					VarioConfig.speedoHorizontalColor);
		}

		if (VarioConfig.showSpeedoVertical) {
			needle(graphics, dial, hubX, hubY, Math.abs(sample.vy()), VERTICAL_LENGTH,
					VarioConfig.speedoVerticalColor);
		}

		graphics.fill(hubX - HUB, hubY - 2 * HUB, hubX + HUB + 1, hubY + 1, MAJOR);
	}

	/** How far from the hub a label's center sits: just inside the ticks, on their own radius. */
	private static int labelInset(SpeedometerDial dial) {
		return dial.radius() - MAJOR_LENGTH - LABEL_GAP - LABEL_RISE;
	}

	/**
	 * Whether there is room on this dial to label every major tick, or whether the digits
	 * would collide and say less than no digits do.
	 *
	 * <p>Measured rather than assumed, since both the radius and the tick spacing are
	 * settings: the labels sit on one circle, adjacent ones are {@code pi * step / maxSpeed}
	 * apart on it, and the widest label is the one at full scale. A dial too small for its own
	 * scale therefore keeps its ticks, which still say where the ends and the quarters are,
	 * and drops only the digits.
	 */
	private static boolean labelsFit(SpeedometerDial dial, Font font) {
		int inset = labelInset(dial);

		if (inset < LABEL_RISE) {
			return false;
		}

		double pitch = inset * Math.PI * dial.fraction(VarioConfig.speedoMajorStep);
		return pitch >= font.width(fmt("%.0f", dial.maxSpeed() * TPS)) + LABEL_GAP;
	}

	/** Always formats with {@link Locale#ROOT}, so decimal separators do not follow the system locale. */
	private static String fmt(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}
}

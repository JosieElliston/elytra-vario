package jealoustone.elytravario.hud;

import java.util.Locale;

import jealoustone.elytravario.VarioConfig;

import net.minecraft.client.gui.Font;

/**
 * The speed bar chart's geometry and linear speed scale.
 *
 * <p>The panel is measured outwards from the plot, so anything switched off takes its space
 * with it and the background shrinks to what is actually drawn. Across:
 *
 * <pre>
 * PAD | scale label | LABEL_GAP | TICK_LENGTH | plot | PAD
 * </pre>
 *
 * <p>and down:
 *
 * <pre>
 * PAD | half a line of label clearance | plot | CATEGORY_GAP | category labels | PAD
 * </pre>
 *
 * <p>The scale label column is zero wide when the labels are off, the clearance above the plot
 * is zero with it, and the category row is zero high when no bars are shown. The plot itself is
 * the bars plus a pixel of bleed on each side for the reference markers that overhang them.
 *
 * <p>Everything the panel's size depends on that is not a setting — the font's line height and
 * the width of the widest scale label — is measured once by {@link #of(Font)} and carried here
 * as a number, so the geometry stays arithmetic that can be reasoned about on its own.
 */
public record BarSpeedometerChart(int plotHeight, double maxSpeed, double majorStep, int barCount,
		int scaleLabelWidth, int textHeight) {
	/** Between the panel edge and anything drawn inside it. */
	public static final int PAD = 4;
	/** How far the scale's rules reach left of the plot, between it and the labels. */
	public static final int TICK_LENGTH = 3;
	/** Between a scale label and its tick. */
	public static final int LABEL_GAP = 2;
	/** Between the baseline and the category labels hanging under it. */
	public static final int CATEGORY_GAP = 3;
	/** Reference markers overhang their bar by a pixel each side; the plot leaves them room. */
	public static final int MARKER_BLEED = 1;
	public static final int BAR_WIDTH = 11;
	public static final int BAR_GAP = 10;
	public static final int BAR_COUNT = 3;
	private static final int MAX_TICKS = 1024;
	private static final double TPS = 20.0;

	public BarSpeedometerChart {
		plotHeight = Math.max(1, plotHeight);
		barCount = Math.clamp(barCount, 0, BAR_COUNT);
		scaleLabelWidth = Math.max(0, scaleLabelWidth);
		textHeight = Math.max(0, textHeight);
	}

	/**
	 * The panel as currently configured, with the parts that are switched off measured out of
	 * it.
	 *
	 * <p>Whether the scale carries labels is the switch's answer alone. The panel used to drop
	 * them on its own once the major step was too fine for them to sit clear of one another,
	 * which meant the panel's width depended on its height: dragging the plot shorter made the
	 * label column vanish and the whole panel jump sideways under the pointer. Labels that
	 * crowd at a fine step are the step's problem and are visibly so, which is better than a
	 * panel that changes shape for reasons the person resizing it cannot see.
	 */
	public static BarSpeedometerChart of(Font font) {
		int barCount = (VarioConfig.showBarSpeedoVertical ? 1 : 0)
				+ (VarioConfig.showBarSpeedoHorizontal ? 1 : 0)
				+ (VarioConfig.showBarSpeedoTotal ? 1 : 0);
		BarSpeedometerChart unlabeled = new BarSpeedometerChart(VarioConfig.barSpeedoHeight,
				VarioConfig.barSpeedoMaxSpeed, VarioConfig.barSpeedoMajorStep, barCount, 0,
				font.lineHeight);
		if (!VarioConfig.showBarSpeedoLabels) return unlabeled;

		int labelWidth = 0;
		for (double speed : unlabeled.steps()) {
			labelWidth = Math.max(labelWidth, font.width(scaleLabel(speed)));
		}
		return new BarSpeedometerChart(unlabeled.plotHeight(), unlabeled.maxSpeed(),
				unlabeled.majorStep(), barCount, labelWidth, font.lineHeight);
	}

	/** Whether the scale carries labels; the column they sit in is theirs alone. */
	public boolean scaleLabeled() { return scaleLabelWidth > 0; }

	public int plotX() { return PAD + scaleLabelColumn() + TICK_LENGTH; }
	public int plotY() { return PAD + topClearance(); }
	public int plotWidth() {
		if (barCount == 0) return 0;
		return barCount * BAR_WIDTH + (barCount - 1) * BAR_GAP + 2 * MARKER_BLEED;
	}
	public int baselineY() { return plotY() + plotHeight; }
	public int width() { return plotX() + plotWidth() + PAD; }
	public int height() { return baselineY() + categoryHeight() + PAD; }

	/** The left edge of a bar, counting only the bars actually shown. */
	public int barX(int index) { return plotX() + MARKER_BLEED + index * (BAR_WIDTH + BAR_GAP); }

	/** The column the right-aligned scale labels end at, a gap short of their ticks. */
	public int scaleLabelRight() { return PAD + scaleLabelColumn() - LABEL_GAP; }

	/** The top of a category label, which hangs under the baseline rather than sitting on it. */
	public int categoryY() { return baselineY() + CATEGORY_GAP; }

	private int scaleLabelColumn() { return scaleLabeled() ? scaleLabelWidth + LABEL_GAP : 0; }
	private int topClearance() { return scaleLabeled() ? textHeight / 2 : 0; }
	private int categoryHeight() { return barCount == 0 ? 0 : CATEGORY_GAP + textHeight; }

	/** The speeds the scale is ruled at, from zero up to the last step within full scale. */
	public double[] steps() {
		if (!(majorStep > 0.0) || !(maxSpeed > 0.0)) return new double[] { 0.0 };
		int count = Math.min(MAX_TICKS, (int) Math.floor(maxSpeed / majorStep + 1.0e-9));
		double[] speeds = new double[count + 1];
		for (int i = 0; i <= count; i++) speeds[i] = i * majorStep;
		return speeds;
	}

	/** A scale label. The scale is kept in blocks per tick and read in blocks per second. */
	public static String scaleLabel(double speed) {
		return String.format(Locale.ROOT, "%.0f", speed * TPS);
	}

	/** Where a speed falls on the scale, clamped to {@code [0, 1]}. */
	public double fraction(double speed) {
		if (!(maxSpeed > 0.0) || !(speed > 0.0)) return 0.0;
		return Math.min(1.0, speed / maxSpeed);
	}

	/** The chart-relative y coordinate for a speed. */
	public int speedY(double speed) {
		return plotY() + (int) Math.round((1.0 - fraction(speed)) * plotHeight);
	}

	/** Whether a speed is outside the scale and therefore clamped. */
	public boolean pegged(double speed) {
		return !(speed <= maxSpeed) || speed < 0.0;
	}
}

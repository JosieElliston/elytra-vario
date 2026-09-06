package jealoustone.elytravario.hud;

/** The speed bar chart's geometry and linear speed scale. */
public record SpeedometerChart(int plotHeight, double maxSpeed) {
	public static final int PAD = 4;
	public static final int TEXT_MARGIN = 5;
	public static final int RIGHT_PAD = 11;
	public static final int SCALE_LABEL_WIDTH = 18;
	public static final int BAR_WIDTH = 11;
	public static final int BAR_GAP = 10;
	public static final int BAR_COUNT = 3;
	public static final int CATEGORY_HEIGHT = 13;
	private static final int TOP_LABEL_CLEARANCE = 4;

	public SpeedometerChart {
		plotHeight = Math.max(1, plotHeight);
	}

	public int plotX() { return PAD + SCALE_LABEL_WIDTH; }
	public int plotY() { return PAD + TOP_LABEL_CLEARANCE; }
	public int baselineY() { return plotY() + plotHeight; }
	public int barX(int index) { return plotX() + index * (BAR_WIDTH + BAR_GAP); }
	public int plotWidth() { return BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP; }
	public int width() { return plotX() + plotWidth() + RIGHT_PAD; }
	public int height() { return baselineY() + CATEGORY_HEIGHT + PAD; }

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

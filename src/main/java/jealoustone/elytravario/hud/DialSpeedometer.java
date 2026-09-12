package jealoustone.elytravario.hud;

/** Geometry and linear speed scale for the semicircular dial speedometer. */
public record DialSpeedometer(int radius, double maxSpeed) {
	public static final int PAD = 5;

	/**
	 * Needle lengths as fractions of the radius, longest to shortest. The gap between them is
	 * the same 0.18 twice, which is what gives each reading a lane of its own on a dial where
	 * all three share one scale.
	 */
	public static final double TOTAL_LENGTH = 0.90;
	public static final double HORIZONTAL_LENGTH = 0.72;
	public static final double VERTICAL_LENGTH = 0.54;

	/**
	 * How far a reference marker reaches either side of its needle's tip, as a fraction of the
	 * radius: a third of the gap between neighboring tips, so a mark stays in its own lane with
	 * daylight on both sides, the way the bar chart's markers sit across their own bar.
	 */
	public static final double MARKER_REACH = 0.06;

	public int rim() { return radius + PAD; }
	public int width() { return 2 * rim() + 1; }
	public int height() { return rim() + 1; }
	public int hubX() { return rim(); }
	public int hubY() { return rim(); }

	/** How far out a needle of this length reaches, in pixels from the hub. */
	public int needleTip(double length) { return (int) Math.round(radius * length); }

	/** The inner end of the radial band a needle's reference markers are drawn in. */
	public int markerFrom(double length) { return needleTip(length) - markerReach(); }

	/** The outer end of that band, one pixel past the last drawn pixel. */
	public int markerTo(double length) { return needleTip(length) + markerReach(); }

	/** At least a pixel each way, so a small dial marks its references faintly rather than not at all. */
	private int markerReach() { return Math.max(1, (int) Math.round(radius * MARKER_REACH)); }

	/** Where a speed falls on the scale, clamped to {@code [0, 1]}. */
	public double fraction(double speed) {
		if (!(maxSpeed > 0.0) || !(speed > 0.0)) return 0.0;
		return Math.min(1.0, speed / maxSpeed);
	}

	/** The clockwise GUI-space angle of a speed, from the left stop to the right stop. */
	public double angle(double speed) { return Math.PI * (1.0 + fraction(speed)); }

	/** Whether a speed is outside the scale and therefore clamped to a stop. */
	public boolean pegged(double speed) {
		return !(speed <= maxSpeed) || speed < 0.0;
	}
}

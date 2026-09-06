package jealoustone.elytravario.hud;

/** Geometry and linear speed scale for the semicircular dial speedometer. */
public record DialSpeedometer(int radius, double maxSpeed) {
	public static final int PAD = 5;

	public int rim() { return radius + PAD; }
	public int width() { return 2 * rim() + 1; }
	public int height() { return rim() + 1; }
	public int hubX() { return rim(); }
	public int hubY() { return rim(); }

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

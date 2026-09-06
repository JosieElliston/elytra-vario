package jealoustone.elytravario.hud;

/**
 * The speedometer's geometry: where the dial sits in its box, and what angle a speed is.
 *
	 * <p>Separate from the drawing because it is arithmetic with no Minecraft in it, so it can
	 * be checked without a game.
 *
 * <h2>The angle</h2>
 *
 * <p>Zero is the left end of the arc, {@link #maxSpeed()} the right, and the scale is linear
 * between them, so half scale is straight up. Angles are returned in the GUI's own coordinate
 * system, where y grows downwards and a rotation therefore turns clockwise on screen — which
 * is why the sweep runs from {@code pi} through {@code 3pi/2} to {@code 2pi} rather than
 * backwards from {@code pi} to zero. A mark at angle {@code a} lies at
 * {@code (cos a, sin a)} times the radius from the hub, needles included; there is one
 * convention here and everything on the dial is placed with it.
 *
 * <h2>The box</h2>
 *
 * <p>The instrument is not a rectangle. It is a half disc of radius {@link #rim()} — the scale,
	 * plus a margin wide enough for the arc's own width and for nothing else. What the box is, then,
	 * is that shape's bounds, which is what positioning and screen clamping need.
 *
 * <p>The width is odd because a half disc spans a whole rim either side of its center column as
 * well as that column itself; an even box would leave one edge of the arc outside the bounds it
 * was placed by. The height counts the same way and comes to one rim plus the hub's own row,
 * which is the bottom of the shape.
 */
public record SpeedometerDial(int radius, double maxSpeed) {
	/** Clearance between the arc and the rim, in GUI pixels. Room for the arc's own width. */
	public static final int PAD = 5;

	/** Radius of the instrument's outer edge: the scale, plus the margin around it. */
	public int rim() {
		return radius + PAD;
	}

	public int width() {
		return 2 * rim() + 1;
	}

	public int height() {
		return rim() + 1;
	}

	/** The hub, relative to the top-left corner of the box. It is the center of the arc. */
	public int hubX() {
		return rim();
	}

	public int hubY() {
		return rim();
	}

	/**
	 * Where a speed falls on the scale, as a fraction of full scale clamped to {@code [0, 1]}.
	 *
	 * <p>A non-positive {@code maxSpeed} would be a dial with no scale at all; it reads as
	 * zero everywhere rather than dividing, so a bad setting is an unmoving needle and not a
	 * crash. Configuration keeps the bound well above that.
	 */
	public double fraction(double speed) {
		if (!(maxSpeed > 0.0) || !(speed > 0.0)) {
			return 0.0;
		}

		return Math.min(1.0, speed / maxSpeed);
	}

	/** The angle a needle or tick at this speed points along, in radians. */
	public double angle(double speed) {
		return Math.PI * (1.0 + fraction(speed));
	}

	/**
	 * Whether the scale has run out under this speed, in which case the needle is at a stop
	 * and its position is a limit rather than a reading.
	 *
	 * <p>A speed off either end of the scale still has to be drawn somewhere, and
	 * {@link #fraction} puts it at the nearer stop. This is what says so. {@code NaN} is
	 * counted as pegged for the same reason: it lands on zero like any other unplaceable
	 * value, and a needle resting on zero should not look like a reading of zero.
	 */
	public boolean pegged(double speed) {
		return !(speed <= maxSpeed) || speed < 0.0;
	}
}

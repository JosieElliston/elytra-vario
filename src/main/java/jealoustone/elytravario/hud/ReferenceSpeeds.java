package jealoustone.elytravario.hud;

/**
 * The two steady states the speedometers mark their readings against, held per component
 * because the states are not one speed each: a marker set is three different numbers that
 * happen to describe one flight, so a single line drawn across unlike quantities would be
 * wrong three ways.
 *
 * <p>Both speedometers read the same set, so the numbers live here rather than in either of
 * them. They are constants of the vanilla physics, not readings and not settings, so nothing
 * about them is configurable; only whether a set is drawn.
 */
record ReferenceSpeeds(double maxHorizontalSpeed, double terminal) {
	// Steady flight at +53.366 degrees, in blocks/tick. Vertical speed is a magnitude.
	private static final double MAX_HORIZONTAL_SPEED_XZ = 3.38879;
	private static final double MAX_HORIZONTAL_SPEED_Y = 1.00954;

	// Straight-down steady-state speed: (v - gravity) * vertical drag = v. Held straight down
	// there is no horizontal speed left to lose, so XZ terminal is zero and the total is the
	// vertical speed alone.
	private static final double TERMINAL_Y = 3.920003814700903;

	static final ReferenceSpeeds VERTICAL = new ReferenceSpeeds(MAX_HORIZONTAL_SPEED_Y, TERMINAL_Y);
	static final ReferenceSpeeds HORIZONTAL = new ReferenceSpeeds(MAX_HORIZONTAL_SPEED_XZ, 0.0);
	static final ReferenceSpeeds TOTAL = new ReferenceSpeeds(
			Math.hypot(MAX_HORIZONTAL_SPEED_XZ, MAX_HORIZONTAL_SPEED_Y), TERMINAL_Y);

	/** The max-horizontal-speed set: the brighter of the two, and the one shown by default. */
	static final int MAX_HORIZONTAL_SPEED_COLOR = 0xFFFFFFFF;

	/** The terminal set: gray, because it marks a state worth knowing rather than one to fly. */
	static final int TERMINAL_COLOR = 0xFF9AA0A6;
}

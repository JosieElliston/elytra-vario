package jealoustone.elytravario;

/**
 * Mutable in-memory settings. Nothing is persisted to disk yet, so these reset each launch.
 */
public final class VarioConfig {
	public static boolean enabled = true;
	public static boolean onlyWhileGliding = false;
	public static boolean showChart = true;
	public static boolean showLadder = true;
	public static boolean showFlightPath = true;

	/** Top-left corner of the HUD, in scaled GUI pixels. */
	public static int originX = 4;
	public static int originY = 4;

	/**
	 * Width of the readout panel. Values are right-aligned against this, so it has to be
	 * wide enough for the longest one plus its label.
	 */
	public static int panelWidth = 132;

	/** Ticks to average the energy rate over. Raw per-tick deltas are far too noisy to read. */
	public static int varioWindow = 10;

	/**
	 * Chart size, in pixels per block/tick. Both the width and the height are derived from
	 * this and the axis ranges, so a pixel is always worth the same change in speed on both
	 * axes, and widening a range grows the chart rather than rescaling it.
	 */
	public static double chartScale = 34.0;
	public static int chartTrailTicks = 100;

	/**
	 * Chart bounds in blocks/<em>tick</em>, matching the units elytrasim plots in, so the
	 * in-game chart and the sim's screenshots can be compared directly. Axis labels are
	 * rendered in blocks/second like the rest of the HUD.
	 *
	 * <p>The horizontal axis starts slightly below zero so that the origin sits inside the
	 * chart rather than on its edge. Neither horizontal speed nor its look-projection is
	 * bounded by that lower edge; it is there for legibility.
	 */
	public static double chartMinVxz = -0.5;
	public static double chartMaxVxz = 3.0;
	public static double chartMinVy = -1.5;
	public static double chartMaxVy = 1.5;

	/**
	 * Cursor colours, as ARGB. The two cursors sit on the same row of the chart and differ
	 * only in their horizontal position, so the gap between them is the sideslip. Set either
	 * to a grey such as {@code 0xFF6A7076} to demote it to a secondary reference.
	 */
	public static int cursorXzColor = 0xFFFFD633;
	public static int cursorForwardColor = 0xFF55CCFF;

	/**
	 * Pitch ladder spacing, in degrees. The rungs are placed by projection, so their spacing
	 * on screen is a tangent and not uniform; this is the angular step, not a pixel pitch.
	 */
	public static int ladderStepDegrees = 5;

	/**
	 * How far above and below the centre of the view the ladder reaches, as a fraction of
	 * half the view height. Rungs projecting outside this band are dropped rather than
	 * clipped, so the ladder stays a band across the middle instead of filling the screen.
	 *
	 * <p>A fraction rather than a pixel count because the band is an <em>angle</em>: the
	 * projection scale is itself proportional to the view height, so a fixed pixel band would
	 * cover a different slice of sky at every GUI scale and resolution. At the default field
	 * of view this reaches a little over 27 degrees either side of where you are looking.
	 *
	 * <p>The rung lengths below are pixel counts for the opposite reason — they are sized
	 * against the labels, which are text and do not scale with the view.
	 */
	public static double ladderBandFraction = 0.75;

	/**
	 * Rung geometry, in scaled GUI pixels, measured from the centre of the screen. Each rung
	 * is drawn twice, mirrored about the centre; {@code ladderCenterGap} is the half-width of
	 * the hole left in the middle so the ladder does not cross the crosshair.
	 */
	public static int ladderCenterGap = 22;
	public static int ladderRungLength = 30;

	/** The horizon is drawn longer than the other rungs so it reads at a glance. */
	public static int ladderHorizonLength = 46;

	/**
	 * Ticks to average velocity over for the flight path marker. One tick of velocity is
	 * noisy enough to make the marker visibly jitter.
	 */
	public static int flightPathWindow = 4;

	/**
	 * Ladder colours, as ARGB. The rungs are deliberately translucent: the ladder sits over
	 * the world rather than over a panel, and at full opacity it obscures more than it says.
	 */
	public static int horizonColor = 0xD8E8EAED;
	public static int rungColor = 0xA8C2C8CE;
	public static int ladderLabelColor = 0xFFB8BEC4;

	/**
	 * Colour of the flight path marker when it is pegged at the edge of the band, meaning
	 * the true flight path is off the ladder and the marker's position is a floor or ceiling
	 * rather than a reading.
	 */
	public static int flightPathPeggedColor = 0xFF6A7076;

	private VarioConfig() {
	}
}

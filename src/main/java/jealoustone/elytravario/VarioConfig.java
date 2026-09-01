package jealoustone.elytravario;

/**
 * Mutable in-memory settings. Nothing is persisted to disk yet, so these reset each launch.
 */
public final class VarioConfig {
	public static boolean enabled = true;
	public static boolean onlyWhileGliding = false;
	public static boolean showChart = true;
	public static boolean showLadder = true;

	/**
	 * Angle of attack, in its two forms: a row on the panel, and the flight path marker on
	 * the ladder, whose vertical gap from the crosshair is the same quantity drawn rather
	 * than printed. Both are off by default.
	 *
	 * <p>The code for both is kept and correct. Nothing in the pump-cycle research refers to
	 * angle of attack yet, so on instruments that are watched continuously they were clutter
	 * competing with readings actually being used — and whether it matters is an open
	 * question rather than a settled one.
	 *
	 * <p>Turning the marker back on also restores the only sideslip cue on the ladder; the
	 * chart's cyan cursor still shows sideslip either way.
	 */
	public static boolean showAngleOfAttack = false;
	public static boolean showFlightPath = false;

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
	public static int ladderStepDegrees = 10;

	/**
	 * Finer ticks are drawn within {@code ladderFineRangeDegrees} of wherever the camera is
	 * pointing, fading out at the edge of that range, so there is extra resolution exactly
	 * where the eye already is without that detail cluttering the rest of the ladder.
	 */
	public static int ladderFineStepDegrees = 2;
	public static double ladderFineRangeDegrees = 12.0;

	/**
	 * How far above and below the centre of the view the ladder reaches, as a fraction of
	 * half the view height. One is exactly the screen edge.
	 *
	 * <p>Asymmetric on purpose. Below the centre the hotbar and the rest of the vanilla HUD
	 * want the room, but above it there is nothing, and stopping short there costs real
	 * range: in a pump cycle the horizon is exactly the mark being aimed at while looking a
	 * long way down, and it is the first thing a low ceiling clips.
	 *
	 * <p>There is a hard limit above this that no setting can lift. The horizon is only in
	 * the rendered view at all while looking down less than half the field of view — 35
	 * degrees at the default 70 — because beyond that it is genuinely off the top of the
	 * screen. A wider FOV is the only thing that extends it.
	 *
	 * <p>Fractions rather than pixel counts because the band is an <em>angle</em>: the
	 * projection scale is itself proportional to the view height, so a fixed pixel band would
	 * cover a different slice of sky at every GUI scale and resolution.
	 *
	 * <p>The rung lengths below are pixel counts for the opposite reason — they are sized
	 * against the labels, which are text and do not scale with the view.
	 */
	public static double ladderBandFractionUp = 0.97;
	public static double ladderBandFractionDown = 0.75;

	/**
	 * The outer fraction of the band over which marks fade out, so that they thin away at
	 * the edge instead of blinking off a pixel at a time as the view moves.
	 */
	public static double ladderFadeFraction = 0.18;

	/**
	 * Rung geometry, in scaled GUI pixels, measured out from the centre of the screen. Each
	 * rung is drawn twice, mirrored about the centre; {@code ladderCenterGap} is the
	 * half-width of the hole left in the middle so the ladder does not cross the crosshair.
	 *
	 * <p>Length and weight both carry the tier, so an angle can be read from the pattern
	 * without reading any digits: fine ticks are stubs, ten-degree rungs are short and
	 * dashed, twenty-degree rungs are solid and longer, and the datum lines are longest.
	 */
	public static int ladderCenterGap = 22;
	public static int ladderFineLength = 7;
	public static int ladderMinorLength = 12;
	public static int ladderMajorLength = 26;
	public static int ladderPrimeLength = 40;

	/**
	 * The horizon is drawn a little longer than the other prime rungs. It shares their weight,
	 * as it should — nothing about the horizon is stronger than plus or minus forty — but it
	 * is the datum the whole ladder is measured from, so it stays findable at a glance. Set to
	 * zero to make all three identical.
	 */
	public static int ladderHorizonExtra = 10;

	/**
	 * Ticks to average velocity over for the flight path marker. One tick of velocity is
	 * noisy enough to make the marker visibly jitter.
	 */
	public static int flightPathWindow = 4;

	/**
	 * Ladder colours, as ARGB, ordered by tier. All are deliberately translucent: the ladder
	 * sits over the world rather than over a panel, and at full opacity it obscures more than
	 * it says. The fine ticks' alpha is the value at the centre of the ladder, scaled down to
	 * nothing at the edge of the fine range.
	 *
	 * <p>The minor and fine tiers share an RGB, so that nothing but strength separates them
	 * and the ramp from prime down to fine is monotonic.
	 *
	 * <p>Nothing distinguishes above the horizon from below it. The sky, the ground and the
	 * labelled datum line already say which way up the world is.
	 */
	public static int ladderPrimeColor = 0xE0E8EAED;
	public static int ladderMajorColor = 0xB4C6CCD2;
	public static int ladderMinorColor = 0x9AB4BAC0;
	public static int ladderFineColor = 0x4AB4BAC0;

	/**
	 * Rung labels. Weak on purpose: the tiers are meant to be read as a pattern in the
	 * periphery, and the digits are there for when you look straight at them.
	 */
	public static int ladderLabelColor = 0xA0949AA0;

	/**
	 * Colour of the flight path marker when it is pegged at the edge of the band, meaning
	 * the true flight path is off the ladder and the marker's position is a floor or ceiling
	 * rather than a reading.
	 */
	public static int flightPathPeggedColor = 0xFF6A7076;

	private VarioConfig() {
	}
}

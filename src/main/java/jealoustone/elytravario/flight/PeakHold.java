package jealoustone.elytravario.flight;

/**
 * Tracks the value reached at the last crest of a metric and holds it steady.
 *
 * <p>A plain maximum over a trailing window is unsatisfying here for two reasons: while the
 * metric is climbing the maximum simply equals the current value, so the readout chases and
 * cannot be read; and the peak silently expires when it falls out of the window, dropping
 * the number mid-glance. Neither problem is fixed by choosing a better window length.
 *
 * <p>So instead of a window this detects the turning point directly. The latched value
 * updates once, at the moment the metric stops rising, and then holds through the whole of
 * the following descent and the next climb. During that climb the held value is the previous
 * cycle's crest, which is exactly the number to beat — so the current value exceeding it is
 * meaningful rather than a glitch.
 *
 * <p>Turning points are confirmed by {@link #HYSTERESIS}, a movement of half a block against
 * the current direction, so that noise does not register as a crest.
 */
public final class PeakHold {
	/** Blocks the metric must move against its trend before a turning point counts. */
	private static final double HYSTERESIS = 0.5;

	/**
	 * Ticks after which a latched crest is treated as stale and the live running maximum is
	 * shown instead, so a peak from an earlier flight cannot linger indefinitely.
	 */
	private static final int STALE_TICKS = 600;

	private boolean rising = true;
	private double runningMax = Double.NEGATIVE_INFINITY;
	private double runningMin = Double.POSITIVE_INFINITY;
	private double latched = Double.NaN;
	private int ticksSinceCrest;

	public void update(double value) {
		if (ticksSinceCrest < Integer.MAX_VALUE) {
			ticksSinceCrest++;
		}

		if (rising) {
			if (value > runningMax) {
				runningMax = value;
			} else if (value < runningMax - HYSTERESIS) {
				// Crest confirmed: latch it and start watching for the trough.
				latched = runningMax;
				ticksSinceCrest = 0;
				rising = false;
				runningMin = value;
			}
		} else {
			if (value < runningMin) {
				runningMin = value;
			} else if (value > runningMin + HYSTERESIS) {
				// Trough confirmed: a new climb has begun, so start a fresh maximum. The
				// latched crest is deliberately left alone until this climb tops out.
				rising = true;
				runningMax = value;
			}
		}
	}

	/** The latched crest, or the live maximum if no crest has been seen recently. */
	public double value() {
		return Double.isNaN(latched) || ticksSinceCrest > STALE_TICKS ? runningMax : latched;
	}

	public void reset() {
		rising = true;
		runningMax = Double.NEGATIVE_INFINITY;
		runningMin = Double.POSITIVE_INFINITY;
		latched = Double.NaN;
		ticksSinceCrest = 0;
	}
}

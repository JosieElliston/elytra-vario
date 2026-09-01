package jealoustone.elytravario.flight;

/**
 * Divides flight into pump cycles at each apex and reports the state reached there.
 *
 * <p>The cycle boundary is the altitude maximum, detected as vertical speed passing from
 * positive to negative. That is the moment worth reading — it is where "how high did I get"
 * is answered — and latching there refreshes the display as the apex is reached rather than
 * half a cycle later.
 *
 * <p>One clock drives every reading, and the whole {@link Sample} from the apex is kept
 * rather than each energy being tracked separately. Independent per-metric peak detectors
 * would latch at different instants — kinetic energy crests at the bottom of the dive,
 * potential energy at the top — so the three held figures would not sum correctly. Taking
 * them from a single instant keeps KE + PE = TE.
 *
 * <p>The apex fires once vertical speed passes {@link #VY_DEADBAND} downwards, which is a
 * little after the true apex, so the sample actually latched is the highest one seen since
 * the previous boundary rather than the one present when the detector tripped.
 */
public final class CycleTracker {
	/**
	 * Vertical speed, in blocks/tick, needed to count as climbing or descending. A pump
	 * swings vertical speed by around a block per tick, so this only suppresses jitter about
	 * zero during near-level flight, which would otherwise latch spurious apexes.
	 */
	private static final double VY_DEADBAND = 0.05;

	/** Ticks after which a latched apex is stale and the running best is shown instead. */
	private static final int STALE_TICKS = 600;

	private boolean climbing;
	private Sample bestSinceBoundary;
	private Sample apex;
	private double lastGain = Double.NaN;
	private int ticksSinceApex;

	public void update(Sample sample) {
		if (ticksSinceApex < Integer.MAX_VALUE) {
			ticksSinceApex++;
		}

		if (bestSinceBoundary == null || sample.potentialHeight() > bestSinceBoundary.potentialHeight()) {
			bestSinceBoundary = sample;
		}

		if (!climbing) {
			if (sample.vy() > VY_DEADBAND) {
				climbing = true;
			}
		} else if (sample.vy() < -VY_DEADBAND) {
			// Past the apex, so close the cycle out.
			climbing = false;

			if (apex != null) {
				lastGain = bestSinceBoundary.totalHeight() - apex.totalHeight();
			}

			apex = bestSinceBoundary;
			bestSinceBoundary = null;
			ticksSinceApex = 0;
		}
	}

	/**
	 * The apex of the last completed cycle, or the best seen so far when no apex has been
	 * reached recently. Null before anything has been recorded.
	 */
	private Sample displayed() {
		if (apex != null && ticksSinceApex <= STALE_TICKS) {
			return apex;
		}

		return bestSinceBoundary;
	}

	public double peakKineticHeight() {
		Sample s = displayed();
		return s == null ? Double.NaN : s.kineticHeight();
	}

	public double peakPotentialHeight() {
		Sample s = displayed();
		return s == null ? Double.NaN : s.potentialHeight();
	}

	public double peakTotalHeight() {
		Sample s = displayed();
		return s == null ? Double.NaN : s.totalHeight();
	}

	/**
	 * Total energy gained between the last two apexes: what the cycle was worth. A cycle
	 * that closes in velocity space has no net kinetic change, so this is also the height
	 * the cycle bought.
	 */
	public double lastCycleGain() {
		return lastGain;
	}

	public void reset() {
		climbing = false;
		bestSinceBoundary = null;
		apex = null;
		lastGain = Double.NaN;
		ticksSinceApex = 0;
	}
}

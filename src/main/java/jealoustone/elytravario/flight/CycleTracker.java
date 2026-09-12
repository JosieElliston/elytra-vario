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
 * <p>The apex fires once vertical speed passes {@link #DESCENT_ENTER} downwards, which is a
 * little after the true apex, so the sample actually latched is the highest one seen since
 * the climb began rather than the one present when the detector tripped. The search starts
 * at the climb rather than at the previous boundary so that a cycle ending lower than it
 * started still latches its own apex; see {@link #update}.
 */
public final class CycleTracker {
	/**
	 * Vertical speed, in blocks/tick, below which the player counts as descending. A pump
	 * swings vertical speed by around a block per tick, so this only suppresses jitter about
	 * zero during near-level flight, which would otherwise latch spurious apexes.
	 */
	private static final double DESCENT_ENTER = -0.05;

	/** Vertical speed above which a descent is considered over and the apex re-arms. */
	private static final double DESCENT_EXIT = -0.01;

	/** Ticks after which a latched apex is stale and the running best is shown instead. */
	private static final int STALE_TICKS = 600;

	private boolean descending;
	private Sample bestSinceBoundary;
	private Sample apex;
	private double lastGain = Double.NaN;
	private int ticksSinceApex = Integer.MAX_VALUE;

	public void update(Sample sample) {
		if (ticksSinceApex < Integer.MAX_VALUE) {
			ticksSinceApex++;
		}

		if (bestSinceBoundary == null || sample.potentialHeight() > bestSinceBoundary.potentialHeight()) {
			bestSinceBoundary = sample;
		}

		double vy = sample.vy();

		if (descending) {
			if (vy > DESCENT_EXIT) {
				descending = false;

				// The climb starts here, and the apex being looked for is the top of it. The
				// running best has to restart with it: it has been carrying the tail of the
				// previous descent, which begins a tick or two past the previous apex and so
				// sits only a fraction of a block below it. Any cycle that ends lower than it
				// started — which is most of them, since a pump trades altitude for speed —
				// would otherwise find that leftover sample higher than its own apex and latch
				// the previous cycle's apex a second time, putting every reading a whole cycle
				// behind.
				bestSinceBoundary = sample;
			}

			return;
		}

		if (vy >= DESCENT_ENTER) {
			return;
		}

		// A descent has begun, so whatever height was reached is now behind us. Keyed on
		// starting to descend rather than on having climbed first, so that walking off a
		// ledge closes a cycle too: there is no climb before it, but there is still a
		// height that was just left behind.
		descending = true;

		if (apex != null) {
			lastGain = bestSinceBoundary.totalHeight() - apex.totalHeight();
		}

		apex = bestSinceBoundary;
		bestSinceBoundary = null;
		ticksSinceApex = 0;
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
		descending = false;
		bestSinceBoundary = null;
		apex = null;
		lastGain = Double.NaN;
		ticksSinceApex = Integer.MAX_VALUE;
	}
}

package jealoustone.elytravario.flight;

/**
 * Divides flight into pump cycles at each apex and reports the state reached there.
 *
 * <p>The cycle boundary is the apex, and the apex is exactly where vertical speed turns
 * from positive to negative. That is the moment worth reading — it is where "how high did I
 * get" is answered — and latching there refreshes the display as the apex is reached rather
 * than half a cycle later.
 *
 * <p>Nothing smooths the turn. A deadband around zero would suppress the odd cycle closed by
 * hovering either side of it, but those are rare, and it would buy that by making the
 * boundary something other than what it says it is. The rule as written is one the player
 * can watch happen: the cycle turns over when the vertical speed readout changes sign.
 *
 * <p>The sample latched is the one <em>before</em> the turn, since that is the last tick
 * still rising and so the highest. Keyed on beginning to fall rather than on having climbed
 * first, so that walking off a ledge closes a cycle too: there is no climb before it, but
 * there is still a height that was just left behind.
 *
 * <p>An apex stands until the next one replaces it, however long that takes. Nothing expires
 * it on a timer: a reading that has not moved for a while is saying that no peak has been
 * reached since, which is the true answer and a visible one, where a readout that blanked
 * itself after some number of ticks would only look like a fault. {@link FlightRecorder}
 * clears the tracker outright when the player teleports or changes dimension, which is the
 * one case where the held apex really is meaningless.
 *
 * <p>One clock drives every reading, and the whole {@link Sample} from the apex is kept
 * rather than each energy being tracked separately. Independent per-metric peak detectors
 * would latch at different instants — kinetic energy crests at the bottom of the dive,
 * potential energy at the top — so the three held figures would not sum correctly. Taking
 * them from a single instant keeps KE + PE = TE.
 */
public final class CycleTracker {
	private Sample previous;
	private boolean falling;
	private Sample apex;
	private double lastGain = Double.NaN;

	public void update(Sample sample) {
		// Zero counts as still rising, so a level stretch ends at its last tick rather than
		// its first, and flat ground does not close a cycle every tick.
		boolean nowFalling = sample.vy() < 0.0;

		if (nowFalling && !falling) {
			// Nothing has been seen above the first sample, so it stands as its own apex.
			Sample top = previous == null ? sample : previous;

			if (apex != null) {
				lastGain = top.totalHeight() - apex.totalHeight();
			}

			apex = top;
		}

		falling = nowFalling;
		previous = sample;
	}

	/** The apex of the last completed cycle; {@code NaN} before there has been one. */
	public double peakPotentialHeight() {
		return apex == null ? Double.NaN : apex.potentialHeight();
	}

	public double peakTotalHeight() {
		return apex == null ? Double.NaN : apex.totalHeight();
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
		previous = null;
		falling = false;
		apex = null;
		lastGain = Double.NaN;
	}
}

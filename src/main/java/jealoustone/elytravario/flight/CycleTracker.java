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
 * <p>One clock drives every reading, and the whole {@link Sample} from the apex is kept
 * rather than each energy being tracked separately. Independent per-metric peak detectors
 * would latch at different instants — kinetic energy crests at the bottom of the dive,
 * potential energy at the top — so the three held figures would not sum correctly. Taking
 * them from a single instant keeps KE + PE = TE.
 */
public final class CycleTracker {
	/** Ticks after which a latched apex is too old to be the reference any more. */
	private static final int STALE_TICKS = 600;

	private Sample previous;
	private boolean falling;
	private Sample apex;
	private double lastGain = Double.NaN;
	private int ticksSinceApex = Integer.MAX_VALUE;

	public void update(Sample sample) {
		if (ticksSinceApex < Integer.MAX_VALUE) {
			ticksSinceApex++;
		}

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
			ticksSinceApex = 0;
		}

		falling = nowFalling;
		previous = sample;
	}

	/** The apex of the last completed cycle, or null when there is none recent enough. */
	private Sample displayed() {
		return ticksSinceApex <= STALE_TICKS ? apex : null;
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
		previous = null;
		falling = false;
		apex = null;
		lastGain = Double.NaN;
		ticksSinceApex = Integer.MAX_VALUE;
	}
}

package jealoustone.elytravario.flight;

/**
 * Latches the three transitions of an elytra bounce: ground contact, leaving the ground, and
 * deploying the elytra. Positions and velocities all come from the same tick samples, so the
 * displayed differences cannot mix render and physics time.
 */
public final class BounceTracker {
	/** A latched event and its tick on this tracker's monotonic clock. */
	public record Event(Sample sample, long tick) { }

	private long tick;
	private Sample previous;
	private Event touch;
	private Event leave;
	private Event deploy;

	public void update(Sample sample) {
		tick++;
		if (previous == null && sample.grounded()) {
			// A recorder beginning on the ground has a valid baseline for the first launch even
			// though it did not observe the landing transition that put the player there.
			touch = new Event(sample, tick);
		} else if (previous != null) {
			if (!previous.grounded() && sample.grounded()) {
				touch = new Event(sample, tick);
				leave = null;
				deploy = null;
			}
			if (previous.grounded() && !sample.grounded()) {
				leave = new Event(sample, tick);
				deploy = null;
			}
			if (!previous.gliding() && sample.gliding()) {
				deploy = new Event(sample, tick);
			}
		}
		previous = sample;
	}

	public Event touch() { return touch; }
	public Event leave() { return leave; }
	public Event deploy() { return deploy; }

	public void reset() {
		tick = 0;
		previous = null;
		touch = null;
		leave = null;
		deploy = null;
	}

	/** Elapsed ticks from {@code from} to {@code to}, or -1 until both exist in that order. */
	public static long ticks(Event from, Event to) {
		return from == null || to == null || to.tick < from.tick ? -1 : to.tick - from.tick;
	}
}

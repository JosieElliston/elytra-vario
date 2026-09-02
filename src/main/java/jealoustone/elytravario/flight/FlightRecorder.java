package jealoustone.elytravario.flight;

import java.util.Arrays;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed-size ring buffer of per-tick {@link Sample}s.
 *
 * <p>The interesting readouts are rates of change — the variometer above all — and a
 * derivative cannot be recovered from a single render frame. Frames also run at the
 * monitor's refresh rate while physics runs at 20 Hz, so sampling on the tick and
 * differencing over a window is both correct and naturally smoothing.
 */
public final class FlightRecorder {
	/** Ten seconds at 20 ticks/second. */
	public static final int CAPACITY = 200;

	/**
	 * Movement in a single tick beyond which we assume a teleport rather than flight. Elytra
	 * speeds stay well under two blocks/tick even with rockets, so this only trips on
	 * discontinuities, which would otherwise register as an enormous bogus energy change.
	 */
	private static final double DISCONTINUITY_SQR = 100.0;

	private final Sample[] buffer = new Sample[CAPACITY];
	private int head = -1;
	private int size;

	private final CycleTracker cycles = new CycleTracker();

	private OptimalPitch optimalPitch;

	/**
	 * The cues that cost real arithmetic but are not wanted every tick, memoised for the tick
	 * they were asked on. The horizon and the window double as the cache keys, so a zero means
	 * nothing has been computed yet — no window or horizon is ever zero.
	 *
	 * <p>Computed on demand rather than in {@link #tick} because their parameters are display
	 * settings, and this package is deliberately free of {@code VarioConfig}: everything here
	 * is a function of its arguments. Memoising keeps the once-per-tick property that matters
	 * — the HUD asks once a frame, and every frame within a tick gets the same answer for the
	 * price of one.
	 */
	private OptimalPitch lookahead;
	private int lookaheadHorizon;
	private float flightPathHold = Float.NaN;
	private int flightPathHoldWindow;

	public void tick(LocalPlayer player) {
		Vec3 position = player.position();
		Sample previous = latest();

		// Velocity is measured as the change in position over the tick, so that it reflects
		// movement that actually happened. getDeltaMovement is the velocity the player is
		// *trying* to have, which gravity keeps pointing downwards even while stood on the
		// ground, since collision cancels it after the fact rather than by changing it.
		// The two are identical in free flight and differ only on contact.
		Vec3 velocity;

		if (previous == null) {
			velocity = player.getDeltaMovement();
		} else {
			velocity = position.subtract(previous.x(), previous.y(), previous.z());

			if (velocity.lengthSqr() > DISCONTINUITY_SQR) {
				clear();
				velocity = player.getDeltaMovement();
			}
		}

		Sample sample = new Sample(
				position.x, position.y, position.z,
				velocity.x, velocity.y, velocity.z,
				player.getXRot(),
				player.getYRot(),
				player.getGravity(),
				player.isFallFlying());

		push(sample);
		cycles.update(sample);

		// Once per tick, not once per frame. The search is cheap — a couple of hundred ticks
		// of vector arithmetic — but it is a function of the tick's state, so recomputing it
		// for every frame would burn a few hundred thousand of them a second to arrive at
		// the same answer, and would let two HUD elements disagree within one frame.
		optimalPitch = OptimalPitch.search(sample, 1);

		// The tick's state has moved, so last tick's answers are stale.
		lookaheadHorizon = 0;
		flightPathHoldWindow = 0;
	}

	/**
	 * The pitch that would gain the most energy over the next tick, or null when there is
	 * none to report — which is most of the time, since it is only defined while gliding.
	 */
	public OptimalPitch optimalPitch() {
		return optimalPitch;
	}

	/**
	 * The same search over a longer horizon: the constant pitch that would gain the most
	 * energy over the next {@code horizon} ticks. Null under the same conditions as
	 * {@link #optimalPitch()}.
	 *
	 * <p>One horizon is cached at a time, which is all the HUD asks for. Alternating between
	 * two would recompute both every frame rather than returning a wrong answer.
	 */
	public OptimalPitch optimalPitch(int horizon) {
		if (horizon <= 1) {
			return optimalPitch;
		}

		if (lookaheadHorizon != horizon) {
			Sample sample = latest();
			lookahead = sample == null ? null : OptimalPitch.search(sample, horizon);
			lookaheadHorizon = horizon;
		}

		return lookahead;
	}

	/**
	 * The pitch that would hold the current flight path angle, or {@code NaN} when no pitch
	 * would — including whenever the player is not gliding, since the rule is about a wing.
	 *
	 * <p>Searched against the velocity averaged over {@code window} ticks rather than the last
	 * one. The answer is a low-gain function of that velocity, so a single tick's wobble would
	 * arrive at the ladder multiplied by about fifteen; see {@link FlightPathHold}.
	 */
	public float flightPathHold(int window) {
		int ticks = Math.max(1, window);

		if (flightPathHoldWindow != ticks) {
			Sample sample = latest();
			flightPathHold = sample == null || !sample.gliding()
					? Float.NaN
					: FlightPathHold.search(smoothedVelocity(ticks), sample.yaw(),
							sample.gravity());
			flightPathHoldWindow = ticks;
		}

		return flightPathHold;
	}

	/**
	 * The direction of travel as a pitch, positive descending, taken over the same smoothing
	 * window — the flight path marker's vertical position, as a number.
	 *
	 * <p>{@code NaN} when there is no movement at all and so no direction to report. There is
	 * no speed floor beyond that, because every caller already gates on gliding and a glide
	 * slow enough for the reading to be noise is not a state the player can hold.
	 */
	public double flightPathPitch(int window) {
		Vec3 velocity = smoothedVelocity(Math.max(1, window));
		double horizontal = velocity.horizontalDistance();

		if (velocity.lengthSqr() == 0.0) {
			return Double.NaN;
		}

		return -Math.toDegrees(Math.atan2(velocity.y, horizontal));
	}

	/** Energies as they stood at the last apex; see {@link CycleTracker}. */
	public double peakPotentialHeight() {
		return cycles.peakPotentialHeight();
	}

	public double peakTotalHeight() {
		return cycles.peakTotalHeight();
	}

	/** Total energy gained across the last complete cycle. */
	public double lastCycleGain() {
		return cycles.lastCycleGain();
	}


	private void push(Sample sample) {
		head = (head + 1) % CAPACITY;
		buffer[head] = sample;

		if (size < CAPACITY) {
			size++;
		}
	}

	/**
	 * Drops all history. Called when the player goes away, since altitude is measured from
	 * an arbitrary origin and a teleport or dimension change would otherwise show up as an
	 * enormous spurious energy change.
	 */
	public void clear() {
		head = -1;
		size = 0;
		Arrays.fill(buffer, null);
		cycles.reset();
		optimalPitch = null;
		lookahead = null;
		lookaheadHorizon = 0;
		flightPathHold = Float.NaN;
		flightPathHoldWindow = 0;
	}

	public int size() {
		return size;
	}

	/** The newest sample, or null if nothing has been recorded yet. */
	public Sample latest() {
		return size == 0 ? null : buffer[head];
	}

	/** The sample {@code ticksAgo} ticks before the newest, or null if history is too short. */
	public Sample ago(int ticksAgo) {
		if (ticksAgo < 0 || ticksAgo >= size) {
			return null;
		}

		return buffer[Math.floorMod(head - ticksAgo, CAPACITY)];
	}

	/**
	 * Mean velocity over the last {@code window} ticks, in blocks/tick.
	 *
	 * <p>Taken as the straight-line displacement over the window rather than as a mean of
	 * the per-tick velocities, which is the same quantity but does not accumulate rounding
	 * over the buffer.
	 *
	 * <p>The flight path marker needs this rather than {@link #latest()}: a single tick of
	 * velocity is noisy enough that an un-smoothed marker jitters by several degrees, and
	 * unlike the numeric readouts a moving symbol makes that obvious.
	 */
	public Vec3 smoothedVelocity(int window) {
		Sample now = latest();

		if (now == null) {
			return Vec3.ZERO;
		}

		Sample then = ago(window);

		if (then == null) {
			return new Vec3(now.vx(), now.vy(), now.vz());
		}

		return new Vec3(now.x() - then.x(), now.y() - then.y(), now.z() - then.z())
				.scale(1.0 / window);
	}

	/**
	 * Total-energy variometer reading, in blocks/second: how fast energy height is changing,
	 * averaged over {@code window} ticks. This is the reading that says whether a pump cycle
	 * is net gaining or losing, independent of whether you happen to be climbing right now.
	 */
	public double energyRate(int window) {
		Sample now = latest();
		Sample then = ago(window);

		if (now == null || then == null) {
			return 0.0;
		}

		return (now.totalHeight() - then.totalHeight()) / window * 20.0;
	}
}

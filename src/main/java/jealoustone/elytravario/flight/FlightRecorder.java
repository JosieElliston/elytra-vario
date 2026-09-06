package jealoustone.elytravario.flight;

import java.util.Arrays;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed-size ring buffer of per-tick {@link Sample}s.
 *
 * <p>Tick history supports the velocity trail, direction markers, and cycle
 * measurements. Sampling at the physics tick rate keeps these independent of render FPS.
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
	private boolean flightPathHoldComputed;

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
		flightPathHoldComputed = false;
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
	 * <p>Uses the latest sampled velocity and caches the answer for the current tick.
	 */
	public float flightPathHold() {
		if (!flightPathHoldComputed) {
			Sample sample = latest();
			flightPathHold = sample == null || !sample.gliding()
					? Float.NaN
					: FlightPathHold.search(velocity(), sample.yaw(), sample.gravity());
			flightPathHoldComputed = true;
		}

		return flightPathHold;
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
		flightPathHoldComputed = false;
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

	/** Latest sampled velocity in blocks/tick, without temporal smoothing. */
	public Vec3 velocity() {
		Sample sample = latest();
		return sample == null ? Vec3.ZERO : new Vec3(sample.vx(), sample.vy(), sample.vz());
	}
}

package jealoustone.elytravario.flight;

import net.minecraft.world.phys.Vec3;

/**
 * The pitch that would leave the flight path angle where it already is: point here and the
 * direction you are travelling in one tick's time is the direction you are travelling now.
 *
 * <p>This is the dive rule. Measured against an optimised three-hundred-tick cycle, the
 * optimal pitch through the whole descent is the pitch that holds the flight path angle, to
 * within a few tenths of a degree — <b>0.73 degrees RMS over a hundred and fifty ticks, with
 * no parameter to tune</b>. Nothing else about the dive needs to be known: not the speed, not
 * how far through it you are, not where the cycle started. The angle you have is enough to
 * place the pitch.
 *
 * <p>It is the counterpart to {@link OptimalPitch}, and it exists because that class cannot
 * do this. Energy is being <em>spent</em> through the dive, so every lookahead worth the name
 * disagrees about how to spend it: short horizons say stay level, long ones say zoom, and the
 * truth is at neither. A rule that never mentions energy fits it instead.
 *
 * <h2>What leaks is a policy, not this reading</h2>
 *
 * <p>This holds the angle exactly. The target is the angle the latest velocity already has,
 * so there is nothing for the search to decay towards and no rate at which it could: the bug
 * moves when the flight moves and not otherwise. Worth saying because the rule is easy to
 * confuse with the <em>flown</em> version of itself, which does leak. A controller aiming each
 * tick a twentieth of the way from the angle it has towards a floor near 17.7 degrees below
 * the horizon reaches 96 percent of the optimal cycle's climb, where holding exactly — with
 * the entry pitch retuned to suit it, since the leak was doing that job too — reaches 89. An
 * exact hold keeps whatever angle the dive was entered at, and the entry angle is not the one
 * worth spending a whole descent at.
 *
 * <p>That rate is not derived and is best treated as arbitrary: anywhere from a twentieth to
 * an eighth of the gap per tick flies within noise of the same, and past about an eighth the
 * cycle stops climbing at all. So there is no leak here worth copying — only a number that
 * would have to be picked, to make a readout drift away from what it is reading.
 *
 * <p>The optimum's own flight path angle does drift, by some ten degrees across the descent,
 * which is where the fit above spends its error. This sits two or three degrees nose-down of
 * the optimum through the first stretch of the dive, while the angle is still coming down
 * fast, and within a twentieth of a degree of it through the middle, where the angle is nearly
 * stationary and the optimum is, tick for tick, holding what it has.
 *
 * <p>The floor a flown leak aims at is derivable rather than fitted. It is the flight path
 * angle of the steady glide that maximises forward speed, which in vanilla's physics is a
 * pitch of about 53 degrees nose-down at 3.39 blocks per tick, descending at 16.58 degrees —
 * a degree below where tuning puts it, across a span of angle the speed curve is flat enough
 * to be indifferent to. Either way it is a target such a policy approaches and never a bound
 * anything is held inside: steering straight at that angle instead of holding is a far weaker
 * rule, 34 degrees RMS, and saturates against the nose-up stop for the first sixty ticks of
 * the dive.
 *
 * <h2>Holding the angle is not pointing along it</h2>
 *
 * <p>Worth saying because it is the obvious misreading, and because the ladder draws both. By
 * the end of a dive the nose is about thirty degrees <em>below</em> the flight path — pitch
 * near 47, flight path near 17 — so this bug and the flight path bug are nowhere near each
 * other, and the gap between them is the angle of attack the wing needs to make that hold.
 * What is being held is the velocity vector's direction, not the nose's.
 *
 * <h2>Why it is bisected and not minimised</h2>
 *
 * <p>The natural phrasing — the pitch minimising how much the angle moves — is a trap. At low
 * speed two separate pitches hold a given angle, one nose-down and one nose-up, and a plain
 * search over the residual's magnitude oscillates between the branches from tick to tick.
 * Flown, that scores worse than doing nothing.
 *
 * <p>So the residual's <em>sign</em> is what is searched, from the nose-down stop upwards, and
 * the first crossing found is the answer. That is the nose-down root by construction, which
 * is the branch the dive is on. Where the residual never changes sign there is no such pitch
 * — a near-vertical fall cannot be sustained by any attitude — and the search says so by
 * returning {@code NaN} rather than by picking the least bad degree.
 *
 * <p>The reading is low-gain: a degree of pitch moves the next tick's flight path angle by
 * something like a fifteenth of a degree. That makes it forgiving to fly and delicate to
 * <em>display</em>, since it multiplies any wobble in the measured velocity by about fifteen
 * on the way to the answer — the caller uses the latest sampled velocity so the reading adds no smoothing lag.
 */
public final class FlightPathHold {
	/**
	 * How far from level the scan reaches, matching {@link OptimalPitch}'s sweep for the same
	 * reason: vanilla's cosine table degenerates at exactly ninety degrees nose-up.
	 */
	private static final int LIMIT = 89;

	/**
	 * Bisection steps taken inside the winning one-degree bracket. Twenty narrows it to about
	 * a millionth of a degree and costs twenty ticks of physics on top of the scan's hundred
	 * and seventy-nine, which together come to under two microseconds.
	 *
	 * <p>Whether that precision is real depends on which way the nose is pointing, which is a
	 * quirk of vanilla's arithmetic worth writing down. Nose-<em>down</em> the residual is
	 * smooth: the look vector's horizontal part divides out of every term it appears in, so
	 * the only pitch left in the tick is inside the lift term's {@code Math.cos}, which is
	 * libm and continuous. Measured, the residual moves by under a millionth of a degree per
	 * ten-thousandth of a degree of pitch, and the bisection lands on roots with residuals
	 * around {@code 1e-7}.
	 *
	 * <p>Nose-<em>up</em> the climb term calls {@code Mth.sin} directly, and that is a
	 * 65536-entry table, so the residual becomes a staircase with steps of up to about
	 * {@code 5e-4} degrees. Bisection then converges on the step rather than on a true root,
	 * to a pitch uncertainty of roughly a hundredth of a degree once the low gain has
	 * multiplied it back up. That is still two orders of magnitude finer than the ladder can
	 * draw, so nothing is done about it — but it is why the nose-up roots come back with
	 * residuals four thousand times larger than the nose-down ones, and that is the table,
	 * not a bug in the search.
	 */
	private static final int BISECT_STEPS = 20;

	/**
	 * Speed below which there is no flight path to hold. Matches the pitch ladder's own floor
	 * for drawing a direction of travel.
	 */
	private static final double MIN_SPEED_SQR = 0.02 * 0.02;

	private FlightPathHold() {
	}

	/**
	 * The nose-down pitch that holds this velocity's flight path angle, in degrees in
	 * Minecraft's convention, or {@code NaN} when no pitch does.
	 *
	 * @param velocity latest sampled velocity in blocks per tick
	 */
	public static float search(Vec3 velocity, float yaw, double gravity) {
		if (velocity.lengthSqr() < MIN_SPEED_SQR) {
			return Float.NaN;
		}

		double target = flightPathAngle(velocity);

		if (Double.isNaN(target)) {
			return Float.NaN;
		}

		// Scanned from the nose-down stop towards nose-up, so the first crossing found is the
		// nose-down root even where there are two.
		double previous = residual(velocity, yaw, gravity, LIMIT, target);

		if (previous == 0.0) {
			return LIMIT;
		}

		for (int pitch = LIMIT - 1; pitch >= -LIMIT; pitch--) {
			double current = residual(velocity, yaw, gravity, pitch, target);

			if (current == 0.0) {
				return pitch;
			}

			if ((current < 0.0) != (previous < 0.0)) {
				return bisect(velocity, yaw, gravity, target, pitch, pitch + 1.0, current);
			}

			previous = current;
		}

		return Float.NaN;
	}

	/**
	 * Narrows a bracket the residual changes sign across. Written to take the sign at the low
	 * end rather than to assume which way round the crossing runs: the residual does grow with
	 * nose-down pitch over the branch that matters, but that is an observation about elytra
	 * physics and not something worth encoding as an assertion here.
	 */
	private static float bisect(Vec3 velocity, float yaw, double gravity, double target,
			double low, double high, double lowResidual) {
		for (int step = 0; step < BISECT_STEPS; step++) {
			double middle = (low + high) / 2.0;
			double residual = residual(velocity, yaw, gravity, middle, target);

			if ((residual < 0.0) == (lowResidual < 0.0)) {
				low = middle;
				lowResidual = residual;
			} else {
				high = middle;
			}
		}

		return (float) ((low + high) / 2.0);
	}

	/** How far the flight path angle moves over one tick at this pitch, in degrees. */
	private static double residual(Vec3 velocity, float yaw, double gravity, double pitch,
			double target) {
		Vec3 next = ElytraPhysics.updateFallFlyingMovement(velocity, (float) pitch, yaw, gravity);
		return flightPathAngle(next) - target;
	}

	/**
	 * The direction of travel as a pitch, positive descending — the same quantity and the same
	 * convention as {@link Sample#flightPathPitch()}, for a velocity that is not a sample's.
	 */
	private static double flightPathAngle(Vec3 velocity) {
		double horizontal = velocity.horizontalDistance();

		if (horizontal == 0.0 && velocity.y == 0.0) {
			return Double.NaN;
		}

		return -Math.toDegrees(Math.atan2(velocity.y, horizontal));
	}
}

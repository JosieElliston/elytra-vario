package jealoustone.elytravario.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A transcription of the two vanilla methods an elytra's motion depends on, so that the
 * next tick can be predicted for a pitch the player is not currently holding.
 *
 * <p>This is the one place in the mod that <em>simulates</em> rather than observes, and it
 * exists only to answer one question: which pitch would gain the most energy this tick. See
 * {@link OptimalPitch}.
 *
 * <p>Both methods are copied from the 26.2 jar's own bytecode rather than written afresh,
 * because elytra motion is a chain of single-precision operations whose rounding is
 * load-bearing. Two habits keep it bit-exact with the game:
 *
 * <ul>
 * <li><b>Vanilla's own {@link Mth} is used, not {@link Math}.</b> {@code Mth.sin} and
 *     {@code Mth.cos} are a 65536-entry lookup table, not the libm functions, so they differ
 *     from the true sine by up to about 5e-5. Where vanilla calls {@code Math.cos} — as the
 *     lift term does, and only the lift term — this calls {@code Math.cos} too.</li>
 * <li><b>Float constants stay floats.</b> The drag factors are written {@code 0.99F} and
 *     {@code 0.98F} because that is what vanilla widens to a double: {@code 0.99F} is
 *     {@code 0.9900000095367432}, which is not {@code 0.99}.</li>
 * </ul>
 *
 * <p>Verified against the elytrasim project, which plots the same physics: for a velocity of
 * {@code (0, 0.065042850, 1.061452210)} both agree on an energy change of
 * {@code -0.008830925} at a pitch of {@code 2.204123497} degrees and {@code -0.008820132} at
 * a pitch of zero, to nine decimal places.
 */
public final class ElytraPhysics {
	private ElytraPhysics() {
	}

	/**
	 * The unit vector a player at this rotation is looking along; a copy of
	 * {@code Entity.calculateViewVector}.
	 *
	 * <p>Vanilla's method is public, but it is an instance method, and the search needs a
	 * look vector for a hundred and eighty pitches the player is not at. Note the negated
	 * yaw and the negated pitch sine: Minecraft's pitch is positive downwards.
	 */
	public static Vec3 viewVector(float pitch, float yaw) {
		float pitchRadians = pitch * Mth.DEG_TO_RAD;
		float yawRadians = -yaw * Mth.DEG_TO_RAD;
		float yawCos = Mth.cos(yawRadians);
		float yawSin = Mth.sin(yawRadians);
		float pitchCos = Mth.cos(pitchRadians);
		float pitchSin = Mth.sin(pitchRadians);
		return new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
	}

	/**
	 * One tick of elytra flight: the velocity a player holding this rotation would have
	 * after the next physics step, given the velocity they have now. A copy of
	 * {@code LivingEntity.updateFallFlyingMovement}.
	 *
	 * <p>The four stages are, in order: gravity reduced by the lift the wing makes at this
	 * pitch; a nose-down trade of descent for forward speed; a nose-up trade of forward
	 * speed for climb, weighted 3.2 to 1 in the climb's favour; and a turn that swings
	 * horizontal velocity towards where the nose points without changing its length. Then
	 * drag, which is the only stage that can never give anything back.
	 *
	 * <p>Position is not advanced here. Vanilla adds the returned velocity to the position
	 * afterwards, and {@link OptimalPitch} accounts for that altitude itself.
	 *
	 * <p>Only the free-flight part is modelled: no collision, no block friction, no rocket.
	 * That is all the search needs, since it looks one tick ahead through open air.
	 *
	 * @param gravity vanilla passes its <em>effective</em> gravity here, which differs from
	 *                the plain gravity this is called with only under Slow Falling
	 */
	public static Vec3 updateFallFlyingMovement(Vec3 velocity, float pitch, float yaw,
			double gravity) {
		Vec3 look = viewVector(pitch, yaw);
		float leanAngle = pitch * Mth.DEG_TO_RAD;
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double moveHorizontal = velocity.horizontalDistance();

		// The one place vanilla uses Math rather than Mth, so the lookup table's error does
		// not enter the lift term.
		double lift = Mth.square(Math.cos((double) leanAngle));

		velocity = velocity.add(0.0, gravity * (-1.0 + lift * 0.75), 0.0);

		if (velocity.y < 0.0 && lookHorizontal > 0.0) {
			double convert = velocity.y * -0.1 * lift;
			velocity = velocity.add(
					look.x * convert / lookHorizontal,
					convert,
					look.z * convert / lookHorizontal);
		}

		if (leanAngle < 0.0F && lookHorizontal > 0.0) {
			double convert = moveHorizontal * -Mth.sin(leanAngle) * 0.04;
			velocity = velocity.add(
					-look.x * convert / lookHorizontal,
					convert * 3.2,
					-look.z * convert / lookHorizontal);
		}

		if (lookHorizontal > 0.0) {
			velocity = velocity.add(
					(look.x / lookHorizontal * moveHorizontal - velocity.x) * 0.1,
					0.0,
					(look.z / lookHorizontal * moveHorizontal - velocity.z) * 0.1);
		}

		// Float constants on purpose: 0.99F widens to 0.9900000095367432, not to 0.99.
		return velocity.multiply(0.99F, 0.98F, 0.99F);
	}
}

package jealoustone.elytravario.flight;

import net.minecraft.world.phys.Vec3;

/**
 * The pitch that would gain the most total energy over the next tick, found by trying every
 * pitch and ticking the physics once at each.
 *
 * <p>This is elytrasim's <em>immediate optimal pitch</em>: greedy, with exactly one tick of
 * lookahead. It is a local reading and not a strategy — flying it every tick does not fly a
 * good cycle, because a one-tick horizon cannot see that trading energy away now buys more
 * of it back later. What it does say is which way the energy gradient points from where you
 * are, which is the thing that is hard to feel and easy to get backwards.
 *
 * <p>Greedy and far-sighted do not always disagree, though, and where they agree is worth
 * knowing: the true optimum matches this one while energy is being gained, and matches it
 * again wherever this one snaps to level. Those are the two regimes the cue can be taken at
 * face value in. It is the losing part of a cycle — the dive that pays for the climb — where
 * one tick of lookahead is too short a view to trust.
 *
 * <h2>How to read it</h2>
 *
 * <p>Three regimes show up across the flight envelope, and they look quite different:
 *
 * <ul>
 * <li><b>In a steady glide the answer is exactly level.</b> Not approximately: the curve has
 *     a genuine cusp at zero, because the nose-up term switches on at negative pitch and
 *     switches off again immediately. The cue parks on the horizon and stays there.</li>
 * <li><b>In a zoom climb it is a real interior optimum</b>, tens of degrees nose-up, and it
 *     moves as speed bleeds off. This is the regime the reading is worth watching in: it
 *     says how hard to pull.</li>
 * <li><b>In a slow descent it runs to the stops, near ninety nose-down.</b> That is not a
 *     glitch. Past about eighty degrees the wing makes no lift and the flight is a free
 *     fall, which at low speed gains energy faster than gliding does.</li>
 * </ul>
 *
 * <h2>Why the search stops a degree short of vertical</h2>
 *
 * <p>Straight up is a special case in vanilla, and an unhelpful one. {@code Mth.cos} is a
 * lookup table, and the index it computes for minus ninety degrees truncates to zero, so the
 * cosine comes back as exactly {@code 0.0} rather than as something merely tiny. The
 * horizontal look length is then exactly zero, every conversion and turning term is skipped
 * by its own {@code > 0} guard, and the flight goes ballistic — no wing at all.
 *
 * <p>That is genuine vanilla behaviour, and it is reachable, because pushing the mouse to
 * the stop pins pitch at exactly minus ninety. But it is a knife edge one hundredth of a
 * degree wide, worth about a block per tick to step off, and across a fast descent it ties
 * with a nose-straight-<em>down</em> dive to within a millionth of a block. Searching it
 * would let the cue jump between the top and the bottom of the ladder on the last bits of a
 * double, which is noise wearing the clothes of a reading.
 *
 * <p>So the sweep runs to eighty-nine. Measured over the whole velocity envelope, the most
 * that exclusion ever costs is 0.0012 blocks per tick, against cycle gains of a few tenths.
 * Note that this asymmetry is the table's, not the model's: plus ninety returns 1.2e-16 and
 * keeps its wing, so only the nose-up bound degenerates.
 *
 * <h2>Where this differs from elytrasim</h2>
 *
 * <p>elytrasim searches a two-dimensional velocity grid with yaw pinned to zero, so its
 * velocities are all in the plane the player is looking along and it has no sideslip to
 * worry about. Here the search runs on the real three-dimensional velocity at the player's
 * real yaw, which is what the game is about to do anyway, so a turn's sideways speed is
 * carried honestly instead of being flattened into the forward component.
 *
 * <p>elytrasim also works in raw energy while this mod works in energy as a height, a factor
 * of gravity apart. Gravity is positive, so the two agree on which pitch is best; only the
 * figures need converting.
 *
 * @param pitch      the best pitch found, in degrees, in Minecraft's convention where
 *                   negative is nose-up
 * @param bestGain   energy change at that pitch, as a height in blocks per tick
 * @param actualGain energy change at the pitch actually being held, in the same units
 */
public record OptimalPitch(float pitch, double bestGain, double actualGain) {
	/**
	 * How far from level the sweep reaches. Vanilla clamps player pitch to ninety, but the
	 * last degree is left out on purpose; see the class notes on straight up.
	 */
	private static final int LIMIT = 89;

	/**
	 * Golden-section steps used to refine the winning degree. Sixteen narrows a two-degree
	 * bracket to about a thousandth of a degree, which is far finer than the ladder can draw
	 * and costs sixteen more ticks of physics on top of the hundred and eighty-one the
	 * coarse sweep already spent.
	 */
	private static final int REFINE_STEPS = 16;

	private static final double INVERSE_PHI = (Math.sqrt(5.0) - 1.0) / 2.0;

	/**
	 * How much better the best pitch is than the one being held, as a height in blocks per
	 * tick. Never negative, and zero when already flying the optimum.
	 */
	public double margin() {
		return bestGain - actualGain;
	}

	/**
	 * Searches for the best pitch at the state this sample describes, or returns null if
	 * there is nothing to search: gravity has to be positive for energy-as-height to mean
	 * anything, and the whole model only applies while actually gliding.
	 */
	public static OptimalPitch search(Sample sample) {
		if (!sample.gliding() || sample.gravity() <= 0.0) {
			return null;
		}

		Vec3 velocity = new Vec3(sample.vx(), sample.vy(), sample.vz());
		int coarse = sweep(velocity, sample.yaw(), sample.gravity());
		double coarseGain = gain(velocity, sample.yaw(), sample.gravity(), coarse);
		float best = refine(sample, velocity, coarse, coarseGain);

		return new OptimalPitch(best, gain(velocity, sample.yaw(), sample.gravity(), best),
				gain(velocity, sample.yaw(), sample.gravity(), sample.pitch()));
	}

	/**
	 * The most energy one tick could gain from this velocity, over every pitch, as a height
	 * in blocks. This is what {@link EnergyField} colours a whole grid of, and it is the
	 * unrefined answer: see that class for why a whole degree is close enough there.
	 */
	public static double bestGain(Vec3 velocity, float yaw, double gravity) {
		return gain(velocity, yaw, gravity, sweep(velocity, yaw, gravity));
	}

	/**
	 * The best whole degree, found by trying all of them.
	 *
	 * <p>An exhaustive sweep rather than a gradient walk, because the curve is not unimodal:
	 * the cusp at level flight and the cliffs at the bounds each make their own local
	 * maximum, so a search started anywhere in particular would find the wrong one.
	 */
	private static int sweep(Vec3 velocity, float yaw, double gravity) {
		int best = 0;
		double bestGain = Double.NEGATIVE_INFINITY;

		for (int pitch = -LIMIT; pitch <= LIMIT; pitch++) {
			double gain = gain(velocity, yaw, gravity, pitch);

			if (gain > bestGain) {
				bestGain = gain;
				best = pitch;
			}
		}

		return best;
	}

	/**
	 * Narrows the winning degree to a fraction of one, so the cue slides rather than
	 * stepping a few pixels at a time.
	 *
	 * <p>Golden section rather than fitting a parabola through the three points around the
	 * winner. In a glide the maximum is a cusp, not a smooth peak — the curve leaves it
	 * steeply on the nose-up side and gently on the nose-down side — and a parabola through
	 * that lands a degree or two down the shallow slope, which would nudge the cue off the
	 * horizon exactly where it belongs on it.
	 *
	 * <p>The bracket is the winning degree plus or minus one, which does contain the true
	 * maximum: a coarser point outside it already scored lower than the winner.
	 */
	private static float refine(Sample sample, Vec3 velocity, int coarse, double coarseGain) {
		double low = Math.max(-LIMIT, coarse - 1.0);
		double high = Math.min(LIMIT, coarse + 1.0);

		if (high <= low) {
			return coarse;
		}

		double a = high - INVERSE_PHI * (high - low);
		double b = low + INVERSE_PHI * (high - low);
		double gainA = gain(velocity, sample.yaw(), sample.gravity(), a);
		double gainB = gain(velocity, sample.yaw(), sample.gravity(), b);

		for (int step = 0; step < REFINE_STEPS; step++) {
			if (gainA < gainB) {
				low = a;
				a = b;
				gainA = gainB;
				b = low + INVERSE_PHI * (high - low);
				gainB = gain(velocity, sample.yaw(), sample.gravity(), b);
			} else {
				high = b;
				b = a;
				gainB = gainA;
				a = high - INVERSE_PHI * (high - low);
				gainA = gain(velocity, sample.yaw(), sample.gravity(), a);
			}
		}

		float refined = (float) ((low + high) / 2.0);

		// Only taken if it really is better. Rounding inside the physics is coarse enough
		// that on a flat peak the refinement can come out a hair below the whole degree it
		// started from, and the cue should never be placed somewhere the sweep beat.
		return gain(velocity, sample.yaw(), sample.gravity(), refined) >= coarseGain ? refined : coarse;
	}

	/**
	 * Change in total energy height over one tick at this pitch, in blocks.
	 *
	 * <p>Both halves of the energy move: kinetic by the change in speed, potential by the
	 * altitude the <em>new</em> velocity carries the player through, since vanilla advances
	 * position after updating velocity. Leaving the second term out would score a dive and a
	 * climb alike and make the whole reading meaningless.
	 */
	private static double gain(Vec3 velocity, float yaw, double gravity, double pitch) {
		Vec3 next = ElytraPhysics.updateFallFlyingMovement(velocity, (float) pitch, yaw, gravity);

		return (next.lengthSqr() - velocity.lengthSqr()) / (2.0 * gravity) + next.y;
	}
}

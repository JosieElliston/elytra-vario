package jealoustone.elytravario.flight;

/**
 * An immutable snapshot of the player's flight state, captured once per client tick.
 *
 * <p>Velocities are in blocks/tick, the units vanilla physics itself works in, so these
 * numbers line up directly with the elytrasim project's state. They are measured as the
 * change in position over one tick rather than read from {@code getDeltaMovement}, so they
 * describe movement that actually happened; see {@link FlightRecorder}.
 *
 * <p>Energies are exposed as <em>heights</em> rather than raw energy. Dividing energy by
 * gravity turns it into the altitude that energy is worth, which makes kinetic and
 * potential terms directly comparable and lets a pump cycle's gain be read off in blocks.
 */
public record Sample(
		double x,
		double y,
		double z,
		double vx,
		double vy,
		double vz,
		float pitch,
		double gravity,
		boolean gliding
) {
	public double horizontalSpeed() {
		return Math.sqrt(vx * vx + vz * vz);
	}

	public double speedSqr() {
		return vx * vx + vy * vy + vz * vz;
	}

	public double speed() {
		return Math.sqrt(speedSqr());
	}

	/** Kinetic energy as a height: the altitude this speed would buy if all traded for height. */
	public double kineticHeight() {
		return gravity > 0.0 ? speedSqr() / (2.0 * gravity) : 0.0;
	}

	/** Potential energy as a height, which in these units is just altitude. */
	public double potentialHeight() {
		return y;
	}

	/**
	 * Total energy height. Its absolute value is arbitrary, since altitude is measured from
	 * an arbitrary origin; differences over time are the meaningful quantity.
	 */
	public double totalHeight() {
		return potentialHeight() + kineticHeight();
	}

	/**
	 * Blocks forward per block down. Negative while climbing, where it reads as blocks
	 * forward per block <em>gained</em>.
	 *
	 * <p>The zero case is special-cased rather than left to IEEE arithmetic: negating a
	 * {@code vy} of {@code 0.0} gives {@code -0.0}, which would make level flight divide out
	 * to negative infinity and so report a climb.
	 */
	public double glideRatio() {
		if (vy == 0.0) {
			return horizontalSpeed() == 0.0 ? Double.NaN : Double.POSITIVE_INFINITY;
		}

		return horizontalSpeed() / -vy;
	}
}

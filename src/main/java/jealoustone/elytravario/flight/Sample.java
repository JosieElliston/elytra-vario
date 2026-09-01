package jealoustone.elytravario.flight;

/**
 * An immutable snapshot of the player's flight state, captured once per client tick.
 *
 * <p>Velocities are in blocks/tick, the units vanilla physics itself works in, so these
 * numbers line up directly with the elytrasim project's state.
 *
 * <p>Energies are exposed as <em>heights</em> rather than raw energy. Dividing energy by
 * gravity turns it into the altitude that energy is worth, which makes kinetic and
 * potential terms directly comparable and lets a pump cycle's gain be read off in blocks.
 */
public record Sample(
		double vx,
		double vy,
		double vz,
		double y,
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

	/** Blocks forward per block down. Infinite while climbing. */
	public double glideRatio() {
		return vy < 0.0 ? horizontalSpeed() / -vy : Double.POSITIVE_INFINITY;
	}
}

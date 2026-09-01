package jealoustone.elytravario.flight;

import net.minecraft.world.phys.Vec3;

/**
 * The best energy change available at every point of the chart's velocity domain: for each
 * cell, the most total energy one tick could gain from that velocity, over every pitch.
 *
 * <p>This is the same quantity the optimal pitch bug reports, evaluated everywhere instead of
 * only where the player happens to be — elytrasim's velocity-space grid, coloured by the
 * energy change at the immediate optimal pitch. Where the bug says <em>which way to point
 * now</em>, the field says <em>where in velocity space energy can be made at all</em>, which
 * is the shape a pump cycle has to be flown around. Its most useful feature is the boundary
 * between the two signs: inside it a cycle can pay for itself, outside it nothing can.
 *
 * <p>This class is only the numbers. Turning them into pixels is
 * {@code hud.EnergyFieldTexture}'s job, so that the colours stay a display decision and the
 * expensive part does not have to be redone to change one.
 *
 * <h2>Why it is a function of two variables and not five</h2>
 *
 * <p>Velocity has three components and the search also needs a yaw, which would make this a
 * four-dimensional table. It collapses to two because elytra physics is indifferent to which
 * way the player is facing: rotate the world about the vertical axis and every term rotates
 * with it. So the field is computed once at yaw zero with the velocity laid along the look
 * direction, and it is correct for every heading.
 *
 * <p>What that does <em>not</em> cover is sideslip. The chart's horizontal axis is total
 * horizontal speed, and the field assumes all of it is going where the nose is pointing. In a
 * turn some of it is not, and the reading under the yellow cursor is then optimistic. The gap
 * between the two cursors is exactly how far off it is — which is the reason both are drawn.
 *
 * <p>Negative horizontal speed, the strip left of the origin, means moving backwards relative
 * to the look direction. The chart draws it because the axis starts below zero for legibility
 * rather than because the state is common, but it is a real state and the physics has a
 * definite answer there: the turning term hauls the velocity round to face forwards, which is
 * violent and expensive.
 *
 * <h2>Cost</h2>
 *
 * <p>A cell costs a hundred and seventy-nine ticks of physics, and the default domain is about
 * fourteen thousand cells. That is roughly 30ms of arithmetic once the JIT has seen it and
 * <em>ten times that</em> the first time, because the first build is also the first few
 * million interpreted executions of the physics — so expect about a third of a second of
 * freeze on the frame it first happens, and nothing afterwards. Gliding for a few seconds
 * before opening the chart warms the same code through the optimal pitch bug and makes the
 * build cheap.
 *
 * <p>It is cached against everything it depends on and rebuilt only when one of those changes,
 * which in practice means the first frame of a session and any time gravity changes under a
 * potion. Baking it at compile time would avoid the hitch entirely and is deliberately not
 * done: the domain is meant to become adjustable, and a table that cannot follow the axes it
 * is drawn against would be worse than no table.
 *
 * <p>The whole-degree sweep is not refined here, unlike in {@link OptimalPitch}. Refinement is
 * worth at most 0.00012 blocks/tick anywhere in the envelope, which is far below one step of
 * an eight-bit colour ramp, so it could not change a single pixel.
 */
public final class EnergyField {
	private static EnergyField cached;

	private final int width;
	private final int height;
	private final double minVxz;
	private final double maxVy;
	private final double scale;
	private final double gravity;

	private final float[] gains;

	/**
	 * The field for this domain, built if the cached one does not already describe it.
	 *
	 * <p>The domain is passed in rather than read from the config so that this stays a
	 * function of its arguments, and so that the caller can guarantee the grid lines up with
	 * the chart pixel for pixel: {@code width} and {@code height} are the chart's, and column
	 * {@code c} is the velocity {@code minVxz + c / scale}, which is the inverse of the
	 * mapping the chart draws with.
	 *
	 * <p>The chart's other two bounds are not arguments because they are not needed: the far
	 * edges of the domain enter only through {@code width} and {@code height}, which the
	 * caller has already derived from them.
	 */
	public static EnergyField of(int width, int height, double minVxz, double maxVy,
			double scale, double gravity) {
		EnergyField field = cached;

		if (field == null || !field.describes(width, height, minVxz, maxVy, scale, gravity)) {
			field = new EnergyField(width, height, minVxz, maxVy, scale, gravity);
			cached = field;
		}

		return field;
	}

	/**
	 * Energy change per cell in blocks/tick, row-major, {@code width} entries a row, starting
	 * at the top left of the chart. Floats rather than doubles because the values are on their
	 * way to eight bits a channel and the array is the largest thing the mod holds.
	 *
	 * <p>Exposed as the raw array because the caller walks all of it to build a texture, and
	 * because it is never mutated after construction.
	 */
	public float[] gains() {
		return gains;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	private EnergyField(int width, int height, double minVxz, double maxVy, double scale,
			double gravity) {
		this.width = width;
		this.height = height;
		this.minVxz = minVxz;
		this.maxVy = maxVy;
		this.scale = scale;
		this.gravity = gravity;
		this.gains = new float[width * height];

		for (int row = 0; row < height; row++) {
			double vy = maxVy - row / scale;

			for (int column = 0; column < width; column++) {
				double vxz = minVxz + column / scale;
				gains[row * width + column] =
						(float) OptimalPitch.bestGain(new Vec3(0.0, vy, vxz), 0.0f, gravity);
			}
		}
	}

	private boolean describes(int width, int height, double minVxz, double maxVy, double scale,
			double gravity) {
		return this.width == width
				&& this.height == height
				&& this.minVxz == minVxz
				&& this.maxVy == maxVy
				&& this.scale == scale
				&& this.gravity == gravity;
	}
}

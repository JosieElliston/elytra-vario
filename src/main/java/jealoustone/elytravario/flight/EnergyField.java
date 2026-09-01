package jealoustone.elytravario.flight;

import net.minecraft.world.phys.Vec3;

/**
 * The best energy change available at every point of the chart's velocity domain: for each
 * cell, the most total energy one tick could gain from that velocity, over every pitch.
 *
 * <p>This is the same quantity the optimal pitch bug reports, evaluated everywhere instead of
 * only where the player happens to be — elytrasim's velocity-space grid, colored by the
 * energy change at the immediate optimal pitch. Where the bug says <em>which way to point
 * now</em>, the field says <em>where in velocity space energy can be made at all</em>, which
 * is the shape a pump cycle has to be flown around. Its most useful feature is the boundary
 * between the two signs: inside it a cycle can pay for itself, outside it nothing can.
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
 * twelve thousand cells, so a build is roughly thirty milliseconds — one visible hitch, once.
 * It is cached against everything it depends on and rebuilt only when one of those changes,
 * which in practice means the first frame of a session and any time gravity changes under a
 * potion. Baking it at compile time would be faster still and is deliberately not done: the
 * domain is meant to become adjustable, and a table that cannot follow the axes it is drawn
 * against would be worse than no table.
 *
 * <p>The whole-degree sweep is not refined here, unlike in {@link OptimalPitch}. Refinement is
 * worth at most 0.00012 blocks/tick anywhere in the envelope, against a quantisation step of
 * about 0.025, so it could not change a single pixel.
 */
public final class EnergyField {
	/**
	 * Quantisation steps per side. The field is stored as levels rather than as numbers so
	 * that equal-colored pixels can be merged into horizontal runs, which is what makes it
	 * affordable to draw: the default domain is twelve thousand cells but only about
	 * twenty-six hundred runs. Twenty-four steps puts the color difference between adjacent
	 * levels below what the eye separates at this chroma, so nothing bands.
	 */
	public static final int LEVELS = 24;

	private static EnergyField cached;

	private final int width;
	private final int height;
	private final double minVxz;
	private final double maxVy;
	private final double scale;
	private final double gravity;
	private final double fieldScale;

	private final int[] runs;

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
	 *
	 * @param fieldScale the energy change, in blocks/tick, at which the ramp is half way to
	 *                   saturated; see {@link #level}
	 */
	public static EnergyField of(int width, int height, double minVxz, double maxVy,
			double scale, double gravity, double fieldScale) {
		EnergyField field = cached;

		if (field == null || !field.describes(width, height, minVxz, maxVy, scale, gravity,
				fieldScale)) {
			field = new EnergyField(width, height, minVxz, maxVy, scale, gravity, fieldScale);
			cached = field;
		}

		return field;
	}

	/**
	 * Horizontal runs of equal level, four ints each: row, first column, last column
	 * inclusive, and level. Levels run from {@code -LEVELS} to {@code +LEVELS}, negative for
	 * energy being lost.
	 *
	 * <p>Exposed as the raw array because it is walked once per frame and there are thousands
	 * of runs; wrapping each one in an object would allocate more than the drawing costs.
	 */
	public int[] runs() {
		return runs;
	}

	private EnergyField(int width, int height, double minVxz, double maxVy, double scale,
			double gravity, double fieldScale) {
		this.width = width;
		this.height = height;
		this.minVxz = minVxz;
		this.maxVy = maxVy;
		this.scale = scale;
		this.gravity = gravity;
		this.fieldScale = fieldScale;

		// Sized for the worst case, one run per cell, and trimmed once the real count is
		// known. Growing it would mean copying a hundred kilobytes mid-build.
		int[] packed = new int[width * height * 4];
		int count = 0;

		for (int row = 0; row < height; row++) {
			double vy = maxVy - row / scale;
			int runLevel = 0;
			int runStart = 0;

			for (int column = 0; column < width; column++) {
				double vxz = minVxz + column / scale;
				int level = level(OptimalPitch.bestGain(new Vec3(0.0, vy, vxz), 0.0f, gravity));

				if (column == 0) {
					runLevel = level;
				} else if (level != runLevel) {
					packed[count++] = row;
					packed[count++] = runStart;
					packed[count++] = column - 1;
					packed[count++] = runLevel;
					runLevel = level;
					runStart = column;
				}
			}

			packed[count++] = row;
			packed[count++] = runStart;
			packed[count++] = width - 1;
			packed[count++] = runLevel;
		}

		this.runs = java.util.Arrays.copyOf(packed, count);
	}

	/**
	 * Quantises an energy change to a signed level.
	 *
	 * <p>The magnitude is compressed by {@code |x| / (|x| + fieldScale)} rather than clipped
	 * to a range. The field spans about three and a half blocks/tick end to end while the
	 * interesting structure — the boundary and the glide band around it — lives in the first
	 * tenth of that, so a linear ramp would saturate almost everywhere and show nothing. The
	 * compression never clips, so the extremes stay distinguishable from the merely large,
	 * and it is smooth through zero, so the boundary is a clean seam rather than a step.
	 *
	 * <p>Zero and negative zero collapse to the same level on purpose. A cell a thousandth of
	 * a block from break-even has no meaningful sign, and coloring it as though it did would
	 * put a hard edge where the data has none.
	 */
	private int level(double energyChange) {
		double magnitude = Math.abs(energyChange);
		int level = (int) Math.round(LEVELS * magnitude / (magnitude + fieldScale));
		return energyChange < 0.0 ? -level : level;
	}

	private boolean describes(int width, int height, double minVxz, double maxVy, double scale,
			double gravity, double fieldScale) {
		return this.width == width
				&& this.height == height
				&& this.minVxz == minVxz
				&& this.maxVy == maxVy
				&& this.scale == scale
				&& this.gravity == gravity
				&& this.fieldScale == fieldScale;
	}
}

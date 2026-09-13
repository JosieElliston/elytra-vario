package jealoustone.elytravario.hud;

/** Stable right-aligned columns shared by the stats matrices. */
public final class MatrixLayout {
	/**
	 * Column templates for the rows that carry more than one figure. Each figure is
	 * right-aligned inside a column reserved from these, so gaining or losing a digit cannot
	 * shove the figure beside it sideways — and since every value has a fixed number of
	 * decimals and a fixed suffix, right-alignment also pins the decimal point. The only motion
	 * left is a leading digit appearing, which is the least a changing number can do.
	 *
	 * <p>They are strings rather than pixel counts so that the columns follow the font, and they
	 * live here rather than beside the drawing because a panel's width floor is measured from
	 * the same templates it reserves its columns with — see {@link StatsPanel}. A value wider
	 * than its template is not clipped, it just encroaches on the column to its left.
	 */
	public static final String DELTA_COLUMN = "-000.0 b";
	public static final String ABSOLUTE_COLUMN = "-0000.0";
	public static final String BOUNCE_COLUMN = "-000.00";

	private final int[] rightEdges;

	private MatrixLayout(int[] rightEdges) {
		this.rightEdges = rightEdges;
	}

	/**
	 * Places columns from right to left. Widths are templates rather than clipping bounds: a
	 * long value may grow into the preceding gap, but changing ordinary digits never moves a
	 * neighboring column.
	 */
	public static MatrixLayout rightAligned(int right, int gap, int... widths) {
		int[] edges = new int[widths.length];
		for (int column = widths.length - 1; column >= 0; column--) {
			edges[column] = right;
			right -= widths[column] + gap;
		}
		return new MatrixLayout(edges);
	}

	public int right(int column) { return rightEdges[column]; }

	public int left(int column, int valueWidth) { return right(column) - valueWidth; }

	public int columns() { return rightEdges.length; }
}

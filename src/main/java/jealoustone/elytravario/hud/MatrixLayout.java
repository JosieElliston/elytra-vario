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
	 *
	 * <p>None of them carries a unit, because no figure in a column does: a panel says what it
	 * is measured in once, on its heading row.
	 *
	 * <p><b>One template, and so one column width, for every figure on every panel.</b> A column
	 * is placed by measuring back from the panel's right edge, so equal templates and equal
	 * widths put every panel's columns on the same screen pixels — and a stack of panels butted
	 * together at one width is meant to read as one instrument, which it does not do if the
	 * energy panel's inner column stands two pixels off the matrices'. Three separate templates
	 * had them agreeing on the rightmost column, since every panel aligns its last column onto
	 * its own right edge whatever the template says, and disagreeing on every column left of it.
	 *
	 * <p>The width is the useful accident that makes one template enough: seven glyphs and one
	 * stop comes to the same 38 pixels whether it is spent on three digits and two decimals or
	 * on four and one, so a two-decimal speed and a one-decimal altitude want the same column.
	 * The names below say which of those a panel is drawing; the width is the same either way.
	 */
	public static final String RATE_COLUMN = "-000.00";
	public static final String HEIGHT_COLUMN = "-0000.0";

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

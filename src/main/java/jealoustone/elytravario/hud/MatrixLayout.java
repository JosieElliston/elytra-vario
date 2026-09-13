package jealoustone.elytravario.hud;

/** Stable right-aligned columns shared by the stats matrices. */
public final class MatrixLayout {
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

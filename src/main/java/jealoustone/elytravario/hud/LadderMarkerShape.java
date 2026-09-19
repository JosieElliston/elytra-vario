package jealoustone.elytravario.hud;

/**
 * The pixel geometry of one half of a mirrored ladder marker.
 *
 * <p>{@code length} runs inwards from the marker's outside edge. A positive
 * {@code verticalStep} repeats each one-pixel retreat for that many rows on either side of
 * the marked pitch; zero is the intentional one-row line special case. {@code inset} is the
 * clearance between the marker's outside edge and the ladder.
 */
public record LadderMarkerShape(int inset, int length, int verticalStep) {
	public LadderMarkerShape {
		if (inset < 0 || length < 1 || verticalStep < 0) {
			throw new IllegalArgumentException("Invalid ladder marker shape");
		}
	}

	/** Total height in pixels. Every wedge is odd; a line is one pixel high. */
	public int height() {
		return verticalStep == 0 ? 1 : 2 * verticalStep * (length - 1) + 1;
	}

	/**
	 * Horizontal pixels drawn on a row relative to the marked pitch, or zero outside the
	 * shape. The center row owns the unique point. All other widths repeat for exactly
	 * {@code verticalStep} rows, with the final run against the outside edge rather than at
	 * the point.
	 */
	public int widthAt(int row) {
		int distance = Math.abs(row);
		if (verticalStep == 0) return distance == 0 ? length : 0;
		int retreat = (distance + verticalStep - 1) / verticalStep;
		return Math.max(0, length - retreat);
	}
}

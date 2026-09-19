package jealoustone.elytravario.hud;

/**
 * The pixel geometry of one half of a mirrored ladder marker.
 *
 * <p>{@code length} runs inwards from the marker's outside edge. A positive
 * {@code horizontalStep} is how many horizontal pixels the edge retreats for each row away
 * from the marked pitch; zero is the intentional one-row line special case. {@code inset} is
 * the clearance between the marker's outside edge and the ladder.
 */
public record LadderMarkerShape(int inset, int length, int horizontalStep) {
	public LadderMarkerShape {
		if (inset < 0 || length < 1 || horizontalStep < 0
				|| horizontalStep >= length && horizontalStep != 0) {
			throw new IllegalArgumentException("Invalid ladder marker shape");
		}
	}

	/** Total height in pixels. Every wedge is odd; a line is one pixel high. */
	public int height() {
		return horizontalStep == 0 ? 1 : 2 * ((length - 1) / horizontalStep) + 1;
	}

	/**
	 * Horizontal pixels drawn on a row relative to the marked pitch, or zero outside the
	 * shape. The center row owns the unique point. Every row away retreats by
	 * {@code horizontalStep}; where the length is not an exact multiple, the remaining pixels
	 * stay at the base rather than blunting the point.
	 */
	public int widthAt(int row) {
		int distance = Math.abs(row);
		if (horizontalStep == 0) return distance == 0 ? length : 0;
		return Math.max(0, length - distance * horizontalStep);
	}
}

package jealoustone.elytravario.hud;

/** Anchor and inward offsets in GUI pixels, clamped to the available screen. */
public record HudPosition(int x, int y) {
	/**
	 * Anchor values past the five screen corners, which the velocity graph alone accepts: it
	 * hangs off the named side of the stats panel instead of off the screen. See
	 * {@link HudLayout}.
	 */
	public static final int ATTACH_LEFT = 5;
	public static final int ATTACH_RIGHT = 6;
	public static final int ATTACH_ABOVE = 7;
	public static final int ATTACH_BELOW = 8;

	public static boolean attaches(int anchor) {
		return anchor >= ATTACH_LEFT;
	}

	public static HudPosition resolve(int anchor, int offsetX, int offsetY,
			int width, int height, int screenWidth, int screenHeight) {
		int x = switch (anchor) {
			case 1, 3 -> screenWidth - width - offsetX;
			case 4 -> (screenWidth - width) / 2 + offsetX;
			default -> offsetX;
		};
		int y = switch (anchor) {
			case 2, 3 -> screenHeight - height - offsetY;
			case 4 -> (screenHeight - height) / 2 + offsetY;
			default -> offsetY;
		};
		return new HudPosition(Math.clamp(x, 0, Math.max(0, screenWidth - width)),
				Math.clamp(y, 0, Math.max(0, screenHeight - height)));
	}

	/** True for the two anchors on the right edge, false for the left pair and for center. */
	static boolean right(int anchor) { return anchor == 1 || anchor == 3; }

	/** True for the two anchors on the bottom edge, false for the top pair and for center. */
	static boolean bottom(int anchor) { return anchor == 2 || anchor == 3; }
}

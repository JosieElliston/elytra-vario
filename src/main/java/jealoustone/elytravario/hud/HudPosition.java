package jealoustone.elytravario.hud;

/** An absolute top-left position in GUI pixels, clamped to the available screen. */
public record HudPosition(int x, int y) {
	public static HudPosition clamp(int x, int y,
			int width, int height, int screenWidth, int screenHeight) {
		return new HudPosition(Math.clamp(x, 0, Math.max(0, screenWidth - width)),
				Math.clamp(y, 0, Math.max(0, screenHeight - height)));
	}
}

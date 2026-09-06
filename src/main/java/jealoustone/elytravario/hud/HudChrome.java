package jealoustone.elytravario.hud;

/**
 * The furniture the panelled instruments share: backgrounds, borders, gridlines and the two
 * text weights. Everything here is chrome rather than a reading — none of it ever encodes a
 * number — which is why none of it is configurable and why it lives in one place.
 *
 * <p>One place because these are a <em>set</em>. Two panels drawn in almost the same grays
 * read as an accident; drawn in the same grays they read as one instrument fit. The colors
 * that do carry meaning are settings on {@link jealoustone.elytravario.VarioConfig} instead,
 * where they can be tuned per instrument.
 *
 * <p>The backgrounds are translucent because the world behind them is worth seeing. The one
 * thing on any of these panels that is not is the velocity graph's heatmap, which is data and
 * is opaque for exactly that reason.
 */
final class HudChrome {
	/** Panel fill. Its alpha is the default; each panel scales it by its own opacity setting. */
	static final int PANEL_BG = 0xB0101014;

	/** Panel outlines and the rules drawn between groups of rows. */
	static final int BORDER = 0xFF3A3F45;

	/** Row labels: present, and quieter than the figure they name. */
	static final int LABEL = 0xFF9AA0A6;

	/** A figure being read. */
	static final int VALUE = 0xFFFFFFFF;

	/** A figure kept for reference rather than for reading, and axis annotations. */
	static final int MUTED = 0xFF6A7076;

	/** The velocity graph's gridlines, and the zero axes picked out from them. */
	static final int GRID = 0x26FFFFFF;
	static final int AXIS = 0x66FFFFFF;

	private HudChrome() {
	}
}

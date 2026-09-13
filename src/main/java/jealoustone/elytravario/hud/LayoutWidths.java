package jealoustone.elytravario.hud;

/**
 * How wide the stats panels' text is, and how wide a row of it has to be drawn to be read.
 *
 * <p>A panel's width floor is a constant of its layout rather than of any one frame, and it is
 * wanted where no client font is in reach: the settings screen clamps a resize against it, the
 * editor stops a drag at it, and the tests measure it with no client at all. So the advances of
 * Minecraft's default font are written out here rather than asked of the font.
 *
 * <p>Every advance below is confirmed by a string that was measured through the real font when
 * the panels were first sized. {@code GLIDE} at 28 gives {@code I} as the one narrow capital,
 * {@code -78.40 b/s} at 54 gives the digits, the stop and the space, {@code -12.34 : 1} at 48
 * the colon, {@code -90.0°} at 31 the degree sign, and {@code +15.09 b/s²} at 59 the
 * superscript. {@link LayoutWidthsTest} holds those five, so a wrong advance here is a failing
 * test rather than a panel that crowds its own figures.
 *
 * <p>The table covers the glyphs the panels draw and nothing else: a template written with a
 * glyph outside it would be measured as full width and could be measured wrong, which is also a
 * failing test rather than a silent pixel or two.
 */
public final class LayoutWidths {
	/** Most glyphs: five pixels of ink and one of spacing. */
	private static final int FULL = 6;

	/** The glyphs the panels draw that are narrower than {@link #FULL}, and their advances. */
	private static final String NARROW = " .:I°²";
	private static final int[] NARROW_ADVANCE = { 4, 2, 2, 4, 5, 5 };

	/** The rest of what the panels draw, every glyph of it {@link #FULL} wide. */
	private static final String WIDE = "-+/0123456789ACDEGHKLNPSTVXYZbs";

	/**
	 * The least gap left between a label and the figure beside it. Two pixels is a third of a
	 * glyph: enough that the two read as separate words at the floor, where by definition there
	 * is nothing else keeping them apart.
	 */
	public static final int CLEARANCE = 2;

	private LayoutWidths() {}

	/** The width of {@code text} in the default font, in unscaled pixels. */
	public static int width(String text) {
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			int narrow = NARROW.indexOf(text.charAt(i));
			width += narrow < 0 ? FULL : NARROW_ADVANCE[narrow];
		}
		return width;
	}

	/** Whether every glyph of {@code text} is one this table was measured for. */
	static boolean measured(String text) {
		for (int i = 0; i < text.length(); i++) {
			char glyph = text.charAt(i);
			if (NARROW.indexOf(glyph) < 0 && WIDE.indexOf(glyph) < 0) return false;
		}
		return true;
	}

	/**
	 * The narrowest layout width one row of a panel can be drawn in: the panel's padding either
	 * side, the label at the left, {@link #CLEARANCE}, and then the figures.
	 *
	 * <p>The figures are right-aligned into columns whose right edges the panel's width fixes,
	 * so only the leftmost of them can meet the label — pass it as {@code figure}, and the
	 * columns to its right as their own templates, since what those cost this row is their full
	 * reserved width whatever this row happens to put in them. A row with no label of its own
	 * passes {@code ""} and is charged no clearance.
	 *
	 * @param figure this row's leftmost figure, which is the one the label can run into
	 * @param columnsToTheRight the templates of the columns beyond it, left to right
	 */
	public static int row(String label, String figure, String... columnsToTheRight) {
		int width = StatsPanel.PAD + width(label);
		if (!label.isEmpty()) width += CLEARANCE;
		width += width(figure);
		for (String column : columnsToTheRight) width += StatsPanel.PAD + width(column);
		return width + StatsPanel.PAD;
	}

	/**
	 * The widest of a panel's rows, which is the panel's floor: every row has to fit, and they
	 * are drawn one above another in the same width.
	 */
	public static int widest(int... rows) {
		int widest = 0;
		for (int row : rows) widest = Math.max(widest, row);
		return widest;
	}
}

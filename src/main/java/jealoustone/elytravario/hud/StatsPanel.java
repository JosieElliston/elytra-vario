package jealoustone.elytravario.hud;

import static jealoustone.elytravario.hud.LayoutWidths.row;
import static jealoustone.elytravario.hud.LayoutWidths.widest;
import static jealoustone.elytravario.hud.MatrixLayout.HEIGHT_COLUMN;
import static jealoustone.elytravario.hud.MatrixLayout.RATE_COLUMN;

import java.util.List;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;

/**
 * The boxes Flight Stats is drawn as, each placed, sized and switched on its own.
 *
 * <p>One panel per <em>kind</em> of reading rather than one panel for all of them. The old
 * single panel could not place or switch kinds independently. Split, the boxes can be stacked,
 * spread across the screen, or switched off one at a time.
 *
 * <p>They are still meant to read as one instrument, which is what the editor's overlap snap is
 * for: butted together with their borders sharing a column, several panels look like one panel
 * ruled into sections — the rule the old panel drew between its speed and energy halves, now
 * available between any two of them and in either direction. See
 * {@link jealoustone.elytravario.config.ModulePositionEditor}.
 *
 * <p>Each panel's settings are one subpage of the Flight Stats page, named by {@link #group()},
 * and every one of them is this panel's prefix plus a suffix. Only the master switch, its
 * gliding-only companion and the positive and negative colors remain shared: the switch is what
 * the toggle key binds to, and the colors are a palette rather than a layout.
 *
 * <p><b>Each panel's width floor is computed from the rows it draws</b> rather than written
 * down as a pixel count: every constant below states its widest rows as the label and the
 * column templates that row carries, and {@link LayoutWidths#row} turns each into the narrowest
 * width it can be read in. A row is declared here in the shape it is drawn in
 * {@link VarioHudElement}, so adding a row or widening a template moves the floor with it, and
 * a floor can be checked by reading the row rather than by trusting the number.
 */
public enum StatsPanel {
	/**
	 * The two readings that are neither a speed nor an energy: attitude, and its efficiency.
	 *
	 * <p>The narrowest of the panels, at 86, which the glide row sets: a ratio is the longer
	 * figure, and it is signed because a climb reads as blocks forward per block gained.
	 */
	OTHER("statsOther", widest(
			row("PITCH", "-90.0°"),
			row("GLIDE", "-00.00 : 1")),
			"showPitch", "showGlideRatio"),

	/**
	 * Vertical, horizontal and total speed, in that order: the signed one first, since it is the
	 * one being flown by, and the two magnitudes under it.
	 *
	 * <p>Its floor is 66, which its figures set. The heading row carries the units and nothing
	 * else — there is one column here, so there is nothing to name — and {@code SPEED b/s} at 52
	 * is eight pixels short of what {@code XYZ} against a speed comes to.
	 */
	SPEED("statsSpeed", widest(
			row("SPEED b/s", ""),
			row("XYZ", RATE_COLUMN)),
			"showVerticalSpeed", "showHorizontalSpeed", "showTotalSpeed"),

	/**
	 * The same three quantities differentiated, at a floor of 67.
	 *
	 * <p>The one panel whose heading row is a pixel wider than its figures: {@code ACCEL b/s²}
	 * is 57 against {@code SPEED b/s}'s 52, because it carries the superscript as well. A
	 * two-digit acceleration runs six wider than the template and encroaches on its label, which
	 * is what a figure that runs long does on any of these panels.
	 */
	ACCEL("statsAccel", widest(
			row("ACCEL b/s²", ""),
			row("XYZ", RATE_COLUMN)),
			"showVerticalAcceleration", "showHorizontalAcceleration", "showTotalAcceleration"),

	/**
	 * Kinetic, potential and total energy, and what the last cycle gained.
	 *
	 * <p>The only panel with two columns of different kinds, and its heading is what names them:
	 * {@code ABS} is the height against the world's origin and {@code REL} the height against
	 * the last apex. Its floor of 116 is that heading row — the longest label of the three
	 * headings, and both columns beside it — rather than any row of figures. Declared in the
	 * two-column mode, the widest of the three energy references and the default, so that
	 * changing the reference never moves the floor under a width that has already been set.
	 */
	ENERGY("statsEnergy", widest(
			row("ENERGY b", "ABS", HEIGHT_COLUMN),
			row("TE", HEIGHT_COLUMN, HEIGHT_COLUMN),
			row("GAIN", HEIGHT_COLUMN)),
			"showKineticEnergy", "showPotentialEnergy", "showTotalEnergy", "showCycleGain"),

	/**
	 * Touch, leave and deploy velocities as columns, with Y, XZ and XYZ as rows.
	 *
	 * <p>The same three quantities the {@link #SPEED} panel carries and in the same order: the
	 * signed vertical component first, then the two magnitudes. A single world axis is not one
	 * of them — X alone means nothing about a bounce, since it depends on which way the world
	 * happens to be oriented rather than on which way the player is flying, and XZ is the
	 * rotation-independent quantity that replaces it.
	 *
	 * <p>The widest panel, at 150, and the one place a heading row costs less than the figures
	 * under it: {@code VEL b/s} is the longest label here, but the headings it runs at are
	 * single letters right-aligned in columns reserved for a whole figure, so it is the rows of
	 * three figures that set the floor.
	 */
	BOUNCE_VELOCITY("statsBounceVelocity", widest(
			row("VEL b/s", "T", RATE_COLUMN, RATE_COLUMN),
			row("XYZ", RATE_COLUMN, RATE_COLUMN, RATE_COLUMN)),
			"showBounceVelocityY", "showBounceVelocityXz", "showBounceVelocityXyz"),

	/**
	 * Leave-touch and deploy-leave position differences as columns.
	 *
	 * <p>An interval is only ever measured against the event before it, so there is no column
	 * for touch: two columns rather than the velocity matrix's three, and a floor of 110 where
	 * that panel needs 150. The columns are still right-aligned onto the same edges, so at equal
	 * widths they line up under that panel's {@code L} and {@code D}.
	 *
	 * <p>Its rows are the velocity matrix's, for the same reason: height gained or lost over the
	 * interval, then the ground track and the whole path, both of which are distances and so
	 * cannot be negative.
	 */
	BOUNCE_DISTANCE("statsBounceDistance", widest(
			row("DELTA b", "L-T", RATE_COLUMN),
			row("XYZ", RATE_COLUMN, RATE_COLUMN)),
			"showBounceDistanceY", "showBounceDistanceXz", "showBounceDistanceXyz"),

	/**
	 * Leave-touch and deploy-leave elapsed ticks as the same two columns.
	 *
	 * <p>The one panel whose heading row has no label at its left. {@code TICKS} names the row of
	 * figures, not the row of column names above it, so that is the row it is drawn on — a label
	 * standing beside {@code L-T} and {@code D-L} is a label for headings rather than for
	 * readings. That puts the longest label of any matrix on the row carrying two full columns,
	 * which is why this panel's floor is 118 and not the 98 it was when {@code TICKS} sat on the
	 * heading row.
	 *
	 * <p>Its columns are the same {@link MatrixLayout#RATE_COLUMN} the two matrices above it
	 * reserve, even though a tick count needs neither a sign nor a decimal point. The template
	 * is what fixes where a column's edges fall, so a narrower one here would leave this panel's
	 * left column standing somewhere the other two have nothing — and three matrices set down at
	 * one width are meant to read as one grid.
	 */
	BOUNCE_TICKS("statsBounceTicks", widest(
			row("", "L-T", RATE_COLUMN),
			row("TICKS", RATE_COLUMN, RATE_COLUMN)),
			"showBounceTicks");

	/** The height of one row in the shared unscaled layout. */
	public static final int LINE = 10;

	/** The padding inside a panel's border, on all four sides. */
	public static final int PAD = 4;

	/**
	 * The largest text size any panel may be set to.
	 *
	 * <p>Eight, because that is where the width setting runs out: the widest panel's rows need
	 * 150 pixels at size one, and the width may be set to 1200. A ninth size would be one no
	 * panel could be made wide enough to hold, so it is not offered.
	 */
	public static final int MAX_TEXT_SIZE = 8;

	/**
	 * The GUI scale the HUD is being drawn through, which is half of what decides the text sizes
	 * a panel can be drawn at. See {@link #fontPixels()}.
	 *
	 * <p>Ambient rather than passed down, like the settings beside it, because it is the same
	 * for every module in a frame and changes only when the player changes it in the video
	 * settings. It holds still across a drag, so it cannot make a resize depend on anything but
	 * the pointer. One is the fallback, under which the drawable sizes are the whole ones.
	 */
	private static int guiScale = 1;

	/** Told to the panels once a frame by whatever is about to draw or edit them. */
	public static void guiScale(int scale) { guiScale = Math.max(1, scale); }

	public static int guiScale() { return guiScale; }
	private final String prefix;
	private final int minLayoutWidth;
	private final List<String> rowKeys;

	/**
	 * @param minLayoutWidth the floor, from {@link LayoutWidths#row} over this panel's rows
	 * @param rowKeys this panel's row switches, in the order the panel draws them
	 */
	StatsPanel(String prefix, int minLayoutWidth, String... rowKeys) {
		this.prefix = prefix;
		this.minLayoutWidth = minLayoutWidth;
		this.rowKeys = List.of(rowKeys);
	}

	/** The subpage this panel's settings live on, which is also the prefix they all share. */
	public String group() { return prefix; }

	public String showKey() { return "showStats" + suffix(); }
	public String xKey() { return prefix + "X"; }
	public String yKey() { return prefix + "Y"; }
	public String widthKey() { return prefix + "Width"; }
	public String textSizeKey() { return prefix + "TextSize"; }
	public String opacityKey() { return prefix + "Opacity"; }
	public String borderKey() { return "showStats" + suffix() + "Border"; }

	private String suffix() { return prefix.substring("stats".length()); }

	/** This panel's row switches, in the order it draws them. */
	public List<String> rowKeys() { return rowKeys; }

	public static StatsPanel byGroup(String group) { return by(group, StatsPanel::group); }
	public static StatsPanel byWidthKey(String key) { return by(key, StatsPanel::widthKey); }

	public static StatsPanel byTextSizeKey(String key) {
		return by(key, StatsPanel::textSizeKey);
	}

	private static StatsPanel by(String name,
			java.util.function.Function<StatsPanel, String> of) {
		for (StatsPanel panel : values()) {
			if (of.apply(panel).equals(name)) return panel;
		}
		return null;
	}

	// The switches below are read rather than held as suppliers so that adding a panel is a
	// compile error in every one of them until it is answered, which is the only thing keeping
	// the settings, the layout and the drawing in step.

	/** Whether this panel is switched on, ignoring the instrument's own switch. */
	public boolean shown() {
		return switch (this) {
			case OTHER -> VarioConfig.showStatsOther;
			case SPEED -> VarioConfig.showStatsSpeed;
			case ACCEL -> VarioConfig.showStatsAccel;
			case ENERGY -> VarioConfig.showStatsEnergy;
			case BOUNCE_VELOCITY -> VarioConfig.showStatsBounceVelocity;
			case BOUNCE_DISTANCE -> VarioConfig.showStatsBounceDistance;
			case BOUNCE_TICKS -> VarioConfig.showStatsBounceTicks;
		};
	}

	public int x() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherX;
			case SPEED -> VarioConfig.statsSpeedX;
			case ACCEL -> VarioConfig.statsAccelX;
			case ENERGY -> VarioConfig.statsEnergyX;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityX;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceX;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksX;
		};
	}

	public int y() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherY;
			case SPEED -> VarioConfig.statsSpeedY;
			case ACCEL -> VarioConfig.statsAccelY;
			case ENERGY -> VarioConfig.statsEnergyY;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityY;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceY;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksY;
		};
	}

	/** The width setting, before {@link #minWidth()} raises it. */
	public int configuredWidth() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherWidth;
			case SPEED -> VarioConfig.statsSpeedWidth;
			case ACCEL -> VarioConfig.statsAccelWidth;
			case ENERGY -> VarioConfig.statsEnergyWidth;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityWidth;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceWidth;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksWidth;
		};
	}

	/** How many times the font's own size this panel's text is set to, before quantizing. */
	public double textSize() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherTextSize;
			case SPEED -> VarioConfig.statsSpeedTextSize;
			case ACCEL -> VarioConfig.statsAccelTextSize;
			case ENERGY -> VarioConfig.statsEnergyTextSize;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityTextSize;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceTextSize;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksTextSize;
		};
	}

	/**
	 * <b>How many screen pixels one pixel of the font covers, which is the number that has to be
	 * whole.</b>
	 *
	 * <p>Minecraft's font is a bitmap — {@code ascii.png} is 128×128 with 8×8 cells and a
	 * declared height of 8, so one pixel of a glyph is one GUI pixel at a text size of one — and
	 * its atlas is sampled {@code NEAREST}, so nothing is ever blended. What goes wrong at a
	 * size like 1.3× is not blurring but rounding: each glyph pixel claims whichever screen
	 * pixels are nearest, so some strokes come out two pixels wide and their neighbours one, and
	 * the same letter is a different shape in different words.
	 *
	 * <p>A glyph pixel covers the text size times the GUI scale, and it is <em>that product</em>
	 * that must be whole. The GUI scale is a whole number ({@code Window.getGuiScale} returns an
	 * {@code int}), so the sizes that survive are the multiples of one over it: at a GUI scale
	 * of 4 those are ¼, ½, ¾, 1, 1¼ and so on, and at 3 they are thirds. <b>Whole text sizes are
	 * only the special case of a GUI scale of one.</b> Half size is not throwing away every
	 * other row of the glyph unless the GUI scale is 1 — at 4 it is a glyph pixel drawn two
	 * screen pixels across, which is as exact as any other.
	 *
	 * <p>So the setting is quantized here rather than restricted where it is written: the set of
	 * sizes a panel can be drawn at depends on a GUI scale the setting knows nothing about and
	 * which can change under a config that is already saved. Never below one, which is the font
	 * at one screen pixel per glyph pixel and the smallest text there is.
	 */
	public int fontPixels() {
		return Math.max(1, (int) Math.round(textSize() * guiScale));
	}

	public double opacity() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherOpacity;
			case SPEED -> VarioConfig.statsSpeedOpacity;
			case ACCEL -> VarioConfig.statsAccelOpacity;
			case ENERGY -> VarioConfig.statsEnergyOpacity;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityOpacity;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceOpacity;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksOpacity;
		};
	}

	public boolean border() {
		return switch (this) {
			case OTHER -> VarioConfig.showStatsOtherBorder;
			case SPEED -> VarioConfig.showStatsSpeedBorder;
			case ACCEL -> VarioConfig.showStatsAccelBorder;
			case ENERGY -> VarioConfig.showStatsEnergyBorder;
			case BOUNCE_VELOCITY -> VarioConfig.showStatsBounceVelocityBorder;
			case BOUNCE_DISTANCE -> VarioConfig.showStatsBounceDistanceBorder;
			case BOUNCE_TICKS -> VarioConfig.showStatsBounceTicksBorder;
		};
	}

	/** How many of this panel's rows are switched on, which is whether it is drawn at all. */
	public int rows() {
		int shown = switch (this) {
			case OTHER -> count(VarioConfig.showPitch, VarioConfig.showGlideRatio);
			case SPEED -> count(VarioConfig.showVerticalSpeed,
					VarioConfig.showHorizontalSpeed, VarioConfig.showTotalSpeed);
			case ACCEL -> count(VarioConfig.showVerticalAcceleration,
					VarioConfig.showHorizontalAcceleration, VarioConfig.showTotalAcceleration);
			case ENERGY -> count(VarioConfig.showKineticEnergy,
					VarioConfig.showPotentialEnergy, VarioConfig.showTotalEnergy,
					VarioConfig.showCycleGain);
			case BOUNCE_VELOCITY -> count(VarioConfig.showBounceVelocityY,
					VarioConfig.showBounceVelocityXz, VarioConfig.showBounceVelocityXyz);
			case BOUNCE_DISTANCE -> count(VarioConfig.showBounceDistanceY,
					VarioConfig.showBounceDistanceXz, VarioConfig.showBounceDistanceXyz);
			case BOUNCE_TICKS -> count(VarioConfig.showBounceTicks);
		};
		return shown == 0 ? 0 : shown + (headed() ? 1 : 0);
	}

	/**
	 * Whether this panel's top row says what its figures are measured in, and which columns they
	 * stand in where it has more than one.
	 *
	 * <p>The heading is not a row that can be switched off: it is what lets the rows under it be
	 * labelled {@code Y} rather than {@code SPEED Y}, so a panel showing any row at all shows it,
	 * and a panel showing none is not drawn at all, heading included. {@link #OTHER} is the one
	 * panel without one, because a pitch in degrees and a dimensionless ratio share no unit that
	 * a heading could state.
	 */
	public boolean headed() {
		return this != OTHER;
	}

	/**
	 * The height this panel lays itself out in with {@code shown} of its rows switched on, which
	 * is the height it wants at a text size of one. Asked for by
	 * {@link jealoustone.elytravario.config.ConfigStore}, which has to give a panel the height
	 * its rows need at a text size read off a file rather than off the panel.
	 */
	public int layoutHeightFor(int shown) {
		return (shown + (headed() ? 1 : 0)) * LINE + PAD * 2;
	}

	private static int count(boolean... switches) {
		int on = 0;
		for (boolean shown : switches) {
			if (shown) on++;
		}
		return on;
	}

	/** Whether the HUD draws this panel: the instrument is up, it is on, and it has a row. */
	public boolean visible(boolean gliding) {
		return VarioInstrument.STATS.visible(gliding) && shown() && rows() > 0;
	}

	/** The height the rows lay themselves out in before scaling. */
	public int layoutHeight() { return rows() * LINE + PAD * 2; }

	/**
	 * The panel's on-screen height, which is its rows at its text size and not a setting.
	 *
	 * <p><b>Derived, so that switching a row off shortens the panel instead of resizing the
	 * text.</b> When the height was the setting and the text size the quotient, turning off a
	 * row shrank the divisor and left the dividend where it was, so a checkbox saying "show
	 * total speed" also enlarged every letter on the panel. This way round a checkbox does the
	 * one thing it says.
	 *
	 * <p>Rounded up, because the rows come to a whole number of <em>screen</em> pixels rather
	 * than a whole number of GUI pixels: at a GUI scale of 4 and a third of the font's size the
	 * rows are three quarters of a GUI pixel short of the box. The slack is under one GUI pixel
	 * and lands in the bottom padding.
	 */
	public int height() {
		return Math.ceilDiv(layoutHeight() * fontPixels(), guiScale);
	}

	/** How many times its own size the font is drawn at, as the renderer's scale factor. */
	public double scale() { return (double) fontPixels() / guiScale; }

	/** The narrowest width that keeps this panel's columns apart at its current text size. */
	public int minWidth() { return minWidth(fontPixels()); }

	/**
	 * The same floor at a text size this panel is not currently set to, which the position
	 * editor asks for: a drag needs a floor that does not move as the drag moves the text size,
	 * or the two settings chase each other from one event to the next. See
	 * {@link jealoustone.elytravario.config.ModulePositionEditor#narrowestWidth}.
	 *
	 * @param fontPixels the text size in screen pixels per font pixel, as {@link #fontPixels()}
	 */
	public int minWidth(int fontPixels) {
		return Math.ceilDiv(minLayoutWidth * fontPixels, guiScale);
	}

	/**
	 * The largest text size this panel's contents still fit in {@code width} pixels at, in
	 * screen pixels per font pixel.
	 *
	 * <p>This is the exact inverse of {@link #minWidth(int)}: that one rounds a width up, so
	 * this one divides the width back down in the same units and every size it returns is one
	 * whose floor really does fit. A panel is therefore never drawn wider than a resize placed
	 * it. Zero where even the smallest text does not fit, which the caller clamps away.
	 */
	public int maxFontPixelsForWidth(int width) {
		return width * guiScale / minLayoutWidth;
	}

	/** The panel's on-screen width, never narrower than its content. */
	public int width() { return Math.max(configuredWidth(), minWidth()); }

	/**
	 * The width used for layout before scaling.
	 *
	 * <p>Rounded down rather than to nearest. The rows are scaled by exactly {@link #scale()} on
	 * both axes, so this is the edge they are right-aligned onto multiplied back up; rounding it
	 * up would put that edge a fraction of a pixel outside the box the panel was given.
	 */
	public int layoutWidth() { return width() * guiScale / fontPixels(); }
}

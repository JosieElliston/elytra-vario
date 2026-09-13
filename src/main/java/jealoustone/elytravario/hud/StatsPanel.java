package jealoustone.elytravario.hud;

import static jealoustone.elytravario.hud.LayoutWidths.row;
import static jealoustone.elytravario.hud.LayoutWidths.widest;
import static jealoustone.elytravario.hud.MatrixLayout.ABSOLUTE_COLUMN;
import static jealoustone.elytravario.hud.MatrixLayout.BOUNCE_COLUMN;
import static jealoustone.elytravario.hud.MatrixLayout.DELTA_COLUMN;

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
	 * <p>Its floor is 116: {@code SPEED XYZ} is 52 pixels of label and the template is 54, a
	 * straight-down terminal velocity being the widest speed that is ever actually read.
	 */
	SPEED("statsSpeed", row("SPEED XYZ", "-00.00 b/s"),
			"showVerticalSpeed", "showHorizontalSpeed", "showTotalSpeed"),

	/**
	 * The same three quantities differentiated, and within a pixel of the same floor at 115:
	 * {@code ACCEL XYZ} measures the same 52 pixels as {@code SPEED XYZ}, and an acceleration is
	 * a pixel inside a speed, the superscript being narrower than a digit. A two-digit
	 * acceleration runs six wider than the template and encroaches on its label, which is what a
	 * figure that runs long does on any of these panels.
	 */
	ACCEL("statsAccel", row("ACCEL XYZ", "+0.00 b/s²"), "showVerticalAcceleration",
			"showHorizontalAcceleration", "showTotalAcceleration"),

	/**
	 * Kinetic, potential and total energy, and what the last cycle gained.
	 *
	 * <p>Its labels are the shortest of the panels and its floor is still 106, because
	 * {@code PE} and {@code TE} carry two figures rather than one. Declared in that two-column
	 * mode — the widest of the three energy references, and the default — so that changing the
	 * reference never moves the floor under a width that has already been set.
	 */
	ENERGY("statsEnergy", widest(
			row("KE", DELTA_COLUMN),
			row("TE", ABSOLUTE_COLUMN, DELTA_COLUMN),
			row("GAIN", DELTA_COLUMN)),
			"showKineticEnergy", "showPotentialEnergy", "showTotalEnergy", "showCycleGain"),

	/**
	 * Touch, leave and deploy velocities as columns, with X, XZ and XYZ as rows.
	 *
	 * <p>The widest panel, at 150, and the one place a heading row costs less than the figures
	 * under it: {@code VEL b/s} is the longest label here, but the headings it runs at are
	 * single letters right-aligned in columns reserved for a whole figure, so it is the rows of
	 * three figures that set the floor.
	 */
	BOUNCE_VELOCITY("statsBounceVelocity", widest(
			row("VEL b/s", "T", BOUNCE_COLUMN, BOUNCE_COLUMN),
			row("XYZ", BOUNCE_COLUMN, BOUNCE_COLUMN, BOUNCE_COLUMN)),
			"showBounceVelocityX", "showBounceVelocityXz", "showBounceVelocityXyz"),

	/**
	 * Leave-touch and deploy-leave position differences as columns.
	 *
	 * <p>An interval is only ever measured against the event before it, so there is no column
	 * for touch: two columns rather than the velocity matrix's three, and a floor of 110 where
	 * that panel needs 150. The columns are still right-aligned onto the same edges, so at equal
	 * widths they line up under that panel's {@code L} and {@code D}.
	 */
	BOUNCE_DISTANCE("statsBounceDistance", widest(
			row("DELTA b", "L-T", BOUNCE_COLUMN),
			row("XYZ", BOUNCE_COLUMN, BOUNCE_COLUMN)),
			"showBounceDistanceX", "showBounceDistanceXz", "showBounceDistanceXyz"),

	/**
	 * Leave-touch and deploy-leave elapsed ticks as the same two columns.
	 *
	 * <p>Its one row of figures carries no label of its own — the panel's heading says what they
	 * are — so the heading row is what sets the floor, at 98.
	 */
	BOUNCE_TICKS("statsBounceTicks", widest(
			row("TICKS", "L-T", BOUNCE_COLUMN),
			row("", BOUNCE_COLUMN, BOUNCE_COLUMN)),
			"showBounceTicks");

	/** The height of one row in the shared unscaled layout. */
	public static final int LINE = 10;

	/** The padding inside a panel's border, on all four sides. */
	public static final int PAD = 4;
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
	public String heightKey() { return prefix + "Height"; }
	public String opacityKey() { return prefix + "Opacity"; }
	public String borderKey() { return "showStats" + suffix() + "Border"; }

	private String suffix() { return prefix.substring("stats".length()); }

	/** This panel's row switches, in the order it draws them. */
	public List<String> rowKeys() { return rowKeys; }

	public static StatsPanel byGroup(String group) { return by(group, StatsPanel::group); }
	public static StatsPanel byWidthKey(String key) { return by(key, StatsPanel::widthKey); }
	public static StatsPanel byHeightKey(String key) { return by(key, StatsPanel::heightKey); }

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

	/** The panel's configured on-screen height. */
	public int height() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherHeight;
			case SPEED -> VarioConfig.statsSpeedHeight;
			case ACCEL -> VarioConfig.statsAccelHeight;
			case ENERGY -> VarioConfig.statsEnergyHeight;
			case BOUNCE_VELOCITY -> VarioConfig.statsBounceVelocityHeight;
			case BOUNCE_DISTANCE -> VarioConfig.statsBounceDistanceHeight;
			case BOUNCE_TICKS -> VarioConfig.statsBounceTicksHeight;
		};
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
		return switch (this) {
			case OTHER -> count(VarioConfig.showPitch, VarioConfig.showGlideRatio);
			case SPEED -> count(VarioConfig.showVerticalSpeed, VarioConfig.showHorizontalSpeed,
					VarioConfig.showTotalSpeed);
			case ACCEL -> count(VarioConfig.showVerticalAcceleration,
					VarioConfig.showHorizontalAcceleration, VarioConfig.showTotalAcceleration);
			case ENERGY -> count(VarioConfig.showKineticEnergy, VarioConfig.showPotentialEnergy,
					VarioConfig.showTotalEnergy, VarioConfig.showCycleGain);
			case BOUNCE_VELOCITY -> headedRows(VarioConfig.showBounceVelocityX,
					VarioConfig.showBounceVelocityXz, VarioConfig.showBounceVelocityXyz);
			case BOUNCE_DISTANCE -> headedRows(VarioConfig.showBounceDistanceX,
					VarioConfig.showBounceDistanceXz, VarioConfig.showBounceDistanceXyz);
			case BOUNCE_TICKS -> headedRows(VarioConfig.showBounceTicks);
		};
	}

	private static int headedRows(boolean... switches) {
		int content = count(switches);
		return content == 0 ? 0 : content + 1;
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

	/** The text size set by the configured height. */
	public double scale() { return (double) height() / layoutHeight(); }

	/** The narrowest width that keeps this panel's columns apart at its current text size. */
	public int minWidth() { return (int) Math.ceil(minLayoutWidth * scale()); }

	/**
	 * The tallest this panel can be while its contents still fit in {@code width} pixels.
	 *
	 * <p>This is the inverse of {@link #minWidth()}. The floor is deliberate: the corresponding
	 * minimum width uses a ceiling, so this is the greatest integer height guaranteed not to
	 * make the rendered panel wider than the edge a resize has already placed.
	 */
	public int maxHeightForWidth(int width) {
		return (int) Math.floorDiv((long) width * layoutHeight(), minLayoutWidth);
	}

	/** The panel's on-screen width, never narrower than its content. */
	public int width() { return Math.max(configuredWidth(), minWidth()); }

	/** The width used for layout before scaling. */
	public int layoutWidth() { return (int) Math.round(width() / scale()); }
}

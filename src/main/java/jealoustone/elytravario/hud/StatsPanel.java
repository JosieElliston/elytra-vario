package jealoustone.elytravario.hud;

import java.util.List;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;

/**
 * The four boxes Flight Stats is drawn as, each placed, sized and switched on its own.
 *
 * <p>One panel per <em>kind</em> of reading rather than one panel for all of them. The old
 * single panel had to give every row the same width, and the width a row wants is set by the
 * widest label and figure it carries: {@code SPEED XYZ} against a speed is 106 pixels of
 * content, while {@code GLIDE} against a ratio is 76, so a panel wide enough for the one was
 * carrying thirty pixels of dead gap on the other. Split, each box is only as wide as its own
 * rows need, and the four can be stacked, spread across the screen, or switched off one at a
 * time. Nothing about what a row <em>says</em> changed.
 *
 * <p>They are still meant to read as one instrument, which is what the editor's overlap snap is
 * for: butted together with their borders sharing a column, four panels look like one panel
 * ruled into sections — the rule the old panel drew between its speed and energy halves, now
 * available between any two of them and in either direction. See
 * {@link jealoustone.elytravario.config.ModulePositionEditor}.
 *
 * <p>Each panel's settings are one subpage of the Flight Stats page, named by {@link #group()},
 * and every one of them is this panel's prefix plus a suffix. Only the master switch, its
 * gliding-only companion and the positive and negative colors remain shared: the switch is what
 * the toggle key binds to, and the colors are a palette rather than a layout.
 */
public enum StatsPanel {
	/**
	 * The two readings that are neither a speed nor an energy: attitude, and its efficiency.
	 *
	 * <p>The narrowest of the four, and the panel that pays for the split on its own: at 86 it
	 * is thirty pixels inside what the speed rows need, and under one shared width every one of
	 * those thirty was dead gap. {@code GLIDE} is 28 pixels of label and a ratio like
	 * {@code -12.34 : 1} is 48 of figure — signed, because a climb reads as blocks forward per
	 * block gained — the padding takes eight, and two are left over. A pitch is narrower than
	 * either, at 31 pixels for {@code -90.0°}.
	 */
	OTHER("statsOther", 86, "showPitch", "showGlideRatio"),

	/**
	 * Vertical, horizontal and total speed, in that order: the signed one first, since it is the
	 * one being flown by, and the two magnitudes under it.
	 *
	 * <p>{@code SPEED XYZ} is 52 pixels of label and a speed like {@code -78.40 b/s} — straight
	 * down terminal velocity, so the widest that is ever actually read — is 54 of figure, the
	 * padding takes eight, and two are left over.
	 */
	SPEED("statsSpeed", 116, "showVerticalSpeed", "showHorizontalSpeed", "showTotalSpeed"),

	/**
	 * The same three quantities differentiated, and the same width: {@code ACCEL XYZ} measures
	 * the same 52 pixels as {@code SPEED XYZ}, and an acceleration like {@code +5.09 b/s²} much
	 * the same as a speed. A two-digit acceleration runs to 59 and encroaches on its label,
	 * which is what a figure that runs long does on any of these panels.
	 */
	ACCEL("statsAccel", 116, "showVerticalAcceleration", "showHorizontalAcceleration",
			"showTotalAcceleration"),

	/**
	 * Kinetic, potential and total energy, and what the last cycle gained.
	 *
	 * <p>Its labels are the shortest of the four and its widest row is still 106, because
	 * {@code PE} and {@code TE} carry two figures rather than one: 12 pixels of label, then the
	 * absolute column's 38, a pad, and the delta column's 42, with the panel's own padding
	 * either side. Measured in that two-column mode — the widest of the three energy references,
	 * and the default — so that changing the reference never moves the floor under a width that
	 * has already been set.
	 */
	ENERGY("statsEnergy", 106, "showKineticEnergy", "showPotentialEnergy", "showTotalEnergy",
			"showCycleGain");

	/** The height of one row, in layout pixels, before a panel's height scales the text. */
	public static final int LINE = 10;

	/** The padding inside a panel's border, on all four sides. */
	public static final int PAD = 4;

	private final String prefix;
	private final int minLayoutWidth;
	private final List<String> rowKeys;

	/**
	 * @param minLayoutWidth the narrowest this panel is laid out before its height's text size
	 *     scales it; see {@link #minWidth()}
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
		};
	}

	public int x() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherX;
			case SPEED -> VarioConfig.statsSpeedX;
			case ACCEL -> VarioConfig.statsAccelX;
			case ENERGY -> VarioConfig.statsEnergyX;
		};
	}

	public int y() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherY;
			case SPEED -> VarioConfig.statsSpeedY;
			case ACCEL -> VarioConfig.statsAccelY;
			case ENERGY -> VarioConfig.statsEnergyY;
		};
	}

	/** The width setting, before {@link #minWidth()} raises it. */
	public int configuredWidth() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherWidth;
			case SPEED -> VarioConfig.statsSpeedWidth;
			case ACCEL -> VarioConfig.statsAccelWidth;
			case ENERGY -> VarioConfig.statsEnergyWidth;
		};
	}

	/** The panel's on-screen height, which is exactly the setting. */
	public int height() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherHeight;
			case SPEED -> VarioConfig.statsSpeedHeight;
			case ACCEL -> VarioConfig.statsAccelHeight;
			case ENERGY -> VarioConfig.statsEnergyHeight;
		};
	}

	public double opacity() {
		return switch (this) {
			case OTHER -> VarioConfig.statsOtherOpacity;
			case SPEED -> VarioConfig.statsSpeedOpacity;
			case ACCEL -> VarioConfig.statsAccelOpacity;
			case ENERGY -> VarioConfig.statsEnergyOpacity;
		};
	}

	public boolean border() {
		return switch (this) {
			case OTHER -> VarioConfig.showStatsOtherBorder;
			case SPEED -> VarioConfig.showStatsSpeedBorder;
			case ACCEL -> VarioConfig.showStatsAccelBorder;
			case ENERGY -> VarioConfig.showStatsEnergyBorder;
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
		};
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

	/** The height the rows lay themselves out in, before the height setting scales them. */
	public int layoutHeight() { return rows() * LINE + PAD * 2; }

	/** The text size, set by the height: the rows are drawn to exactly fill it. */
	public double scale() { return (double) height() / layoutHeight(); }

	/**
	 * The narrowest this panel may be drawn at its current text size.
	 *
	 * <p>Each panel's layout minimum is where its own widest row has met itself — label, figure
	 * and padding with nothing between them — so that narrowing further would stack one column
	 * on the other rather than close a gap. The figure each is measured against is a
	 * representative one rather than the worst imaginable; see the constants above for the
	 * arithmetic, every figure in it measured through the font rather than guessed at.
	 *
	 * <p>The grips stop here too, so only a width typed into the box can ask for less, and that
	 * is drawn at the minimum rather than refused.
	 */
	public int minWidth() { return (int) Math.ceil(minLayoutWidth * scale()); }

	/** The panel's on-screen width, never narrower than its rows need. */
	public int width() { return Math.max(configuredWidth(), minWidth()); }

	/** The width the panel lays its two columns out in, before the text size scales it up. */
	public int layoutWidth() { return (int) Math.round(width() / scale()); }
}

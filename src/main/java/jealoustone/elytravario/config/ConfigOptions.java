package jealoustone.elytravario.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.hud.StatsPanel;

/** One schema for screen controls, disk validation, defaults, and runtime application. */
public final class ConfigOptions {
	private static final List<Option> OPTIONS = new ArrayList<>();
	private static final List<String> MARKER_PREFIXES = List.of("holdPitch", "optimalPitch",
			"lookaheadPitch", "minimumFallSpeedPitch", "zeroPitch", "maxHorizontalSpeedPitch");
	// Which page a setting is on, and which section of it, are both answered here, so that the
	// settings screen, the layout editor and the config file all read the same order.

	public record Option(Field field, int page, String group, double min, double max, double factor,
			int choices, boolean color, boolean advanced, String defaultValue) {
		public String key() { return field.getName(); }
		public boolean toggle() { return field.getType() == boolean.class; }
		/** Whether the setting counts in whole units, so that a computed value has to round. */
		public boolean integral() { return field.getType() == int.class; }
		public boolean opaque() { return key().startsWith("chartField") && color; }

		public Object parse(String text) {
			if (toggle()) {
				if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException(key());
				return Boolean.parseBoolean(text);
			}
			if (color) {
				String hex = text.startsWith("#") ? text.substring(1) : text;
				if (!hex.matches(opaque() ? "[0-9a-fA-F]{6}" : "[0-9a-fA-F]{8}")) {
					throw new IllegalArgumentException(key());
				}
				return (int) Long.parseLong(hex, 16) | (opaque() ? 0xFF000000 : 0);
			}
			double displayed = Double.parseDouble(text);
			if (!Double.isFinite(displayed) || displayed < min || displayed > max) {
				throw new IllegalArgumentException(key());
			}
			double value = displayed / factor;
			if (field.getType() == int.class) {
				if (Math.abs(value - Math.rint(value)) > 1e-7) throw new IllegalArgumentException(key());
				return (int) Math.round(value);
			}
			return value;
		}

		public String format(Object value) {
			if (toggle()) return value.toString();
			if (color) return String.format(Locale.ROOT, opaque() ? "%06X" : "%08X",
					((Number) value).intValue() & (opaque() ? 0xFFFFFF : 0xFFFFFFFF));
			double displayed = ((Number) value).doubleValue() * factor;
			return java.math.BigDecimal.valueOf(displayed).setScale(6, java.math.RoundingMode.HALF_UP)
					.stripTrailingZeros().toPlainString();
		}

		public String current() {
			try { return format(field.get(null)); }
			catch (IllegalAccessException e) { throw new IllegalStateException(e); }
		}
	}

	static {
		// Every setting names the section it is drawn in, and the screen draws a page's
		// sections in the order their first setting is declared here. "general" leads every
		// page: the layout button, the toggle key, the instrument's switch and its
		// gliding-only companion, in that order on all seven pages. The screen adds the first
		// two itself — neither is a setting of this mod's own file — so a page's general
		// section starts here at the switch.
		add("enabled", 0, "general", 0, 1, 1, 0, false, false);
		add("hudGlidingOnly", 0, "general", 0, 1, 1, 0, false, false);
		add("positionMargin", 0, "layoutEditor", 0, 64, 1, 0, false, false);
		add("positionSnapDistance", 0, "layoutEditor", 0, 64, 1, 0, false, false);

		add("showLadder", 1, "general", 0, 1, 1, 0, false, false);
		add("ladderGlidingOnly", 1, "general", 0, 1, 1, 0, false, false);

		// The band is where the ladder reaches to, and the ladder markers are drawn in the
		// same one, so these four are the settings the Ladder Markers page shares — which is
		// why they stay available while either page's switch is on.
		add("ladderCenterGap", 1, "ladderBand", 12, 100, 1, 0, false, false);
		add("ladderBandFractionUp", 1, "ladderBand", 5, 100, 100, 0, false, false);
		add("ladderBandFractionDown", 1, "ladderBand", 5, 100, 100, 0, false, false);
		add("ladderFadeFraction", 1, "ladderBand", 0, 50, 100, 0, false, true);

		add("ladderOpacity", 1, "ladderRungs", 0, 100, 100, 0, false, false);
		add("showLadderLabels", 1, "ladderRungs", 0, 1, 1, 0, false, false);
		add("ladderMinorLength", 1, "ladderRungs", 1, 100, 1, 0, false, true);
		add("ladderMajorLength", 1, "ladderRungs", 1, 150, 1, 0, false, true);
		add("ladderPrimeLength", 1, "ladderRungs", 1, 200, 1, 0, false, true);
		add("ladderHorizonExtra", 1, "ladderRungs", 0, 100, 1, 0, false, true);

		add("showFineTicks", 1, "ladderFineTicks", 0, 1, 1, 0, false, false);
		add("ladderFineLength", 1, "ladderFineTicks", 1, 60, 1, 0, false, true);
		add("ladderFineStepDegrees", 1, "ladderFineTicks", 1, 10, 1, 0, false, true);
		add("ladderFineRangeDegrees", 1, "ladderFineTicks", 1, 30, 1, 0, false, true);

		add("showLadderMarkers", 2, "general", 0, 1, 1, 0, false, false);
		add("ladderMarkersGlidingOnly", 2, "general", 0, 1, 1, 0, false, false);

		// Both shadow switches cut across the markers rather than belonging to one, so they
		// are a section of their own above the per-marker ones.
		add("showDynamicMarkerShadows", 2, "markerShadows", 0, 1, 1, 0, false, false);
		add("showStaticMarkerShadows", 2, "markerShadows", 0, 1, 1, 0, false, false);

		// One section per marker, in the order they are drawn in and of the rows within each.
		add("showFlightPath", 2, "flightPath", 0, 1, 1, 0, false, false);
		add("flightPathColor", 2, "flightPath", 0, 1, 1, 0, true, false);

		add("showHoldPitch", 2, "hold", 0, 1, 1, 0, false, false);
		add("holdPitchColor", 2, "hold", 0, 1, 1, 0, true, false);
		addMarkerShape("holdPitch", "hold");

		add("showOptimalPitch", 2, "optimal", 0, 1, 1, 0, false, false);
		add("optimalPitchColor", 2, "optimal", 0, 1, 1, 0, true, false);
		addMarkerShape("optimalPitch", "optimal");

		add("showLookaheadPitch", 2, "lookahead", 0, 1, 1, 0, false, false);
		add("lookaheadPitchColor", 2, "lookahead", 0, 1, 1, 0, true, false);
		addMarkerShape("lookaheadPitch", "lookahead");
		add("lookaheadTicks", 2, "lookahead", 1, 60, 1, 0, false, true);

		add("showMinimumFallSpeedPitch", 2, "minimumFallSpeed", 0, 1, 1, 0, false, false);
		add("minimumFallSpeedPitchColor", 2, "minimumFallSpeed", 0, 1, 1, 0, true, false);
		addMarkerShape("minimumFallSpeedPitch", "minimumFallSpeed");

		add("showZeroPitch", 2, "zero", 0, 1, 1, 0, false, false);
		add("zeroPitchColor", 2, "zero", 0, 1, 1, 0, true, false);
		addMarkerShape("zeroPitch", "zero");

		add("showMaxHorizontalSpeedPitch", 2, "maxHorizontalSpeed", 0, 1, 1, 0, false, false);
		add("maxHorizontalSpeedPitchColor", 2, "maxHorizontalSpeed", 0, 1, 1, 0, true, false);
		addMarkerShape("maxHorizontalSpeedPitch", "maxHorizontalSpeed");

		// Position and size are the layout editor's, so they never reach the settings list;
		// they are declared in the general section because that is where its button is.
		add("showChart", 3, "general", 0, 1, 1, 0, false, false);
		add("chartGlidingOnly", 3, "general", 0, 1, 1, 0, false, false);
		add("chartX", 3, "general", -4096, 4096, 1, 0, false, false);
		add("chartY", 3, "general", -4096, 4096, 1, 0, false, false);
		add("chartSize", 3, "general", 2, 512, 1, 0, false, false);

		add("chartMinVxz", 3, "chartAxes", -200, 200, 20, 0, false, false);
		add("chartMaxVxz", 3, "chartAxes", -200, 200, 20, 0, false, false);
		add("chartMinVy", 3, "chartAxes", -200, 200, 20, 0, false, false);
		add("chartMaxVy", 3, "chartAxes", -200, 200, 20, 0, false, false);
		add("showGrid", 3, "chartAxes", 0, 1, 1, 0, false, false);
		add("showAxisLabels", 3, "chartAxes", 0, 1, 1, 0, false, false);

		add("showTrail", 3, "chartTrail", 0, 1, 1, 0, false, false);
		add("chartTrailTicks", 3, "chartTrail", 0.05, 10, 0.05, 0, false, false);
		add("trailColor", 3, "chartTrail", 0, 1, 1, 0, true, false);

		add("showEnergyField", 3, "chartEnergyField", 0, 1, 1, 0, false, false);
		add("chartFieldZeroColor", 3, "chartEnergyField", 0, 1, 1, 0, true, false);
		add("chartFieldGainColor", 3, "chartEnergyField", 0, 1, 1, 0, true, false);
		add("chartFieldLossColor", 3, "chartEnergyField", 0, 1, 1, 0, true, false);
		add("chartFieldScale", 3, "chartEnergyField", 0.001, 10, 1, 0, false, true);

		// A cursor and the arrow projecting it forward are one reading in two parts, so each
		// cursor takes a section rather than the cursors and the arrows taking one each.
		add("showHorizontalCursor", 3, "chartHorizontalCursor", 0, 1, 1, 0, false, false);
		add("cursorXzColor", 3, "chartHorizontalCursor", 0, 1, 1, 0, true, false);
		add("showHorizontalAccelerationArrow", 3, "chartHorizontalCursor", 0, 1, 1, 0, false, false);
		add("horizontalAccelerationArrowColor", 3, "chartHorizontalCursor", 0, 1, 1, 0, true, false);

		add("showForwardCursor", 3, "chartForwardCursor", 0, 1, 1, 0, false, false);
		add("cursorForwardColor", 3, "chartForwardCursor", 0, 1, 1, 0, true, false);
		add("showForwardAccelerationArrow", 3, "chartForwardCursor", 0, 1, 1, 0, false, false);
		add("forwardAccelerationArrowColor", 3, "chartForwardCursor", 0, 1, 1, 0, true, false);

		add("showStats", 4, "general", 0, 1, 1, 0, false, false);
		add("statsGlidingOnly", 4, "general", 0, 1, 1, 0, false, false);

		// The sign colors are a palette for every panel rather than a layout, so they sit
		// above the panels instead of inside one.
		add("positiveColor", 4, "statsColors", 0, 1, 1, 0, true, false);
		add("negativeColor", 4, "statsColors", 0, 1, 1, 0, true, false);

		for (StatsPanel panel : StatsPanel.values()) {
			String group = panel.group();
			add(panel.showKey(), 4, group, 0, 1, 1, 0, false, false);
			add(panel.xKey(), 4, group, -4096, 4096, 1, 0, false, false);
			add(panel.yKey(), 4, group, -4096, 4096, 1, 0, false, false);
			add(panel.widthKey(), 4, group, 32, 1200, 1, 0, false, false);
			// The panel's other dimension is its text size rather than its height, so that the
			// height is always exactly the rows it draws. The range runs down to a sixteenth
			// rather than to one: the drawable sizes are the multiples of one over the GUI
			// scale, so how far below one a panel can go is the player's GUI scale to decide and
			// not this. Whatever is set here is quantized to a drawable size when it is used —
			// see StatsPanel#fontPixels — so a hand-edited file cannot ask for a blurred one.
			add(panel.textSizeKey(), 4, group, 1.0 / 16, StatsPanel.MAX_TEXT_SIZE, 1, 0,
					false, false);
			add(panel.opacityKey(), 4, group, 0, 100, 100, 0, false, false);
			add(panel.borderKey(), 4, group, 0, 1, 1, 0, false, false);
			for (String row : panel.rowKeys()) add(row, 4, group, 0, 1, 1, 0, false, false);
			// The reference is the energy rows' own question: it is what PE and TE are measured
			// from, and it is what makes that panel's widest row two columns rather than one.
			if (panel == StatsPanel.ENERGY) {
				add("energyReference", 4, group, 0, 1, 1, 3, false, false);
			}
		}

		add("showBarSpeedo", 5, "general", 0, 1, 1, 0, false, false);
		add("barSpeedoGlidingOnly", 5, "general", 0, 1, 1, 0, false, false);
		add("barSpeedoX", 5, "general", -4096, 4096, 1, 0, false, false);
		add("barSpeedoY", 5, "general", -4096, 4096, 1, 0, false, false);
		add("barSpeedoHeight", 5, "general", 12, 200, 1, 0, false, false);

		// What is plotted comes before how it is scaled and what is drawn over it: a section
		// per bar, then the scale the bars are read against, then the marks laid on them.
		add("showBarSpeedoTotal", 5, "barSpeedoTotal", 0, 1, 1, 0, false, false);
		add("barSpeedoTotalColor", 5, "barSpeedoTotal", 0, 1, 1, 0, true, false);
		add("showBarSpeedoHorizontal", 5, "barSpeedoHorizontal", 0, 1, 1, 0, false, false);
		add("barSpeedoHorizontalColor", 5, "barSpeedoHorizontal", 0, 1, 1, 0, true, false);
		add("showBarSpeedoVertical", 5, "barSpeedoVertical", 0, 1, 1, 0, false, false);
		add("barSpeedoVerticalColor", 5, "barSpeedoVertical", 0, 1, 1, 0, true, false);

		add("barSpeedoMaxSpeed", 5, "barSpeedoScale", 1, 400, 20, 0, false, false);
		add("showBarSpeedoLabels", 5, "barSpeedoScale", 0, 1, 1, 0, false, false);
		add("barSpeedoMajorStep", 5, "barSpeedoScale", 1, 400, 20, 0, false, true);
		add("barSpeedoPeggedColor", 5, "barSpeedoScale", 0, 1, 1, 0, true, true);

		add("showBarSpeedoAcceleration", 5, "barSpeedoOverlays", 0, 1, 1, 0, false, false);
		add("showBarSpeedoMaxHorizontalSpeedMarkers", 5, "barSpeedoOverlays", 0, 1, 1, 0, false, false);
		add("showBarSpeedoTerminalVelocityMarkers", 5, "barSpeedoOverlays", 0, 1, 1, 0, false, false);

		add("showBarSpeedoBorder", 5, "barSpeedoPanel", 0, 1, 1, 0, false, false);
		add("barSpeedoOpacity", 5, "barSpeedoPanel", 0, 100, 100, 0, false, false);

		add("showDialSpeedo", 6, "general", 0, 1, 1, 0, false, false);
		add("dialSpeedoGlidingOnly", 6, "general", 0, 1, 1, 0, false, false);
		add("dialSpeedoX", 6, "general", -4096, 4096, 1, 0, false, false);
		add("dialSpeedoY", 6, "general", -4096, 4096, 1, 0, false, false);
		add("dialSpeedoRadius", 6, "general", 12, 200, 1, 0, false, false);

		add("showDialSpeedoTotal", 6, "dialSpeedoTotal", 0, 1, 1, 0, false, false);
		add("dialSpeedoTotalColor", 6, "dialSpeedoTotal", 0, 1, 1, 0, true, false);
		add("showDialSpeedoHorizontal", 6, "dialSpeedoHorizontal", 0, 1, 1, 0, false, false);
		add("dialSpeedoHorizontalColor", 6, "dialSpeedoHorizontal", 0, 1, 1, 0, true, false);
		add("showDialSpeedoVertical", 6, "dialSpeedoVertical", 0, 1, 1, 0, false, false);
		add("dialSpeedoVerticalColor", 6, "dialSpeedoVertical", 0, 1, 1, 0, true, false);

		add("dialSpeedoMaxSpeed", 6, "dialSpeedoScale", 1, 400, 20, 0, false, false);
		add("showDialSpeedoLabels", 6, "dialSpeedoScale", 0, 1, 1, 0, false, false);
		add("dialSpeedoMajorStep", 6, "dialSpeedoScale", 1, 400, 20, 0, false, true);
		add("dialSpeedoMinorStep", 6, "dialSpeedoScale", 1, 400, 20, 0, false, true);
		add("dialSpeedoPeggedColor", 6, "dialSpeedoScale", 0, 1, 1, 0, true, true);

		add("showDialSpeedoAcceleration", 6, "dialSpeedoOverlays", 0, 1, 1, 0, false, false);
		add("showDialSpeedoMaxHorizontalSpeedMarkers", 6, "dialSpeedoOverlays", 0, 1, 1, 0, false, false);
		add("showDialSpeedoTerminalVelocityMarkers", 6, "dialSpeedoOverlays", 0, 1, 1, 0, false, false);

		add("showDialSpeedoBorder", 6, "dialSpeedoPanel", 0, 1, 1, 0, false, false);
		add("dialSpeedoBackgroundOpacity", 6, "dialSpeedoPanel", 0, 100, 100, 0, false, false);
	}

	private static void addMarkerShape(String prefix, String group) {
		add(prefix + "Inset", 2, group, 0, 100, 1, 0, false, false);
		add(prefix + "Length", 2, group, 1, 100, 1, 0, false, false);
		add(prefix + "Step", 2, group, 0, 4, 1, 0, false, false);
	}

	private static void add(String key, int page, String group, double min, double max, double factor,
			int choices, boolean color, boolean advanced) {
		try {
			Field field = VarioConfig.class.getField(key);
			Option spec = new Option(field, page, group, min, choices > 0 ? choices - 1 : max,
					factor, choices, color, advanced, "");
			OPTIONS.add(new Option(field, page, group, spec.min(), spec.max(), factor,
					choices, color, advanced, spec.current()));
		} catch (NoSuchFieldException e) { throw new ExceptionInInitializerError(e); }
	}

	public static List<Option> all() { return List.copyOf(OPTIONS); }
	public static List<String> markerPrefixes() { return MARKER_PREFIXES; }

	/**
	 * The sections of a page that carry settings, in declaration order, the first always
	 * {@code general}.
	 *
	 * <p>A section the screen fills itself — page 0's keys — is not among them, there being no
	 * setting of this mod's own that names it.
	 */
	public static List<String> groups(int page) {
		List<String> groups = new ArrayList<>();
		for (Option option : OPTIONS) {
			if (option.page() == page && !groups.contains(option.group())) {
				groups.add(option.group());
			}
		}
		return groups;
	}

	public static Map<String, String> snapshot() {
		Map<String, String> values = new LinkedHashMap<>();
		for (Option option : OPTIONS) values.put(option.key(), option.current());
		return values;
	}

	public static Map<String, String> defaults() {
		Map<String, String> values = new LinkedHashMap<>();
		for (Option option : OPTIONS) values.put(option.key(), option.defaultValue());
		return values;
	}

	/** Returns a translation suffix, or null when all values and cross-field bounds are valid. */
	public static String error(Map<String, String> values) {
		Map<String, Object> parsed = new LinkedHashMap<>();
		try {
			for (Option option : OPTIONS) parsed.put(option.key(), option.parse(values.get(option.key())));
		} catch (RuntimeException e) { return "invalid"; }
		double x = number(parsed, "chartMaxVxz") - number(parsed, "chartMinVxz");
		double y = number(parsed, "chartMaxVy") - number(parsed, "chartMinVy");
		if (x < 0.05 - 1e-9 || y < 0.05 - 1e-9) return "range";
		double scale = number(parsed, "chartSize") / x;
		if (Math.round(y * scale) < 2 || Math.round(y * scale) > 512) return "size";
		int centerGap = (Integer) parsed.get("ladderCenterGap");
		for (String prefix : MARKER_PREFIXES) {
			int inset = (Integer) parsed.get(prefix + "Inset");
			int length = (Integer) parsed.get(prefix + "Length");
			if (inset + length > centerGap) return "markerSize";
			int step = (Integer) parsed.get(prefix + "Step");
			if (step >= length && step != 0) return "markerStep";
		}
		return null;
	}

	private static double number(Map<String, Object> values, String key) {
		return ((Number) values.get(key)).doubleValue();
	}

	/**
	 * Applies a set of values only if every one of them is complete and in range, so that a
	 * half-typed number leaves the last good values in force rather than reverting the HUD.
	 * This is what makes editing safe to apply as it is typed.
	 */
	public static void applyIfValid(Map<String, String> values) {
		if (error(values) == null) apply(values);
	}

	public static void apply(Map<String, String> values) {
		if (error(values) != null) throw new IllegalArgumentException("Invalid config");
		try {
			for (Option option : OPTIONS) option.field().set(null, option.parse(values.get(option.key())));
		} catch (IllegalAccessException e) { throw new IllegalStateException(e); }
	}

	private ConfigOptions() { }
}

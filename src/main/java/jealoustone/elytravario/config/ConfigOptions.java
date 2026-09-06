package jealoustone.elytravario.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jealoustone.elytravario.VarioConfig;

/** One schema for screen controls, disk validation, defaults, and runtime application. */
public final class ConfigOptions {
	private static final List<Option> OPTIONS = new ArrayList<>();
	// Which options sit above a page's subpage selector rather than in its list is the screen's
	// question, and VarioInstrument already names them, so nothing here has to.

	public record Option(Field field, int page, String group, double min, double max, double factor,
			int choices, boolean color, boolean advanced, String defaultValue) {
		public String key() { return field.getName(); }
		public boolean toggle() { return field.getType() == boolean.class; }
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
		add("enabled", 0, 0, 1, 1, 0, false, false);

		add("showLadder", 1, 0, 1, 1, 0, false, false);
		add("ladderGlidingOnly", 1, 0, 1, 1, 0, false, false);
		add("ladderOpacity", 1, 0, 100, 100, 0, false, false);
		add("ladderCenterGap", 1, 12, 100, 1, 0, false, false);
		add("ladderBandFractionUp", 1, 5, 100, 100, 0, false, false);
		add("ladderBandFractionDown", 1, 5, 100, 100, 0, false, false);
		add("showLadderLabels", 1, 0, 1, 1, 0, false, false);
		add("showFineTicks", 1, 0, 1, 1, 0, false, false);
		add("ladderFineLength", 1, 1, 60, 1, 0, false, true);
		add("ladderMinorLength", 1, 1, 100, 1, 0, false, true);
		add("ladderMajorLength", 1, 1, 150, 1, 0, false, true);
		add("ladderPrimeLength", 1, 1, 200, 1, 0, false, true);
		add("ladderHorizonExtra", 1, 0, 100, 1, 0, false, true);
		add("ladderFineStepDegrees", 1, 1, 10, 1, 0, false, true);
		add("ladderFineRangeDegrees", 1, 1, 30, 1, 0, false, true);
		add("ladderFadeFraction", 1, 0, 50, 100, 0, false, true);

		// The on/off switch heads the page; the rest is one subpage per marker, in the
		// order of the subpage dropdown and of the rows within each subpage.
		add("showMarkers", 2, 0, 1, 1, 0, false, false);
		add("markersGlidingOnly", 2, 0, 1, 1, 0, false, false);

		add("showLookaheadPitch", 2, "lookahead", 0, 1, 1, 0, false, false);
		add("lookaheadPitchColor", 2, "lookahead", 0, 1, 1, 0, true, false);
		add("lookaheadTicks", 2, "lookahead", 1, 60, 1, 0, false, true);

		add("showHoldPitch", 2, "hold", 0, 1, 1, 0, false, false);
		add("holdPitchColor", 2, "hold", 0, 1, 1, 0, true, false);

		add("showOptimalPitch", 2, "optimal", 0, 1, 1, 0, false, false);
		add("optimalPitchColor", 2, "optimal", 0, 1, 1, 0, true, false);

		add("showFlightPath", 2, "flightPath", 0, 1, 1, 0, false, false);
		add("flightPathColor", 2, "flightPath", 0, 1, 1, 0, true, false);

		add("showChart", 3, 0, 1, 1, 0, false, false);
		add("chartGlidingOnly", 3, 0, 1, 1, 0, false, false);
		add("chartAnchor", 3, 0, 1, 1, 9, false, false);
		add("chartX", 3, -4096, 4096, 1, 0, false, false);
		add("chartY", 3, -4096, 4096, 1, 0, false, false);
		add("chartScale", 3, 4, 128, 1, 0, false, false);
		add("chartMinVxz", 3, -200, 200, 20, 0, false, false);
		add("chartMaxVxz", 3, -200, 200, 20, 0, false, false);
		add("chartMinVy", 3, -200, 200, 20, 0, false, false);
		add("chartMaxVy", 3, -200, 200, 20, 0, false, false);
		add("showGrid", 3, 0, 1, 1, 0, false, false);
		add("showAxisLabels", 3, 0, 1, 1, 0, false, false);
		add("showTrail", 3, 0, 1, 1, 0, false, false);
		add("chartTrailTicks", 3, 0.05, 10, 0.05, 0, false, false);
		add("trailColor", 3, 0, 1, 1, 0, true, false);
		add("showEnergyField", 3, 0, 1, 1, 0, false, false);
		add("chartFieldZeroColor", 3, 0, 1, 1, 0, true, false);
		add("chartFieldGainColor", 3, 0, 1, 1, 0, true, false);
		add("chartFieldLossColor", 3, 0, 1, 1, 0, true, false);
		add("showHorizontalCursor", 3, 0, 1, 1, 0, false, false);
		add("cursorXzColor", 3, 0, 1, 1, 0, true, false);
		add("showForwardCursor", 3, 0, 1, 1, 0, false, false);
		add("cursorForwardColor", 3, 0, 1, 1, 0, true, false);
		add("chartFieldScale", 3, 0.001, 10, 1, 0, false, true);

		add("showStats", 4, 0, 1, 1, 0, false, false);
		add("statsGlidingOnly", 4, 0, 1, 1, 0, false, false);
		add("statsAnchor", 4, 0, 1, 1, 5, false, false);
		add("originX", 4, -4096, 4096, 1, 0, false, false);
		add("originY", 4, -4096, 4096, 1, 0, false, false);
		add("panelScale", 4, 0.5, 3, 1, 0, false, false);
		add("panelOpacity", 4, 0, 100, 100, 0, false, false);
		add("showPanelBorder", 4, 0, 1, 1, 0, false, false);
		add("showPitch", 4, 0, 1, 1, 0, false, false);
		add("showHorizontalSpeed", 4, 0, 1, 1, 0, false, false);
		add("showTotalSpeed", 4, 0, 1, 1, 0, false, false);
		add("showVerticalSpeed", 4, 0, 1, 1, 0, false, false);
		add("showGlideRatio", 4, 0, 1, 1, 0, false, false);
		add("showAngleOfAttack", 4, 0, 1, 1, 0, false, false);
		add("showKineticEnergy", 4, 0, 1, 1, 0, false, false);
		add("showPotentialEnergy", 4, 0, 1, 1, 0, false, false);
		add("showTotalEnergy", 4, 0, 1, 1, 0, false, false);
		add("showCycleGain", 4, 0, 1, 1, 0, false, false);
		add("energyReference", 4, 0, 1, 1, 3, false, false);
		add("positiveColor", 4, 0, 1, 1, 0, true, false);
		add("negativeColor", 4, 0, 1, 1, 0, true, false);
		add("panelWidth", 4, 132, 400, 1, 0, false, true);

		add("showSpeedo", 5, 0, 1, 1, 0, false, false);
		add("speedoGlidingOnly", 5, 0, 1, 1, 0, false, false);
		add("speedoAnchor", 5, 0, 1, 1, 5, false, false);
		add("speedoX", 5, -4096, 4096, 1, 0, false, false);
		add("speedoY", 5, -4096, 4096, 1, 0, false, false);
		add("speedoRadius", 5, 12, 200, 1, 0, false, false);
		add("speedoMaxSpeed", 5, 1, 400, 20, 0, false, false);
		add("showSpeedoTotal", 5, 0, 1, 1, 0, false, false);
		add("speedoTotalColor", 5, 0, 1, 1, 0, true, false);
		add("showSpeedoHorizontal", 5, 0, 1, 1, 0, false, false);
		add("speedoHorizontalColor", 5, 0, 1, 1, 0, true, false);
		add("showSpeedoVertical", 5, 0, 1, 1, 0, false, false);
		add("speedoVerticalColor", 5, 0, 1, 1, 0, true, false);
		add("showSpeedoLabels", 5, 0, 1, 1, 0, false, false);
		add("showSpeedoBorder", 5, 0, 1, 1, 0, false, false);
		add("speedoOpacity", 5, 0, 100, 100, 0, false, false);
		add("speedoMajorStep", 5, 1, 400, 20, 0, false, true);
		add("speedoMinorStep", 5, 1, 400, 20, 0, false, true);
		add("speedoPeggedColor", 5, 0, 1, 1, 0, true, true);
	}

	private static void add(String key, int page, double min, double max, double factor,
			int choices, boolean color, boolean advanced) {
		add(key, page, null, min, max, factor, choices, color, advanced);
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

	/** The page's subpages, in declaration order; empty when the page is not divided. */
	public static List<String> groups(int page) {
		List<String> groups = new ArrayList<>();
		for (Option option : OPTIONS) {
			if (option.page() == page && option.group() != null && !groups.contains(option.group())) {
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
		double scale = number(parsed, "chartScale");
		if (Math.round(x * scale) < 2 || Math.round(y * scale) < 2
				|| Math.round(x * scale) > 512 || Math.round(y * scale) > 512) return "size";
		return null;
	}

	private static double number(Map<String, Object> values, String key) {
		return ((Number) values.get(key)).doubleValue();
	}

	public static void apply(Map<String, String> values) {
		if (error(values) != null) throw new IllegalArgumentException("Invalid config");
		try {
			for (Option option : OPTIONS) option.field().set(null, option.parse(values.get(option.key())));
		} catch (IllegalAccessException e) { throw new IllegalStateException(e); }
	}

	private ConfigOptions() { }
}

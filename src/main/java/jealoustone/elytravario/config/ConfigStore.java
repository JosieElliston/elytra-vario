package jealoustone.elytravario.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.hud.StatsPanel;
import net.fabricmc.loader.api.FabricLoader;

/** Validates before applying and replaces the saved file atomically. */
public final class ConfigStore {
	/** The two switches a retired three-way visibility setting became. */
	private record Split(String show, String glidingOnly) { }

	/**
	 * Per-instrument visibility used to be one choice of always, only-while-gliding, or hidden,
	 * before the toggle keys made the on/off half of it worth having on its own. A file written
	 * before that split is read here rather than silently falling back to the defaults, which
	 * would turn every hidden instrument back on. The old key is not written back, so one save
	 * finishes the migration; this table can go once no config file predates the split.
	 */
	private static final Map<String, Split> RETIRED = Map.of(
			"ladderVisibility", new Split("showLadder", "ladderGlidingOnly"),
			"markerVisibility", new Split("showLadderMarkers", "ladderMarkersGlidingOnly"),
			"chartVisibility", new Split("showChart", "chartGlidingOnly"),
			"statsVisibility", new Split("showStats", "statsGlidingOnly"));
	/** Retired settings whose value belongs to one direct replacement. */
	private static final Map<String, String> RETIRED_KEYS = Map.of(
			"showMarkers", "showLadderMarkers",
			"markersGlidingOnly", "ladderMarkersGlidingOnly",
			"showBounceVelocityX", "showBounceVelocityY",
			"showBounceDistanceX", "showBounceDistanceY");
	/** Rows from the retired single Flight Stats panel, used to recover its text scale. */
	private static final List<String> SPEED_ROWS = List.of("showPitch", "showGlideRatio",
			"showHorizontalSpeed", "showTotalSpeed", "showVerticalSpeed",
			"showHorizontalAcceleration", "showTotalAcceleration", "showVerticalAcceleration");
	private static final List<String> ENERGY_ROWS = List.of("showKineticEnergy",
			"showPotentialEnergy", "showTotalEnergy", "showCycleGain");
	/** The layout width Flight Stats was scaled from, and the on-screen width it defaulted to. */
	private static final long RETIRED_PANEL_WIDTH = 132;
	/** Where the retired panel sat, before either coordinate had a setting of its own. */
	private static final long RETIRED_PANEL_X = 4;
	private static final long RETIRED_PANEL_Y = 4;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("elytra-vario.json");
	}

	public static void load() {
		// Capture factory defaults before the HUD key can mutate runtime settings.
		ConfigOptions.all();
		if (!Files.exists(path())) return;
		try {
			ConfigOptions.apply(decode(Files.readString(path(), StandardCharsets.UTF_8)));
		} catch (IOException | RuntimeException e) {
			ElytraVario.LOGGER.warn("Could not load elytra-vario.json; using defaults and leaving the file intact", e);
		}
	}

	public static Map<String, String> decode(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, String> values = ConfigOptions.defaults();
		boolean oldDial = root.has("speedoRadius") && !root.has("speedoHeight");
		// Marker geometry and its pixel preview arrived as one redesign. A config from before
		// that redesign has colors but no shape keys; carry its visibility and calculations
		// forward, but deliberately adopt the new appearance as one coherent default. Once the
		// file has been saved with shape keys, later appearance edits are ordinary persisted
		// settings again.
		boolean oldMarkerAppearance = !root.has("lookaheadPitchInset");
		for (var entry : RETIRED.entrySet()) {
			// A file holding both the old key and the new ones was written by a newer build, so
			// what it says now wins over what it used to say.
			if (!root.has(entry.getKey()) || root.has(entry.getValue().show())) continue;
			String mode = root.get(entry.getKey()).getAsString();
			values.put(entry.getValue().show(), Boolean.toString(!mode.equals("2")));
			values.put(entry.getValue().glidingOnly(), Boolean.toString(mode.equals("1")));
		}
		if (root.has("speedoVisibility")) {
			Split speedometer = oldDial
					? new Split("showDialSpeedo", "dialSpeedoGlidingOnly")
					: new Split("showBarSpeedo", "barSpeedoGlidingOnly");
			if (!root.has(speedometer.show())) {
				String mode = root.get("speedoVisibility").getAsString();
				values.put(speedometer.show(), Boolean.toString(!mode.equals("2")));
				values.put(speedometer.glidingOnly(), Boolean.toString(mode.equals("1")));
			}
		}
		for (var entry : RETIRED_KEYS.entrySet()) {
			if (root.has(entry.getKey()) && !root.has(entry.getValue())) {
				values.put(entry.getValue(), root.get(entry.getKey()).getAsString());
			}
		}
		for (var option : ConfigOptions.all()) {
			String legacy = oldDial ? legacyDialKey(option.key()) : legacyBarKey(option.key());
			if (legacy != null && root.has(legacy)) {
				values.put(option.key(), root.get(legacy).getAsString());
			}
			if (root.has(option.key())
					&& !(oldMarkerAppearance && markerAppearance(option.key()))) {
				values.put(option.key(), root.get(option.key()).getAsString());
			}
		}
		// Module size used to be stored as a floating-point scale. Preserve the exact rendered
		// width those settings produced; one save replaces the retired keys with pixel sizes.
		if (root.has("chartScale") && !root.has("chartSize")) {
			double horizontalRange = number(values, "chartMaxVxz") - number(values, "chartMinVxz");
			values.put("chartSize", Long.toString(Math.round(
					horizontalRange * root.get("chartScale").getAsDouble())));
		}
		splitStatsPanel(root, values);
		statsHeightToTextSize(root, values);
		if (ConfigOptions.error(values) != null) throw new IllegalArgumentException("Invalid config values");
		return values;
	}

	private static boolean markerAppearance(String key) {
		if (key.equals("flightPathColor")) return true;
		for (String prefix : ConfigOptions.markerPrefixes()) {
			if (key.equals(prefix + "Color") || key.equals(prefix + "Inset")
					|| key.equals(prefix + "Length") || key.equals(prefix + "Step")) return true;
		}
		return false;
	}

	/**
	 * Turns the retired single Flight Stats panel into the four it became, drawn where it was.
	 *
	 * <p>Each original panel keeps the retired position's x and width, takes the height its rows
	 * need at the retired panel's text size, and stacks under its predecessor with a shared border
	 * column. A panel with every row off is sized for its possible rows but is not stacked.
	 *
	 * <p>One save replaces every retired key. This can go once no config file predates the split.
	 */
	private static void splitStatsPanel(JsonObject root, Map<String, String> values) {
		// A file naming the panels was written after the split and says what it means.
		if (root.has(StatsPanel.OTHER.yKey()) || root.has(StatsPanel.OTHER.showKey())) return;
		// An unrelated partial config has no retired stats geometry to preserve.
		if (java.util.stream.Stream.of("panelWidth", "statsWidth", "statsSize", "panelScale",
				"statsHeight", "statsX", "statsY", "originX", "originY", "panelOpacity",
				"showPanelBorder", "showPitch", "showGlideRatio", "showHorizontalSpeed",
				"showTotalSpeed", "showVerticalSpeed", "showHorizontalAcceleration",
				"showTotalAcceleration", "showVerticalAcceleration", "showKineticEnergy",
				"showPotentialEnergy", "showTotalEnergy", "showCycleGain")
				.noneMatch(root::has)) return;
		long layoutWidth = root.has("panelWidth")
				? root.get("panelWidth").getAsLong() : RETIRED_PANEL_WIDTH;
		long width;
		if (root.has("statsWidth")) {
			width = root.get("statsWidth").getAsLong();
		} else if (root.has("statsSize")) {
			width = root.get("statsSize").getAsLong();
		} else if (root.has("panelScale")) {
			width = (long) Math.ceil(layoutWidth * root.get("panelScale").getAsDouble());
		} else {
			// Both defaulted to the same 132, so a file naming only the layout width was
			// drawing the panel at the default width and at whatever scale that came to.
			width = RETIRED_PANEL_WIDTH;
		}
		int retiredLayoutHeight = retiredLayoutHeight(values);
		// The retired panel was scaled by one factor on both axes, so either dimension recovers
		// it. Taken from whichever the file actually stated: deriving the height from the width
		// first, as this did while the answer was a whole number anyway, rounded a panel up to
		// the next whole pixel and then divided that rounding back into the text size.
		double scale = root.has("statsHeight")
				? (double) root.get("statsHeight").getAsLong() / retiredLayoutHeight
				: (double) width / layoutWidth;
		long x = number(root, "statsX", "originX", RETIRED_PANEL_X);
		long top = number(root, "statsY", "originY", RETIRED_PANEL_Y);
		// Only the four panels split out of the retired instrument belong in this migration.
		// Bounce panels did not exist in that file and keep their own factory positions.
		for (StatsPanel panel : List.of(StatsPanel.OTHER, StatsPanel.SPEED,
				StatsPanel.ACCEL, StatsPanel.ENERGY)) {
			int rows = rows(values, panel.rowKeys());
			int drawn = rows > 0 ? rows : panel.rowKeys().size();
			// The retired panel's text size, carried across as it stood. That is the whole point
			// of the migration: the same reading, the same size, in more boxes. It is quantized
			// to a size the font is drawn at when it is used rather than here, because that
			// depends on the GUI scale, which this file does not record and the player can
			// change afterwards.
			double textSize = Math.clamp(scale, 1.0 / 16, StatsPanel.MAX_TEXT_SIZE);
			// The height it will actually be drawn at, for stacking the next panel under it.
			// Asked of the panel rather than worked out here, so that a row the panel draws and
			// this file never knew about — its units heading — is counted. Rounded up for the
			// same reason StatsPanel#height rounds up, and so that the stack agrees with it:
			// rounding to nearest put a panel whose rows fall short of a whole pixel a pixel
			// above where it is drawn, and the one under it a pixel into it.
			//
			// The two agree wherever this size is one the font is drawn at. Where it is not —
			// three quarters at a GUI scale of two — the panel is drawn at the nearest size
			// that is, and stands a little taller or shorter than the stack expects. No stored
			// stack can help that: only a whole size has a height that is the same at every GUI
			// scale, and this migration is not willing to round the size to one.
			long panelHeight = (long) Math.ceil(panel.layoutHeightFor(drawn) * textSize);
			values.put(panel.xKey(), Long.toString(Math.clamp(x, -4096, 4096)));
			values.put(panel.yKey(), Long.toString(Math.clamp(top, -4096, 4096)));
			values.put(panel.widthKey(), Long.toString(Math.clamp(width, 32, 1200)));
			values.put(panel.textSizeKey(), format(panel.textSizeKey(), textSize));
			if (root.has("panelOpacity")) {
				values.put(panel.opacityKey(), root.get("panelOpacity").getAsString());
			}
			if (root.has("showPanelBorder")) {
				values.put(panel.borderKey(), root.get("showPanelBorder").getAsString());
			}
			if (rows > 0) top += panelHeight - ModulePositionEditor.OVERLAP;
		}
	}

	/**
	 * Turns a panel's retired pixel height into the text size that height was drawing it at.
	 *
	 * <p>The height was the setting and the text size the quotient; they have changed places, so
	 * a file from before that swap says how tall to be and nothing about how large to write.
	 * Dividing by the rows it was drawing recovers exactly what it meant.
	 *
	 * <p>A panel with every row switched off is measured against the rows it could draw, since
	 * that is what its height was set against. This can go once no config file predates the
	 * swap; the heights it reads were never released.
	 */
	private static void statsHeightToTextSize(JsonObject root, Map<String, String> values) {
		for (StatsPanel panel : StatsPanel.values()) {
			String retired = panel.group() + "Height";
			// Against the file, not against values: values is seeded with every default, so a
			// text size is always present there and would veto every migration.
			if (!root.has(retired) || root.has(panel.textSizeKey())) continue;
			int rows = rows(values, panel.rowKeys());
			int drawn = rows > 0 ? rows : panel.rowKeys().size();
			double size = (double) root.get(retired).getAsLong() / panel.layoutHeightFor(drawn);
			values.put(panel.textSizeKey(), format(panel.textSizeKey(),
					Math.clamp(size, 1.0 / 16, StatsPanel.MAX_TEXT_SIZE)));
		}
	}

	/**
	 * Writes a value the way the settings screen would, so it parses back to the same number.
	 *
	 * <p>Through the option rather than formatted here, so that a migration and a hand edit of
	 * the same setting are held to one notation and one precision.
	 */
	private static String format(String key, double value) {
		for (var option : ConfigOptions.all()) {
			if (option.key().equals(key)) return option.format(value);
		}
		throw new IllegalArgumentException(key);
	}

	/** How tall the retired panel laid itself out, including its optional separator row. */
	private static int retiredLayoutHeight(Map<String, String> values) {
		int speed = rows(values, SPEED_ROWS);
		int energy = rows(values, ENERGY_ROWS);
		return (speed + energy + (speed > 0 && energy > 0 ? 1 : 0)) * StatsPanel.LINE
				+ StatsPanel.PAD * 2;
	}

	/** The first of these keys the file names, or {@code fallback} where it names none. */
	private static long number(JsonObject root, String key, String retiredKey, long fallback) {
		if (root.has(key)) return root.get(key).getAsLong();
		if (root.has(retiredKey)) return root.get(retiredKey).getAsLong();
		return fallback;
	}

	/** How many of these rows the file leaves switched on. */
	private static int rows(Map<String, String> values, List<String> keys) {
		int count = 0;
		for (String key : keys) {
			if (Boolean.parseBoolean(values.get(key))) count++;
		}
		return count;
	}

	/** Parses a displayed config value through the same unit conversion as normal loading. */
	private static double number(Map<String, String> values, String key) {
		for (var option : ConfigOptions.all()) {
			if (option.key().equals(key)) return ((Number) option.parse(values.get(key))).doubleValue();
		}
		throw new IllegalArgumentException(key);
	}

	private static String legacyBarKey(String key) {
		if (key.equals("showBarSpeedoMaxHorizontalSpeedMarkers")) return "showSpeedoSoftMaxMarker";
		if (key.equals("showBarSpeedoTerminalVelocityMarkers")) return "showSpeedoTerminalMarker";
		if (key.startsWith("showBarSpeedo")) return "showSpeedo" + key.substring(13);
		if (key.startsWith("barSpeedo")) return "speedo" + key.substring(9);
		return null;
	}

	private static String legacyDialKey(String key) {
		if (key.startsWith("showDialSpeedo")) return "showSpeedo" + key.substring(14);
		if (key.startsWith("dialSpeedo")) return "speedo" + key.substring(10);
		return null;
	}

	public static String encode(Map<String, String> values) {
		if (ConfigOptions.error(values) != null) throw new IllegalArgumentException("Invalid config values");
		JsonObject root = new JsonObject();
		root.addProperty("_format", "Display units: speeds in b/s, durations as labeled, colors in hexadecimal.");
		for (var option : ConfigOptions.all()) root.addProperty(option.key(), values.get(option.key()));
		return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
	}

	public static void save(Map<String, String> values) throws IOException {
		save(path(), values);
	}

	public static void save(Path destination, Map<String, String> values) throws IOException {
		String json = encode(values);
		Files.createDirectories(destination.getParent());
		Path temp = Files.createTempFile(destination.getParent(), "elytra-vario-", ".tmp");
		try {
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			try {
				Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temp); }
	}

	private ConfigStore() { }
}

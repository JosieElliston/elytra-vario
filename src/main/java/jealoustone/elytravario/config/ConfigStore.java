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
	/** Ladder Markers used to be named just Markers, before the speedometers also had markers. */
	private static final Map<String, String> RETIRED_MARKER_KEYS = Map.of(
			"showMarkers", "showLadderMarkers",
			"markersGlidingOnly", "ladderMarkersGlidingOnly");
	/**
	 * The rows the retired single Flight Stats panel drew, in the two groups it ruled a line
	 * between. Only {@link #splitStatsPanel} needs them now, to work out how tall that panel
	 * was; each row's own setting survived the split untouched and merely moved subpage.
	 */
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
		for (var entry : RETIRED_MARKER_KEYS.entrySet()) {
			if (root.has(entry.getKey()) && !root.has(entry.getValue())) {
				values.put(entry.getValue(), root.get(entry.getKey()).getAsString());
			}
		}
		for (var option : ConfigOptions.all()) {
			String legacy = oldDial ? legacyDialKey(option.key()) : legacyBarKey(option.key());
			if (legacy != null && root.has(legacy)) {
				values.put(option.key(), root.get(legacy).getAsString());
			}
			if (root.has(option.key())) values.put(option.key(), root.get(option.key()).getAsString());
		}
		// Module size used to be stored as a floating-point scale. Preserve the exact rendered
		// width those settings produced; one save replaces the retired keys with pixel sizes.
		if (root.has("chartScale") && !root.has("chartSize")) {
			double horizontalRange = number(values, "chartMaxVxz") - number(values, "chartMinVxz");
			values.put("chartSize", Long.toString(Math.round(
					horizontalRange * root.get("chartScale").getAsDouble())));
		}
		splitStatsPanel(root, values);
		if (ConfigOptions.error(values) != null) throw new IllegalArgumentException("Invalid config values");
		return values;
	}

	/**
	 * Turns the retired single Flight Stats panel into the four it became, drawn where it was.
	 *
	 * <p>The whole of what that panel was is a position, a width and a height — the rows it
	 * carried are unchanged settings that merely moved subpage — so the split is a matter of
	 * dividing its box rather than of inventing anything. Each new panel keeps the old
	 * position's x and the old width, takes the height its own rows want at the <em>old
	 * panel's text size</em>, and is stacked under the one before it with their borders sharing
	 * a column, which is the overlap the editor now snaps to. So a migrated HUD reads at the
	 * size it read at, in the order it read in, with one extra rule where there was one before.
	 *
	 * <p>Finding that text size is the only arithmetic here, and it is the retired layout run
	 * backwards: the old panel laid its rows out at a fixed line height, added a rule between
	 * its halves when both had rows, and scaled the lot onto the height it was given.
	 *
	 * <p>The height itself was, in turn, three different retired settings — an on-screen width
	 * and, behind Advanced, the layout width it was scaled from; before that a scale. Each of
	 * them fixed the height too, so each is read here the way the panel used to read it.
	 *
	 * <p>A panel whose rows are all switched off is given the size its rows <em>would</em> want
	 * and is not stacked, since it is not drawn: it is the size it would appear at if one of
	 * them were switched back on, rather than a box left at some default.
	 *
	 * <p>One save replaces every retired key. This can go once no config file predates the split.
	 */
	private static void splitStatsPanel(JsonObject root, Map<String, String> values) {
		// A file naming the panels was written after the split and says what it means.
		if (root.has(StatsPanel.OTHER.yKey())) return;
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
		long height = root.has("statsHeight") ? root.get("statsHeight").getAsLong()
				: Math.ceilDiv(width * retiredLayoutHeight, layoutWidth);
		double scale = (double) height / retiredLayoutHeight;
		long x = number(root, "statsX", "originX", RETIRED_PANEL_X);
		long top = number(root, "statsY", "originY", RETIRED_PANEL_Y);
		for (StatsPanel panel : StatsPanel.values()) {
			int rows = rows(values, panel.rowKeys());
			int drawn = rows > 0 ? rows : panel.rowKeys().size();
			long panelHeight = Math.clamp(
					Math.round((drawn * StatsPanel.LINE + StatsPanel.PAD * 2) * scale), 16, 1200);
			values.put(panel.xKey(), Long.toString(Math.clamp(x, -4096, 4096)));
			values.put(panel.yKey(), Long.toString(Math.clamp(top, -4096, 4096)));
			values.put(panel.widthKey(), Long.toString(Math.clamp(width, 32, 1200)));
			values.put(panel.heightKey(), Long.toString(panelHeight));
			if (root.has("panelOpacity")) {
				values.put(panel.opacityKey(), root.get("panelOpacity").getAsString());
			}
			if (root.has("showPanelBorder")) {
				values.put(panel.borderKey(), root.get("showPanelBorder").getAsString());
			}
			if (rows > 0) top += panelHeight - ModulePositionEditor.OVERLAP;
		}
	}

	/** How tall the retired panel laid itself out, rows and the rule between its halves. */
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

package jealoustone.elytravario.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import jealoustone.elytravario.ElytraVario;
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
			"markerVisibility", new Split("showMarkers", "markersGlidingOnly"),
			"chartVisibility", new Split("showChart", "chartGlidingOnly"),
			"statsVisibility", new Split("showStats", "statsGlidingOnly"));
	/** Flight Stats used to call its coordinates an origin. */
	private static final Map<String, String> RETIRED_POSITIONS = Map.of(
			"originX", "statsX",
			"originY", "statsY");

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
		for (var entry : RETIRED_POSITIONS.entrySet()) {
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
		if (root.has("panelScale") && !root.has("statsSize")) {
			values.put("statsSize", Long.toString((long) Math.ceil(
					number(values, "panelWidth")
							* root.get("panelScale").getAsDouble())));
		}
		if (ConfigOptions.error(values) != null) throw new IllegalArgumentException("Invalid config values");
		return values;
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

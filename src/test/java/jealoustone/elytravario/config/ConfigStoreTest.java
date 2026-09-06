package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
	@TempDir Path directory;

	@Test void defaultsAreValidAndRoundTrip() {
		var defaults = ConfigOptions.defaults();
		assertNull(ConfigOptions.error(defaults));
		assertEquals(defaults, ConfigStore.decode(ConfigStore.encode(defaults)));
	}

	@Test void omittedSettingsUseDefaultsAndUnknownSettingsAreIgnored() {
		var values = ConfigStore.decode("{\"chartMinVxz\":\"-20\",\"futureOption\":true}");
		assertEquals("-20", values.get("chartMinVxz"));
		assertEquals("true", values.get("showHoldPitch"));
		assertEquals("true", values.get("showMaxHorizontalSpeedPitch"));
		assertEquals("true", values.get("showMinimumFallSpeedPitch"));
		assertEquals("true", values.get("showZeroPitch"));
		assertEquals("144", values.get("barSpeedoX"));
		assertEquals("240", values.get("dialSpeedoX"));
		assertEquals("4", values.get("positionMargin"));
		assertEquals("4", values.get("positionSnapDistance"));
		assertFalse(values.containsKey("speedoAnchor"));
		assertFalse(values.containsKey("futureOption"));
	}

	@Test void legacyDialSettingsReturnToTheDial() {
		var migrated = ConfigStore.decode("{\"speedoRadius\":48,\"speedoOpacity\":30,"
				+ "\"speedoX\":12,\"showSpeedoTotal\":false}");
		assertEquals("48", migrated.get("dialSpeedoRadius"));
		assertEquals("30", migrated.get("dialSpeedoOpacity"));
		assertEquals("12", migrated.get("dialSpeedoX"));
		assertEquals("false", migrated.get("showDialSpeedoTotal"));
		assertEquals("96", migrated.get("barSpeedoHeight"));
	}

	@Test void versionThirteenSettingsMigrateToTheBarSpeedometer() {
		var migrated = ConfigStore.decode("{\"speedoHeight\":72,\"speedoOpacity\":30,"
				+ "\"showSpeedoSoftMaxMarker\":false}");
		assertEquals("72", migrated.get("barSpeedoHeight"));
		assertEquals("30", migrated.get("barSpeedoOpacity"));
		assertEquals("false", migrated.get("showBarSpeedoMaxHorizontalSpeedMarkers"));
		assertEquals("64", migrated.get("dialSpeedoRadius"));
	}

	@Test void retiredSpeedometerVisibilityFollowsTheInstrumentVersion() {
		var dial = ConfigStore.decode("{\"speedoRadius\":64,\"speedoVisibility\":\"2\"}");
		assertEquals("false", dial.get("showDialSpeedo"));
		assertEquals("true", dial.get("showBarSpeedo"));

		var bar = ConfigStore.decode("{\"speedoHeight\":96,\"speedoVisibility\":\"1\"}");
		assertEquals("true", bar.get("showBarSpeedo"));
		assertEquals("true", bar.get("barSpeedoGlidingOnly"));
		assertEquals("true", bar.get("showDialSpeedo"));
	}

	@Test void retiredVisibilityChoicesBecomeTheirTwoSwitches() {
		var values = ConfigStore.decode(
				"{\"ladderVisibility\":\"2\",\"chartVisibility\":\"1\",\"statsVisibility\":\"0\"}");
		assertEquals("false", values.get("showLadder"));
		assertEquals("false", values.get("ladderGlidingOnly"));
		assertEquals("true", values.get("showChart"));
		assertEquals("true", values.get("chartGlidingOnly"));
		assertEquals("true", values.get("showStats"));
		assertEquals("false", values.get("statsGlidingOnly"));
		// Untouched instruments keep their defaults, and the old key does not survive a save.
		assertEquals("true", values.get("showMarkers"));
		assertFalse(ConfigStore.encode(values).contains("ladderVisibility"));
		// A file holding both was written after the split, so the new keys win.
		assertEquals("true", ConfigStore.decode(
				"{\"ladderVisibility\":\"2\",\"showLadder\":\"true\"}").get("showLadder"));
	}

	@Test void retiredStatsOriginBecomesItsAbsolutePosition() {
		var values = ConfigStore.decode("{\"originX\":\"12\",\"originY\":\"34\"}");
		assertEquals("12", values.get("statsX"));
		assertEquals("34", values.get("statsY"));
		assertFalse(ConfigStore.encode(values).contains("originX"));
	}

	@Test void savingReplacesTheFileAndPreservesDisplayUnits() throws Exception {
		var values = ConfigOptions.defaults();
		values.put("chartMinVxz", "-20");
		values.put("chartTrailTicks", "7.5");
		values.put("holdPitchColor", "A0123456");
		Path path = directory.resolve("config.json");
		Files.writeString(path, "previous contents");
		ConfigStore.save(path, values);
		assertEquals(values, ConfigStore.decode(Files.readString(path)));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}

	@Test void invalidSaveLeavesExistingFileIntact() throws Exception {
		Path path = directory.resolve("config.json");
		Files.writeString(path, "preserve me");
		var values = ConfigOptions.defaults();
		values.put("chartMaxVxz", values.get("chartMinVxz"));
		assertThrows(IllegalArgumentException.class, () -> ConfigStore.save(path, values));
		assertEquals("preserve me", Files.readString(path));
	}

	@Test void badNumbersAndColorsCannotReachTheRenderer() {
		for (var bad : Map.of("chartScale", "NaN", "chartTrailTicks", "0.07",
				"lookaheadTicks", "1.5", "ladderOpacity", "101", "statsX", "5000",
				"holdPitchColor", "garbage", "chartFieldGainColor", "009E3692").entrySet()) {
			var values = ConfigOptions.defaults();
			values.put(bad.getKey(), bad.getValue());
			assertEquals("invalid", ConfigOptions.error(values), bad.getKey());
		}
		assertThrows(RuntimeException.class, () -> ConfigStore.decode("{broken"));
		assertThrows(RuntimeException.class, () -> ConfigStore.decode("{\"enabled\":null}"));
	}

	@Test void invertedAndOversizedDomainsAreRejected() {
		var values = ConfigOptions.defaults();
		values.put("chartMinVy", "80");
		assertEquals("range", ConfigOptions.error(values));
		values = ConfigOptions.defaults();
		values.put("chartMaxVxz", "200");
		values.put("chartScale", "128");
		assertEquals("size", ConfigOptions.error(values));
	}

	@Test void applyingConvertsUnitsAndInvalidDraftCannotPartiallyApply() {
		var original = ConfigOptions.snapshot();
		try {
			var values = ConfigOptions.defaults();
			values.put("chartMinVxz", "-20");
			values.put("chartTrailTicks", "7.5");
			values.put("chartFieldGainColor", "123456");
			values.put("showLadder", "false");
			ConfigOptions.apply(values);
			assertEquals(-1, VarioConfig.chartMinVxz);
			assertEquals(150, VarioConfig.chartTrailTicks);
			assertEquals(0xFF123456, VarioConfig.chartFieldGainColor);
			assertTrue(VarioConfig.visible(VarioConfig.showMarkers, VarioConfig.markersGlidingOnly, true));
			assertFalse(VarioConfig.visible(VarioConfig.showLadder, VarioConfig.ladderGlidingOnly, true));
			values.put("enabled", "false");
			values.put("chartScale", "Infinity");
			assertThrows(IllegalArgumentException.class, () -> ConfigOptions.apply(values));
			assertTrue(VarioConfig.enabled);
		} finally { ConfigOptions.apply(original); }
	}
}

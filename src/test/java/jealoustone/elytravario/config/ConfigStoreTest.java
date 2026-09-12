package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.hud.StatsPanel;
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
		var values = ConfigStore.decode("{\"chartMinVxz\":\"-20\",\"futureOption\":true,"
				+ "\"showAngleOfAttack\":true}");
		assertEquals("-20", values.get("chartMinVxz"));
		assertEquals("true", values.get("showHoldPitch"));
		assertEquals("true", values.get("showMaxHorizontalSpeedPitch"));
		assertEquals("true", values.get("showMinimumFallSpeedPitch"));
		assertEquals("true", values.get("showZeroPitch"));
		assertEquals("true", values.get("showFlightPath"));
		assertEquals("144", values.get("barSpeedoX"));
		assertEquals("240", values.get("dialSpeedoX"));
		assertEquals("4", values.get("positionMargin"));
		assertEquals("4", values.get("positionSnapDistance"));
		assertFalse(values.containsKey("speedoAnchor"));
		assertFalse(values.containsKey("futureOption"));
		assertFalse(values.containsKey("showAngleOfAttack"));
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
		assertEquals("true", values.get("showLadderMarkers"));
		assertFalse(ConfigStore.encode(values).contains("ladderVisibility"));
		// A file holding both was written after the split, so the new keys win.
		assertEquals("true", ConfigStore.decode(
				"{\"ladderVisibility\":\"2\",\"showLadder\":\"true\"}").get("showLadder"));
	}

	@Test void retiredMarkerNamesBecomeLadderMarkerNames() {
		var values = ConfigStore.decode(
				"{\"showMarkers\":false,\"markersGlidingOnly\":true}");
		assertEquals("false", values.get("showLadderMarkers"));
		assertEquals("true", values.get("ladderMarkersGlidingOnly"));
		String encoded = ConfigStore.encode(values);
		assertFalse(encoded.contains("\"showMarkers\""));
		assertFalse(encoded.contains("\"markersGlidingOnly\""));

		var visibility = ConfigStore.decode("{\"markerVisibility\":\"1\"}");
		assertEquals("true", visibility.get("showLadderMarkers"));
		assertEquals("true", visibility.get("ladderMarkersGlidingOnly"));
		assertEquals("true", ConfigStore.decode(
				"{\"showMarkers\":false,\"showLadderMarkers\":true}")
				.get("showLadderMarkers"));
	}

	@Test void retiredStatsOriginBecomesTheFourPanelsAbsolutePositions() {
		var values = ConfigStore.decode("{\"originX\":\"12\",\"originY\":\"34\"}");
		// Every panel keeps the old left edge, and they stack from the old top edge down with
		// each one's border sharing a column with the one above it.
		for (var panel : StatsPanel.values()) assertEquals("12", values.get(panel.xKey()));
		assertEquals("34", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("61", values.get(StatsPanel.SPEED.yKey()));
		assertEquals("98", values.get(StatsPanel.ACCEL.yKey()));
		assertEquals("135", values.get(StatsPanel.ENERGY.yKey()));
		assertFalse(ConfigStore.encode(values).contains("originX"));
	}

	@Test void retiredModuleScalesBecomeExactPixelWidths() {
		var values = ConfigStore.decode("{\"chartScale\":37.714286,\"panelScale\":1.25}");
		assertEquals("132", values.get("chartSize"));
		// The panel was 132 layout pixels wide and, with every row on, 138 tall, so a scale of
		// 1.25 drew it 165 by 173 and every new panel is that width and that text size.
		for (var panel : StatsPanel.values()) assertEquals("165", values.get(panel.widthKey()));
		assertEquals("35", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals("48", values.get(StatsPanel.SPEED.heightKey()));
		assertEquals("48", values.get(StatsPanel.ACCEL.heightKey()));
		assertEquals("60", values.get(StatsPanel.ENERGY.heightKey()));
		assertFalse(ConfigStore.encode(values).contains("chartScale"));
		assertFalse(ConfigStore.encode(values).contains("panelScale"));
	}

	@Test void theRetiredSingleStatsSizeBecomesTheSizeItWasDrawnAt() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"panelWidth\":132}");
		// The width it named exactly, and the rows at the text size its scale of 1.5 gave them.
		for (var panel : StatsPanel.values()) assertEquals("198", values.get(panel.widthKey()));
		assertEquals("42", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals("57", values.get(StatsPanel.SPEED.heightKey()));
		assertEquals("57", values.get(StatsPanel.ACCEL.heightKey()));
		assertEquals("72", values.get(StatsPanel.ENERGY.heightKey()));
		// Which is the old panel's 207 pixels back, less the three shared border columns.
		assertEquals("4", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("157", values.get(StatsPanel.ENERGY.yKey()));
		assertFalse(ConfigStore.encode(values).contains("statsSize"));
		assertFalse(ConfigStore.encode(values).contains("panelWidth"));
	}

	@Test void theRetiredStatsSizeIsReadAgainstTheRowsThatWereOn() {
		// Four rows and no separator: 48 layout pixels, at the same scale of 1.5.
		var values = ConfigStore.decode("{\"statsSize\":198,\"showPitch\":\"false\","
				+ "\"showGlideRatio\":\"false\",\"showHorizontalSpeed\":\"false\","
				+ "\"showTotalSpeed\":\"false\",\"showVerticalSpeed\":\"false\","
				+ "\"showHorizontalAcceleration\":\"false\","
				+ "\"showTotalAcceleration\":\"false\","
				+ "\"showVerticalAcceleration\":\"false\"}");
		assertEquals("72", values.get(StatsPanel.ENERGY.heightKey()));
		// The three panels left with no rows are not stacked, since they are not drawn, but
		// they are still sized for the rows they would draw at the same text size.
		assertEquals("4", values.get(StatsPanel.ENERGY.yKey()));
		assertEquals("4", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("42", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals("57", values.get(StatsPanel.SPEED.heightKey()));
	}

	@Test void theRetiredContentWidthSetsTheHeightItGaveTheDefaultPanel() {
		// Only the layout width was ever changed: the panel stayed 132 pixels wide on screen and
		// was drawn at two thirds size to fit 198 pixels of layout into them.
		var values = ConfigStore.decode("{\"panelWidth\":198}");
		for (var panel : StatsPanel.values()) assertEquals("132", values.get(panel.widthKey()));
		assertEquals("19", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals("25", values.get(StatsPanel.SPEED.heightKey()));
		assertEquals("32", values.get(StatsPanel.ENERGY.heightKey()));
	}

	@Test void theNewerRetiredWidthAndHeightWinOverTheOlderSingleSize() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"statsWidth\":90,"
				+ "\"statsHeight\":120}");
		for (var panel : StatsPanel.values()) assertEquals("90", values.get(panel.widthKey()));
		// 120 against the 138 the rows laid out in, so every panel is at that same text size.
		assertEquals("24", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals("33", values.get(StatsPanel.SPEED.heightKey()));
		assertEquals("42", values.get(StatsPanel.ENERGY.heightKey()));
	}

	@Test void theRetiredPanelChromeReachesAllFourPanels() {
		var values = ConfigStore.decode("{\"panelOpacity\":\"30\","
				+ "\"showPanelBorder\":\"false\"}");
		for (var panel : StatsPanel.values()) {
			assertEquals("30", values.get(panel.opacityKey()), panel.name());
			assertEquals("false", values.get(panel.borderKey()), panel.name());
		}
		String encoded = ConfigStore.encode(values);
		assertFalse(encoded.contains("\"panelOpacity\""));
		assertFalse(encoded.contains("\"showPanelBorder\""));
	}

	/** A file naming the panels was written after the split and is not second-guessed. */
	@Test void panelsOfItsOwnAreLeftAlone() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"panelOpacity\":\"30\","
				+ "\"statsOtherY\":\"200\",\"statsSpeedWidth\":\"64\"}");
		assertEquals("200", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("64", values.get(StatsPanel.SPEED.widthKey()));
		assertEquals("132", values.get(StatsPanel.ACCEL.widthKey()));
		assertEquals("28", values.get(StatsPanel.OTHER.heightKey()));
		assertEquals(ConfigOptions.defaults().get(StatsPanel.OTHER.opacityKey()),
				values.get(StatsPanel.OTHER.opacityKey()));
	}

	/** A fresh file has nothing to migrate, so the defaults have to survive the split untouched. */
	@Test void aFileWithNoStatsSettingsGetsTheDefaultStack() {
		var values = ConfigStore.decode("{\"chartMinVxz\":\"-20\"}");
		var defaults = ConfigOptions.defaults();
		for (var panel : StatsPanel.values()) {
			for (String key : java.util.List.of(panel.xKey(), panel.yKey(),
					panel.widthKey(), panel.heightKey())) {
				assertEquals(defaults.get(key), values.get(key), key);
			}
		}
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
		for (var bad : Map.of("chartSize", "1.5", "chartTrailTicks", "0.07",
				"lookaheadTicks", "1.5", "ladderOpacity", "101", "statsOtherX", "5000",
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
		values.put("chartMaxVy", "200");
		values.put("chartSize", "512");
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
			assertTrue(VarioConfig.visible(VarioConfig.showLadderMarkers,
					VarioConfig.ladderMarkersGlidingOnly, true));
			assertFalse(VarioConfig.visible(VarioConfig.showLadder, VarioConfig.ladderGlidingOnly, true));
			values.put("enabled", "false");
			values.put("chartSize", "513");
			assertThrows(IllegalArgumentException.class, () -> ConfigOptions.apply(values));
			assertTrue(VarioConfig.enabled);
		} finally { ConfigOptions.apply(original); }
	}
}

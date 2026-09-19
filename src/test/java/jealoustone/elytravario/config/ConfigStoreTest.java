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
	private static final java.util.List<StatsPanel> LEGACY_PANELS = java.util.List.of(
			StatsPanel.OTHER, StatsPanel.SPEED, StatsPanel.ACCEL, StatsPanel.ENERGY);

	@Test void defaultsAreValidAndRoundTrip() {
		var defaults = ConfigOptions.defaults();
		assertNull(ConfigOptions.error(defaults));
		assertEquals(defaults, ConfigStore.decode(ConfigStore.encode(defaults)));
	}

	@Test void markerAppearanceDefaultsMatchTheTunedSet() {
		var defaults = ConfigOptions.defaults();
		for (String prefix : java.util.List.of("holdPitch", "optimalPitch", "lookaheadPitch")) {
			assertEquals("2", defaults.get(prefix + "Inset"));
			assertEquals("8", defaults.get(prefix + "Length"));
			assertEquals("2", defaults.get(prefix + "Step"));
		}
		assertEquals("66FFFFFF", defaults.get("minimumFallSpeedPitchColor"));
		assertEquals("E0E8EAED", defaults.get("zeroPitchColor"));
		assertEquals("66E8EAED", defaults.get("maxHorizontalSpeedPitchColor"));
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
		assertEquals("false", values.get("hudGlidingOnly"));
		assertFalse(values.containsKey("speedoAnchor"));
		assertFalse(values.containsKey("futureOption"));
		assertFalse(values.containsKey("showAngleOfAttack"));
	}

	@Test void preShapeConfigsAdoptTheNewMarkerAppearanceOnce() {
		var defaults = ConfigOptions.defaults();
		var migrated = ConfigStore.decode("{\"showHoldPitch\":false,"
				+ "\"holdPitchColor\":\"A0123456\",\"flightPathColor\":\"A0654321\"}");
		assertEquals("false", migrated.get("showHoldPitch"));
		assertEquals(defaults.get("holdPitchColor"), migrated.get("holdPitchColor"));
		assertEquals(defaults.get("flightPathColor"), migrated.get("flightPathColor"));

		var current = ConfigStore.decode("{\"lookaheadPitchInset\":2,"
				+ "\"holdPitchColor\":\"A0123456\",\"flightPathColor\":\"A0654321\"}");
		assertEquals("A0123456", current.get("holdPitchColor"));
		assertEquals("A0654321", current.get("flightPathColor"));
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

	@Test void retiredBounceAxisNamesBecomeVerticalAxisNames() {
		var values = ConfigStore.decode(
				"{\"showBounceVelocityX\":false,\"showBounceDistanceX\":false}");
		assertEquals("false", values.get("showBounceVelocityY"));
		assertEquals("false", values.get("showBounceDistanceY"));
		String encoded = ConfigStore.encode(values);
		assertFalse(encoded.contains("\"showBounceVelocityX\""));
		assertFalse(encoded.contains("\"showBounceDistanceX\""));

		// A file holding both names was written by the newer build, so the new name wins.
		values = ConfigStore.decode(
				"{\"showBounceVelocityX\":false,\"showBounceVelocityY\":true,"
				+ "\"showBounceDistanceX\":false,\"showBounceDistanceY\":true}");
		assertEquals("true", values.get("showBounceVelocityY"));
		assertEquals("true", values.get("showBounceDistanceY"));
	}

	@Test void retiredStatsOriginBecomesTheFourPanelsAbsolutePositions() {
		var values = ConfigStore.decode("{\"originX\":\"12\",\"originY\":\"34\"}");
		// Every panel keeps the old left edge, and they stack from the old top edge down with
		// each one's border sharing a column with the one above it.
		for (var panel : LEGACY_PANELS) assertEquals("12", values.get(panel.xKey()));
		assertEquals("34", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("61", values.get(StatsPanel.SPEED.yKey()));
		assertEquals("108", values.get(StatsPanel.ACCEL.yKey()));
		assertEquals("155", values.get(StatsPanel.ENERGY.yKey()));
		assertFalse(ConfigStore.encode(values).contains("originX"));
	}

	/**
	 * What the migration is for: the same readings at the same size, in more boxes. The size is
	 * carried across exactly, fraction and all — what a panel can be <em>drawn</em> at is a
	 * multiple of one over the GUI scale, and the GUI scale is not something a config file
	 * records or fixes, so the quantizing happens where the panel is drawn rather than here.
	 * Each panel's own height then follows from its own rows, which is not the height the
	 * retired panel would have given them: three of the four draw a units heading it never had.
	 */
	@Test void theSplitPanelsAreDrawnAtTheTextSizeTheRetiredPanelWas() {
		for (String size : new String[] { "132", "198", "90", "330" }) {
			// The retired panel laid out 138 tall with every row on, so 276 is exactly twice.
			var values = ConfigStore.decode("{\"statsWidth\":" + size + ",\"statsHeight\":276}");
			for (var panel : LEGACY_PANELS) {
				assertEquals("2", values.get(panel.textSizeKey()), panel.name());
			}
			// And 207 is one and a half times, which is carried as one and a half. At a GUI
			// scale of 2 or 4 that is a size the font is drawn at exactly; at 3 it is not, and
			// the panel is drawn at the nearest third instead.
			var half = ConfigStore.decode("{\"statsWidth\":" + size + ",\"statsHeight\":207}");
			for (var panel : LEGACY_PANELS) {
				assertEquals("1.5", half.get(panel.textSizeKey()), panel.name());
			}
		}
	}

	@Test void retiredModuleScalesBecomeExactPixelWidths() {
		var values = ConfigStore.decode("{\"chartScale\":37.714286,\"panelScale\":1.25}");
		assertEquals("132", values.get("chartSize"));
		// The panel was 132 layout pixels wide and, with every row on, 138 tall, so a scale of
		// 1.25 drew it 165 by 173, and both the width and the size carry over exactly. The
		// size is read back off the width the file stated rather than off a height derived
		// from it, which would have divided that derivation's rounding into the answer.
		for (var panel : LEGACY_PANELS) assertEquals("165", values.get(panel.widthKey()));
		for (var panel : LEGACY_PANELS) assertEquals("1.25", values.get(panel.textSizeKey()));
		assertFalse(ConfigStore.encode(values).contains("chartScale"));
		assertFalse(ConfigStore.encode(values).contains("panelScale"));
	}

	@Test void theRetiredSingleStatsSizeBecomesTheSizeItWasDrawnAt() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"panelWidth\":132}");
		// The width it named exactly, and the scale of 1.5 that width came to.
		for (var panel : LEGACY_PANELS) assertEquals("198", values.get(panel.widthKey()));
		for (var panel : LEGACY_PANELS) assertEquals("1.5", values.get(panel.textSizeKey()));
		// The stack is laid out from the heights those sizes come to: 42, 72 and 72, each
		// butted onto the one above it.
		assertEquals("4", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("45", values.get(StatsPanel.SPEED.yKey()));
		assertEquals("116", values.get(StatsPanel.ACCEL.yKey()));
		assertEquals("187", values.get(StatsPanel.ENERGY.yKey()));
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
		assertEquals("1.5", values.get(StatsPanel.ENERGY.textSizeKey()));
		// The three panels left with no rows are not stacked, since they are not drawn, but
		// retain their origin for when a row is switched back on.
		assertEquals("4", values.get(StatsPanel.ENERGY.yKey()));
		assertEquals("4", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("1.5", values.get(StatsPanel.OTHER.textSizeKey()));
		assertEquals("1.5", values.get(StatsPanel.SPEED.textSizeKey()));
	}

	@Test void theRetiredContentWidthSetsTheSizeItGaveTheDefaultPanel() {
		// Only the layout width was ever changed: the panel stayed 132 pixels wide on screen and
		// was drawn at two thirds size to fit 198 pixels of layout into them. Two thirds is a
		// size the font is drawn at exactly whenever the GUI scale is a multiple of three.
		var values = ConfigStore.decode("{\"panelWidth\":198}");
		for (var panel : LEGACY_PANELS) assertEquals("132", values.get(panel.widthKey()));
		for (var panel : LEGACY_PANELS) {
			assertEquals("0.666667", values.get(panel.textSizeKey()));
		}
	}

	/**
	 * The stack is laid out from the heights the panels round <em>up</em> to, because that is
	 * what they are drawn at: a panel's rows come to a whole number of screen pixels rather
	 * than of GUI ones, and the slack lands in its bottom padding. Rounding to nearest here
	 * stood a panel whose rows fall a fraction short of a pixel one pixel above where it draws
	 * itself, and butted the next one a pixel into it.
	 */
	@Test void theStackIsLaidOutFromTheHeightsThePanelsRoundUpTo() {
		// A third of a size, which is what 132 screen pixels of a 396-pixel layout comes to.
		var values = ConfigStore.decode("{\"panelWidth\":396}");
		for (var panel : LEGACY_PANELS) {
			assertEquals("0.333333", values.get(panel.textSizeKey()), panel.name());
		}
		// Other lays out 28 tall and Speed and Acceleration 48, so a third of them is nine and
		// a third and sixteen: the first rounds up to 10, the two whole ones stay put, and each
		// panel overlaps the one above it by a border.
		assertEquals("4", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("13", values.get(StatsPanel.SPEED.yKey()));
		assertEquals("28", values.get(StatsPanel.ACCEL.yKey()));
		assertEquals("43", values.get(StatsPanel.ENERGY.yKey()));
	}

	@Test void theNewerRetiredWidthAndHeightWinOverTheOlderSingleSize() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"statsWidth\":90,"
				+ "\"statsHeight\":120}");
		for (var panel : LEGACY_PANELS) assertEquals("90", values.get(panel.widthKey()));
		// 120 over the 138 the retired panel laid out in, carried as it stood.
		for (var panel : LEGACY_PANELS) {
			assertEquals("0.869565", values.get(panel.textSizeKey()));
		}
	}

	@Test void theRetiredPanelChromeReachesAllFourPanels() {
		var values = ConfigStore.decode("{\"panelOpacity\":\"30\","
				+ "\"showPanelBorder\":\"false\"}");
		for (var panel : LEGACY_PANELS) {
			assertEquals("30", values.get(panel.opacityKey()), panel.name());
			assertEquals("false", values.get(panel.borderKey()), panel.name());
		}
		String encoded = ConfigStore.encode(values);
		assertFalse(encoded.contains("\"panelOpacity\""));
		assertFalse(encoded.contains("\"showPanelBorder\""));
	}

	/**
	 * A panel that named its own pixel height keeps the size that height was writing at. The two
	 * settings changed places, so such a file says how tall to be and nothing about how large to
	 * write; dividing by the rows it was drawing recovers what it meant.
	 */
	@Test void aPanelsRetiredHeightBecomesTheTextSizeItWasDrawnAt() {
		// Speed lays out 48 tall with its three rows and its heading, so 96 was twice over.
		var values = ConfigStore.decode("{\"statsOtherY\":\"200\",\"statsSpeedHeight\":96,"
				+ "\"statsEnergyHeight\":58}");
		assertEquals("2", values.get(StatsPanel.SPEED.textSizeKey()));
		assertEquals("1", values.get(StatsPanel.ENERGY.textSizeKey()));
		// A panel the file never sized keeps the default rather than being guessed at.
		assertEquals(ConfigOptions.defaults().get(StatsPanel.ACCEL.textSizeKey()),
				values.get(StatsPanel.ACCEL.textSizeKey()));
		assertFalse(ConfigStore.encode(values).contains("statsSpeedHeight"));
	}

	/** A panel with every row switched off is read against the rows it could draw. */
	@Test void aRetiredHeightIsReadAgainstThePossibleRowsWhenNoneAreOn() {
		var values = ConfigStore.decode("{\"statsOtherY\":\"200\",\"statsSpeedHeight\":96,"
				+ "\"showVerticalSpeed\":false,\"showHorizontalSpeed\":false,"
				+ "\"showTotalSpeed\":false}");
		assertEquals("2", values.get(StatsPanel.SPEED.textSizeKey()));
	}

	/** A file naming the panels was written after the split and is not second-guessed. */
	@Test void panelsOfItsOwnAreLeftAlone() {
		var values = ConfigStore.decode("{\"statsSize\":198,\"panelOpacity\":\"30\","
				+ "\"statsOtherY\":\"200\",\"statsSpeedWidth\":\"64\"}");
		assertEquals("200", values.get(StatsPanel.OTHER.yKey()));
		assertEquals("64", values.get(StatsPanel.SPEED.widthKey()));
		assertEquals(ConfigOptions.defaults().get(StatsPanel.ACCEL.widthKey()),
				values.get(StatsPanel.ACCEL.widthKey()));
		assertEquals("1", values.get(StatsPanel.OTHER.textSizeKey()));
		assertEquals(ConfigOptions.defaults().get(StatsPanel.OTHER.opacityKey()),
				values.get(StatsPanel.OTHER.opacityKey()));
	}

	/** A fresh file has nothing to migrate, so the defaults have to survive the split untouched. */
	@Test void aFileWithNoStatsSettingsGetsTheDefaultStack() {
		var values = ConfigStore.decode("{\"chartMinVxz\":\"-20\"}");
		var defaults = ConfigOptions.defaults();
		for (var panel : StatsPanel.values()) {
			for (String key : java.util.List.of(panel.xKey(), panel.yKey(),
					panel.widthKey(), panel.textSizeKey())) {
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

	@Test void markersMustFitInsideTheLadderCenterGap() {
		var values = ConfigOptions.defaults();
		values.put("lookaheadPitchInset", "17");
		assertEquals("markerSize", ConfigOptions.error(values));

		values.put("lookaheadPitchLength", "5");
		assertNull(ConfigOptions.error(values));
	}

	@Test void zeroIsTheOnlyStepThatProducesALine() {
		var values = ConfigOptions.defaults();
		values.put("optimalPitchLength", "4");
		values.put("optimalPitchStep", "4");
		assertEquals("markerStep", ConfigOptions.error(values));

		values.put("optimalPitchStep", "0");
		assertNull(ConfigOptions.error(values));
	}

	@Test void markerStepSliderStopsAtFour() {
		var values = ConfigOptions.defaults();
		values.put("lookaheadPitchStep", "5");
		assertEquals("invalid", ConfigOptions.error(values));
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

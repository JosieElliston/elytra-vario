package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class ConfigOptionsTest {
	@Test void masterHudCanBeLimitedToGliding() {
		Map<String, String> original = ConfigOptions.snapshot();
		try {
			Map<String, String> values = ConfigOptions.snapshot();
			values.put("enabled", "true");
			values.put("hudGlidingOnly", "true");
			ConfigOptions.apply(values);
			assertFalse(VarioConfig.visible(true, false, false));
			assertTrue(VarioConfig.visible(true, false, true));

			values.put("hudGlidingOnly", "false");
			ConfigOptions.apply(values);
			assertTrue(VarioConfig.visible(true, false, false));
		} finally { ConfigOptions.apply(original); }
	}

	@Test void ordinarySettingsExcludeOnlyModuleGeometry() {
		Set<String> geometry = ConfigOptions.all().stream()
				.map(ConfigOptions.Option::key)
				.filter(VarioConfigScreen::geometry)
				.collect(Collectors.toSet());
		assertEquals(Set.of(
				"chartX", "chartY", "chartSize",
				"statsOtherX", "statsOtherY", "statsOtherWidth", "statsOtherTextSize",
				"statsSpeedX", "statsSpeedY", "statsSpeedWidth", "statsSpeedTextSize",
				"statsAccelX", "statsAccelY", "statsAccelWidth", "statsAccelTextSize",
				"statsEnergyX", "statsEnergyY", "statsEnergyWidth", "statsEnergyTextSize",
				"statsBounceVelocityX", "statsBounceVelocityY",
				"statsBounceVelocityWidth", "statsBounceVelocityTextSize",
				"statsBounceDistanceX", "statsBounceDistanceY",
				"statsBounceDistanceWidth", "statsBounceDistanceTextSize",
				"statsBounceTicksX", "statsBounceTicksY",
				"statsBounceTicksWidth", "statsBounceTicksTextSize",
				"barSpeedoX", "barSpeedoY", "barSpeedoHeight",
				"dialSpeedoX", "dialSpeedoY", "dialSpeedoRadius"), geometry);
	}

	@Test void eachSpeedometerHasOneSectionPerReading() {
		assertEquals(List.of("general", "barSpeedoTotal", "barSpeedoHorizontal",
				"barSpeedoVertical", "barSpeedoScale", "barSpeedoOverlays", "barSpeedoPanel"),
				ConfigOptions.groups(5));
		assertEquals(List.of("general", "dialSpeedoTotal", "dialSpeedoHorizontal",
				"dialSpeedoVertical", "dialSpeedoScale", "dialSpeedoOverlays", "dialSpeedoPanel"),
				ConfigOptions.groups(6));

		assertEquals(List.of("showBarSpeedoTotal", "barSpeedoTotalColor"),
				keysIn("barSpeedoTotal"));
		assertEquals(List.of("showBarSpeedoHorizontal", "barSpeedoHorizontalColor"),
				keysIn("barSpeedoHorizontal"));
		assertEquals(List.of("showBarSpeedoVertical", "barSpeedoVerticalColor"),
				keysIn("barSpeedoVertical"));
		assertEquals(List.of("showDialSpeedoTotal", "dialSpeedoTotalColor"),
				keysIn("dialSpeedoTotal"));
		assertEquals(List.of("showDialSpeedoHorizontal", "dialSpeedoHorizontalColor"),
				keysIn("dialSpeedoHorizontal"));
		assertEquals(List.of("showDialSpeedoVertical", "dialSpeedoVerticalColor"),
				keysIn("dialSpeedoVertical"));
	}

	/** The settings of one section, in the order the screen draws them. */
	private static List<String> keysIn(String group) {
		return ConfigOptions.all().stream()
				.filter(option -> option.group().equals(group))
				.map(ConfigOptions.Option::key).toList();
	}

	/** Editing applies as it is typed, so half-typed input must leave the last good values in
	 * force rather than reverting the HUD to defaults or refusing to draw. */
	@Test void applyIfValidKeepsTheLastValidValues() {
		Map<String, String> original = ConfigOptions.snapshot();
		try {
			Map<String, String> values = ConfigOptions.snapshot();
			values.put("chartMinVxz", "-20");
			ConfigOptions.applyIfValid(values);
			assertEquals(-1.0, VarioConfig.chartMinVxz);
			for (String invalid : new String[] {"", "-", "NaN", "80"}) {
				values.put("chartMinVxz", invalid);
				ConfigOptions.applyIfValid(values);
				assertEquals(-1.0, VarioConfig.chartMinVxz);
			}
			values.put("chartMinVxz", "-30");
			ConfigOptions.applyIfValid(values);
			assertEquals(-1.5, VarioConfig.chartMinVxz);
		} finally { ConfigOptions.apply(original); }
	}

	@Test void ladderMarkerSectionsRunFromMeasuredDirectionThroughReferences() {
		assertEquals(List.of("general", "markerShadows", "flightPath", "hold", "optimal",
				"lookahead", "minimumFallSpeed", "zero", "maxHorizontalSpeed"),
				ConfigOptions.groups(2));
	}

	/**
	 * Every page opens on the same four rows, of which the schema names the last two; the
	 * settings screen puts the layout button and the toggle key above them.
	 */
	@Test void everyPageOpensOnItsGeneralSection() {
		for (int page = 0; page < 7; page++) {
			assertEquals("general", ConfigOptions.groups(page).get(0), "page " + page);
		}
		assertEquals(List.of("enabled", "hudGlidingOnly", "showLadder", "ladderGlidingOnly",
				"showLadderMarkers", "ladderMarkersGlidingOnly", "showChart", "chartGlidingOnly",
				"chartX", "chartY", "chartSize", "showStats", "statsGlidingOnly",
				"showBarSpeedo", "barSpeedoGlidingOnly", "barSpeedoX", "barSpeedoY",
				"barSpeedoHeight", "showDialSpeedo", "dialSpeedoGlidingOnly", "dialSpeedoX",
				"dialSpeedoY", "dialSpeedoRadius"),
				ConfigOptions.all().stream()
						.filter(option -> option.group().equals("general"))
						.map(ConfigOptions.Option::key).toList());
	}

	/** A setting with no section would be drawn above every heading, so none may have one. */
	@Test void everySettingNamesASection() {
		assertEquals(List.of(), ConfigOptions.all().stream()
				.filter(option -> option.group() == null)
				.map(ConfigOptions.Option::key).toList());
	}
}

package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class ConfigOptionsTest {
	@Test void eachSpeedometerHasOneSubpagePerReading() {
		assertEquals(List.of("barSpeedoTotal", "barSpeedoHorizontal", "barSpeedoVertical"),
				ConfigOptions.groups(5));
		assertEquals(List.of("dialSpeedoTotal", "dialSpeedoHorizontal", "dialSpeedoVertical"),
				ConfigOptions.groups(6));

		Map<String, String> groups = ConfigOptions.all().stream()
				.filter(option -> option.page() == 5 && option.group() != null)
				.collect(Collectors.toMap(ConfigOptions.Option::key, ConfigOptions.Option::group));
		assertEquals(Map.of(
				"showBarSpeedoTotal", "barSpeedoTotal",
				"barSpeedoTotalColor", "barSpeedoTotal",
				"showBarSpeedoHorizontal", "barSpeedoHorizontal",
				"barSpeedoHorizontalColor", "barSpeedoHorizontal",
				"showBarSpeedoVertical", "barSpeedoVertical",
				"barSpeedoVerticalColor", "barSpeedoVertical"), groups);

		groups = ConfigOptions.all().stream()
				.filter(option -> option.page() == 6 && option.group() != null)
				.collect(Collectors.toMap(ConfigOptions.Option::key, ConfigOptions.Option::group));
		assertEquals(Map.of(
				"showDialSpeedoTotal", "dialSpeedoTotal",
				"dialSpeedoTotalColor", "dialSpeedoTotal",
				"showDialSpeedoHorizontal", "dialSpeedoHorizontal",
				"dialSpeedoHorizontalColor", "dialSpeedoHorizontal",
				"showDialSpeedoVertical", "dialSpeedoVertical",
				"dialSpeedoVerticalColor", "dialSpeedoVertical"), groups);
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
}

package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ConfigOptionsTest {
	@Test void speedometerHasOneSubpagePerNeedle() {
		assertEquals(List.of("speedoTotal", "speedoHorizontal", "speedoVertical"),
				ConfigOptions.groups(5));

		Map<String, String> groups = ConfigOptions.all().stream()
				.filter(option -> option.page() == 5 && option.group() != null)
				.collect(Collectors.toMap(ConfigOptions.Option::key, ConfigOptions.Option::group));
		assertEquals(Map.of(
				"showSpeedoTotal", "speedoTotal",
				"speedoTotalColor", "speedoTotal",
				"showSpeedoHorizontal", "speedoHorizontal",
				"speedoHorizontalColor", "speedoHorizontal",
				"showSpeedoVertical", "speedoVertical",
				"speedoVerticalColor", "speedoVertical"), groups);
	}
}

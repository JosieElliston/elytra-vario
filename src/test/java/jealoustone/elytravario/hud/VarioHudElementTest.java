package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class VarioHudElementTest {
	@Test void theConfiguredChartSizeIsAnExactPixelWidth() {
		int chartSize = VarioConfig.chartSize;
		try {
			VarioConfig.chartSize = 137;
			assertEquals(137, VarioHudElement.chartWidth());
		} finally {
			VarioConfig.chartSize = chartSize;
		}
	}

	@Test void accelerationArrowStaysWholeInsideChart() {
		assertEquals(1.0, VarioHudElement.clippedFraction(5, 5, 2, -3, 0, 0, 10, 10));
	}

	@Test void accelerationArrowClipsAtFirstEdgeItMeets() {
		assertEquals(0.5, VarioHudElement.clippedFraction(5, 5, 10, 4, 0, 0, 10, 10));
		assertEquals(0.25, VarioHudElement.clippedFraction(5, 5, -20, 12, 0, 0, 10, 10));
	}

	@Test void accelerationArrowCanStartOnAnEdgeAndPointBackIntoChart() {
		assertEquals(1.0, VarioHudElement.clippedFraction(0, 5, 4, 0, 0, 0, 10, 10));
		assertEquals(0.0, VarioHudElement.clippedFraction(0, 5, -4, 0, 0, 0, 10, 10));
	}
}

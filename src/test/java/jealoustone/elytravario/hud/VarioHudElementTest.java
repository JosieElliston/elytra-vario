package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class VarioHudElementTest {
	@Test void configuredSizesAreExactPixels() {
		int chartSize = VarioConfig.chartSize;
		int statsWidth = VarioConfig.statsWidth;
		int statsHeight = VarioConfig.statsHeight;
		try {
			VarioConfig.chartSize = 137;
			VarioConfig.statsWidth = 151;
			VarioConfig.statsHeight = 163;
			assertEquals(137, VarioHudElement.chartWidth());
			assertEquals(151, VarioHudElement.statsWidth());
			assertEquals(163, VarioHudElement.statsHeight());
		} finally {
			VarioConfig.chartSize = chartSize;
			VarioConfig.statsWidth = statsWidth;
			VarioConfig.statsHeight = statsHeight;
		}
	}

	@Test void theDefaultHeightIsTheDefaultRowsAtTheirNaturalSize() {
		// The default panel has to come out exactly as it did when one setting sized it, which
		// means its height default is the height its rows lay themselves out in.
		assertEquals(VarioHudElement.panelHeight(), VarioConfig.statsHeight);
		assertEquals(VarioConfig.statsWidth, VarioHudElement.panelWidth());
	}

	@Test void panelWidthAndHeightMoveIndependently() {
		int statsWidth = VarioConfig.statsWidth;
		int statsHeight = VarioConfig.statsHeight;
		try {
			// The default twelve rows are 138 layout pixels tall, so this is the natural size.
			VarioConfig.statsWidth = 132;
			VarioConfig.statsHeight = VarioHudElement.panelHeight();
			assertEquals(132, VarioHudElement.panelWidth());

			// Narrowing spends the gap between the columns and leaves the text where it was.
			VarioConfig.statsWidth = 120;
			assertEquals(120, VarioHudElement.panelWidth());

			// Doubling the height doubles the text size, so a width that has not changed buys
			// half the layout it did before.
			VarioConfig.statsWidth = 264;
			assertEquals(264, VarioHudElement.panelWidth());
			VarioConfig.statsHeight = 2 * VarioHudElement.panelHeight();
			assertEquals(132, VarioHudElement.panelWidth());
		} finally {
			VarioConfig.statsWidth = statsWidth;
			VarioConfig.statsHeight = statsHeight;
		}
	}

	@Test void aPanelIsNeverDrawnNarrowerThanItsRowsNeed() {
		int statsWidth = VarioConfig.statsWidth;
		int statsHeight = VarioConfig.statsHeight;
		try {
			VarioConfig.statsHeight = VarioHudElement.panelHeight();
			VarioConfig.statsWidth = 32;
			assertEquals(VarioHudElement.MIN_PANEL_WIDTH, VarioHudElement.minStatsWidth());
			assertEquals(VarioHudElement.MIN_PANEL_WIDTH, VarioHudElement.statsWidth());

			// The minimum is a layout width, so it grows with the text size the height sets.
			VarioConfig.statsHeight = 3 * VarioHudElement.panelHeight();
			assertEquals(3 * VarioHudElement.MIN_PANEL_WIDTH, VarioHudElement.statsWidth());
		} finally {
			VarioConfig.statsWidth = statsWidth;
			VarioConfig.statsHeight = statsHeight;
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

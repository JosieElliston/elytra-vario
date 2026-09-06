package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SpeedometerChartTest {
	private static final SpeedometerChart CHART = new SpeedometerChart(64, 4.0);

	@Test void zeroIsAtTheBottomFullScaleAtTheTopAndHalfScaleIsCentered() {
		assertEquals(CHART.baselineY(), CHART.speedY(0.0));
		assertEquals(CHART.plotY() + 32, CHART.speedY(2.0));
		assertEquals(CHART.plotY(), CHART.speedY(4.0));
	}

	@Test void theScaleIsLinearAndClamped() {
		for (int i = 0; i <= 8; i++) {
			double speed = i * 0.5;
			assertEquals(i / 8.0, CHART.fraction(speed), 1.0e-12);
		}
		assertEquals(0.0, CHART.fraction(-1.0));
		assertEquals(1.0, CHART.fraction(9.0));
	}

	@Test void speedsOffEitherEndSayTheyAreClamped() {
		assertFalse(CHART.pegged(4.0));
		assertTrue(CHART.pegged(4.0001));
		assertTrue(CHART.pegged(-1.0));
		assertTrue(CHART.pegged(Double.NaN));
	}

	@Test void aChartWithNoScaleReadsZeroRatherThanDividing() {
		SpeedometerChart degenerate = new SpeedometerChart(64, 0.0);
		assertEquals(0.0, degenerate.fraction(2.0));
		assertEquals(degenerate.baselineY(), degenerate.speedY(2.0));
	}

	@Test void threeBarsFitExactlyInsideThePlot() {
		assertEquals(CHART.plotX(), CHART.barX(0));
		assertEquals(CHART.barX(0) + SpeedometerChart.BAR_WIDTH + SpeedometerChart.BAR_GAP,
				CHART.barX(1));
		assertEquals(CHART.plotX() + CHART.plotWidth(),
				CHART.barX(2) + SpeedometerChart.BAR_WIDTH);
		assertEquals(CHART.baselineY() + SpeedometerChart.CATEGORY_HEIGHT + SpeedometerChart.PAD,
				CHART.height());
	}
}

package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BarSpeedometerChartTest {
	private static final int TEXT_HEIGHT = 9;
	private static final int LABEL_WIDTH = 12;

	/** A full panel: three bars, a scale ruled every 1.0, and labels wide enough for "80". */
	private static final BarSpeedometerChart CHART =
			new BarSpeedometerChart(64, 4.0, 1.0, 3, LABEL_WIDTH, TEXT_HEIGHT);

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
		BarSpeedometerChart degenerate = new BarSpeedometerChart(64, 0.0, 1.0, 3, 0, TEXT_HEIGHT);
		assertEquals(0.0, degenerate.fraction(2.0));
		assertEquals(degenerate.baselineY(), degenerate.speedY(2.0));
		assertArrayEquals(new double[] { 0.0 }, degenerate.steps());
	}

	@Test void theScaleIsRuledFromZeroUpToTheLastStepWithinFullScale() {
		assertArrayEquals(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 }, CHART.steps(), 1.0e-12);
		assertArrayEquals(new double[] { 0.0, 1.5, 3.0 },
				new BarSpeedometerChart(64, 4.0, 1.5, 3, 0, TEXT_HEIGHT).steps(), 1.0e-12);
		assertArrayEquals(new double[] { 0.0 },
				new BarSpeedometerChart(64, 4.0, 0.0, 3, 0, TEXT_HEIGHT).steps());
	}

	@Test void theBarsSitInsideThePlotWithRoomForTheirMarkersOnEitherSide() {
		assertEquals(CHART.plotX() + BarSpeedometerChart.MARKER_BLEED, CHART.barX(0));
		assertEquals(CHART.barX(0) + BarSpeedometerChart.BAR_WIDTH + BarSpeedometerChart.BAR_GAP,
				CHART.barX(1));
		assertEquals(CHART.plotX() + CHART.plotWidth(),
				CHART.barX(2) + BarSpeedometerChart.BAR_WIDTH + BarSpeedometerChart.MARKER_BLEED);
	}

	@Test void thePanelIsThePlotPlusItsMarginsLabelsAndCategoryRow() {
		assertEquals(BarSpeedometerChart.PAD + LABEL_WIDTH + BarSpeedometerChart.LABEL_GAP
				+ BarSpeedometerChart.TICK_LENGTH, CHART.plotX());
		assertEquals(CHART.plotX() + CHART.plotWidth() + BarSpeedometerChart.PAD, CHART.width());
		assertEquals(CHART.baselineY() + BarSpeedometerChart.CATEGORY_GAP + TEXT_HEIGHT
				+ BarSpeedometerChart.PAD, CHART.height());
		assertEquals(CHART.baselineY() + BarSpeedometerChart.CATEGORY_GAP, CHART.categoryY());
	}

	@Test void theTopLabelClearsThePanelEdgeByHalfALine() {
		assertEquals(BarSpeedometerChart.PAD + TEXT_HEIGHT / 2, CHART.plotY());
		assertTrue(CHART.speedY(CHART.maxSpeed()) - TEXT_HEIGHT / 2 >= 0);
	}

	@Test void droppingTheScaleLabelsTakesTheirColumnAndClearanceWithThem() {
		BarSpeedometerChart unlabeled = new BarSpeedometerChart(64, 4.0, 1.0, 3, 0, TEXT_HEIGHT);
		assertTrue(CHART.scaleLabeled());
		assertFalse(unlabeled.scaleLabeled());
		assertEquals(BarSpeedometerChart.PAD + BarSpeedometerChart.TICK_LENGTH, unlabeled.plotX());
		assertEquals(BarSpeedometerChart.PAD, unlabeled.plotY());
		assertEquals(CHART.width() - LABEL_WIDTH - BarSpeedometerChart.LABEL_GAP,
				unlabeled.width());
		assertEquals(CHART.height() - TEXT_HEIGHT / 2, unlabeled.height());
		assertEquals(CHART.plotWidth(), unlabeled.plotWidth());
	}

	@Test void hiddenBarsCloseTheirGapRatherThanLeavingAHole() {
		BarSpeedometerChart two = new BarSpeedometerChart(64, 4.0, 1.0, 2, LABEL_WIDTH, TEXT_HEIGHT);
		assertEquals(CHART.barX(0), two.barX(0));
		assertEquals(CHART.barX(1), two.barX(1));
		assertEquals(CHART.width() - BarSpeedometerChart.BAR_WIDTH - BarSpeedometerChart.BAR_GAP,
				two.width());
		assertEquals(CHART.height(), two.height());
	}

	@Test void aPanelWithNoBarsKeepsOnlyItsScale() {
		BarSpeedometerChart none = new BarSpeedometerChart(64, 4.0, 1.0, 0, LABEL_WIDTH, TEXT_HEIGHT);
		assertEquals(0, none.plotWidth());
		assertEquals(none.plotX() + BarSpeedometerChart.PAD, none.width());
		assertEquals(none.baselineY() + BarSpeedometerChart.PAD, none.height());
	}

	@Test void aScaleLabelReadsInBlocksPerSecond() {
		assertEquals("0", BarSpeedometerChart.scaleLabel(0.0));
		assertEquals("80", BarSpeedometerChart.scaleLabel(4.0));
	}
}

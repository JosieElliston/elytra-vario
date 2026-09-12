package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DialSpeedometerTest {
	private static final DialSpeedometer DIAL = new DialSpeedometer(48, 4.0);

	private static int[] mark(double speed, int distance) {
		double angle = DIAL.angle(speed);
		return new int[] { (int) Math.round(Math.cos(angle) * distance),
				(int) Math.round(Math.sin(angle) * distance) };
	}

	@Test void zeroIsLeftFullScaleIsRightAndHalfScaleIsUp() {
		assertArrayEquals(new int[] { -10, 0 }, mark(0.0, 10));
		assertArrayEquals(new int[] { 0, -10 }, mark(2.0, 10));
		assertArrayEquals(new int[] { 10, 0 }, mark(4.0, 10));
	}

	@Test void theScaleIsLinearAndClamped() {
		for (int i = 0; i <= 8; i++) assertEquals(i / 8.0, DIAL.fraction(i * 0.5), 1.0e-12);
		assertEquals(DIAL.angle(4.0), DIAL.angle(9.0));
		assertEquals(DIAL.angle(0.0), DIAL.angle(-1.0));
		assertFalse(DIAL.pegged(4.0));
		assertTrue(DIAL.pegged(4.0001));
		assertTrue(DIAL.pegged(-1.0));
		assertTrue(DIAL.pegged(Double.NaN));
	}

	@Test void aDialWithNoScaleReadsZero() {
		DialSpeedometer degenerate = new DialSpeedometer(48, 0.0);
		assertEquals(0.0, degenerate.fraction(2.0));
		assertEquals(Math.PI, degenerate.angle(2.0));
	}

	@Test void eachNeedlesMarkerLaneIsCenteredOnItsTipAndKeepsClearOfTheNext() {
		int[] tips = { DIAL.needleTip(DialSpeedometer.TOTAL_LENGTH),
				DIAL.needleTip(DialSpeedometer.HORIZONTAL_LENGTH),
				DIAL.needleTip(DialSpeedometer.VERTICAL_LENGTH) };
		assertArrayEquals(new int[] { 43, 35, 26 }, tips);

		double[] lengths = { DialSpeedometer.TOTAL_LENGTH, DialSpeedometer.HORIZONTAL_LENGTH,
				DialSpeedometer.VERTICAL_LENGTH };
		for (int i = 0; i < lengths.length; i++) {
			int from = DIAL.markerFrom(lengths[i]);
			int to = DIAL.markerTo(lengths[i]);
			assertEquals(tips[i] - 3, from);
			assertEquals(tips[i] + 3, to);
			assertTrue(from > 0, "a marker stays clear of the hub");
			assertTrue(to <= DIAL.radius(), "a marker stays inside the scale arc");
			if (i > 0) assertTrue(DIAL.markerTo(lengths[i]) < DIAL.markerFrom(lengths[i - 1]),
					"neighboring lanes keep daylight between them");
		}
	}

	/** Below the settable minimum radius the lane rounds away; it is floored, not dropped. */
	@Test void aDialTooSmallToScaleTheLaneStillMarksAPixelEitherWay() {
		DialSpeedometer tiny = new DialSpeedometer(8, 4.0);
		assertEquals(tiny.needleTip(DialSpeedometer.TOTAL_LENGTH) - 1,
				tiny.markerFrom(DialSpeedometer.TOTAL_LENGTH));
		assertEquals(tiny.needleTip(DialSpeedometer.TOTAL_LENGTH) + 1,
				tiny.markerTo(DialSpeedometer.TOTAL_LENGTH));
	}

	@Test void theBoxExactlyBoundsTheHalfDisc() {
		assertEquals(0, DIAL.hubX() - DIAL.rim());
		assertEquals(DIAL.width() - 1, DIAL.hubX() + DIAL.rim());
		assertEquals(0, DIAL.hubY() - DIAL.rim());
		assertEquals(DIAL.height() - 1, DIAL.hubY());
		assertEquals(1, DIAL.width() % 2);
	}
}

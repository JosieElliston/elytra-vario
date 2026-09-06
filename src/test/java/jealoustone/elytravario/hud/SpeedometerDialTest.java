package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SpeedometerDialTest {
	/** A radius-48 dial reading to 4 blocks/tick, which is the default eighty blocks/second. */
	private static final SpeedometerDial DIAL = new SpeedometerDial(48, 4.0);

	/** Where a mark at this speed lands relative to the hub, in GUI pixels. */
	private static int[] mark(double speed, int distance) {
		double angle = DIAL.angle(speed);
		return new int[] { (int) Math.round(Math.cos(angle) * distance),
				(int) Math.round(Math.sin(angle) * distance) };
	}

	@Test void zeroIsTheLeftEndFullScaleTheRightAndHalfIsStraightUp() {
		// y grows downwards, so straight up is negative. These four are the whole convention.
		assertArrayEquals(new int[] { -10, 0 }, mark(0.0, 10));
		assertArrayEquals(new int[] { 0, -10 }, mark(2.0, 10));
		assertArrayEquals(new int[] { 10, 0 }, mark(4.0, 10));
		assertArrayEquals(new int[] { -7, -7 }, mark(1.0, 10));
	}

	@Test void theScaleIsLinearBetweenTheEnds() {
		for (int i = 0; i <= 8; i++) {
			double speed = i * 0.5;
			assertEquals(i / 8.0, DIAL.fraction(speed), 1.0e-12);
			assertEquals(Math.PI * (1.0 + i / 8.0), DIAL.angle(speed), 1.0e-12);
		}
	}

	@Test void speedsOffEitherEndRestAtTheNearerStopAndSayTheyAreThere() {
		assertFalse(DIAL.pegged(4.0));
		assertTrue(DIAL.pegged(4.0001));
		assertEquals(DIAL.angle(4.0), DIAL.angle(9.0));
		assertEquals(DIAL.angle(0.0), DIAL.angle(-1.0));
		assertTrue(DIAL.pegged(-1.0));

		// Unreachable from a speed, which is a square root of a sum of squares, but a needle
		// resting on zero must not be mistaken for a reading of zero.
		assertEquals(DIAL.angle(0.0), DIAL.angle(Double.NaN));
		assertTrue(DIAL.pegged(Double.NaN));
	}

	@Test void aDialWithNoScaleReadsZeroRatherThanDividing() {
		SpeedometerDial degenerate = new SpeedometerDial(48, 0.0);
		assertEquals(0.0, degenerate.fraction(2.0));
		assertEquals(Math.PI, degenerate.angle(2.0));
	}

	@Test void theBoxIsExactlyTheHalfDiscsBoundsWithNothingToSpare() {
		assertEquals(48 + SpeedometerDial.PAD, DIAL.rim());

		// Every pixel of the shape is inside the box, and the box has no row or column the
		// shape does not reach: the extremes of the half disc are its own edges.
		assertEquals(0, DIAL.hubX() - DIAL.rim());
		assertEquals(DIAL.width() - 1, DIAL.hubX() + DIAL.rim());
		assertEquals(0, DIAL.hubY() - DIAL.rim());
		assertEquals(DIAL.height() - 1, DIAL.hubY());

		// The width is odd because the hub's own column sits between two equal halves. The
		// height has no such symmetry to keep — it is one rim plus the hub's row.
		assertEquals(1, DIAL.width() % 2);
		assertEquals(DIAL.rim() + 1, DIAL.height());
	}
}

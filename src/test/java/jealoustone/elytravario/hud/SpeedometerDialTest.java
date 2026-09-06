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

	@Test void theBoxIsTwiceTheRadiusWideAndTheHubSitsOnItsFlatSide() {
		assertEquals(2 * (48 + SpeedometerDial.PAD), DIAL.width());
		assertEquals(48 + SpeedometerDial.PAD + SpeedometerDial.FOOT, DIAL.height());
		assertEquals(DIAL.width() / 2, DIAL.hubX());

		// The arc reaches the padding at the top and both sides, and the foot is what is left
		// below the flat side.
		assertEquals(SpeedometerDial.PAD, DIAL.hubY() - 48);
		assertEquals(SpeedometerDial.PAD, DIAL.hubX() - 48);
		assertEquals(SpeedometerDial.FOOT, DIAL.height() - DIAL.hubY());
	}
}

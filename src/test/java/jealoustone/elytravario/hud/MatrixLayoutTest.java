package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MatrixLayoutTest {
	@Test void columnsAreReservedFromTheRightWithoutDependingOnValues() {
		MatrixLayout layout = MatrixLayout.rightAligned(100, 4, 20, 30, 40);
		assertEquals(22, layout.right(0));
		assertEquals(56, layout.right(1));
		assertEquals(100, layout.right(2));
		assertEquals(3, layout.columns());
		assertEquals(49, layout.left(1, 7));
	}
}

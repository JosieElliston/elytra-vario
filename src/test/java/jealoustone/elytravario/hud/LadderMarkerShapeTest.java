package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LadderMarkerShapeTest {
	@Test void oneToOneHasAUniquePointAndNinetyDegreeStaircase() {
		var shape = new LadderMarkerShape(2, 4, 1);
		assertEquals(7, shape.height());
		assertEquals(1, shape.widthAt(-3));
		assertEquals(2, shape.widthAt(-2));
		assertEquals(3, shape.widthAt(-1));
		assertEquals(4, shape.widthAt(0));
		assertEquals(3, shape.widthAt(1));
		assertEquals(2, shape.widthAt(2));
		assertEquals(1, shape.widthAt(3));
		assertEquals(0, shape.widthAt(4));
	}

	@Test void largerStepsMakeSharperPoints() {
		var shape = new LadderMarkerShape(5, 8, 2);
		assertEquals(7, shape.height());
		assertEquals(8, shape.widthAt(0));
		assertEquals(6, shape.widthAt(1));
		assertEquals(4, shape.widthAt(2));
		assertEquals(2, shape.widthAt(3));
		assertEquals(0, shape.widthAt(4));
	}

	@Test void incompleteStepsRemainAtTheBase() {
		var shape = new LadderMarkerShape(2, 8, 3);
		assertEquals(5, shape.height());
		assertEquals(8, shape.widthAt(0));
		assertEquals(5, shape.widthAt(1));
		assertEquals(2, shape.widthAt(2));
		assertEquals(0, shape.widthAt(3));
	}

	@Test void zeroStepIsAnIntentionalOneRowLine() {
		var shape = new LadderMarkerShape(2, 8, 0);
		assertEquals(1, shape.height());
		assertEquals(8, shape.widthAt(0));
		assertEquals(0, shape.widthAt(-1));
		assertEquals(0, shape.widthAt(1));
	}

	@Test void invalidDimensionsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new LadderMarkerShape(-1, 1, 0));
		assertThrows(IllegalArgumentException.class, () -> new LadderMarkerShape(0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new LadderMarkerShape(0, 1, -1));
		assertThrows(IllegalArgumentException.class, () -> new LadderMarkerShape(0, 4, 4));
	}
}

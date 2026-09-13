package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayoutWidthsTest {
	/**
	 * The strings the panels were first sized by, measured through the real font at the time and
	 * written into their javadocs. Reproducing all six pins every advance the table names: the
	 * capitals and the narrow {@code I}, the digits, the stop, the colon, the space, the degree
	 * sign, the superscript, and the {@code B} and {@code R} the panel headings added.
	 */
	@Test void theTableReproducesWhatTheFontMeasured() {
		assertEquals(28, LayoutWidths.width("GLIDE"));
		assertEquals(52, LayoutWidths.width("SPEED XYZ"));
		assertEquals(54, LayoutWidths.width("-78.40 b/s"));
		assertEquals(48, LayoutWidths.width("-12.34 : 1"));
		assertEquals(31, LayoutWidths.width("-90.0°"));
		assertEquals(59, LayoutWidths.width("+15.09 b/s²"));
		assertEquals(40, LayoutWidths.width("ABS REL"));
	}

	/**
	 * A glyph the table does not name is measured as a full one, which for a narrow glyph would
	 * be wrong by a pixel or four. Nothing the panels draw may rest on that.
	 */
	@Test void everyGlyphThePanelsDrawIsOneTheTableWasMeasuredFor() {
		for (String text : new String[] { "PITCH", "GLIDE", "SPEED b/s", "ACCEL b/s²",
				"ENERGY b", "ABS", "REL", "KE", "PE", "TE", "GAIN", "VEL b/s",
				"DELTA b", "TICKS", "Y", "XZ", "XYZ", "T", "L", "D", "L-T", "D-L", "--",
				"-90.0°", "-00.00 : 1",
				MatrixLayout.HEIGHT_COLUMN, MatrixLayout.RATE_COLUMN }) {
			assertTrue(LayoutWidths.measured(text), text);
		}
	}

	/** A row is its padding either side, its label, the clearance, and then its figures. */
	@Test void aRowIsChargedForEveryColumnToTheRightOfItsOwnFigure() {
		assertEquals(4 + 12 + 2 + 38 + 4, LayoutWidths.row("TE", "-0000.0"));
		assertEquals(4 + 12 + 2 + 38 + 4 + 38 + 4,
				LayoutWidths.row("TE", "-0000.0", "-000.00"));
	}

	/** A row with no label of its own is charged no clearance for one. */
	@Test void anUnlabelledRowIsChargedNoClearance() {
		assertEquals(4 + 38 + 4 + 38 + 4, LayoutWidths.row("", "-000.00", "-000.00"));
	}

	/**
	 * One column width for every figure on every panel, so that a stack of panels butted
	 * together at one width has one grid of columns rather than several that nearly agree.
	 */
	@Test void everyFigureColumnIsTheSameWidth() {
		assertEquals(38, LayoutWidths.width(MatrixLayout.RATE_COLUMN));
		assertEquals(38, LayoutWidths.width(MatrixLayout.HEIGHT_COLUMN));
	}

	@Test void aPanelIsAsWideAsItsWidestRow() {
		assertEquals(0, LayoutWidths.widest());
		assertEquals(86, LayoutWidths.widest(69, 86, 12));
	}
}

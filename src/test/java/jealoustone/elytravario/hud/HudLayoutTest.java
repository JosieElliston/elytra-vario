package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
	// A 100x50 panel and a 120x80 chart, four pixels apart, on a 640x360 screen.
	private static HudLayout at(int anchor, int attach) {
		return HudLayout.attached(anchor, 4, 6, attach, 100, 50, 120, 80, 4, 640, 360);
	}

	@Test void theSideIsTheOneNamedAndNotOneDerivedFromTheAnchor() {
		assertEquals(new HudPosition(4, 6), at(0, HudPosition.ATTACH_BELOW).panel());
		assertEquals(new HudPosition(4, 60), at(0, HudPosition.ATTACH_BELOW).chart());
		assertEquals(new HudPosition(4, 90), at(0, HudPosition.ATTACH_ABOVE).panel());
		assertEquals(new HudPosition(4, 6), at(0, HudPosition.ATTACH_ABOVE).chart());
		assertEquals(new HudPosition(4, 6), at(0, HudPosition.ATTACH_RIGHT).panel());
		assertEquals(new HudPosition(108, 6), at(0, HudPosition.ATTACH_RIGHT).chart());
		assertEquals(new HudPosition(128, 6), at(0, HudPosition.ATTACH_LEFT).panel());
		assertEquals(new HudPosition(4, 6), at(0, HudPosition.ATTACH_LEFT).chart());
	}

	@Test void aSideFacingAScreenEdgePushesThePanelInRatherThanLeaving() {
		// Below a bottom-anchored panel: the chart takes the corner and the panel rises.
		HudLayout below = at(2, HudPosition.ATTACH_BELOW);
		assertEquals(new HudPosition(4, 220), below.panel());
		assertEquals(new HudPosition(4, 274), below.chart());
		assertEquals(354, below.chart().y() + 80);
		// Right of a right-anchored panel, likewise on the other axis.
		HudLayout right = at(1, HudPosition.ATTACH_RIGHT);
		assertEquals(new HudPosition(412, 6), right.panel());
		assertEquals(new HudPosition(516, 6), right.chart());
		assertEquals(636, right.chart().x() + 120);
	}

	@Test void aSideFacingAwayLeavesThePanelExactlyWhereItWouldBeAlone() {
		HudPosition alone = HudPosition.resolve(1, 4, 6, 100, 50, 640, 360);
		assertEquals(alone, at(1, HudPosition.ATTACH_LEFT).panel());
		assertEquals(alone, at(1, HudPosition.ATTACH_BELOW).panel());
	}

	@Test void theCrossAxisIsFlushWithTheEdgeTheAnchorNames() {
		// Stacked vertically under a right anchor: both right edges land on 640 - 4.
		HudLayout vertical = at(1, HudPosition.ATTACH_BELOW);
		assertEquals(636, vertical.panel().x() + 100);
		assertEquals(636, vertical.chart().x() + 120);
		// Side by side under a bottom anchor: both bottom edges land on 360 - 6.
		HudLayout horizontal = at(2, HudPosition.ATTACH_RIGHT);
		assertEquals(354, horizontal.panel().y() + 50);
		assertEquals(354, horizontal.chart().y() + 80);
	}

	@Test void centerCentersThePairAndBothBoxesWithinIt() {
		HudLayout vertical = at(4, HudPosition.ATTACH_BELOW);
		assertEquals(new HudPosition(274, 119), vertical.panel());
		assertEquals(new HudPosition(264, 173), vertical.chart());
		HudLayout horizontal = at(4, HudPosition.ATTACH_RIGHT);
		assertEquals(new HudPosition(212, 161), horizontal.panel());
		assertEquals(new HudPosition(316, 146), horizontal.chart());
	}

	@Test void aHiddenPanelLeavesTheChartWhereThePairWouldHaveStarted() {
		for (int anchor = 0; anchor <= 4; anchor++) {
			HudPosition alone = HudPosition.resolve(anchor, 4, 6, 120, 80, 640, 360);
			for (int attach = HudPosition.ATTACH_LEFT; attach <= HudPosition.ATTACH_BELOW; attach++) {
				assertEquals(alone, HudLayout.attached(anchor, 4, 6, attach,
						0, 0, 120, 80, 0, 640, 360).chart());
			}
		}
	}
}

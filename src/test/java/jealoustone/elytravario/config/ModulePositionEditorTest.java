package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModulePositionEditorTest {
	@Test
	void preferredModuleWinsOverPaintOrderWhenBoundsOverlap() {
		var stats = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 10, 10, 40, 40);
		var speedometer = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 20, 20, 40, 40);
		var bounds = java.util.List.of(stats, speedometer);

		assertEquals(speedometer, ModulePositionEditor.at(bounds, 25, 25));
		assertEquals(stats, ModulePositionEditor.at(bounds, 25, 25,
				ModulePositionEditor.Module.STATS));
		assertEquals(speedometer, ModulePositionEditor.at(bounds, 55, 25,
				ModulePositionEditor.Module.STATS));
	}

	@Test
	void snapAlignsEdgesCentersAndAdjacentEdgesOnEachAxis() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 100, 70, 40, 30);
		var bounds = java.util.List.of(other);

		assertEquals(new ModulePositionEditor.Position(100, 70),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						103, 68, 20, 10, bounds, 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(110, 80),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						108, 83, 20, 10, bounds, 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(144, 104),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						146, 106, 20, 10, bounds, 320, 240, 4, 4).position());
	}

	@Test
	void snapUsesMarginAtScreenEdgesAndScreenCenter() {
		assertEquals(new ModulePositionEditor.Position(4, 4),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						1, 7, 20, 10, java.util.List.of(), 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(150, 115),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						153, 112, 20, 10, java.util.List.of(), 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(296, 226),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						293, 228, 20, 10, java.util.List.of(), 320, 240, 4, 4).position());
	}

	@Test
	void snapLeavesPositionsOutsideDistanceAlone() {
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				10, 20, 20, 10, java.util.List.of(), 320, 240, 4, 4);
		assertEquals(new ModulePositionEditor.Position(10, 20), snap.position());
		assertEquals(java.util.List.of(), snap.verticalGuides());
		assertEquals(java.util.List.of(), snap.horizontalGuides());
	}

	@Test
	void snapReportsTheTargetEdgesThatCausedIt() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 100, 70, 40, 30);
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				146, 106, 20, 10, java.util.List.of(other), 320, 240, 4, 4);

		assertEquals(java.util.List.of(new ModulePositionEditor.Guide(140, 70, 100)),
				snap.verticalGuides());
		assertEquals(java.util.List.of(new ModulePositionEditor.Guide(100, 100, 140)),
				snap.horizontalGuides());
	}

	@Test
	void snapReportsEveryGuideThatProducesTheWinningPosition() {
		var stats = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 4, 20, 40, 30);
		var speedometer = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 4, 80, 40, 30);
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				6, 150, 20, 10, java.util.List.of(stats, speedometer), 320, 240, 4, 4);

		assertEquals(new ModulePositionEditor.Position(4, 150), snap.position());
		assertEquals(java.util.List.of(
				new ModulePositionEditor.Guide(0, 0, 240),
				new ModulePositionEditor.Guide(4, 20, 50),
				new ModulePositionEditor.Guide(4, 80, 110)), snap.verticalGuides());
	}

	@Test
	void nudgeChangesAbsolutePositionInScreenDirection() {
		Map<String, String> draft = chart(10, 20);
		assertTrue(ModulePositionEditor.nudge(ModulePositionEditor.Module.CHART, 1, 1, draft));
		assertEquals("11", draft.get("chartX"));
		assertEquals("21", draft.get("chartY"));
	}

	@Test
	void nudgeClampsAndLeavesIncompleteExactInputAlone() {
		Map<String, String> draft = chart(4096, 4096);
		assertFalse(ModulePositionEditor.nudge(ModulePositionEditor.Module.CHART, 1, 1, draft));

		draft.put("chartX", "-");
		assertFalse(ModulePositionEditor.nudge(ModulePositionEditor.Module.CHART, 1, 0, draft));
		assertEquals("-", draft.get("chartX"));
	}

	@Test
	void renderedBoundsNormalizeCoordinatesAndStopAtScreenEdges() {
		Map<String, String> draft = chart(4000, -20);
		var rendered = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 220, 0, 100, 50);
		assertTrue(ModulePositionEditor.nudge(ModulePositionEditor.Module.CHART,
				1, -1, draft, rendered, 320, 240));
		assertEquals("220", draft.get("chartX"));
		assertEquals("0", draft.get("chartY"));
	}

	@Test
	void gripTakesTheCornersAndLeavesTheEdgesAndTheMiddleToTheMove() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 10, 10, 60, 40);

		assertEquals(ModulePositionEditor.Corner.TOP_LEFT,
				ModulePositionEditor.grip(bounds, 10, 10));
		assertEquals(ModulePositionEditor.Corner.TOP_RIGHT,
				ModulePositionEditor.grip(bounds, 69, 14));
		assertEquals(ModulePositionEditor.Corner.BOTTOM_LEFT,
				ModulePositionEditor.grip(bounds, 14, 49));
		assertEquals(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				ModulePositionEditor.grip(bounds, 69, 49));
		assertNull(ModulePositionEditor.grip(bounds, 40, 10));
		assertNull(ModulePositionEditor.grip(bounds, 10, 30));
		assertNull(ModulePositionEditor.grip(bounds, 40, 30));
		assertNull(ModulePositionEditor.grip(bounds, 70, 50));
	}

	@Test
	void gripLeavesASmallModuleAMiddleToBePickedUpBy() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 0, 0, 9, 9);

		assertEquals(ModulePositionEditor.Corner.TOP_LEFT,
				ModulePositionEditor.grip(bounds, 2, 2));
		assertNull(ModulePositionEditor.grip(bounds, 3, 3));
	}

	@Test
	void resizeFollowsThePointerDownTheDiagonalWhenBothAxesGrow() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 0, 0, 50, 50);

		assertEquals(70, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 50, new ModulePositionEditor.Growth(1, 1), 80, 60,
				1, 200, true, 320, 240));
	}

	@Test
	void resizeMeasuresPastTheBoxTheSettingDoesNotPayFor() {
		// The dial at radius 64: two radii and two rims across, one radius and one rim down.
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.DIAL_SPEEDOMETER, 10, 10, 139, 70);

		assertEquals(32, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 64, new ModulePositionEditor.Growth(2, 1), 85, 48,
				12, 200, true, 320, 240));
	}

	@Test
	void resizeIgnoresTheAxisTheSettingDoesNotChange() {
		// The bar chart's width is its bars and its labels, so only the pointer's height counts.
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 10, 60, 120);

		assertEquals(126, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 96, new ModulePositionEditor.Growth(0, 1), 300, 160,
				12, 200, true, 320, 240));
	}

	@Test
	void resizeStopsAtTheScreenEdgeAndAtTheSettingsOwnRange() {
		var atEdge = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 300, 200, 20, 20);
		assertEquals(20, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				atEdge, 20, new ModulePositionEditor.Growth(1, 1), 400, 400,
				4, 200, true, 320, 240));

		var inTheClear = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 0, 0, 20, 20);
		assertEquals(30, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				inTheClear, 20, new ModulePositionEditor.Growth(1, 1), 200, 200,
				4, 30, true, 320, 240));
		assertEquals(4, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				inTheClear, 20, new ModulePositionEditor.Growth(1, 1), 0, 0,
				4, 30, true, 320, 240));
	}

	@Test
	void resizeMeasuresFromTheCornerOppositeTheGrip() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 100, 100, 40, 40);

		// The pinned corner is the bottom right, at (140, 140).
		assertEquals(60, ModulePositionEditor.resize(ModulePositionEditor.Corner.TOP_LEFT,
				bounds, 40, new ModulePositionEditor.Growth(1, 1), 80, 80,
				4, 200, true, 320, 240));
	}

	@Test
	void aWholeDragHoldsThePinnedCornerWhileTheSizeFollowsThePointer() {
		// The screen resizes by applying the setting and measuring the module again, so the
		// drag is only as steady as that round trip: here it is the dial's, at whole radii.
		int radius = 40;
		var bounds = new ModulePositionEditor.Bounds(ModulePositionEditor.Module.DIAL_SPEEDOMETER,
				120, 100, 2 * radius + 11, radius + 6);
		int pinnedRight = bounds.x() + bounds.width();
		int pinnedBottom = bounds.y() + bounds.height();
		double pointerX = bounds.x();
		double pointerY = bounds.y();
		for (int step = 0; step < 30; step++) {
			pointerX--;
			pointerY--;
			radius = (int) ModulePositionEditor.resize(ModulePositionEditor.Corner.TOP_LEFT,
					bounds, radius, new ModulePositionEditor.Growth(2, 1), pointerX, pointerY,
					12, 200, true, 320, 240);
			var position = ModulePositionEditor.anchored(ModulePositionEditor.Corner.TOP_LEFT,
					bounds, 2 * radius + 11, radius + 6);
			bounds = new ModulePositionEditor.Bounds(ModulePositionEditor.Module.DIAL_SPEEDOMETER,
					position.x(), position.y(), 2 * radius + 11, radius + 6);
			assertEquals(pinnedRight, bounds.x() + bounds.width());
			assertEquals(pinnedBottom, bounds.y() + bounds.height());
		}
		assertEquals(58, radius);
	}

	@Test
	void anchoredHoldsTheCornerOppositeTheGrip() {
		var before = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 10, 10, 40, 30);

		assertEquals(new ModulePositionEditor.Position(0, -20),
				ModulePositionEditor.anchored(ModulePositionEditor.Corner.TOP_LEFT,
						before, 50, 60));
		assertEquals(new ModulePositionEditor.Position(10, -20),
				ModulePositionEditor.anchored(ModulePositionEditor.Corner.TOP_RIGHT,
						before, 50, 60));
		assertEquals(new ModulePositionEditor.Position(10, 10),
				ModulePositionEditor.anchored(ModulePositionEditor.Corner.BOTTOM_RIGHT,
						before, 50, 60));
	}

	private static Map<String, String> chart(int x, int y) {
		Map<String, String> draft = new HashMap<>();
		draft.put("chartX", Integer.toString(x));
		draft.put("chartY", Integer.toString(y));
		return draft;
	}
}

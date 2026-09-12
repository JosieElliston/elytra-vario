package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModulePositionEditorTest {
	@Test
	void preferredModuleWinsOverPaintOrderWhenBoundsOverlap() {
		var stats = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 10, 10, 40, 40);
		var speedometer = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 20, 20, 40, 40);
		var bounds = List.of(stats, speedometer);

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
		var bounds = List.of(other);

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
						1, 7, 20, 10, List.of(), 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(150, 115),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						153, 112, 20, 10, List.of(), 320, 240, 4, 4).position());
		assertEquals(new ModulePositionEditor.Position(296, 226),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
						293, 228, 20, 10, List.of(), 320, 240, 4, 4).position());
	}

	@Test
	void snapLeavesPositionsOutsideDistanceAlone() {
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				10, 20, 20, 10, List.of(), 320, 240, 4, 4);
		assertEquals(new ModulePositionEditor.Position(10, 20), snap.position());
		assertEquals(List.of(), snap.verticalGuides());
		assertEquals(List.of(), snap.horizontalGuides());
	}

	@Test
	void snapReportsTheTargetEdgesThatCausedIt() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 100, 70, 40, 30);
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				146, 106, 20, 10, List.of(other), 320, 240, 4, 4);

		assertEquals(List.of(new ModulePositionEditor.Guide(140, 70, 100)),
				snap.verticalGuides());
		assertEquals(List.of(new ModulePositionEditor.Guide(100, 100, 140)),
				snap.horizontalGuides());
	}

	@Test
	void snapReportsEveryGuideThatProducesTheWinningPosition() {
		var stats = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 4, 20, 40, 30);
		var speedometer = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 4, 80, 40, 30);
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.CHART,
				6, 150, 20, 10, List.of(stats, speedometer), 320, 240, 4, 4);

		assertEquals(new ModulePositionEditor.Position(4, 150), snap.position());
		assertEquals(List.of(
				new ModulePositionEditor.Guide(0, 0, 240),
				new ModulePositionEditor.Guide(4, 20, 50),
				new ModulePositionEditor.Guide(4, 80, 110)), snap.verticalGuides());
	}

	@Test
	void moveGuidesCanShareLeftAndCenterAnswersButNotTheRightAfterRounding() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 100, 20, 40, 30);
		// With integer centers, widths 40 and 41 can have the same left edge and center while
		// their right edges differ by one. Left and center both answer x=100; right answers x=99.
		var snap = ModulePositionEditor.snap(ModulePositionEditor.Module.DIAL_SPEEDOMETER,
				100, 80, 41, 20, List.of(other), 320, 240, 4, 4);

		assertEquals(new ModulePositionEditor.Position(100, 80), snap.position());
		assertEquals(List.of(
				new ModulePositionEditor.Guide(100, 20, 50),
				new ModulePositionEditor.Guide(120, 20, 50)), snap.verticalGuides());
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
	void theGripUnderThePointerIsDrawnLongerThanItIs() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 10, 10, 60, 40);

		assertEquals(ModulePositionEditor.GRIP, ModulePositionEditor.gripReach(bounds, false));
		assertEquals(ModulePositionEditor.GRIP_ACTIVE,
				ModulePositionEditor.gripReach(bounds, true));
		// Whatever it is drawn at, the pointer is inside the plain reach when it is showing.
		assertNull(ModulePositionEditor.grip(bounds, 10 + ModulePositionEditor.GRIP, 10));
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
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 1, 200, true);

		assertEquals(70, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 50, sizing, 80, 60, List.of(), 320, 240, 4, 0));
	}

	@Test
	void resizeMeasuresPastTheBoxTheSettingDoesNotPayFor() {
		// The dial at radius 64: two radii and two rims across, one radius and one rim down.
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.DIAL_SPEEDOMETER, 10, 10, 139, 70);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(2, 1), 12, 200, true);

		assertEquals(32, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 64, sizing, 85, 48, List.of(), 320, 240, 4, 0));
	}

	@Test
	void resizeIgnoresTheAxisTheSettingDoesNotChange() {
		// The bar chart's width is its bars and its labels, so only the pointer's height counts.
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 10, 60, 120);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(0, 1), 12, 200, true);

		assertEquals(126, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 96, sizing, 300, 160, List.of(), 320, 240, 4, 0));
	}

	@Test
	void resizeStopsAtTheScreenEdgeAndAtTheSettingsOwnRange() {
		var wide = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);
		var narrow = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 30, true);
		var atEdge = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 300, 200, 20, 20);
		assertEquals(20, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				atEdge, 20, wide, 400, 400, List.of(), 320, 240, 4, 0));

		var inTheClear = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 0, 0, 20, 20);
		assertEquals(30, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				inTheClear, 20, narrow, 200, 200, List.of(), 320, 240, 4, 0));
		assertEquals(4, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				inTheClear, 20, narrow, 0, 0, List.of(), 320, 240, 4, 0));
	}

	@Test
	void resizeMeasuresFromTheCornerOppositeTheGrip() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 100, 100, 40, 40);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);

		// The pinned corner is the bottom right, at (140, 140).
		assertEquals(60, ModulePositionEditor.resize(ModulePositionEditor.Corner.TOP_LEFT,
				bounds, 40, sizing, 80, 80, List.of(), 320, 240, 4, 0));
	}

	@Test
	void aWholeDragHoldsThePinnedCornerWhileTheSizeFollowsThePointer() {
		// The screen resizes by applying the setting and measuring the module again, so the
		// drag is only as steady as that round trip: here it is the dial's, at whole radii.
		int radius = 40;
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(2, 1), 12, 200, true);
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
					bounds, radius, sizing, pointerX, pointerY, List.of(), 320, 240, 4, 0);
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

	@Test
	void resizeSnapsAMovingEdgeFlushWithAnotherModule() {
		// To the right, spanning y 20 to 100, and clear of the screen's center line so that
		// only the dragged edge has anything to land on.
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 150, 20, 60, 80);
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 40, 60, 60, 40);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(0, 1), 12, 200, true);

		// Pinned at the top, the bottom edge is dragged to 97 and finds the other module's
		// bottom edge three pixels further down.
		assertEquals(40, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 200, 97, List.of(other), 320, 240, 4, 4));
		// Out of reach of anything, the pointer is followed exactly.
		assertEquals(30, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 200, 90, List.of(other), 320, 240, 4, 4));
	}

	@Test
	void resizeSnapsTheNearerOfTheLinesItMoves() {
		// Right edge at 200, one pixel out from where the drag leaves the dragged edge.
		var right = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 160, 20, 40, 40);
		// Bottom edge at 196, three pixels short of it.
		var below = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 176, 30, 20);
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 100, 100, 40, 40);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);

		// Every line moves off the one setting, so only one of them can land: dragged to
		// (199, 199) the right edge is a pixel from a right edge and the bottom edge three from
		// a bottom edge, and the width that makes the nearer one flush is what the drag takes.
		assertEquals(100, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 199, 199, List.of(right, below), 320, 240, 4, 4));
		// Without the nearer rest to beat it, the same drag lands the bottom edge instead.
		assertEquals(96, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 199, 199, List.of(below), 320, 240, 4, 4));
	}

	@Test
	void resizeChoosesTheCandidateClosestToTheMouseInBothDimensions() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.DIAL_SPEEDOMETER, 100, 100, 91, 46);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(2, 1), 12, 200, true);
		// The right-edge rest four pixels away proposes radius 42. The bottom-edge rest only
		// three pixels away proposes radius 43, but moves the full corner farther from the mouse.
		var right = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 155, 20, 40, 40);
		var below = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 129, 30, 20);

		// Radius 42 moves the corner by (4, 2), versus (6, 3) for radius 43.
		assertEquals(42, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 191, 146, List.of(right, below), 320, 240, 4, 4));
	}

	@Test
	void resizeSnapsTheCenterTheDraggedEdgeTrails() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 150, 100, 40, 60);
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 40, 60, 100);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(0, 1), 12, 200, true);

		// Nothing is near the dragged edge at 199, but the center it trails is a pixel off the
		// screen's own center line, and a height of 160 puts it exactly on it.
		var resize = ModulePositionEditor.resizeWithMarkers(
				ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 100, sizing, 200, 199, List.of(other), 320, 240, 4, 4);
		assertEquals(160, resize.value());
		assertEquals(new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 40, 60, 159),
				resize.trueBounds());
		var landed = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 40, 60, 160);
		assertEquals(List.of(new ModulePositionEditor.Guide(120, 0, 320)),
				ModulePositionEditor.resizeGuides(resize, landed,
						ModulePositionEditor.Corner.BOTTOM_RIGHT)
						.horizontal());
	}

	@Test
	void resizeCenterSnapCannotPullTheCornerTwiceTheSnapDistance() {
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 40, 60, 100);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(0, 1), 12, 200, true);

		// The free height is 152, putting the center four pixels above the screen center.
		// Snapping that center would grow the dragged edge eight pixels, outside the configured
		// four-pixel distance, so the corner continues to follow the pointer.
		assertEquals(152, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 100, sizing, 200, 192, List.of(), 320, 240, 4, 4));
		// One pixel nearer, the center needs only four pixels of edge travel and can snap.
		assertEquals(160, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 100, sizing, 200, 196, List.of(), 320, 240, 4, 4));
	}

	@Test
	void resizePassesOverARestTheSettingCannotReach() {
		// The dial is two radii and two rims across, so it has only odd widths: a rest that
		// asks for an even one cannot be taken however near it is.
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.DIAL_SPEEDOMETER, 10, 60, 91, 46);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(2, 1), 12, 200, true);
		// Offers a left edge to stop a margin short of, three pixels in from the dial's right
		// edge, which would want a width of 88 and so a radius of 38.5.
		var beside = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 102, 30, 40, 40);
		// Offers a top edge to stop a margin short of, three pixels below the dial's bottom.
		var under = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 200, 113, 30, 30);

		assertEquals(43, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 101, 106, List.of(beside, under), 320, 240, 4, 4));
		// On its own the unreachable rest changes nothing, and the pointer is followed.
		assertEquals(40, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 101, 106, List.of(beside), 320, 240, 4, 4));
	}

	@Test
	void aResizedEdgeRestsWhereAMovedBoxWould() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 100, 20, 40, 60);
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 10, 100, 89, 89);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);

		// Grown towards the other module's left edge, the resized edge stops a margin short of
		// it at 96, rather than butting flush against it: a width of 86 from a left edge at 10.
		assertEquals(86, ModulePositionEditor.resize(ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 89, sizing, 99, 189, List.of(other), 320, 240, 4, 4));
		// Which is where a move of the same box comes to rest too: x 6 with a width of 90 is a
		// right edge at 96, the same relationship reached the other way round.
		assertEquals(new ModulePositionEditor.Position(6, 100),
				ModulePositionEditor.snap(ModulePositionEditor.Module.CHART, 10, 100, 90, 90,
						List.of(other), 320, 240, 4, 4).position());
	}

	@Test
	void resizeGuidesDrawOnlyMarkersThatGiveTheWinningAnswerAndActuallyLanded() {
		var other = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 30, 20, 70, 60);
		var before = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 40, 40, 59, 39);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);
		var resize = ModulePositionEditor.resizeWithMarkers(
				ModulePositionEditor.Corner.BOTTOM_RIGHT,
				before, 39, sizing, 99, 79, List.of(other), 320, 240, 4, 4);
		var landed = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 40, 40, 60, 40);

		// Both rests independently produce size 40, so both belong to the winning answer.
		assertEquals(40, resize.value());
		var guides = ModulePositionEditor.resizeGuides(resize, landed,
				ModulePositionEditor.Corner.BOTTOM_RIGHT);
		assertEquals(List.of(new ModulePositionEditor.Guide(100, 20, 80)), guides.vertical());
		assertEquals(List.of(new ModulePositionEditor.Guide(80, 30, 100)), guides.horizontal());

		// Applying a nonintegral setting can round a predicted line away. Even though its marker
		// gave the winning arithmetic answer, it is not drawn unless the rendered box landed.
		var shorter = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 40, 40, 60, 37);
		assertEquals(List.of(), ModulePositionEditor.resizeGuides(resize, shorter,
				ModulePositionEditor.Corner.BOTTOM_RIGHT)
				.horizontal());
	}

	@Test
	void resizeMarkersNeverCombineDifferentAnswers() {
		var right = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.STATS, 160, 20, 40, 40);
		var below = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.BAR_SPEEDOMETER, 10, 176, 30, 20);
		var bounds = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 100, 100, 40, 40);
		var sizing = new ModulePositionEditor.Sizing(
				new ModulePositionEditor.Growth(1, 1), 4, 200, true);

		// The right-edge rest answers 100 and the bottom-edge rest answers 96. Only the nearer
		// right-edge answer wins, so the solver must not return the bottom marker with it.
		var resize = ModulePositionEditor.resizeWithMarkers(
				ModulePositionEditor.Corner.BOTTOM_RIGHT,
				bounds, 40, sizing, 199, 199, List.of(right, below), 320, 240, 4, 4);
		var landed = new ModulePositionEditor.Bounds(
				ModulePositionEditor.Module.CHART, 100, 100, 100, 100);
		var guides = ModulePositionEditor.resizeGuides(resize, landed,
				ModulePositionEditor.Corner.BOTTOM_RIGHT);

		assertEquals(100, resize.value());
		assertEquals(List.of(new ModulePositionEditor.Guide(200, 20, 60)), guides.vertical());
		assertEquals(List.of(), guides.horizontal());
	}

	private static Map<String, String> chart(int x, int y) {
		Map<String, String> draft = new HashMap<>();
		draft.put("chartX", Integer.toString(x));
		draft.put("chartY", Integer.toString(y));
		return draft;
	}
}

package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
				ModulePositionEditor.Module.SPEEDOMETER, 20, 20, 40, 40);
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
				ModulePositionEditor.Module.SPEEDOMETER, 4, 80, 40, 30);
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

	private static Map<String, String> chart(int x, int y) {
		Map<String, String> draft = new HashMap<>();
		draft.put("chartX", Integer.toString(x));
		draft.put("chartY", Integer.toString(y));
		return draft;
	}
}

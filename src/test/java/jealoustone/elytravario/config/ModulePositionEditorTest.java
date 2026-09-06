package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModulePositionEditorTest {
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

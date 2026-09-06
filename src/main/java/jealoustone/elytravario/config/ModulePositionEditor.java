package jealoustone.elytravario.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.HudPosition;
import jealoustone.elytravario.hud.SpeedometerDial;
import jealoustone.elytravario.hud.VarioHudElement;

/** Geometry and coordinate changes for the in-world module position editor. */
final class ModulePositionEditor {
	enum Module {
		CHART(3, "chartX", "chartY"),
		STATS(4, "statsX", "statsY"),
		SPEEDOMETER(5, "speedoX", "speedoY");

		final int page;
		final String xKey;
		final String yKey;

		Module(int page, String xKey, String yKey) {
			this.page = page;
			this.xKey = xKey;
			this.yKey = yKey;
		}
	}

	record Bounds(Module module, int x, int y, int width, int height) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	/** Bounds in paint order; callers search backwards so the topmost overlapping module wins. */
	static List<Bounds> bounds(int screenWidth, int screenHeight, boolean gliding) {
		List<Bounds> result = new ArrayList<>();
		if (!VarioConfig.enabled) return result;

		boolean stats = VarioInstrument.STATS.visible(gliding) && VarioHudElement.statsHeight() > 0;
		boolean chart = VarioInstrument.CHART.visible(gliding);
		int statsWidth = VarioHudElement.statsWidth();
		int statsHeight = VarioHudElement.statsHeight();
		int chartWidth = VarioHudElement.chartWidth();
		int chartHeight = VarioHudElement.chartHeight();
		HudPosition statsPosition = HudPosition.clamp(VarioConfig.statsX, VarioConfig.statsY,
				statsWidth, statsHeight, screenWidth, screenHeight);
		HudPosition chartPosition = HudPosition.clamp(VarioConfig.chartX, VarioConfig.chartY,
				chartWidth, chartHeight, screenWidth, screenHeight);
		if (stats) result.add(new Bounds(Module.STATS, statsPosition.x(), statsPosition.y(),
				statsWidth, statsHeight));
		if (chart) result.add(new Bounds(Module.CHART, chartPosition.x(), chartPosition.y(),
				chartWidth, chartHeight));

		if (VarioInstrument.SPEEDOMETER.visible(gliding)) {
			SpeedometerDial dial = new SpeedometerDial(VarioConfig.speedoRadius,
					VarioConfig.speedoMaxSpeed);
			HudPosition position = HudPosition.clamp(VarioConfig.speedoX, VarioConfig.speedoY,
					dial.width(), dial.height(), screenWidth, screenHeight);
			result.add(new Bounds(Module.SPEEDOMETER, position.x(), position.y(),
					dial.width(), dial.height()));
		}
		return result;
	}

	static Bounds at(List<Bounds> bounds, double x, double y) {
		for (int i = bounds.size() - 1; i >= 0; i--) {
			if (bounds.get(i).contains(x, y)) return bounds.get(i);
		}
		return null;
	}

	/** Moves the module's absolute top-left coordinates in screen space. */
	static boolean nudge(Module module, int dx, int dy, Map<String, String> draft) {
		return nudge(module, dx, dy, draft, null, 0, 0);
	}

	/**
	 * Moves from the actual rendered position, normalizing coordinates clamped by a resize or
	 * exact input and preventing a drag from walking beyond a screen edge.
	 */
	static boolean nudge(Module module, int dx, int dy, Map<String, String> draft,
			Bounds rendered, int screenWidth, int screenHeight) {
		int offsetX;
		int offsetY;
		try {
			offsetX = Integer.parseInt(draft.get(module.xKey));
			offsetY = Integer.parseInt(draft.get(module.yKey));
		} catch (RuntimeException e) {
			// An exact-coordinate box may temporarily contain incomplete input.
			return false;
		}
		int nextX = rendered == null ? Math.clamp(offsetX + dx, -4096, 4096)
				: Math.clamp(rendered.x + dx, 0, Math.max(0, screenWidth - rendered.width));
		int nextY = rendered == null ? Math.clamp(offsetY + dy, -4096, 4096)
				: Math.clamp(rendered.y + dy, 0, Math.max(0, screenHeight - rendered.height));
		if (nextX == offsetX && nextY == offsetY) return false;
		draft.put(module.xKey, Integer.toString(nextX));
		draft.put(module.yKey, Integer.toString(nextY));
		return true;
	}

	private ModulePositionEditor() { }
}

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

	record Position(int x, int y) { }
	record Guide(int coordinate, int from, int to) { }
	record Snap(Position position, List<Guide> verticalGuides, List<Guide> horizontalGuides) { }
	private record Candidate(int position, Guide guide) { }
	private record AxisSnap(int position, List<Guide> guides) { }

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

	/** Gives an actively dragged module first claim when overlapping another module. */
	static Bounds at(List<Bounds> bounds, double x, double y, Module preferred) {
		if (preferred != null) {
			for (Bounds candidate : bounds) {
				if (candidate.module == preferred && candidate.contains(x, y)) return candidate;
			}
		}
		return at(bounds, x, y);
	}

	/**
	 * Snaps a drag independently on each axis. Equal edges and centers align directly;
	 * opposing edges keep {@code margin} pixels between the modules. Screen edges use the
	 * same margin, while the screen center aligns directly.
	 */
	static Snap snap(Module moving, int x, int y, int width, int height,
			List<Bounds> bounds, int screenWidth, int screenHeight, int margin, int distance) {
		int maxX = Math.max(0, screenWidth - width);
		int maxY = Math.max(0, screenHeight - height);
		int rawX = Math.clamp(x, 0, maxX);
		int rawY = Math.clamp(y, 0, maxY);
		List<Candidate> xs = new ArrayList<>();
		List<Candidate> ys = new ArrayList<>();
		addIfVisible(xs, margin, maxX, new Guide(0, 0, screenHeight));
		addIfVisible(xs, maxX - margin, maxX, new Guide(screenWidth, 0, screenHeight));
		addIfVisible(xs, (screenWidth - width) / 2, maxX,
				new Guide(screenWidth / 2, 0, screenHeight));
		addIfVisible(ys, margin, maxY, new Guide(0, 0, screenWidth));
		addIfVisible(ys, maxY - margin, maxY, new Guide(screenHeight, 0, screenWidth));
		addIfVisible(ys, (screenHeight - height) / 2, maxY,
				new Guide(screenHeight / 2, 0, screenWidth));
		for (Bounds other : bounds) {
			if (other.module == moving) continue;
			Guide left = new Guide(other.x, other.y, other.y + other.height);
			Guide centerX = new Guide(other.x + other.width / 2,
					other.y, other.y + other.height);
			Guide right = new Guide(other.x + other.width, other.y, other.y + other.height);
			addIfVisible(xs, other.x, maxX, left);
			addIfVisible(xs, other.x + other.width - width, maxX, right);
			addIfVisible(xs, other.x + (other.width - width) / 2, maxX, centerX);
			addIfVisible(xs, other.x + other.width + margin, maxX, right);
			addIfVisible(xs, other.x - margin - width, maxX, left);
			Guide top = new Guide(other.y, other.x, other.x + other.width);
			Guide centerY = new Guide(other.y + other.height / 2,
					other.x, other.x + other.width);
			Guide bottom = new Guide(other.y + other.height, other.x, other.x + other.width);
			addIfVisible(ys, other.y, maxY, top);
			addIfVisible(ys, other.y + other.height - height, maxY, bottom);
			addIfVisible(ys, other.y + (other.height - height) / 2, maxY, centerY);
			addIfVisible(ys, other.y + other.height + margin, maxY, bottom);
			addIfVisible(ys, other.y - margin - height, maxY, top);
		}
		AxisSnap snappedX = nearest(rawX, xs, distance);
		AxisSnap snappedY = nearest(rawY, ys, distance);
		return new Snap(new Position(snappedX.position, snappedY.position),
				snappedX.guides, snappedY.guides);
	}

	private static void addIfVisible(List<Candidate> candidates, int value, int maximum,
			Guide guide) {
		if (value >= 0 && value <= maximum) candidates.add(new Candidate(value, guide));
	}

	private static AxisSnap nearest(int value, List<Candidate> candidates, int distance) {
		Integer position = null;
		int closest = distance + 1;
		for (Candidate candidate : candidates) {
			int gap = Math.abs(candidate.position - value);
			if (gap < closest) {
				position = candidate.position;
				closest = gap;
			}
		}
		if (position == null) return new AxisSnap(value, List.of());
		List<Guide> guides = new ArrayList<>();
		for (Candidate candidate : candidates) {
			if (candidate.position == position && !guides.contains(candidate.guide)) {
				guides.add(candidate.guide);
			}
		}
		return new AxisSnap(position, List.copyOf(guides));
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

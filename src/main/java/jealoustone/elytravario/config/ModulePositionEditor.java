package jealoustone.elytravario.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.HudPosition;
import jealoustone.elytravario.hud.BarSpeedometerChart;
import jealoustone.elytravario.hud.DialSpeedometer;
import jealoustone.elytravario.hud.VarioHudElement;

import net.minecraft.client.gui.Font;

/** Geometry and coordinate changes for the in-world module position editor. */
final class ModulePositionEditor {
	enum Module {
		CHART(3, "chartX", "chartY", "chartScale"),
		STATS(4, "statsX", "statsY", "panelScale"),
		BAR_SPEEDOMETER(5, "barSpeedoX", "barSpeedoY", "barSpeedoHeight"),
		DIAL_SPEEDOMETER(6, "dialSpeedoX", "dialSpeedoY", "dialSpeedoRadius");

		final int page;
		final String xKey;
		final String yKey;
		/** The one setting the module's size is a function of; see {@link #growth}. */
		final String sizeKey;

		Module(int page, String xKey, String yKey, String sizeKey) {
			this.page = page;
			this.xKey = xKey;
			this.yKey = yKey;
			this.sizeKey = sizeKey;
		}
	}

	/**
	 * A corner grip, named for the corner it holds rather than the one it pins.
	 *
	 * <p>Corners and not edges, because no module has a nonuniform resize to offer: each one's
	 * box is a function of a single size setting, so an edge would have nothing to drag that a
	 * corner does not already drag.
	 */
	enum Corner {
		TOP_LEFT(true, true),
		TOP_RIGHT(false, true),
		BOTTOM_LEFT(true, false),
		BOTTOM_RIGHT(false, false);

		final boolean left;
		final boolean top;

		Corner(boolean left, boolean top) {
			this.left = left;
			this.top = top;
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
	static List<Bounds> bounds(Font font, int screenWidth, int screenHeight, boolean gliding) {
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

		if (VarioInstrument.BAR_SPEEDOMETER.visible(gliding)) {
			BarSpeedometerChart speedometerChart = BarSpeedometerChart.of(font);
			HudPosition position = HudPosition.clamp(VarioConfig.barSpeedoX, VarioConfig.barSpeedoY,
					speedometerChart.width(), speedometerChart.height(), screenWidth, screenHeight);
			result.add(new Bounds(Module.BAR_SPEEDOMETER, position.x(), position.y(),
					speedometerChart.width(), speedometerChart.height()));
		}
		if (VarioInstrument.DIAL_SPEEDOMETER.visible(gliding)) {
			DialSpeedometer dial = new DialSpeedometer(VarioConfig.dialSpeedoRadius,
					VarioConfig.dialSpeedoMaxSpeed);
			HudPosition position = HudPosition.clamp(VarioConfig.dialSpeedoX, VarioConfig.dialSpeedoY,
					dial.width(), dial.height(), screenWidth, screenHeight);
			result.add(new Bounds(Module.DIAL_SPEEDOMETER, position.x(), position.y(),
					dial.width(), dial.height()));
		}
		return result;
	}

	/** How far into a corner a resize grip reaches, before a small module shrinks it. */
	static final int GRIP = 5;

	/**
	 * The reach of this module's grips. Never more than a third of its shorter side, so that a
	 * small module keeps a middle to pick it up by.
	 */
	static int gripReach(Bounds bounds) {
		return Math.clamp(Math.min(bounds.width, bounds.height) / 3, 1, GRIP);
	}

	/**
	 * The corner grip under the pointer, or null where the pointer is asking to move the module
	 * rather than to resize it.
	 */
	static Corner grip(Bounds bounds, double x, double y) {
		if (!bounds.contains(x, y)) return null;
		int reach = gripReach(bounds);
		boolean left = x < bounds.x + reach;
		boolean right = x >= bounds.x + bounds.width - reach;
		boolean top = y < bounds.y + reach;
		boolean bottom = y >= bounds.y + bounds.height - reach;
		if (!left && !right) return null;
		if (!top && !bottom) return null;
		if (top) return left ? Corner.TOP_LEFT : Corner.TOP_RIGHT;
		return left ? Corner.BOTTOM_LEFT : Corner.BOTTOM_RIGHT;
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

	/** How many pixels of width and of height one unit of a module's size setting buys. */
	record Growth(double width, double height) { }

	/**
	 * How the module's box grows with its size setting.
	 *
	 * <p>Every module's box is affine in that one setting, so this slope and the size the module
	 * is currently drawn at describe it exactly: whatever the box carries that the setting does
	 * not pay for — a label column, the dial's rim — falls out as the offset between them. The
	 * resize therefore never has to predict a size it could measure instead.
	 */
	static Growth growth(Module module) {
		return switch (module) {
			// One scale in pixels per block/tick serves both of the chart's axes, so it grows
			// by its domains, and keeps its aspect only where the two domains are equal.
			case CHART -> new Growth(VarioConfig.chartMaxVxz - VarioConfig.chartMinVxz,
					VarioConfig.chartMaxVy - VarioConfig.chartMinVy);
			case STATS -> new Growth(VarioConfig.panelWidth, VarioHudElement.panelHeight());
			// The bar chart is as wide as its bars and its scale labels, and neither is the
			// setting: only the plot's height follows the pointer.
			case BAR_SPEEDOMETER -> new Growth(0, 1);
			// A semicircle is two radii across and one tall.
			case DIAL_SPEEDOMETER -> new Growth(2, 1);
		};
	}

	/**
	 * The size setting that puts the dragged corner as near the pointer as the module's shape
	 * allows, with the opposite corner pinned.
	 *
	 * <p>One setting has to answer a pointer that moves in two dimensions, so the answer is the
	 * least-squares one: the value whose box comes closest to the box the pointer is asking for.
	 * Where both axes grow that is the pointer projected onto the box's diagonal, which is what
	 * dragging a locked-aspect corner looks like anywhere else; for the bar chart, whose width
	 * is not a setting, the same expression collapses to following the pointer vertically.
	 *
	 * <p>The value is capped so that a module cannot be grown off the screen past its pinned
	 * corner, and clamped to the setting's own range. Values are in the setting's own units, not
	 * the screen's displayed ones.
	 */
	static double resize(Corner corner, Bounds rendered, double value, Growth growth,
			double pointerX, double pointerY, double min, double max, boolean integral,
			int screenWidth, int screenHeight) {
		double width = growth.width();
		double height = growth.height();
		if (width <= 0 && height <= 0) return value;
		int anchorX = corner.left ? rendered.x + rendered.width : rendered.x;
		int anchorY = corner.top ? rendered.y + rendered.height : rendered.y;
		double widthOffset = rendered.width - width * value;
		double heightOffset = rendered.height - height * value;
		double wantedWidth = Math.abs(Math.clamp(pointerX, 0, screenWidth) - anchorX);
		double wantedHeight = Math.abs(Math.clamp(pointerY, 0, screenHeight) - anchorY);
		double solved = (width * (wantedWidth - widthOffset)
				+ height * (wantedHeight - heightOffset)) / (width * width + height * height);
		double limit = Math.min(max, Math.min(
				fits(corner.left ? anchorX : screenWidth - anchorX, width, widthOffset),
				fits(corner.top ? anchorY : screenHeight - anchorY, height, heightOffset)));
		if (integral) limit = Math.floor(limit);
		// Where even the smallest size does not fit, the setting's own range wins over the
		// screen: the drag can still reach the minimum, which is the best the module can do.
		limit = Math.max(min, limit);
		double capped = Math.clamp(solved, min, limit);
		return integral ? Math.clamp(Math.rint(capped), min, limit) : capped;
	}

	/** The largest setting whose axis still fits in {@code available} pixels. */
	private static double fits(double available, double slope, double offset) {
		return slope > 0 ? (available - offset) / slope : Double.MAX_VALUE;
	}

	/** The top-left a resized module takes when the corner opposite the grip is pinned. */
	static Position anchored(Corner corner, Bounds before, int width, int height) {
		return new Position(corner.left ? before.x + before.width - width : before.x,
				corner.top ? before.y + before.height - height : before.y);
	}

	private ModulePositionEditor() { }
}

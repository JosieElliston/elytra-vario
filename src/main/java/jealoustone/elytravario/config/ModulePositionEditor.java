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
	/** The guides a drag has come to rest on: vertical ones are columns, horizontal ones rows. */
	record Guides(List<Guide> vertical, List<Guide> horizontal) { }
	/** The snapped answer, the unsnapped box requested by the pointer, and its winning markers. */
	record Resize(double value, Bounds trueBounds, List<Marker> markers) { }
	private record Candidate(int position, Guide guide) { }
	private record AxisSnap(int position, List<Guide> guides) { }
	private record Marker(boolean vertical, boolean center, Guide guide) { }
	private record ResizeCandidate(double value, double error, Marker marker) { }

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
	 * The reach of the grip the pointer is on. It is drawn larger than it is, which is the
	 * point: the pointer is inside the plain reach whenever the larger mark is showing, so the
	 * mark says what the next click will do without ever claiming ground the click does not.
	 */
	static final int GRIP_ACTIVE = 9;

	/**
	 * The reach of this module's grips. Never more than a third of its shorter side, so that a
	 * small module keeps a middle to pick it up by.
	 */
	static int gripReach(Bounds bounds, boolean active) {
		return Math.clamp(Math.min(bounds.width, bounds.height) / 3, 1,
				active ? GRIP_ACTIVE : GRIP);
	}

	/**
	 * The corner grip under the pointer, or null where the pointer is asking to move the module
	 * rather than to resize it.
	 */
	static Corner grip(Bounds bounds, double x, double y) {
		if (!bounds.contains(x, y)) return null;
		int reach = gripReach(bounds, false);
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
		return new AxisSnap(position, resting(candidates, position));
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

	/** A module's one size setting: how its box grows with it, and the range it may take. */
	record Sizing(Growth growth, double min, double max, boolean integral) { }

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
	 * dragging a locked-aspect corner looks like anywhere else; for a module whose width is not
	 * a setting, the same expression collapses to following the pointer vertically.
	 *
	 * <p>The value is capped so that a module cannot be grown off the screen past its pinned
	 * corner, and clamped to the setting's own range. Values are in the setting's own units, not
	 * the screen's displayed ones.
	 *
	 * <p>A line of the box that comes within {@code distance} of somewhere it could rest is put
	 * there exactly instead. Four lines are looking: the two edges the corner moves, and the two
	 * center lines, which move at half their rate and are worth snapping in their own right —
	 * a module centered on its neighbor reads as deliberate however its edges fall. They rest
	 * where {@link #snap} would let a move rest and nowhere else; see {@link #rests}. Only one
	 * answer can win, because one setting places all four of them: every feasible answer is
	 * scored by how near its resulting corner is to the pointer, and the nearest takes it. A rest
	 * the setting cannot actually reach, because it is out of range or because the module lays
	 * itself out in whole pixels, is passed over for one it can.
	 * Distance is measured at the dragged edge rather than at the line: a center moves half as
	 * fast, and measuring its own gap would let it pull the corner twice the configured distance.
	 */
	static double resize(Corner corner, Bounds rendered, double value, Sizing sizing,
			double pointerX, double pointerY, List<Bounds> bounds,
			int screenWidth, int screenHeight, int margin, int distance) {
		return resizeWithMarkers(corner, rendered, value, sizing, pointerX, pointerY, bounds,
				screenWidth, screenHeight, margin, distance).value;
	}

	/** The resize answer together with only the markers which give that same answer. */
	static Resize resizeWithMarkers(Corner corner, Bounds rendered, double value, Sizing sizing,
			double pointerX, double pointerY, List<Bounds> bounds,
			int screenWidth, int screenHeight, int margin, int distance) {
		double width = sizing.growth().width();
		double height = sizing.growth().height();
		if (width <= 0 && height <= 0) return new Resize(value, rendered, List.of());
		int anchorX = corner.left ? rendered.x + rendered.width : rendered.x;
		int anchorY = corner.top ? rendered.y + rendered.height : rendered.y;
		double widthOffset = rendered.width - width * value;
		double heightOffset = rendered.height - height * value;
		double wantedWidth = Math.abs(Math.clamp(pointerX, 0, screenWidth) - anchorX);
		double wantedHeight = Math.abs(Math.clamp(pointerY, 0, screenHeight) - anchorY);
		double solved = (width * (wantedWidth - widthOffset)
				+ height * (wantedHeight - heightOffset)) / (width * width + height * height);
		// Where even the smallest size does not fit, the setting's own range wins over the
		// screen: the drag can still reach the minimum, which is the best the module can do.
		double limit = Math.max(sizing.min(), Math.min(sizing.max(), Math.min(
				fits(corner.left ? anchorX : screenWidth - anchorX, width, widthOffset),
				fits(corner.top ? anchorY : screenHeight - anchorY, height, heightOffset))));
		if (sizing.integral()) limit = Math.max(sizing.min(), Math.floor(limit));
		double free = settled(solved, sizing, limit);
		int freeWidth = (int) Math.round(width * free + widthOffset);
		int freeHeight = (int) Math.round(height * free + heightOffset);
		Position freePosition = anchored(corner, rendered, freeWidth, freeHeight);
		Bounds trueBounds = new Bounds(rendered.module,
				Math.clamp(freePosition.x, 0, Math.max(0, screenWidth - freeWidth)),
				Math.clamp(freePosition.y, 0, Math.max(0, screenHeight - freeHeight)),
				freeWidth, freeHeight);

		double best = free;
		double bestError = Double.POSITIVE_INFINITY;
		List<ResizeCandidate> candidates = new ArrayList<>();
		// Four lines are looking for a rest — the corner's two edges, and the two center lines
		// that trail them at half the speed. Each reachable rest proposes a size; the size whose
		// rendered corner is nearest the pointer wins across both axes.
		for (int axis = 0; axis < 2; axis++) {
			boolean vertical = axis == 0;
			double slope = vertical ? width : height;
			if (slope <= 0) continue;
			double offset = vertical ? widthOffset : heightOffset;
			int anchor = vertical ? anchorX : anchorY;
			boolean lower = vertical ? corner.left : corner.top;
			for (int line = 0; line < 2; line++) {
				boolean center = line == 1;
				int at = lineAt(anchor, lower, slope, offset, free, center);
				for (Candidate candidate : rests(vertical, lower, center, bounds,
						rendered.module, vertical ? screenWidth : screenHeight,
						vertical ? screenHeight : screenWidth, margin)) {
					int gap = Math.abs(candidate.position - at);
					// Moving a center one pixel takes two pixels of size. Comparing that one-pixel
					// gap directly with an edge's would give centers twice the capture range and
					// make the grabbed corner jump twice as far when it pulled free.
					int travel = center ? gap * 2 : gap;
					if (travel > distance) continue;
					double reaching = reaching(candidate.position, anchor, lower, slope, offset,
							center, sizing, limit);
					if (Double.isNaN(reaching)) continue;
					int candidateWidth = (int) Math.round(width * reaching + widthOffset);
					int candidateHeight = (int) Math.round(height * reaching + heightOffset);
					double error = squared(candidateWidth - wantedWidth)
							+ squared(candidateHeight - wantedHeight);
					candidates.add(new ResizeCandidate(reaching, error,
							new Marker(vertical, center, candidate.guide)));
					if (error < bestError) {
						best = reaching;
						bestError = error;
					}
				}
			}
		}
		if (candidates.isEmpty()) return new Resize(best, trueBounds, List.of());
		List<Marker> markers = new ArrayList<>();
		for (ResizeCandidate candidate : candidates) {
			if (Double.compare(candidate.value, best) == 0
					&& !markers.contains(candidate.marker)) {
				markers.add(candidate.marker);
			}
		}
		return new Resize(best, trueBounds, List.copyOf(markers));
	}

	private static double squared(double value) {
		return value * value;
	}

	/**
	 * The winning answer's guides which the resized module genuinely landed on. A module lays
	 * itself out in whole pixels and a scale is a real number, so applying an answer can round
	 * away from a line it reached arithmetically; that line must not be drawn.
	 */
	static Guides resizeGuides(Resize resize, Bounds resized, Corner corner) {
		List<Guide> vertical = new ArrayList<>();
		List<Guide> horizontal = new ArrayList<>();
		for (Marker marker : resize.markers) {
			int line = marker.vertical
					? marker.center ? resized.x + resized.width / 2
							: corner.left ? resized.x : resized.x + resized.width
					: marker.center ? resized.y + resized.height / 2
							: corner.top ? resized.y : resized.y + resized.height;
			if (line != marker.guide.coordinate) continue;
			List<Guide> guides = marker.vertical ? vertical : horizontal;
			if (!guides.contains(marker.guide)) guides.add(marker.guide);
		}
		return new Guides(List.copyOf(vertical), List.copyOf(horizontal));
	}

	/**
	 * Where one of a resize's moving lines can come to rest on one axis. These are the rests
	 * {@link #snap} offers a move and no others, which is the whole point of them: a module
	 * ought to land in the same places whether it was carried there or grown there, and a rest
	 * that only one of the two operations knows about is a rest nobody can predict.
	 *
	 * <p>A move relates two whole boxes — like edge to like edge, center to center, or a
	 * margin's clearance between them — so what a resize can take is that same list restricted
	 * to the lines it actually moves. For the dragged edge, that is the other module's edge of
	 * the same kind, the far side of the other module plus a margin, and the screen's own margin
	 * on the side the edge is: an edge dragged rightwards rests flush on a right edge or a
	 * margin short of a left edge, and never flush against the left edge itself, because a move
	 * would never put two modules together with nothing between them either. For the center,
	 * which a resize drags along at half the rate, it is the other centers and the screen's own.
	 *
	 * <p>{@code vertical} asks for the columns a line in x can rest on, and {@code lower} says
	 * the dragged edge is the box's near edge — its left or top — rather than its far one. The
	 * guide each rest draws spans the module that offered it, or the screen for the screen's own.
	 */
	private static List<Candidate> rests(boolean vertical, boolean lower, boolean center,
			List<Bounds> bounds, Module moving, int screenSize, int crossSize, int margin) {
		List<Candidate> candidates = new ArrayList<>();
		if (center) {
			candidates.add(new Candidate(screenSize / 2, new Guide(screenSize / 2, 0, crossSize)));
		} else if (lower) {
			candidates.add(new Candidate(margin, new Guide(0, 0, crossSize)));
		} else {
			candidates.add(new Candidate(screenSize - margin,
					new Guide(screenSize, 0, crossSize)));
		}
		for (Bounds other : bounds) {
			if (other.module == moving) continue;
			int near = vertical ? other.x : other.y;
			int size = vertical ? other.width : other.height;
			int from = vertical ? other.y : other.x;
			int to = from + (vertical ? other.height : other.width);
			if (center) {
				candidates.add(new Candidate(near + size / 2,
						new Guide(near + size / 2, from, to)));
			} else if (lower) {
				candidates.add(new Candidate(near, new Guide(near, from, to)));
				candidates.add(new Candidate(near + size + margin,
						new Guide(near + size, from, to)));
			} else {
				candidates.add(new Candidate(near + size, new Guide(near + size, from, to)));
				candidates.add(new Candidate(near - margin, new Guide(near, from, to)));
			}
		}
		return candidates;
	}

	/** Every distinct guide sitting on one of these coordinates, in the order they were offered. */
	private static List<Guide> resting(List<Candidate> candidates, int... coordinates) {
		List<Guide> guides = new ArrayList<>();
		for (Candidate candidate : candidates) {
			for (int coordinate : coordinates) {
				if (candidate.position == coordinate && !guides.contains(candidate.guide)) {
					guides.add(candidate.guide);
				}
			}
		}
		return List.copyOf(guides);
	}

	/** A value in range, and in whole units where the setting counts in them. */
	private static double settled(double value, Sizing sizing, double limit) {
		double capped = Math.clamp(value, sizing.min(), limit);
		return sizing.integral()
				? Math.clamp(Math.rint(capped), sizing.min(), limit) : capped;
	}

	/**
	 * Where one of the box's moving lines falls at this size: the edge opposite the pinned one,
	 * or the center, which the pinned edge drags along at half the rate. Centers are taken the
	 * way every other center here is, as the near edge plus half the size.
	 */
	private static int lineAt(int anchor, boolean lower, double slope, double offset,
			double value, boolean center) {
		int size = (int) Math.round(slope * value + offset);
		if (!center) return lower ? anchor - size : anchor + size;
		return (lower ? anchor - size : anchor) + size / 2;
	}

	/** The size that would put that line on a coordinate, before range and rounding. */
	private static double valueAt(int line, int anchor, boolean lower, double slope,
			double offset, boolean center) {
		double size = (lower ? anchor - line : line - anchor) * (center ? 2 : 1);
		return (size - offset) / slope;
	}

	/**
	 * The setting that lands the line exactly on a rest, or {@code NaN} where none does.
	 *
	 * <p>A setting counting in whole units cannot put every line on every coordinate — a center
	 * moves half a pixel for each unit of a size, so half the rests on its axis are between two
	 * of the sizes it can take — so the two values either side of the one the arithmetic asks
	 * for are tried before the rest is given up on. Landing is checked rather than assumed,
	 * because a rest that is merely close is a guide that does not line up with anything.
	 */
	private static double reaching(int rest, int anchor, boolean lower, double slope,
			double offset, boolean center, Sizing sizing, double limit) {
		double settled = settled(valueAt(rest, anchor, lower, slope, offset, center),
				sizing, limit);
		if (lineAt(anchor, lower, slope, offset, settled, center) == rest) return settled;
		if (!sizing.integral()) return Double.NaN;
		for (int step = -1; step <= 1; step += 2) {
			double beside = settled(settled + step, sizing, limit);
			if (lineAt(anchor, lower, slope, offset, beside, center) == rest) return beside;
		}
		return Double.NaN;
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

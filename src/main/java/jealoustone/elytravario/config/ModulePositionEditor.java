package jealoustone.elytravario.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.HudPosition;
import jealoustone.elytravario.hud.BarSpeedometerChart;
import jealoustone.elytravario.hud.DialSpeedometer;
import jealoustone.elytravario.hud.StatsPanel;
import jealoustone.elytravario.hud.VarioHudElement;

import net.minecraft.client.gui.Font;

/** Geometry and coordinate changes for the in-world module position editor. */
final class ModulePositionEditor {
	enum Module {
		CHART(3, "chartX", "chartY", "chartSize"),
		STATS_OTHER(StatsPanel.OTHER),
		STATS_SPEED(StatsPanel.SPEED),
		STATS_ACCEL(StatsPanel.ACCEL),
		STATS_ENERGY(StatsPanel.ENERGY),
		BAR_SPEEDOMETER(5, "barSpeedoX", "barSpeedoY", "barSpeedoHeight"),
		DIAL_SPEEDOMETER(6, "dialSpeedoX", "dialSpeedoY", "dialSpeedoRadius");

		/** The Flight Stats page, whose four modules are its four subpages. */
		private static final int STATS_PAGE = 4;

		final int page;
		/**
		 * The subpage this module's settings are on, or null where its page carries only it.
		 *
		 * <p>It is what lets a page hold more than one module: the screen selects the module
		 * whose subpage is showing, so arrow-key nudges follow the dropdown, and clicking a
		 * module in the world moves the dropdown to match.
		 */
		final String group;
		final String xKey;
		final String yKey;
		/** The settings the module's size is a function of; see {@link #growth}. */
		final List<String> sizeKeys;
		/** The stats panel this module is, or null for the modules that are not one. */
		final StatsPanel panel;

		Module(int page, String xKey, String yKey, String... sizeKeys) {
			this(page, null, null, xKey, yKey, sizeKeys);
		}

		// Height before width: a panel's narrowest width follows the text size its height sets,
		// so a drag that changes both wants the height of the event it is answering.
		Module(StatsPanel panel) {
			this(STATS_PAGE, panel.group(), panel, panel.xKey(), panel.yKey(),
					new String[] { panel.heightKey(), panel.widthKey() });
		}

		Module(int page, String group, StatsPanel panel, String xKey, String yKey,
				String[] sizeKeys) {
			this.page = page;
			this.group = group;
			this.panel = panel;
			this.xKey = xKey;
			this.yKey = yKey;
			this.sizeKeys = List.of(sizeKeys);
		}

		static Module of(StatsPanel panel) {
			for (Module module : values()) {
				if (module.panel == panel) return module;
			}
			throw new IllegalArgumentException(panel.name());
		}
	}

	/**
	 * A corner grip, named for the corner it holds rather than the one it pins.
	 *
	 * <p>Corners and not edges. Three of the modules are a single size setting, so an edge would
	 * have nothing to drag that a corner does not already drag; the stats panel's two settings
	 * are solved one per axis, so its corner already follows the pointer in width and in height
	 * independently, which is the same thing two edges would do one at a time.
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
	/** A logical snap line and the in-bounds pixel on which its guide is painted. */
	record Guide(int coordinate, int from, int to, int strokeCoordinate) {
		Guide(int coordinate, int from, int to) {
			this(coordinate, from, to, coordinate);
		}

		/** A right or bottom edge, whose logical coordinate is just beyond its last pixel. */
		static Guide farEdge(int coordinate, int from, int to) {
			return new Guide(coordinate, from, to, coordinate - 1);
		}
	}
	record Snap(Position position, List<Guide> verticalGuides, List<Guide> horizontalGuides) { }
	/** The guides a drag has come to rest on: vertical ones are columns, horizontal ones rows. */
	record Guides(List<Guide> vertical, List<Guide> horizontal) { }
	/** The snapped answer, the unsnapped box requested by the pointer, and its winning markers. */
	record Resize(double value, Bounds trueBounds, List<Marker> markers) { }
	private record Candidate(int position, Guide guide) { }
	private record AxisSnap(int position, List<Guide> guides) { }
	/** A target guide and the coordinate where the moving edge must rest to earn it. */
	private record Marker(boolean vertical, int rest, Guide guide) { }
	private record ResizeCandidate(double value, double error, Marker marker) { }

	/** Bounds in paint order; callers search backwards so the topmost overlapping module wins. */
	static List<Bounds> bounds(Font font, int screenWidth, int screenHeight, boolean gliding) {
		List<Bounds> result = new ArrayList<>();
		if (!VarioConfig.enabled) return result;

		// The same test the HUD makes: a panel with every row switched off is not drawn, and
		// so is not there to be dragged either.
		for (StatsPanel panel : StatsPanel.values()) {
			if (!panel.visible(gliding)) continue;
			int width = panel.width();
			int height = panel.height();
			HudPosition position = HudPosition.clamp(panel.x(), panel.y(),
					width, height, screenWidth, screenHeight);
			result.add(new Bounds(Module.of(panel), position.x(), position.y(), width, height));
		}
		int chartWidth = VarioHudElement.chartWidth();
		int chartHeight = VarioHudElement.chartHeight();
		HudPosition chartPosition = HudPosition.clamp(VarioConfig.chartX, VarioConfig.chartY,
				chartWidth, chartHeight, screenWidth, screenHeight);
		if (VarioInstrument.CHART.visible(gliding)) {
			result.add(new Bounds(Module.CHART, chartPosition.x(), chartPosition.y(),
					chartWidth, chartHeight));
		}

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
	 * How far a module's near edge is set back from the far edge it is butting against, so that
	 * the two borders land on one column of pixels instead of two.
	 *
	 * <p>Minus one rather than zero. Every panelled module draws a one-pixel border, so setting
	 * two of them down edge to edge puts two identical gray lines side by side — a two-pixel
	 * rule that reads as a seam rather than as a division. Overlapped by one they share a
	 * column, and a row of butted panels is ruled exactly the way the single stats panel used
	 * to rule between its own halves: one line, the same weight as the outline around the pair.
	 *
	 * <p>Nothing is lost to the overlap. The column belongs to both borders, and a border is
	 * chrome rather than a reading, so the only pixel either module gives up is one it was
	 * spending on saying where it ends — which is what the shared line now says for both.
	 */
	static final int OVERLAP = 1;

	/**
	 * Snaps a drag independently on each axis. Equal edges align directly; opposing edges either
	 * keep {@code margin} pixels between the modules or butt together with their borders sharing
	 * a column, {@link #OVERLAP} pixels inside each other. Screen edges use the margin only,
	 * having no border to share.
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
		addIfVisible(xs, maxX - margin, maxX, Guide.farEdge(screenWidth, 0, screenHeight));
		addIfVisible(ys, margin, maxY, new Guide(0, 0, screenWidth));
		addIfVisible(ys, maxY - margin, maxY, Guide.farEdge(screenHeight, 0, screenWidth));
		for (Bounds other : bounds) {
			if (other.module == moving) continue;
			Guide left = new Guide(other.x, other.y, other.y + other.height);
			Guide right = Guide.farEdge(other.x + other.width,
					other.y, other.y + other.height);
			addIfVisible(xs, other.x, maxX, left);
			addIfVisible(xs, other.x + other.width - width, maxX, right);
			addIfVisible(xs, other.x + other.width + margin, maxX, right);
			addIfVisible(xs, other.x - margin - width, maxX, left);
			// Butted, sharing the column the two borders land on.
			addIfVisible(xs, other.x + other.width - OVERLAP, maxX, right);
			addIfVisible(xs, other.x + OVERLAP - width, maxX, left);
			Guide top = new Guide(other.y, other.x, other.x + other.width);
			Guide bottom = Guide.farEdge(other.y + other.height,
					other.x, other.x + other.width);
			addIfVisible(ys, other.y, maxY, top);
			addIfVisible(ys, other.y + other.height - height, maxY, bottom);
			addIfVisible(ys, other.y + other.height + margin, maxY, bottom);
			addIfVisible(ys, other.y - margin - height, maxY, top);
			addIfVisible(ys, other.y + other.height - OVERLAP, maxY, bottom);
			addIfVisible(ys, other.y + OVERLAP - height, maxY, top);
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
	 * How the module's box grows with one of its size settings.
	 *
	 * <p>Every module's box is affine in its settings, so these slopes and the size the module is
	 * currently drawn at describe it exactly: whatever the box carries that the settings do not
	 * pay for — a label column, the dial's rim — falls out as the offset between them. The resize
	 * therefore never has to predict a size it could measure instead.
	 *
	 * <p>Where a module has two settings they are orthogonal, one slope each, which is what lets
	 * each of them be solved on its own axis by the same arithmetic that solves a single one.
	 */
	static Growth growth(String sizeKey) {
		// A stats panel's two settings are its two dimensions, each on its own.
		if (StatsPanel.byWidthKey(sizeKey) != null) return new Growth(1, 0);
		if (StatsPanel.byHeightKey(sizeKey) != null) return new Growth(0, 1);
		return switch (sizeKey) {
			// Size is the exact width; height follows the chart's aspect ratio.
			case "chartSize" -> new Growth(1, (VarioConfig.chartMaxVy - VarioConfig.chartMinVy)
					/ (VarioConfig.chartMaxVxz - VarioConfig.chartMinVxz));
			// The bar chart is as wide as its bars and its scale labels, and neither is the
			// setting: only the plot's height follows the pointer.
			case "barSpeedoHeight" -> new Growth(0, 1);
			// A semicircle is two radii across and one tall.
			case "dialSpeedoRadius" -> new Growth(2, 1);
			default -> throw new IllegalArgumentException(sizeKey);
		};
	}

	/**
	 * The smallest a grip may drag this setting to: the setting's own range, or what the module
	 * will actually draw where that is larger.
	 *
	 * <p>The stats panel is drawn no narrower than its rows need at the text size its height is
	 * asking for, so the grip stops where the panel stops rather than writing widths that would
	 * leave the box sitting still while the number under it kept falling. A text size extreme
	 * enough to need more than the setting can hold leaves the drag at the top of its range
	 * rather than out of it.
	 */
	static double smallest(String sizeKey, double min, double max) {
		StatsPanel panel = StatsPanel.byWidthKey(sizeKey);
		if (panel == null) return min;
		return Math.clamp(panel.minWidth(), min, max);
	}

	/**
	 * The size setting that puts the dragged corner as near the pointer as the module's shape
	 * allows, with the opposite corner pinned.
	 *
	 * <p>One setting has to answer a pointer that moves in two dimensions, so the answer is the
	 * least-squares one: the value whose box comes closest to the box the pointer is asking for.
	 * A module carrying two settings is solved a setting at a time, which is the same arithmetic
	 * and not an approximation of a joint solve: the two are orthogonal, so each least-squares
	 * solve falls entirely on its own axis and neither can move what the other answers.
	 * Where both axes grow that is the pointer projected onto the box's diagonal, which is what
	 * dragging a locked-aspect corner looks like anywhere else; for a module whose width is not
	 * a setting, the same expression collapses to following the pointer vertically.
	 *
	 * <p>The value is capped so that a module cannot be grown off the screen past its pinned
	 * corner, and clamped to the setting's own range. Values are in the setting's own units, not
	 * the screen's displayed ones.
	 *
	 * <p>A line of the box that comes within {@code distance} of somewhere it could rest is put
	 * there exactly instead. The two edges the corner moves look for the same rests that
	 * {@link #snap} would let a move take and nowhere else; see {@link #rests}. Only one answer
	 * can win, because one setting may place both edges: every feasible answer is
	 * scored by how near its resulting corner is to the pointer, and the nearest takes it. A rest
	 * the setting cannot actually reach, because it is out of range or because the module lays
	 * itself out in whole pixels, is passed over for one it can.
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
		// The corner's two moving edges look for a rest. Each reachable rest proposes a size;
		// the size whose rendered corner is nearest the pointer wins across both axes.
		for (int axis = 0; axis < 2; axis++) {
			boolean vertical = axis == 0;
			double slope = vertical ? width : height;
			if (slope <= 0) continue;
			double offset = vertical ? widthOffset : heightOffset;
			int anchor = vertical ? anchorX : anchorY;
			boolean lower = vertical ? corner.left : corner.top;
			int at = lineAt(anchor, lower, slope, offset, free);
			for (Candidate candidate : rests(vertical, lower, bounds,
					rendered.module, vertical ? screenWidth : screenHeight,
					vertical ? screenHeight : screenWidth, margin)) {
				int gap = Math.abs(candidate.position - at);
				if (gap > distance) continue;
				double reaching = reaching(candidate.position, anchor, lower, slope, offset,
						sizing, limit);
				if (Double.isNaN(reaching)) continue;
				int candidateWidth = (int) Math.round(width * reaching + widthOffset);
				int candidateHeight = (int) Math.round(height * reaching + heightOffset);
				double error = squared(candidateWidth - wantedWidth)
						+ squared(candidateHeight - wantedHeight);
				candidates.add(new ResizeCandidate(reaching, error,
						new Marker(vertical, candidate.position, candidate.guide)));
				if (error < bestError) {
					best = reaching;
					bestError = error;
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
	 * itself out in whole pixels and its aspect ratio can make the secondary dimension
	 * fractional, so applying an answer can round away from a line it reached arithmetically;
	 * that line must not be drawn.
	 */
	static Guides resizeGuides(Resize resize, Bounds resized, Corner corner) {
		List<Guide> vertical = new ArrayList<>();
		List<Guide> horizontal = new ArrayList<>();
		for (Marker marker : resize.markers) {
			int line = marker.vertical
					? corner.left ? resized.x : resized.x + resized.width
					: corner.top ? resized.y : resized.y + resized.height;
			if (line != marker.rest) continue;
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
	 * <p>A move relates two whole boxes — edge to like edge, a margin's clearance between them,
	 * or butted with their borders sharing a column — so what a resize can take is that same
	 * list restricted to the lines it actually moves. For the dragged edge, that is the other
	 * module's edge of the same kind, the far side of the other module plus a margin or less
	 * {@link #OVERLAP}, and the screen's own margin on the side the edge is: an edge dragged
	 * rightwards rests flush on a right edge, a margin short of a left edge, or one pixel past
	 * it, and never flush against the left edge itself, because a move would never put two
	 * modules together with two borders abreast either.
	 *
	 * <p>{@code vertical} asks for the columns a line in x can rest on, and {@code lower} says
	 * the dragged edge is the box's near edge — its left or top — rather than its far one. The
	 * guide each rest draws spans the module that offered it, or the screen for the screen's own.
	 */
	private static List<Candidate> rests(boolean vertical, boolean lower,
			List<Bounds> bounds, Module moving, int screenSize, int crossSize, int margin) {
		List<Candidate> candidates = new ArrayList<>();
		if (lower) {
			candidates.add(new Candidate(margin, new Guide(0, 0, crossSize)));
		} else {
			candidates.add(new Candidate(screenSize - margin,
					Guide.farEdge(screenSize, 0, crossSize)));
		}
		for (Bounds other : bounds) {
			if (other.module == moving) continue;
			int near = vertical ? other.x : other.y;
			int size = vertical ? other.width : other.height;
			int from = vertical ? other.y : other.x;
			int to = from + (vertical ? other.height : other.width);
			if (lower) {
				candidates.add(new Candidate(near, new Guide(near, from, to)));
				candidates.add(new Candidate(near + size + margin,
						Guide.farEdge(near + size, from, to)));
				candidates.add(new Candidate(near + size - OVERLAP,
						Guide.farEdge(near + size, from, to)));
			} else {
				candidates.add(new Candidate(near + size,
						Guide.farEdge(near + size, from, to)));
				candidates.add(new Candidate(near - margin, new Guide(near, from, to)));
				candidates.add(new Candidate(near + OVERLAP, new Guide(near, from, to)));
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
	 * Where the box's moving edge falls at this size.
	 */
	private static int lineAt(int anchor, boolean lower, double slope, double offset,
			double value) {
		int size = (int) Math.round(slope * value + offset);
		return lower ? anchor - size : anchor + size;
	}

	/** The size that would put that line on a coordinate, before range and rounding. */
	private static double valueAt(int line, int anchor, boolean lower, double slope,
			double offset) {
		double size = lower ? anchor - line : line - anchor;
		return (size - offset) / slope;
	}

	/**
	 * The setting that lands the line exactly on a rest, or {@code NaN} where none does.
	 *
	 * <p>A setting counting in whole units may not put a line on every coordinate, so the two
	 * values either side of the one the arithmetic asks for are tried before the rest is given
	 * up on. Landing is checked rather than assumed,
	 * because a rest that is merely close is a guide that does not line up with anything.
	 */
	private static double reaching(int rest, int anchor, boolean lower, double slope,
			double offset, Sizing sizing, double limit) {
		double settled = settled(valueAt(rest, anchor, lower, slope, offset),
				sizing, limit);
		if (lineAt(anchor, lower, slope, offset, settled) == rest) return settled;
		if (!sizing.integral()) return Double.NaN;
		for (int step = -1; step <= 1; step += 2) {
			double beside = settled(settled + step, sizing, limit);
			if (lineAt(anchor, lower, slope, offset, beside) == rest) return beside;
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

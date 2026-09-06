package jealoustone.elytravario.hud;

/**
 * Where the stats panel and the velocity graph sit when the graph is attached to a named side
 * of the panel rather than anchored to the screen itself.
 *
 * <p>An attached graph has no anchor of its own. The pair is boxed together and that one box is
 * anchored, which is what keeps an attachment attached: both instruments move as one when the
 * anchor or its offsets change, and the pair as a whole is what gets clamped onto the screen.
 * A side that would take the graph past a screen edge therefore pushes the <em>panel</em> in
 * from that edge instead of sending the graph off it — attaching below a bottom-anchored panel
 * puts the graph in the corner and lifts the panel above it.
 *
 * <p>On the cross axis the two boxes are flush with the edge the anchor names — left edges under
 * a left anchor, top edges under a top one — and centered under the center anchor, so the pair
 * reads as one block against whichever edge it was put on.
 *
 * <p>A hidden or empty panel is passed as a zero-sized box with no gap, which places the graph
 * exactly where the pair's box would have started: it stands in for the panel instead of
 * jumping to a corner of its own.
 */
public record HudLayout(HudPosition panel, HudPosition chart) {
	/**
	 * @param anchor one of the five screen anchors, applied to the pair as a whole
	 * @param offsetX the panel's own inward offsets, which move the pair
	 * @param attach which side of the panel the graph goes on, as an {@code HudPosition.ATTACH_*}
	 * @param gap the clearance between the two boxes; pass zero when the panel is not drawn
	 */
	public static HudLayout attached(int anchor, int offsetX, int offsetY, int attach,
			int panelWidth, int panelHeight, int chartWidth, int chartHeight, int gap,
			int screenWidth, int screenHeight) {
		boolean horizontal = attach == HudPosition.ATTACH_LEFT || attach == HudPosition.ATTACH_RIGHT;
		boolean chartFirst = attach == HudPosition.ATTACH_LEFT || attach == HudPosition.ATTACH_ABOVE;
		int width = horizontal ? panelWidth + gap + chartWidth : Math.max(panelWidth, chartWidth);
		int height = horizontal ? Math.max(panelHeight, chartHeight) : panelHeight + gap + chartHeight;
		HudPosition group = HudPosition.resolve(anchor, offsetX, offsetY, width, height,
				screenWidth, screenHeight);

		int panelMain = chartFirst ? (horizontal ? chartWidth : chartHeight) + gap : 0;
		int chartMain = chartFirst ? 0 : (horizontal ? panelWidth : panelHeight) + gap;
		boolean center = anchor == 4;
		boolean far = horizontal ? HudPosition.bottom(anchor) : HudPosition.right(anchor);
		int span = horizontal ? height : width;
		int panelCross = cross(span, horizontal ? panelHeight : panelWidth, center, far);
		int chartCross = cross(span, horizontal ? chartHeight : chartWidth, center, far);

		HudPosition panel = horizontal
				? new HudPosition(group.x() + panelMain, group.y() + panelCross)
				: new HudPosition(group.x() + panelCross, group.y() + panelMain);
		HudPosition chart = horizontal
				? new HudPosition(group.x() + chartMain, group.y() + chartCross)
				: new HudPosition(group.x() + chartCross, group.y() + chartMain);
		return new HudLayout(panel, chart);
	}

	private static int cross(int span, int own, boolean center, boolean far) {
		if (center) return (span - own) / 2;
		return far ? span - own : 0;
	}
}

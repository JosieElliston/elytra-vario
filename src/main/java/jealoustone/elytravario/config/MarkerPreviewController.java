package jealoustone.elytravario.config;

import java.util.Map;

import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import jealoustone.elytravario.hud.LadderMarkerShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A live, one-GUI-pixel-per-marker-pixel preview inside a marker's settings group. */
final class MarkerPreviewController implements Controller<Integer> {
	private final Option<Integer> option;
	private final String prefix;
	private final Map<String, String> values;

	MarkerPreviewController(Option<Integer> option, String prefix, Map<String, String> values) {
		this.option = option;
		this.prefix = prefix;
		this.values = values;
	}

	@Override public Option<Integer> option() { return option; }
	@Override public Component formatValue() { return option.name(); }

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dimension) {
		return new Element(this, screen, dimension.withHeight(48));
	}

	private static final class Element extends ControllerWidget<MarkerPreviewController> {
		private static final int BACKGROUND = 0xA0101114;
		private static final int BORDER = 0x607C828A;
		private static final int LADDER = 0xA0B4BAC0;
		private static final double SHADOW_OPACITY = 0.35;

		Element(MarkerPreviewController controller, YACLScreen screen,
				Dimension<Integer> dimension) {
			super(controller, screen, dimension);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float delta) {
			Dimension<Integer> bounds = getDimension();
			int x = bounds.x();
			int y = bounds.y();
			int width = bounds.width();
			int height = bounds.height();
			graphics.fill(x, y, x + width, y + height, BACKGROUND);
			graphics.outline(x, y, width, height, BORDER);
			graphics.text(textRenderer, control.option().name(), x + 6, y + 5, 0xFFD7DADE);

			Map<String, String> values = control.values;
			String prefix = control.prefix;
			LadderMarkerShape shape;
			try {
				shape = new LadderMarkerShape(
						Integer.parseInt(values.get(prefix + "Inset")),
						Integer.parseInt(values.get(prefix + "Length")),
						Integer.parseInt(values.get(prefix + "Step")));
			} catch (IllegalArgumentException e) {
				graphics.centeredText(textRenderer,
						Component.translatable("config.elytra-vario.markerPreview.invalid"),
						x + width / 2, y + 27, 0xFFFF6969);
				return;
			}
			int centerGap = Integer.parseInt(values.get("ladderCenterGap"));
			int color = (int) Long.parseLong(values.get(prefix + "Color"), 16);
			int centerX = x + width / 2;
			int centerY = y + 31;
			int insideLeft = Math.max(x + 2, centerX - centerGap - 16);
			int insideRight = Math.min(x + width - 2, centerX + centerGap + 16);
			fillClipped(graphics, insideLeft, centerY, centerX - centerGap, centerY + 1,
					x + 2, x + width - 2, LADDER);
			fillClipped(graphics, centerX + centerGap, centerY, insideRight, centerY + 1,
					x + 2, x + width - 2, LADDER);

			int outside = centerGap - shape.inset();
			int top = y + 17;
			int bottom = y + height - 3;
			drawShadow(graphics, shape, outside, centerX, centerY,
					top, bottom, x + 2, x + width - 2, shadow(color));
			drawMarker(graphics, shape, outside, centerX, centerY,
					top, bottom, x + 2, x + width - 2, color);
		}

		private static void drawShadow(GuiGraphicsExtractor graphics, LadderMarkerShape shape,
				int outside, int centerX, int centerY, int top, int bottom,
				int clipLeft, int clipRight, int color) {
			int radius = shape.height() / 2;
			for (int row = -radius; row <= radius; row++) {
				int py = centerY + row + 1;
				if (py < top || py >= bottom) continue;
				int markerWidth = shape.widthAt(row);
				int inside = outside - markerWidth;
				int maskWidth = shape.widthAt(row + 1);
				if (maskWidth == 0) {
					fillClipped(graphics, centerX + 1 - outside, py,
							centerX + 1 - inside, py + 1, clipLeft, clipRight, color);
					fillClipped(graphics, centerX + 1 + inside, py,
							centerX + 1 + outside, py + 1, clipLeft, clipRight, color);
					continue;
				}

				int maskInside = outside - maskWidth;
				fillClippedExcluding(graphics, centerX + 1 - outside,
						centerX + 1 - inside, centerX - outside, centerX - maskInside,
						py, clipLeft, clipRight, color);
				fillClippedExcluding(graphics, centerX + 1 + inside,
						centerX + 1 + outside, centerX + maskInside, centerX + outside,
						py, clipLeft, clipRight, color);
			}
		}

		private static void drawMarker(GuiGraphicsExtractor graphics, LadderMarkerShape shape,
				int outside, int centerX, int centerY, int top, int bottom,
				int clipLeft, int clipRight, int color) {
			int radius = shape.height() / 2;
			for (int row = -radius; row <= radius; row++) {
				int py = centerY + row;
				if (py < top || py >= bottom) continue;
				int markerWidth = shape.widthAt(row);
				int inside = outside - markerWidth;
				fillClipped(graphics, centerX - outside, py, centerX - inside, py + 1,
						clipLeft, clipRight, color);
				fillClipped(graphics, centerX + inside, py, centerX + outside, py + 1,
						clipLeft, clipRight, color);
			}
		}

		private static int shadow(int color) {
			int alpha = (int) Math.round(((color >>> 24) & 0xFF) * SHADOW_OPACITY);
			return alpha << 24;
		}

		private static void fillClipped(GuiGraphicsExtractor graphics, int left, int top,
				int right, int bottom, int clipLeft, int clipRight, int color) {
			left = Math.max(left, clipLeft);
			right = Math.min(right, clipRight);
			if (left < right) graphics.fill(left, top, right, bottom, color);
		}

		private static void fillClippedExcluding(GuiGraphicsExtractor graphics,
				int left, int right, int maskLeft, int maskRight, int y,
				int clipLeft, int clipRight, int color) {
			fillClipped(graphics, left, y, Math.min(right, maskLeft), y + 1,
					clipLeft, clipRight, color);
			fillClipped(graphics, Math.max(left, maskRight), y, right, y + 1,
					clipLeft, clipRight, color);
		}

		@Override protected int getHoveredControlWidth() { return 0; }
	}
}

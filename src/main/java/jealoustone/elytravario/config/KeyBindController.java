package jealoustone.elytravario.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Inline key capture for YACL, whose stock controllers do not include key mappings. */
final class KeyBindController implements Controller<InputConstants.Key> {
	private final Option<InputConstants.Key> option;

	KeyBindController(Option<InputConstants.Key> option) {
		this.option = option;
	}

	@Override public Option<InputConstants.Key> option() { return option; }
	@Override public Component formatValue() { return option.pendingValue().getDisplayName(); }

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dimension) {
		return new Element(this, screen, dimension);
	}

	static final class Element extends ControllerWidget<KeyBindController> {
		private boolean capturing;

		Element(KeyBindController controller, YACLScreen screen, Dimension<Integer> dimension) {
			super(controller, screen, dimension);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
			if (!isMouseOver(event.x(), event.y()) || !isAvailable()) return false;
			playDownSound();
			capturing = true;
			if (screen instanceof VarioConfigScreen config) config.capture(this);
			return true;
		}

		void accept(InputConstants.Key key) {
			control.option().requestSet(key);
			capturing = false;
		}

		void cancelCapture() { capturing = false; }

		@Override
		protected Component getValueText() {
			Component value = super.getValueText();
			return capturing ? Component.literal("> ").append(value).append(" <") : value;
		}

		@Override
		protected void extractValueText(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float delta) {
			super.extractValueText(graphics, mouseX, mouseY, delta);
			if (hovered) graphics.requestCursor(isAvailable()
					? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
		}

		@Override protected int getHoveredControlWidth() { return getUnhoveredControlWidth(); }
	}
}

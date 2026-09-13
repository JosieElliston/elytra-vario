package jealoustone.elytravario.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
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

		// The 26.2 branch also asks for a pointer cursor while hovered. 1.21.11 has no
		// cursor API, so the control is left with the arrow the whole screen uses.

		@Override protected int getHoveredControlWidth() { return getUnhoveredControlWidth(); }
	}
}

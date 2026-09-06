package jealoustone.elytravario.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.ElytraVarioClient;
import jealoustone.elytravario.VarioInstrument;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Six scrollable pages with live HUD previews and an explicit, non-closing Save.
 * A page may divide into subpages, chosen by a dropdown under the switches the whole page shares. */
public final class VarioConfigScreen extends Screen {
	private static final int PAGE_COUNT = 6;
	/** The page whose switch and key are the whole mod's rather than one instrument's. */
	private static final int GLOBAL_PAGE = 0;
	private final Screen parent;
	private final ConfigPreview preview = new ConfigPreview();
	private final Map<String, String> draft = preview.draft();
	/** Remembers which subpage each divided page was last showing. */
	private final Map<Integer, Integer> subpages = new HashMap<>();
	private int page;
	/** The bind waiting for the next key or mouse press, if any. */
	private KeyMapping capturing;
	private KeyControl keyControl;
	private boolean advanced;
	private String saveError;
	private Button saveButton;
	private boolean savedNotice;
	private int panelLeft;
	private int panelWidth;
	private int panelCenter;

	public VarioConfigScreen(Screen parent) {
		super(text("title"));
		this.parent = parent;
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable("config.elytra-vario." + key, arguments);
	}

	@Override
	protected void init() {
		// A rebuild discards the control that armed the capture, so the capture goes with it.
		capturing = null;
		keyControl = null;
		int span = Math.min(width - 16, minecraft.level != null ? 320 : 600);
		int left = minecraft.level != null ? width - span - 8 : (width - span) / 2;
		panelLeft = left;
		panelWidth = span;
		panelCenter = left + span / 2;
		int columns = span < 400 ? 2 : PAGE_COUNT;
		int tabWidth = span / columns;
		for (int i = 0; i < PAGE_COUNT; i++) {
			final int target = i;
			Button tab = addRenderableWidget(Button.builder(text("page." + i), button -> {
				page = target;
				rebuildWidgets();
			}).bounds(left + i % columns * tabWidth, 28 + i / columns * 22, tabWidth - 2, 20).build());
			tab.active = page != i;
		}
		int tabRows = (PAGE_COUNT + columns - 1) / columns;
		int top = 32 + tabRows * 22;
		// Whether the instrument is shown, whether it is wanted only while gliding, and the key
		// that flips the first: three answers about the page as a whole, so they sit above both
		// the subpage selector and the scrolling list rather than among the settings for how the
		// instrument draws. On the markers page in particular they are not the selected marker's.
		//
		// Global has the same shape, switches then key: the master switch governs every
		// instrument, and the key answers for the screen rather than for anything on it.
		VarioInstrument instrument = instrument(page);
		List<String> pageWide = instrument != null
				? List.of(instrument.showKey(), instrument.glidingOnlyKey())
				: page == GLOBAL_PAGE ? List.of("enabled") : List.of();
		for (String key : pageWide) {
			addRenderableWidget(valueSelector(option(key), left + 4, top, span - 8));
			top += 24;
		}
		if (instrument != null) {
			keyControl = new KeyControl(instrument.key(), "toggleKey");
		} else if (page == GLOBAL_PAGE) {
			keyControl = new KeyControl(ElytraVarioClient.settingsKey(), "settingsKey");
		}
		if (keyControl != null) {
			addRenderableWidget(keyControl.button(left + 4, top, span - 8));
			top += 24;
		}
		List<String> groups = ConfigOptions.groups(page);
		final String group = groups.isEmpty() ? null
				: groups.get(Math.min(subpages.getOrDefault(page, 0), groups.size() - 1));
		if (group != null) {
			addRenderableWidget(CycleButton.<String>builder(id -> text("group." + id), group)
					.withValues(groups)
					.create(left + 4, top, span - 8, 20, text("page." + page + ".group"), (button, value) -> {
						subpages.put(page, groups.indexOf(value));
						rebuildWidgets();
					}));
			top += 24;
		}
		List<OptionRow> rows = new ArrayList<>();
		for (var option : ConfigOptions.all()) {
			if (option.page() != page || pageWide.contains(option.key()) || !shown(option, group)) continue;
			rows.add(new OptionRow(option));
		}
		// Global lifts its one setting above the list, so on that page there is no list to draw:
		// an empty pane would read as settings that failed to appear.
		if (!rows.isEmpty()) {
			OptionList list = addRenderableWidget(new OptionList(top, height - top - 76));
			for (OptionRow row : rows) list.append(row);
		}
		int half = Math.min(span / 2, 180);
		boolean hasAdvanced = ConfigOptions.all().stream()
				.anyMatch(option -> option.page() == page && option.advanced()
						&& (option.group() == null || option.group().equals(group)));
		Button advancedButton = addRenderableWidget(Button.builder(text("advanced", text(hasAdvanced && advanced ? "on" : "off")), button -> {
			advanced = !advanced;
			rebuildWidgets();
		}).bounds(panelCenter - half, height - 68, half - 2, 20)
				.tooltip(Tooltip.create(text("advanced.tooltip"))).build());
		advancedButton.active = hasAdvanced;
		addRenderableWidget(Button.builder(text("reset"), button -> {
			for (var option : ConfigOptions.all()) {
				// Only what this subpage shows, advanced rows included.
				if (option.page() == page && (option.group() == null || option.group().equals(group))) {
					draft.put(option.key(), option.defaultValue());
				}
			}
			changed();
			rebuildWidgets();
		}).bounds(panelCenter + 2, height - 68, half - 2, 20)
				.tooltip(Tooltip.create(text("reset.tooltip"))).build());
		saveButton = addRenderableWidget(Button.builder(text("save"), button -> save())
				.bounds(panelCenter - half, height - 26, half - 2, 20).build());
		addRenderableWidget(Button.builder(text("cancel"), button -> onClose())
				.bounds(panelCenter + 2, height - 26, half - 2, 20).build());
	}

	private static ConfigOptions.Option option(String key) {
		for (var option : ConfigOptions.all()) {
			if (option.key().equals(key)) return option;
		}
		throw new IllegalStateException(key);
	}

	/** The instrument this page governs as a whole, found through its visibility settings. */
	private static VarioInstrument instrument(int page) {
		for (var option : ConfigOptions.all()) {
			if (option.page() != page) continue;
			VarioInstrument found = VarioInstrument.byOptionKey(option.key());
			if (found != null) return found;
		}
		return null;
	}

	/**
	 * Binds the armed toggle, or unbinds it when the press was Escape.
	 *
	 * <p>Key binds are vanilla options rather than this mod's, so this writes straight through to
	 * options.txt instead of into the draft. That is the only way the two menus can agree — the
	 * vanilla Controls screen edits the same mapping, and a bind held in a draft would be
	 * silently reverted by a Cancel there. The cost is that Cancel and Reset here do not undo
	 * it, which the control's tooltip says.
	 */
	private void bind(InputConstants.Key key) {
		capturing.setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		capturing = null;
		if (keyControl != null) keyControl.refresh();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing != null) {
			bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		if (super.keyPressed(event)) return true;
		// The key that opened the settings closes them again, the way Escape does — a bind you
		// press to look at the HUD settings is one you press again to get back to flying.
		//
		// Unlike Escape, it yields to a field being typed into. The bind is a plain letter by
		// default, and a letter meant for a number or color box must reach the box; Escape needs
		// no such care because nothing on this screen wants it. Offering the event to the widgets
		// first is not enough on its own, since a text box takes its ordinary characters through
		// charTyped and so refuses this event, hence the explicit check.
		KeyMapping settings = ElytraVarioClient.settingsKey();
		if (settings != null && settings.matches(event) && !typing(getFocused())) {
			onClose();
			return true;
		}
		return false;
	}

	/** Whether the focus path ends in a text box that is taking input. */
	private static boolean typing(GuiEventListener focused) {
		if (focused instanceof EditBox box) return box.canConsumeInput();
		if (focused instanceof ContainerEventHandler container) return typing(container.getFocused());
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (capturing == null) return super.mouseClicked(event, doubled);
		bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
		return true;
	}

	/** A choice or toggle as a standalone dropdown, for options shown outside the list. */
	private CycleButton<String> valueSelector(ConfigOptions.Option option, int x, int y, int listWidth) {
		List<String> values = new ArrayList<>();
		if (option.toggle()) {
			values.add("false");
			values.add("true");
		} else {
			for (int i = 0; i < option.choices(); i++) values.add(Integer.toString(i));
		}
		CycleButton<String> button = CycleButton.<String>builder(value -> valueLabel(option, value),
				draft.get(option.key())).withValues(values)
				.create(x, y, listWidth, 20, text(option.key()), (widget, value) -> {
					draft.put(option.key(), value);
					changed();
				});
		button.setTooltip(Tooltip.create(tooltip(option)));
		return button;
	}

	private Component valueLabel(ConfigOptions.Option option, String value) {
		return option.toggle() ? text(Boolean.parseBoolean(value) ? "on" : "off")
				: text(option.key() + "." + value);
	}

	private Component tooltip(ConfigOptions.Option option) {
		Component body = text(option.key() + ".tooltip");
		if (!option.toggle() && option.choices() == 0 && !option.color()) {
			body = body.copy().append("\n" + option.min() + " \u2013 " + option.max());
		}
		return text(option.key()).copy().append("\n").append(body);
	}

	/** Advanced rows hide; rows belonging to another subpage are not part of this page's view. */
	private boolean shown(ConfigOptions.Option option, String group) {
		if (option.advanced() && !advanced) return false;
		return option.group() == null || option.group().equals(group);
	}

	private boolean save() {
		if (ConfigOptions.error(draft) != null) return false;
		try {
			ConfigStore.save(draft);
			preview.markSaved();
			saveError = null;
			savedNotice = true;
			return true;
		} catch (IOException | RuntimeException e) {
			ElytraVario.LOGGER.error("Could not save Elytra Vario settings", e);
			saveError = "save_error";
			return false;
		}
	}

	private void changed() {
		saveError = null;
		savedNotice = false;
		preview.preview();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			super.renderBackground(graphics, mouseX, mouseY, delta);
		} else {
			// Leave the world and HUD sharp so edits can be inspected without closing settings.
			graphics.fill(panelLeft - 4, 0, panelLeft + panelWidth + 4, height, 0xB0101014);
		}
	}

	@Override
	public void onClose() {
		if (!preview.hasUnsavedChanges()) {
			minecraft.setScreen(parent);
			return;
		}
		// Opening a child screen must preserve both the draft and its live preview.
		// Restore runtime settings only after an explicit discard confirmation.
		minecraft.setScreen(new ConfirmScreen(discard -> {
			if (discard) {
				preview.restore();
				minecraft.setScreen(parent);
			} else {
				minecraft.setScreen(this);
			}
		}, text("discard.title"), text("discard.message"),
				text("discard.confirm"), text("discard.keep")) {
			@Override
			protected void addButtons(LinearLayout buttons) {
				// Stack all three actions so the popup also fits narrow GUI sizes.
				LinearLayout actions = buttons.addChild(LinearLayout.vertical().spacing(4));
				Button saveAndExit = actions.addChild(Button.builder(text("discard.save"), button -> {
					if (VarioConfigScreen.this.save()) minecraft.setScreen(parent);
				}).build());
				String error = ConfigOptions.error(draft);
				saveAndExit.active = error == null;
				if (error != null) saveAndExit.setTooltip(Tooltip.create(text(error)));
				super.addButtons(actions);
			}

			@Override
			public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
				super.render(graphics, mouseX, mouseY, delta);
				String error = ConfigOptions.error(draft);
				if (error != null || saveError != null) {
					graphics.drawCenteredString(font, text(error != null ? error : saveError),
							width / 2, height - 20, 0xFFFF7777);
				}
			}
		});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		String error = ConfigOptions.error(draft);
		saveButton.active = error == null;
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, panelCenter, 10, 0xFFFFFFFF);
		if (error != null || saveError != null) {
			graphics.drawCenteredString(font, text(error != null ? error : saveError), panelCenter, height - 42, 0xFFFF7777);
		} else if (savedNotice) {
			graphics.drawCenteredString(font, text("saved"), panelCenter, height - 42, 0xFF66DD77);
		}
	}

	private final class OptionList extends ContainerObjectSelectionList<OptionRow> {
		OptionList(int top, int listHeight) {
			super(VarioConfigScreen.this.minecraft, panelWidth, listHeight, top, 46);
			setX(panelLeft);
		}
		@Override protected void renderListBackground(GuiGraphics graphics) {
			if (minecraft.level == null) super.renderListBackground(graphics);
		}
		@Override protected void renderListSeparators(GuiGraphics graphics) {
			if (minecraft.level == null) super.renderListSeparators(graphics);
		}
		void append(OptionRow row) { addEntry(row); }
		@Override public int getRowWidth() { return panelWidth - 24; }
	}

	/**
	 * The page's own key — an instrument's toggle, or on Global the one that opens this screen —
	 * rebindable here so that the whole of a page's behavior is in one place rather than split
	 * between this screen and the vanilla Controls list. Every one of these mappings is
	 * registered with the game, so they are all in that list too, and either screen sets them.
	 *
	 * <p>It reads as one more switch beside those above it, and is labeled the way they are,
	 * because that is what it is — but <b>it is not part of the draft</b>, unlike every setting
	 * on this screen: see {@link #bind}. It takes effect and is saved as soon as it is pressed.
	 *
	 * <p>A key already spoken for elsewhere is shown in red with the offending binds named,
	 * rather than refused. Vanilla allows the clash and so does this; what a conflicting key
	 * does is fire both actions, which is occasionally even what was wanted.
	 */
	private final class KeyControl {
		/** Null only if the screen is somehow open before client init registered the keys. */
		private final KeyMapping mapping;
		private final String labelKey;
		private final Button button;

		KeyControl(KeyMapping mapping, String labelKey) {
			this.mapping = mapping;
			this.labelKey = labelKey;
			this.button = Button.builder(Component.empty(), widget -> {
				capturing = mapping;
				refresh();
			}).build();
		}

		Button button(int x, int y, int buttonWidth) {
			button.setX(x);
			button.setY(y);
			button.setWidth(buttonWidth);
			refresh();
			return button;
		}

		void refresh() {
			button.active = mapping != null;
			if (mapping == null) {
				button.setMessage(text(labelKey));
				return;
			}
			// "Name: value", the shape CycleButton gives the switches above this one.
			Component name = mapping.getTranslatedKeyMessage();
			if (capturing == mapping) {
				button.setMessage(labeled(Component.literal("> ")
						.append(name.copy().withStyle(ChatFormatting.YELLOW))
						.append(" <").withStyle(ChatFormatting.YELLOW)));
				button.setTooltip(Tooltip.create(text("keyBind.capturing")));
				return;
			}
			Component conflicts = conflicts(mapping);
			button.setMessage(labeled(conflicts == null ? name : name.copy().withStyle(ChatFormatting.RED)));
			Component tooltip = text(labelKey).copy().append("\n").append(text(labelKey + ".tooltip"));
			if (conflicts != null) {
				tooltip = tooltip.copy().append("\n").append(text("keyBind.conflict", conflicts));
			}
			button.setTooltip(Tooltip.create(tooltip));
		}

		private Component labeled(Component value) {
			return text(labelKey).copy().append(": ").append(value);
		}

		/** The names of every other bind on the same key, or null when there are none. */
		private Component conflicts(KeyMapping mapping) {
			if (mapping.isUnbound()) return null;
			Component names = null;
			for (KeyMapping other : minecraft.options.keyMappings) {
				if (other == mapping || !other.same(mapping)) continue;
				Component name = Component.translatable(other.getName());
				names = names == null ? name : names.copy().append(", ").append(name);
			}
			return names;
		}
	}

	private final class OptionRow extends ContainerObjectSelectionList.Entry<OptionRow> {
		private final ConfigOptions.Option option;
		private final AbstractWidget control;

		OptionRow(ConfigOptions.Option option) {
			this.option = option;
			Component label = text(option.key());
			if (option.toggle() || option.choices() > 0) {
				control = Button.builder(valueLabel(), button -> {
					String value = draft.get(option.key());
					draft.put(option.key(), option.toggle() ? Boolean.toString(!Boolean.parseBoolean(value))
							: Integer.toString((Integer.parseInt(value) + 1) % option.choices()));
					button.setMessage(valueLabel());
					changed();
				}).bounds(0, 0, 180, 20).build();
			} else {
				EditBox box = new EditBox(font, 0, 0, 180, 20, label);
				box.setMaxLength(32);
				box.setValue(draft.get(option.key()));
				box.setResponder(value -> {
					draft.put(option.key(), value);
					box.setTextColor(valid(value) ? 0xFFE0E0E0 : 0xFFFF7777);
					changed();
				});
				box.setTextColor(valid(box.getValue()) ? 0xFFE0E0E0 : 0xFFFF7777);
				control = box;
			}
			control.setTooltip(Tooltip.create(tooltip(option)));
		}

		private boolean valid(String value) {
			try { option.parse(value); return true; }
			catch (RuntimeException e) { return false; }
		}

		private Component valueLabel() {
			return VarioConfigScreen.this.valueLabel(option, draft.get(option.key()));
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			graphics.drawString(font, text(option.key()), getContentX(), getContentY(), 0xFFFFFFFF);
			control.setX(getContentX());
			control.setY(getContentY() + 13);
			control.setWidth(getContentWidth());
			control.render(graphics, mouseX, mouseY, delta);
		}

		@Override public List<? extends GuiEventListener> children() { return List.of(control); }
		@Override public List<? extends NarratableEntry> narratables() { return List.of(control); }
	}
}

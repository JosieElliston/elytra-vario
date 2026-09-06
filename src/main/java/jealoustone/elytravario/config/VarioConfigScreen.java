package jealoustone.elytravario.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.VarioInstrument;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
 * A page may divide into subpages: shared settings, a rule, then the selected subpage's own. */
public final class VarioConfigScreen extends Screen {
	private static final int PAGE_COUNT = 6;
	private final Screen parent;
	private final ConfigPreview preview = new ConfigPreview();
	private final Map<String, String> draft = preview.draft();
	/** Remembers which subpage each divided page was last showing. */
	private final Map<Integer, Integer> subpages = new HashMap<>();
	private int page;
	/** The instrument whose toggle key is waiting for the next key or mouse press, if any. */
	private VarioInstrument capturing;
	private KeyRow keyRow;
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
		// A rebuild discards the row that armed the capture, so the capture goes with it.
		capturing = null;
		keyRow = null;
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
		// The master switch stays in view while the subpage selector changes under it.
		ConfigOptions.Option header = ConfigOptions.header(page);
		if (header != null) {
			addRenderableWidget(valueSelector(header, left + 4, top, span - 8));
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
		OptionList list = addRenderableWidget(new OptionList(top, height - top - 76));
		VarioInstrument instrument = instrument(page);
		// The toggle key belongs directly under the visibility switch it overrides — which on a
		// page whose switch is the header means at the top of the list instead.
		if (instrument != null && header != null) list.append(keyRow = new KeyRow(instrument));
		boolean separated = false;
		for (var option : ConfigOptions.all()) {
			if (option.page() != page || option.equals(header) || !shown(option, group)) continue;
			if (option.group() != null && !separated) {
				// The shared settings above always exist, so the rule never opens the list.
				list.append(new SeparatorRow());
				separated = true;
			}
			list.append(new OptionRow(option));
			if (instrument != null && keyRow == null
					&& VarioInstrument.byVisibilityKey(option.key()) == instrument) {
				list.append(keyRow = new KeyRow(instrument));
			}
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

	/** The instrument this page governs as a whole, found through its visibility setting. */
	private static VarioInstrument instrument(int page) {
		for (var option : ConfigOptions.all()) {
			if (option.page() != page) continue;
			VarioInstrument found = VarioInstrument.byVisibilityKey(option.key());
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
	 * it, which the row's tooltip says.
	 */
	private void bind(InputConstants.Key key) {
		capturing.key().setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		capturing = null;
		if (keyRow != null) keyRow.refresh();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing == null) return super.keyPressed(event);
		bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		return true;
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			super.extractBackground(graphics, mouseX, mouseY, delta);
		} else {
			// Leave the world and HUD sharp so edits can be inspected without closing settings.
			graphics.fill(panelLeft - 4, 0, panelLeft + panelWidth + 4, height, 0xB0101014);
		}
	}

	@Override
	public void onClose() {
		if (!preview.hasUnsavedChanges()) {
			minecraft.gui.setScreen(parent);
			return;
		}
		// Opening a child screen must preserve both the draft and its live preview.
		// Restore runtime settings only after an explicit discard confirmation.
		minecraft.gui.setScreen(new ConfirmScreen(discard -> {
			if (discard) {
				preview.restore();
				minecraft.gui.setScreen(parent);
			} else {
				minecraft.gui.setScreen(this);
			}
		}, text("discard.title"), text("discard.message"),
				text("discard.confirm"), text("discard.keep")) {
			@Override
			protected void addButtons(LinearLayout buttons) {
				// Stack all three actions so the popup also fits narrow GUI sizes.
				LinearLayout actions = buttons.addChild(LinearLayout.vertical().spacing(4));
				Button saveAndExit = actions.addChild(Button.builder(text("discard.save"), button -> {
					if (VarioConfigScreen.this.save()) minecraft.gui.setScreen(parent);
				}).build());
				String error = ConfigOptions.error(draft);
				saveAndExit.active = error == null;
				if (error != null) saveAndExit.setTooltip(Tooltip.create(text(error)));
				super.addButtons(actions);
			}

			@Override
			public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
				super.extractRenderState(graphics, mouseX, mouseY, delta);
				String error = ConfigOptions.error(draft);
				if (error != null || saveError != null) {
					graphics.centeredText(font, text(error != null ? error : saveError),
							width / 2, height - 20, 0xFFFF7777);
				}
			}
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		String error = ConfigOptions.error(draft);
		saveButton.active = error == null;
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, panelCenter, 10, 0xFFFFFFFF);
		if (error != null || saveError != null) {
			graphics.centeredText(font, text(error != null ? error : saveError), panelCenter, height - 42, 0xFFFF7777);
		} else if (savedNotice) {
			graphics.centeredText(font, text("saved"), panelCenter, height - 42, 0xFF66DD77);
		}
	}

	private final class OptionList extends ContainerObjectSelectionList<Row> {
		OptionList(int top, int listHeight) {
			super(VarioConfigScreen.this.minecraft, panelWidth, listHeight, top, 46);
			setX(panelLeft);
		}
		@Override protected void extractListBackground(GuiGraphicsExtractor graphics) {
			if (minecraft.level == null) super.extractListBackground(graphics);
		}
		@Override protected void extractListSeparators(GuiGraphicsExtractor graphics) {
			if (minecraft.level == null) super.extractListSeparators(graphics);
		}
		void append(Row row) { addEntry(row); }
		@Override public int getRowWidth() { return panelWidth - 24; }
	}

	/** The list holds two kinds of row, so they share one self-typed base. */
	private abstract class Row extends ContainerObjectSelectionList.Entry<Row> { }

	/** A rule between the settings shared by every marker and the selected marker's own. */
	private final class SeparatorRow extends Row {
		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int y = getContentYMiddle();
			graphics.fill(getContentX(), y, getContentX() + getContentWidth(), y + 1, 0x30FFFFFF);
		}

		@Override public List<? extends GuiEventListener> children() { return List.of(); }
		@Override public List<? extends NarratableEntry> narratables() { return List.of(); }
	}

	/**
	 * The instrument's toggle key, rebindable here so that the whole of a page's behavior is in
	 * one place rather than split between this screen and the vanilla Controls list. The same
	 * mapping is registered with the game, so it is still in that list too, and either screen
	 * sets it.
	 *
	 * <p><b>Not part of the draft</b>, unlike every other row: see {@link #bind}. It takes effect
	 * and is saved as soon as it is pressed.
	 *
	 * <p>A key already spoken for elsewhere is shown in red with the offending binds named,
	 * rather than refused. Vanilla allows the clash and so does this; what a conflicting key
	 * does is fire both actions, which is occasionally even what was wanted.
	 */
	private final class KeyRow extends Row {
		private final VarioInstrument instrument;
		private final Button control;

		KeyRow(VarioInstrument instrument) {
			this.instrument = instrument;
			this.control = Button.builder(Component.empty(), button -> {
				capturing = instrument;
				refresh();
			}).bounds(0, 0, 180, 20).build();
			refresh();
		}

		void refresh() {
			KeyMapping mapping = instrument.key();
			// Null only if the screen is somehow open before client init registered the keys.
			control.active = mapping != null;
			if (mapping == null) {
				control.setMessage(Component.empty());
				return;
			}
			Component name = mapping.getTranslatedKeyMessage();
			if (capturing == instrument) {
				control.setMessage(Component.literal("> ")
						.append(name.copy().withStyle(ChatFormatting.YELLOW))
						.append(" <").withStyle(ChatFormatting.YELLOW));
				control.setTooltip(Tooltip.create(text("toggleKey.capturing")));
				return;
			}
			Component conflicts = conflicts(mapping);
			control.setMessage(conflicts == null ? name : name.copy().withStyle(ChatFormatting.RED));
			Component tooltip = text("toggleKey").copy().append("\n").append(text("toggleKey.tooltip"));
			if (conflicts != null) {
				tooltip = tooltip.copy().append("\n").append(text("toggleKey.conflict", conflicts));
			}
			control.setTooltip(Tooltip.create(tooltip));
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

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			graphics.text(font, text("toggleKey"), getContentX(), getContentY(), 0xFFFFFFFF);
			control.setX(getContentX());
			control.setY(getContentY() + 13);
			control.setWidth(getContentWidth());
			control.extractRenderState(graphics, mouseX, mouseY, delta);
		}

		@Override public List<? extends GuiEventListener> children() { return List.of(control); }
		@Override public List<? extends NarratableEntry> narratables() { return List.of(control); }
	}

	private final class OptionRow extends Row {
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
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			graphics.text(font, text(option.key()), getContentX(), getContentY(), 0xFFFFFFFF);
			control.setX(getContentX());
			control.setY(getContentY() + 13);
			control.setWidth(getContentWidth());
			control.extractRenderState(graphics, mouseX, mouseY, delta);
		}

		@Override public List<? extends GuiEventListener> children() { return List.of(control); }
		@Override public List<? extends NarratableEntry> narratables() { return List.of(control); }
	}
}

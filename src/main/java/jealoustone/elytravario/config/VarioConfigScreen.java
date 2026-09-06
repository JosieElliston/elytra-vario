package jealoustone.elytravario.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jealoustone.elytravario.ElytraVario;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Five scrollable pages with live HUD previews and an explicit, non-closing Save. */
public final class VarioConfigScreen extends Screen {
	private static final int PAGE_COUNT = 6;
	private final Screen parent;
	private final ConfigPreview preview = new ConfigPreview();
	private final Map<String, String> draft = preview.draft();
	private int page;
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
		OptionList list = addRenderableWidget(new OptionList(top, height - top - 76));
		for (var option : ConfigOptions.all()) {
			if (option.page() == page && (!option.advanced() || advanced)) {
				list.append(new OptionRow(option));
			}
		}
		int half = Math.min(span / 2, 180);
		boolean hasAdvanced = ConfigOptions.all().stream().anyMatch(option -> option.page() == page && option.advanced());
		Button advancedButton = addRenderableWidget(Button.builder(text("advanced", text(hasAdvanced && advanced ? "on" : "off")), button -> {
			advanced = !advanced;
			rebuildWidgets();
		}).bounds(panelCenter - half, height - 68, half - 2, 20)
				.tooltip(Tooltip.create(text("advanced.tooltip"))).build());
		advancedButton.active = hasAdvanced;
		addRenderableWidget(Button.builder(text("reset"), button -> {
			for (var option : ConfigOptions.all()) {
				if (option.page() == page) draft.put(option.key(), option.defaultValue());
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
			Component tooltip = text(option.key() + ".tooltip");
			if (!option.toggle() && option.choices() == 0 && !option.color()) {
				tooltip = tooltip.copy().append("\n" + option.min() + " – " + option.max());
			}
			control.setTooltip(Tooltip.create(label.copy().append("\n").append(tooltip)));
		}

		private boolean valid(String value) {
			try { option.parse(value); return true; }
			catch (RuntimeException e) { return false; }
		}

		private Component valueLabel() {
			String value = draft.get(option.key());
			return option.toggle() ? text(Boolean.parseBoolean(value) ? "on" : "off")
					: text(option.key() + "." + value);
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

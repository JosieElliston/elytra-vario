package jealoustone.elytravario.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.ElytraVarioClient;
import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.VarioHudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Seven scrollable pages whose edits take effect in the HUD as they are made and save themselves.
 * A page may divide into subpages, chosen by a dropdown under the switches the whole page shares. */
public final class VarioConfigScreen extends Screen {
	private static final int PAGE_COUNT = 7;
	/** The page whose switch and key are the whole mod's rather than one instrument's. */
	private static final int GLOBAL_PAGE = 0;
	/** How thick a resize grip's arms are drawn, over the outline they thicken. */
	private static final int GRIP_THICKNESS = 2;
	private final Screen parent;
	private final Map<String, String> settings = ConfigOptions.snapshot();
	/** The boxes for the settings the in-world editor also writes: positions and sizes. */
	private final Map<String, EditBox> editorBoxes = new HashMap<>();
	// Where you were reading is not a setting, but losing it is felt like one: the screen is
	// opened and closed repeatedly while flying, to try one number and watch the HUD. Page,
	// subpage, scroll and the Advanced switch therefore outlive the screen, and are remembered
	// for the session rather than written to disk, since they say nothing about how the mod
	// should behave.
	/** Remembers which subpage each divided page was last showing. */
	private static final Map<Integer, Integer> subpages = new HashMap<>();
	/** Remembers how far down each page's list was scrolled. */
	private static final Map<Integer, Double> scrolls = new HashMap<>();
	private static int page;
	private static boolean advanced;
	/** The page the current list was built for, which is not {@link #page} once a tab has been
	 * clicked and before the rebuild that answers it. */
	private int listPage;
	/** The bind waiting for the next key or mouse press, if any. */
	private KeyMapping capturing;
	private final List<KeyControl> keyControls = new ArrayList<>();
	/** An edit the next write will persist. */
	private boolean dirty;
	private String saveError;
	private OptionList optionList;
	private int panelLeft;
	private int panelWidth;
	private int panelCenter;
	private ModulePositionEditor.Module draggingModule;
	/** The grip being dragged, when the drag is a resize rather than a move. */
	private ModulePositionEditor.Corner draggingCorner;
	/** Pointer-driven position before snapping, retained so a snapped module can pull free.
	 * A move drags the module's top-left; a resize drags the grabbed corner. */
	private double dragX;
	private double dragY;
	private List<ModulePositionEditor.Guide> snapVerticalGuides = List.of();
	private List<ModulePositionEditor.Guide> snapHorizontalGuides = List.of();
	private boolean updatingEditorBoxes;

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
		keyControls.clear();
		optionList = null;
		editorBoxes.clear();
		int span = Math.min(width - 16, minecraft.level != null ? 320 : 600);
		int left = minecraft.level != null ? width - span - 8 : (width - span) / 2;
		panelLeft = left;
		panelWidth = span;
		panelCenter = left + span / 2;
		int columns = span < 300 ? 2 : span < 400 ? 3 : PAGE_COUNT;
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
		// Global has the same shape, switch then keys: the master switch governs every
		// instrument, with separate bindings for toggling it and opening this screen.
		VarioInstrument instrument = instrument(page);
		List<String> pageWide = instrument != null
				? List.of(instrument.showKey(), instrument.glidingOnlyKey())
				: page == GLOBAL_PAGE ? List.of("enabled") : List.of();
		for (String key : pageWide) {
			addRenderableWidget(valueSelector(option(key), left + 4, top, span - 8));
			top += 24;
		}
		if (instrument != null) {
			addKeyControl(instrument.key(), "toggleKey", left, top, span);
			top += 24;
		} else if (page == GLOBAL_PAGE) {
			addKeyControl(ElytraVarioClient.visibilityKey(), "visibilityKey", left, top, span);
			top += 24;
			addKeyControl(ElytraVarioClient.settingsKey(), "settingsKey", left, top, span);
			top += 24;
		}
		List<String> groups = ConfigOptions.groups(page);
		final String group = groups.isEmpty() ? null
				: groups.get(Math.min(subpages.getOrDefault(page, 0), groups.size() - 1));
		List<ConfigRow> rows = new ArrayList<>();
		// Shared settings precede the selector; only the selected subpage's settings follow it.
		// Keeping all three in one list matters on pages such as the speedometer, whose shared
		// controls are too numerous to fit in a separate fixed pane above the selector.
		for (var option : ConfigOptions.all()) {
			if (option.page() == page && option.group() == null
					&& !pageWide.contains(option.key()) && shown(option, group)) {
				rows.add(new OptionRow(option));
			}
		}
		if (group != null) {
			if (rows.isEmpty()) {
				// With nothing shared, keep the selector fixed above the list as on Markers.
				addRenderableWidget(subpageSelector(groups, group, left + 4, top, span - 8));
				top += 24;
			} else {
				rows.add(new SubpageRow(groups, group));
			}
		}
		for (var option : ConfigOptions.all()) {
			if (option.page() == page && option.group() != null && shown(option, group)) {
				rows.add(new OptionRow(option));
			}
		}
		// Do not draw an empty pane on pages whose settings all live above the list.
		if (!rows.isEmpty()) {
			optionList = addRenderableWidget(new OptionList(top, height - top - 76));
			for (ConfigRow row : rows) optionList.append(row);
			listPage = page;
			// After the entries, so that the list knows how far it is able to scroll.
			optionList.setScrollAmount(scrolls.getOrDefault(page, 0.0));
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
					settings.put(option.key(), option.defaultValue());
				}
			}
			changed();
			rebuildWidgets();
		}).bounds(panelCenter + 2, height - 68, half - 2, 20)
				.tooltip(Tooltip.create(text("reset.tooltip"))).build());
		// One button where Save and Close used to be a pair: there is nothing left to decide on
		// the way out, since everything above has already been written. It sits directly under
		// Advanced and Reset, in the row the pair used to leave empty above it, with only the
		// error line below.
		addRenderableWidget(Button.builder(text("close"), button -> onClose())
				.bounds(panelCenter - half / 2, height - 44, half - 2, 20).build());
	}

	/** The list is discarded by a rebuild, by a resize and by leaving the screen alike, so each
	 * of the three records where it had got to before letting go of it. */
	private void rememberScroll() {
		if (optionList != null) scrolls.put(listPage, optionList.scrollAmount());
	}

	@Override
	protected void rebuildWidgets() {
		rememberScroll();
		super.rebuildWidgets();
	}

	@Override
	public void resize(int width, int height) {
		rememberScroll();
		super.resize(width, height);
	}

	@Override
	public void removed() {
		rememberScroll();
		flush();
		// A screen closed mid-drag never sees the mouse come up.
		VarioHudElement.stretchEnergyField = false;
		super.removed();
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

	private void addKeyControl(KeyMapping mapping, String labelKey, int left, int top, int span) {
		KeyControl control = new KeyControl(mapping, labelKey);
		keyControls.add(control);
		addRenderableWidget(control.button(left + 4, top, span - 8));
	}

	/**
	 * Pulls externally toggled visibility values into the screen without replacing unrelated
	 * edits, including a half-typed value that is not yet valid enough to apply.
	 */
	private void syncVisibilitySettings() {
		Map<String, String> live = ConfigOptions.snapshot();
		settings.put("enabled", live.get("enabled"));
		for (VarioInstrument instrument : VarioInstrument.values()) {
			settings.put(instrument.showKey(), live.get(instrument.showKey()));
		}
	}

	/**
	 * Binds the armed toggle, or unbinds it when the press was Escape.
	 *
	 * <p>Key binds are vanilla options rather than this mod's, so this writes straight through to
	 * options.txt instead of into this screen's values. That is the only way the two menus can
	 * agree — the vanilla Controls screen edits the same mapping, and a bind held here until
	 * later would be silently reverted by a Cancel there. The cost is that Reset does not undo
	 * it, which the control's tooltip says.
	 */
	private void bind(InputConstants.Key key) {
		capturing.setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		capturing = null;
		for (KeyControl control : keyControls) control.refresh();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing != null) {
			bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		// A text box keeps the arrows for moving its caret. Everywhere else they belong to the
		// module whose settings page is selected, so page selection and module selection cannot
		// disagree.
		if (!typing(getFocused())) {
			int dx = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
			int dy = event.isUp() ? -1 : event.isDown() ? 1 : 0;
			ModulePositionEditor.Module selected = selectedModule();
			if (selected != null && (dx != 0 || dy != 0)) {
				move(selected, dx, dy);
				return true;
			}
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
		if (!typing(getFocused())) {
			boolean handled = ElytraVarioClient.toggleVisibilityIfMatches(event);
			handled |= VarioInstrument.toggleMatching(event);
			KeyMapping settingsKey = ElytraVarioClient.settingsKey();
			if (handled) syncVisibilitySettings();
			if (settingsKey != null && settingsKey.matches(event)) {
				onClose();
				return true;
			}
			if (handled) rebuildWidgets();
			return handled;
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
		if (capturing != null) {
			bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		// Containers may consume their empty background, so use the leaf hit test directly:
		// real controls own their pixels, while modules get first claim everywhere else.
		if (overControl(event.x(), event.y())) return super.mouseClicked(event, doubled);
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && minecraft.level != null) {
			ModulePositionEditor.Bounds target = moduleAt(
					event.x(), event.y(), selectedModule());
			if (target != null) {
				if (page != target.module().page) {
					page = target.module().page;
					rebuildWidgets();
				}
				clearFocus();
				draggingModule = target.module();
				draggingCorner = ModulePositionEditor.grip(target, event.x(), event.y());
				// Resizing the chart would otherwise rebuild the heatmap on every pixel of the
				// drag, so it stretches the one it has until the mouse comes up.
				VarioHudElement.stretchEnergyField = draggingCorner != null
						&& draggingModule == ModulePositionEditor.Module.CHART;
				dragX = draggingCorner != null && !draggingCorner.left
						? target.x() + target.width() : target.x();
				dragY = draggingCorner != null && !draggingCorner.top
						? target.y() + target.height() : target.y();
				snapVerticalGuides = List.of();
				snapHorizontalGuides = List.of();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (draggingModule == null) return super.mouseDragged(event, deltaX, deltaY);
		dragX += deltaX;
		dragY += deltaY;
		if (draggingCorner == null) {
			drag(draggingModule, (int) Math.round(dragX), (int) Math.round(dragY));
		} else {
			resize(draggingModule, draggingCorner, dragX, dragY);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingModule == null) return super.mouseReleased(event);
		draggingModule = null;
		draggingCorner = null;
		VarioHudElement.stretchEnergyField = false;
		snapVerticalGuides = List.of();
		snapHorizontalGuides = List.of();
		return true;
	}

	private List<ModulePositionEditor.Bounds> moduleBounds() {
		boolean gliding = minecraft.player != null && minecraft.player.isFallFlying();
		return ModulePositionEditor.bounds(font, width, height, gliding);
	}

	private ModulePositionEditor.Bounds moduleAt(double x, double y,
			ModulePositionEditor.Module preferred) {
		return ModulePositionEditor.at(moduleBounds(), x, y, preferred);
	}

	private ModulePositionEditor.Module selectedModule() {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page == page) return module;
		}
		return null;
	}

	private void move(ModulePositionEditor.Module module, int dx, int dy) {
		if (!ModulePositionEditor.nudge(module, dx, dy, settings, renderedBounds(module),
				width, height)) {
			return;
		}
		editorWrote(module.xKey, module.yKey);
	}

	/** The module as it is currently drawn, or null while it is hidden. */
	private ModulePositionEditor.Bounds renderedBounds(ModulePositionEditor.Module module) {
		return boundsOf(moduleBounds(), module);
	}

	private static ModulePositionEditor.Bounds boundsOf(List<ModulePositionEditor.Bounds> bounds,
			ModulePositionEditor.Module module) {
		for (ModulePositionEditor.Bounds candidate : bounds) {
			if (candidate.module() == module) return candidate;
		}
		return null;
	}

	private void drag(ModulePositionEditor.Module module, int x, int y) {
		ModulePositionEditor.Bounds moving = null;
		List<ModulePositionEditor.Bounds> bounds = moduleBounds();
		for (ModulePositionEditor.Bounds candidate : bounds) {
			if (candidate.module() == module) moving = candidate;
		}
		if (moving == null) return;
		ModulePositionEditor.Snap snap = ModulePositionEditor.snap(module, x, y,
				moving.width(), moving.height(), bounds, width, height,
				VarioConfig.positionMargin, VarioConfig.positionSnapDistance);
		snapVerticalGuides = snap.verticalGuides();
		snapHorizontalGuides = snap.horizontalGuides();
		ModulePositionEditor.Position snapped = snap.position();
		String nextX = Integer.toString(snapped.x());
		String nextY = Integer.toString(snapped.y());
		if (nextX.equals(settings.get(module.xKey)) && nextY.equals(settings.get(module.yKey))) return;
		settings.put(module.xKey, nextX);
		settings.put(module.yKey, nextY);
		editorWrote(module.xKey, module.yKey);
	}

	/**
	 * Scales the module so that the grabbed corner follows the pointer and the corner opposite
	 * it stays where it is.
	 *
	 * <p>The new size is measured off the module after the setting has been applied rather than
	 * predicted from it, so that the pinned corner survives whatever rounding the module does
	 * when it lays itself out: a scale is a real number and a box is a whole number of pixels.
	 */
	private void resize(ModulePositionEditor.Module module, ModulePositionEditor.Corner corner,
			double pointerX, double pointerY) {
		List<ModulePositionEditor.Bounds> bounds = moduleBounds();
		ModulePositionEditor.Bounds before = boundsOf(bounds, module);
		if (before == null) return;
		ConfigOptions.Option size = option(module.sizeKey);
		double value;
		try {
			value = Double.parseDouble(settings.get(size.key())) / size.factor();
		} catch (RuntimeException e) {
			// A size box may temporarily contain incomplete input.
			return;
		}
		double resized = ModulePositionEditor.resize(corner, before, value,
				new ModulePositionEditor.Sizing(ModulePositionEditor.growth(module),
						size.min() / size.factor(), size.max() / size.factor(), size.integral()),
				pointerX, pointerY, bounds, width, height,
				VarioConfig.positionMargin, VarioConfig.positionSnapDistance);
		String next = size.format(resized);
		if (!next.equals(settings.get(size.key()))) {
			settings.put(size.key(), next);
			editorWrote(size.key());
		}
		ModulePositionEditor.Bounds after = renderedBounds(module);
		if (after == null) return;
		ModulePositionEditor.Position position = ModulePositionEditor.anchored(corner, before,
				after.width(), after.height());
		int x = Math.clamp(position.x(), 0, Math.max(0, width - after.width()));
		int y = Math.clamp(position.y(), 0, Math.max(0, height - after.height()));
		String nextX = Integer.toString(x);
		String nextY = Integer.toString(y);
		if (!nextX.equals(settings.get(module.xKey))
				|| !nextY.equals(settings.get(module.yKey))) {
			settings.put(module.xKey, nextX);
			settings.put(module.yKey, nextY);
			editorWrote(module.xKey, module.yKey);
		}
		// Asked of the module as it ended up, so a guide is drawn only where an edge is
		// genuinely on it. The others have not moved, so the rests they offer are unchanged.
		ModulePositionEditor.Guides guides = ModulePositionEditor.resizeGuides(
				new ModulePositionEditor.Bounds(module, x, y, after.width(), after.height()),
				corner, bounds, width, height, VarioConfig.positionMargin);
		snapVerticalGuides = guides.vertical();
		snapHorizontalGuides = guides.horizontal();
	}

	/** Shows what the in-world editor wrote in the boxes that show the same settings. */
	private void editorWrote(String... keys) {
		updatingEditorBoxes = true;
		for (String key : keys) {
			EditBox box = editorBoxes.get(key);
			if (box != null) box.setValue(settings.get(key));
		}
		updatingEditorBoxes = false;
		changed();
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
				settings.get(option.key())).withValues(values)
				.create(x, y, listWidth, 20, text(option.key()), (widget, value) -> {
					settings.put(option.key(), value);
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
		if (isCoordinate(option.key())) {
			body = body.copy().append("\n").append(text("positionEditor.controls"));
		} else if (isSize(option.key())) {
			body = body.copy().append("\n").append(text("positionEditor.sizeControls"));
		}
		return text(option.key()).copy().append("\n").append(body);
	}

	private static boolean isCoordinate(String key) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (key.equals(module.xKey) || key.equals(module.yKey)) return true;
		}
		return false;
	}

	/** Whether the setting is the one a module's resize grips write. */
	private static boolean isSize(String key) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (key.equals(module.sizeKey)) return true;
		}
		return false;
	}

	/** Advanced rows hide; rows belonging to another subpage are not part of this page's view. */
	private boolean shown(ConfigOptions.Option option, String group) {
		if (option.advanced() && !advanced) return false;
		return option.group() == null || option.group().equals(group);
	}

	private void changed() {
		saveError = null;
		dirty = true;
		ConfigOptions.applyIfValid(settings);
	}

	@Override
	public void tick() {
		super.tick();
		flush();
	}

	/**
	 * Writes the edits made since the last write, if the screen holds nothing half-typed.
	 *
	 * <p>Once per tick rather than once per edit, because a dragged slider or a held arrow key
	 * changes a value far faster than a file wants replacing; and again as the screen closes, so
	 * that the last edit is on disk before the settings are out of sight. A set of values with a
	 * half-typed number in it is simply not written yet — the error under Close says why —
	 * and it becomes writable again as soon as the offending box does.
	 *
	 * <p>A failed write is reported and the edit kept: it still applies for this session, and
	 * retrying every tick would only fill the log.
	 */
	private void flush() {
		if (!dirty || ConfigOptions.error(settings) != null) return;
		dirty = false;
		try {
			ConfigStore.save(settings);
		} catch (IOException | RuntimeException e) {
			ElytraVario.LOGGER.error("Could not save Elytra Vario settings", e);
			saveError = "save_error";
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			super.extractBackground(graphics, mouseX, mouseY, delta);
		} else {
			// Editor marks share the HUD's layer. The panel remains above both, so its controls
			// are never obscured when a module lies beneath them.
			drawPositionEditor(graphics, mouseX, mouseY);
			graphics.fill(panelLeft - 4, 0, panelLeft + panelWidth + 4, height, 0xB0101014);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		String error = ConfigOptions.error(settings);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, panelCenter, 10, 0xFFFFFFFF);
		if (error != null || saveError != null) {
			graphics.centeredText(font, text(error != null ? error : saveError), panelCenter, height - 18, 0xFFFF7777);
		}
		if (minecraft.level != null && !overControl(mouseX, mouseY)) {
			ModulePositionEditor.Bounds hovered = moduleAt(mouseX, mouseY, draggingModule);
			if (hovered == null) return;
			int tooltipWidth = Math.max(40, Math.min(240, width - 24));
			graphics.setTooltipForNextFrame(font,
					font.split(text("positionEditor.tooltip",
							text("page." + hovered.module().page)), tooltipWidth), mouseX, mouseY);
		}
	}

	private void drawPositionEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ModulePositionEditor.Bounds hovered = overControl(mouseX, mouseY)
				? null : moduleAt(mouseX, mouseY, draggingModule);
		ModulePositionEditor.Corner grip = hovered == null ? null
				: ModulePositionEditor.grip(hovered, mouseX, mouseY);
		ModulePositionEditor.Module selected = selectedModule();
		for (ModulePositionEditor.Bounds bounds : moduleBounds()) {
			boolean isHovered = hovered != null && bounds.module() == hovered.module();
			boolean isDragging = bounds.module() == draggingModule;
			if (bounds.module() != selected && !isHovered && !isDragging) continue;
			ModulePositionEditor.Corner active = isDragging ? draggingCorner
					: isHovered ? grip : null;
			// The white outline says the module is the thing a drag would pick up and carry, so
			// it goes as soon as the pointer finds a grip and the drag would resize instead.
			// What is left is the quieter outline of the module whose page is open, and the
			// grip itself, which grows to say it is the one that has been found.
			boolean carrying = (isHovered || isDragging) && active == null;
			int color = carrying ? 0xFFFFFFFF : 0xFF66CCFF;
			if (carrying || bounds.module() == selected) {
				graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
			}
			// Grips only on the module the pointer can actually take hold of, so that a
			// module selected from its settings page still reads as a plain outline.
			if (isHovered || isDragging) drawGrips(graphics, bounds, color, active);
		}
		if (draggingModule == null) return;
		drawSnapGuides(graphics);
		// A resize needs no ghost: the module is already drawn at the size the drag is asking
		// for, and the corner it is pinning has not moved.
		if (draggingCorner != null) return;
		for (ModulePositionEditor.Bounds bounds : moduleBounds()) {
			if (bounds.module() != draggingModule) continue;
			int ghostX = Math.clamp((int) Math.round(dragX), 0,
					Math.max(0, width - bounds.width()));
			int ghostY = Math.clamp((int) Math.round(dragY), 0,
					Math.max(0, height - bounds.height()));
			if (ghostX != bounds.x() || ghostY != bounds.y()) {
				graphics.outline(ghostX, ghostY, bounds.width(), bounds.height(), 0xA0FFFFFF);
			}
		}
	}

	/**
	 * Thickened corners, marking where a module can be taken hold of to resize it. The
	 * {@code active} corner is the one the pointer has found, or the one being dragged, and is
	 * drawn longer and thicker than the rest.
	 */
	private static void drawGrips(GuiGraphicsExtractor graphics, ModulePositionEditor.Bounds bounds,
			int color, ModulePositionEditor.Corner active) {
		int right = bounds.x() + bounds.width();
		int bottom = bounds.y() + bounds.height();
		for (ModulePositionEditor.Corner corner : ModulePositionEditor.Corner.values()) {
			boolean grown = corner == active;
			int reach = ModulePositionEditor.gripReach(bounds, grown);
			int thickness = Math.min(grown ? GRIP_THICKNESS + 1 : GRIP_THICKNESS, reach);
			int x = corner.left ? bounds.x() : right - reach;
			int y = corner.top ? bounds.y() : bottom - reach;
			int column = corner.left ? bounds.x() : right - thickness;
			int row = corner.top ? bounds.y() : bottom - thickness;
			graphics.fill(x, row, x + reach, row + thickness, color);
			graphics.fill(column, y, column + thickness, y + reach, color);
		}
	}

	/** Whether the pointer is over a leaf control rather than container background. */
	private boolean overControl(double mouseX, double mouseY) {
		return getChildAt(mouseX, mouseY)
				.map(child -> overControl(child, mouseX, mouseY)).orElse(false);
	}

	private static boolean overControl(GuiEventListener listener, double mouseX, double mouseY) {
		if (listener instanceof ContainerEventHandler container) {
			return container.getChildAt(mouseX, mouseY)
					.map(child -> overControl(child, mouseX, mouseY)).orElse(false);
		}
		return listener instanceof AbstractWidget && listener.isMouseOver(mouseX, mouseY);
	}

	private void drawSnapGuides(GuiGraphicsExtractor graphics) {
		int color = 0xFFFFD866;
		for (ModulePositionEditor.Guide guide : snapVerticalGuides) {
			int x = Math.clamp(guide.coordinate(), 0, Math.max(0, width - 1));
			graphics.fill(x, guide.from(), x + 1, guide.to(), color);
		}
		for (ModulePositionEditor.Guide guide : snapHorizontalGuides) {
			int y = Math.clamp(guide.coordinate(), 0, Math.max(0, height - 1));
			graphics.fill(guide.from(), y, guide.to(), y + 1, color);
		}
	}

	private final class OptionList extends ContainerObjectSelectionList<ConfigRow> {
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
		void append(ConfigRow row) { addEntry(row); }
		@Override public int getRowWidth() { return panelWidth - 24; }
	}

	private abstract class ConfigRow extends ContainerObjectSelectionList.Entry<ConfigRow> { }

	private CycleButton<String> subpageSelector(List<String> groups, String group,
			int x, int y, int width) {
		return CycleButton.<String>builder(id -> text("group." + id), group)
				.withValues(groups)
				.create(x, y, width, 20, text("page." + page + ".group"), (button, value) -> {
					subpages.put(page, groups.indexOf(value));
					rebuildWidgets();
				});
	}

	/** The boundary between settings shared by the page and settings for one selected subpage. */
	private final class SubpageRow extends ConfigRow {
		private final CycleButton<String> control;

		SubpageRow(List<String> groups, String group) {
			control = subpageSelector(groups, group, 0, 0, 180);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float delta) {
			control.setX(getContentX());
			control.setY(getContentY());
			control.setWidth(getContentWidth());
			control.extractRenderState(graphics, mouseX, mouseY, delta);
		}

		@Override public List<? extends GuiEventListener> children() { return List.of(control); }
		@Override public List<? extends NarratableEntry> narratables() { return List.of(control); }
	}

	/**
	 * The page's own keys — an instrument's toggle, or on Global the visibility and settings
	 * keys — rebindable here so that the whole of a page's behavior is in one place rather than split
	 * between this screen and the vanilla Controls list. Every one of these mappings is
	 * registered with the game, so they are all in that list too, and either screen sets them.
	 *
	 * <p>It reads as one more switch beside those above it, and is labeled the way they are,
	 * because that is what it is — but <b>it is not one of this mod's settings</b>, unlike
	 * everything else on this screen: see {@link #bind}. It is written to options.txt rather
	 * than to elytra-vario.json, and Reset leaves it alone.
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

	private final class OptionRow extends ConfigRow {
		private final ConfigOptions.Option option;
		private final AbstractWidget control;

		OptionRow(ConfigOptions.Option option) {
			this.option = option;
			Component label = text(option.key());
			if (option.color()) {
				control = Button.builder(colorLabel(option, settings.get(option.key())), button ->
						minecraft.gui.setScreen(new ColorPickerScreen(option)))
						.bounds(0, 0, 180, 20).build();
			} else if (option.toggle() || option.choices() > 0) {
				control = Button.builder(valueLabel(), button -> {
					String value = settings.get(option.key());
					settings.put(option.key(), option.toggle() ? Boolean.toString(!Boolean.parseBoolean(value))
							: Integer.toString((Integer.parseInt(value) + 1) % option.choices()));
					button.setMessage(valueLabel());
					changed();
				}).bounds(0, 0, 180, 20).build();
			} else {
				EditBox box = new EditBox(font, 0, 0, 180, 20, label);
				box.setMaxLength(32);
				box.setValue(settings.get(option.key()));
				box.setResponder(value -> {
					settings.put(option.key(), value);
					box.setTextColor(valid(value) ? 0xFFE0E0E0 : 0xFFFF7777);
					if (!updatingEditorBoxes) changed();
				});
				box.setTextColor(valid(box.getValue()) ? 0xFFE0E0E0 : 0xFFFF7777);
				if (isCoordinate(option.key()) || isSize(option.key())) {
					editorBoxes.put(option.key(), box);
				}
				control = box;
			}
			control.setTooltip(Tooltip.create(tooltip(option)));
		}

		private boolean valid(String value) {
			try { option.parse(value); return true; }
			catch (RuntimeException e) { return false; }
		}

		private Component valueLabel() {
			return VarioConfigScreen.this.valueLabel(option, settings.get(option.key()));
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

	/** A compact swatch and the exact persisted value, so colors remain easy to compare. */
	private static Component colorLabel(ConfigOptions.Option option, String value) {
		int color = (int) option.parse(value);
		return Component.literal("\u25a0 ").withColor(color & 0xFFFFFF).append(value);
	}

	/**
	 * Channel-based color picker used by every color option. Changes are previewed in the HUD as
	 * the sliders move. Done keeps them; Cancel and Escape restore the value from when the picker
	 * opened. Heatmap colors omit opacity because their schema deliberately requires opaque RGB.
	 */
	private final class ColorPickerScreen extends Screen {
		private static final int PICKER_WIDTH = 280;
		private static final int PREVIEW_HEIGHT = 44;
		private final ConfigOptions.Option option;
		private final String initialValue;
		private int color;
		private int previewLeft;
		private int previewTop;
		private int previewWidth;

		ColorPickerScreen(ConfigOptions.Option option) {
			super(text("colorPicker.title", text(option.key())));
			this.option = option;
			initialValue = settings.get(option.key());
			color = (int) option.parse(initialValue);
		}

		@Override
		protected void init() {
			int pickerWidth = Math.min(PICKER_WIDTH, width - 24);
			int left = (width - pickerWidth) / 2;
			int channels = option.opaque() ? 3 : 4;
			int contentHeight = PREVIEW_HEIGHT + 12 + channels * 24 + 28;
			previewLeft = left;
			previewTop = Math.max(34, (height - contentHeight) / 2);
			previewWidth = pickerWidth;

			int y = previewTop + PREVIEW_HEIGHT + 12;
			addRenderableWidget(new ColorSlider(left, y, pickerWidth, 1));
			addRenderableWidget(new ColorSlider(left, y + 24, pickerWidth, 2));
			addRenderableWidget(new ColorSlider(left, y + 48, pickerWidth, 3));
			if (!option.opaque()) addRenderableWidget(new ColorSlider(left, y + 72, pickerWidth, 0));

			int buttonsY = y + channels * 24 + 4;
			addRenderableWidget(Button.builder(text("colorPicker.done"), button -> finish())
					.bounds(left, buttonsY, pickerWidth / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(text("colorPicker.cancel"), button -> cancel())
					.bounds(left + pickerWidth / 2 + 2, buttonsY, pickerWidth / 2 - 2, 20).build());
		}

		private void setChannel(int channel, int value) {
			int shift = switch (channel) {
				case 0 -> 24;
				case 1 -> 16;
				case 2 -> 8;
				default -> 0;
			};
			color = color & ~(0xFF << shift) | value << shift;
			if (option.opaque()) color |= 0xFF000000;
			settings.put(option.key(), option.format(color));
			changed();
		}

		private void finish() {
			minecraft.gui.setScreen(VarioConfigScreen.this);
		}

		private void cancel() {
			settings.put(option.key(), initialValue);
			changed();
			minecraft.gui.setScreen(VarioConfigScreen.this);
		}

		@Override public void onClose() { cancel(); }

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float delta) {
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			graphics.centeredText(font, title, width / 2, previewTop - 18, 0xFFFFFFFF);
			// A checkerboard makes partial opacity visible rather than merely making the swatch dim.
			for (int y = 0; y < PREVIEW_HEIGHT; y += 8) {
				for (int x = 0; x < previewWidth; x += 8) {
					int checker = ((x / 8 + y / 8) & 1) == 0 ? 0xFFB8B8B8 : 0xFF686868;
					graphics.fill(previewLeft + x, previewTop + y,
							previewLeft + Math.min(x + 8, previewWidth),
							previewTop + Math.min(y + 8, PREVIEW_HEIGHT), checker);
				}
			}
			int previewRight = previewLeft + previewWidth;
			graphics.fill(previewLeft, previewTop, previewRight, previewTop + PREVIEW_HEIGHT, color);
			graphics.outline(previewLeft, previewTop, previewRight - previewLeft, PREVIEW_HEIGHT,
					0xFFFFFFFF);
			graphics.centeredText(font, Component.literal(option.format(color)), width / 2,
					previewTop + (PREVIEW_HEIGHT - font.lineHeight) / 2, ARGB.opaque(contrast(color)));
		}

		/** Black or white text, based on the opaque RGB luminance of the selected color. */
		private int contrast(int color) {
			return ARGB.red(color) * 299 + ARGB.green(color) * 587 + ARGB.blue(color) * 114
					>= 128_000 ? 0x000000 : 0xFFFFFF;
		}

		private final class ColorSlider extends AbstractSliderButton {
			private final int channel;

			ColorSlider(int x, int y, int width, int channel) {
				super(x, y, width, 20, Component.empty(), channel(color, channel) / 255.0);
				this.channel = channel;
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				int amount = Mth.clamp((int) Math.round(value * 255.0), 0, 255);
				setMessage(text("colorPicker.channel." + channel, amount));
			}

			@Override
			protected void applyValue() {
				setChannel(channel, Mth.clamp((int) Math.round(value * 255.0), 0, 255));
			}
		}

		private int channel(int color, int channel) {
			return switch (channel) {
				case 0 -> ARGB.alpha(color);
				case 1 -> ARGB.red(color);
				case 2 -> ARGB.green(color);
				default -> ARGB.blue(color);
			};
		}
	}
}

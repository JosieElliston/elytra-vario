package jealoustone.elytravario.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.ElytraVarioClient;
import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.StatsPanel;
import jealoustone.elytravario.hud.VarioHudElement;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full-screen, in-world editor for moving and resizing HUD modules. */
public final class HudLayoutScreen extends Screen {
	/** How thick a resize grip's arms are drawn, over the outline they thicken. */
	private static final int GRIP_THICKNESS = 2;
	private final Screen parent;
	private final Map<String, String> settings = ConfigOptions.snapshot();
	// Which module you were placing is not a setting, but losing it is felt like one: the screen
	// is opened and closed repeatedly while flying, to move one module and watch the HUD. The
	// selection therefore outlives the screen, and is remembered for the session rather than
	// written to disk, since it says nothing about how the mod should behave.
	/** Remembers which settings section each page had selected, as an index into its own. */
	private static final Map<Integer, Integer> sections = new HashMap<>();
	private static int page;
	/** An edit the next write will persist. */
	private boolean dirty;
	private String saveError;
	private int panelCenter;
	private int toolbarBottom;
	private ModulePositionEditor.Module draggingModule;
	/** The grip being dragged, when the drag is a resize rather than a move. */
	private ModulePositionEditor.Corner draggingCorner;
	/** Immutable geometry at the start of a resize, so rounding cannot feed one frame into the next. */
	private ModulePositionEditor.Bounds resizeOrigin;
	/** The size settings as they stood when the resize began, one per key the module carries. */
	private final Map<String, Double> resizeOriginValues = new HashMap<>();
	/** The unsnapped box requested by the pointer, shared by move and resize rendering. */
	private ModulePositionEditor.Bounds trueDragBounds;
	/** Pointer-driven position before snapping, retained so a snapped module can pull free.
	 * A move drags the module's top-left; a resize drags the grabbed corner. */
	private double dragX;
	private double dragY;
	private List<ModulePositionEditor.Guide> snapVerticalGuides = List.of();
	private List<ModulePositionEditor.Guide> snapHorizontalGuides = List.of();
	private final List<Button> contextButtons = new ArrayList<>();

	public HudLayoutScreen(Screen parent, ModulePositionEditor.Module module) {
		super(text("layout.title"));
		this.parent = parent;
		if (module != null) {
			page = module.page;
			if (module.group != null) {
				sections.put(module.page, ConfigOptions.groups(module.page).indexOf(module.group));
			}
		}
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable("config.elytra-vario." + key, arguments);
	}

	@Override
	protected void init() {
		contextButtons.clear();
		settings.clear();
		settings.putAll(ConfigOptions.snapshot());
		panelCenter = width / 2;
		int buttonWidth = Math.min(180, Math.max(100, width / 4));
		addRenderableWidget(Button.builder(text("layout.settings"), button -> {
			ModulePositionEditor.Module selected = selectedModule();
			openSettings(selected == null ? 0 : selected.page);
		}).bounds(8, 8, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(text("close"), button -> onClose())
				.bounds(width - buttonWidth - 8, 8, buttonWidth, 20).build());
		toolbarBottom = 32;
	}

	/**
	 * Opens the settings on a module's page.
	 *
	 * <p>Reached from the settings, this replaces them rather than stacking a second copy behind
	 * this editor: the two screens are crossed back and forth while a module is being placed, and
	 * a fresh pair each way would pile up without limit. The new copy therefore takes the old
	 * one's place in the chain, so backing out of it leads where backing out of the old one did.
	 */
	private void openSettings(int settingsPage) {
		flush();
		Screen behind = parent instanceof VarioConfigScreen settings ? settings.parentScreen() : this;
		minecraft.gui.setScreen(VarioConfigScreen.create(behind, settingsPage));
	}

	@Override
	public void removed() {
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

	/**
	 * Pulls externally toggled visibility values into the screen without disturbing the geometry
	 * a drag may be part-way through writing.
	 */
	private void syncVisibilitySettings() {
		Map<String, String> live = ConfigOptions.snapshot();
		settings.put("enabled", live.get("enabled"));
		for (VarioInstrument instrument : VarioInstrument.values()) {
			settings.put(instrument.showKey(), live.get(instrument.showKey()));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The context menu is a transient layer over the editor. Consume Escape here before the
		// base screen can close the editor (and return to settings), just like a normal menu does.
		if (!contextButtons.isEmpty() && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			clearContextMenu();
			return true;
		}
		if (draggingModule != null && isShift(event)) {
			applyDrag(false);
			return true;
		}
		// The arrows belong to the module whose settings page is selected, so page selection and
		// module selection cannot disagree. Nothing on this screen types, so nothing wants them.
		int dx = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
		int dy = event.isUp() ? -1 : event.isDown() ? 1 : 0;
		ModulePositionEditor.Module selected = selectedModule();
		if (selected != null && (dx != 0 || dy != 0)) {
			move(selected, dx, dy);
			return true;
		}
		if (super.keyPressed(event)) return true;
		// The key that opened the editor closes it again, the way Escape does — a bind you press
		// to look at the HUD layout is one you press again to get back to flying.
		boolean handled = ElytraVarioClient.toggleVisibilityIfMatches(event);
		handled |= VarioInstrument.toggleMatching(event);
		if (handled) syncVisibilitySettings();
		KeyMapping settingsKey = ElytraVarioClient.settingsKey();
		if (settingsKey != null && settingsKey.matches(event)) {
			onClose();
			return true;
		}
		return handled;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (draggingModule != null && isShift(event)) {
			applyDrag(true);
			return true;
		}
		return super.keyReleased(event);
	}

	private static boolean isShift(KeyEvent event) {
		return event.key() == GLFW.GLFW_KEY_LEFT_SHIFT || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		// Containers may consume their empty background, so use the leaf hit test directly:
		// real controls own their pixels, while modules get first claim everywhere else.
		if (overControl(event.x(), event.y())) return super.mouseClicked(event, doubled);
		if (!contextButtons.isEmpty()) clearContextMenu();
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && minecraft.level != null) {
			ModulePositionEditor.Bounds target = moduleAt(event.x(), event.y(), selectedModule());
			if (target != null) {
				select(target.module());
				openContextMenu(target.module(), (int) event.x(), (int) event.y());
				return true;
			}
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && minecraft.level != null) {
			ModulePositionEditor.Bounds target = moduleAt(
					event.x(), event.y(), selectedModule());
			if (target != null) {
				select(target.module());
				clearFocus();
				draggingModule = target.module();
				draggingCorner = ModulePositionEditor.grip(target, event.x(), event.y());
				resizeOrigin = null;
				trueDragBounds = null;
				if (draggingCorner != null) {
					resizeOriginValues.clear();
					try {
						for (String key : draggingModule.sizeKeys) {
							ConfigOptions.Option size = option(key);
							resizeOriginValues.put(key,
									Double.parseDouble(settings.get(key)) / size.factor());
						}
						resizeOrigin = target;
					} catch (RuntimeException ignored) {
						// A half-typed size cannot normally be reached through a module, but if it is,
						// leave the drag inert instead of inventing an origin for it.
					}
				}
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

	private void openContextMenu(ModulePositionEditor.Module module, int mouseX, int mouseY) {
		clearContextMenu();
		int menuWidth = 150;
		int left = Math.clamp(mouseX, 4, Math.max(4, width - menuWidth - 4));
		int top = Math.clamp(mouseY, toolbarBottom, Math.max(toolbarBottom, height - 68));
		contextButtons.add(addRenderableWidget(Button.builder(text("layout.moduleSettings"),
				button -> openSettings(module.page)).bounds(left, top, menuWidth, 20).build()));
		contextButtons.add(addRenderableWidget(Button.builder(text("layout.exactGeometry"), button -> {
			flush();
			minecraft.gui.setScreen(new ExactGeometryScreen(module));
		}).bounds(left, top + 22, menuWidth, 20).build()));
		contextButtons.add(addRenderableWidget(Button.builder(text("layout.resetGeometry"), button -> {
			Map<String, String> defaults = ConfigOptions.defaults();
			settings.put(module.xKey, defaults.get(module.xKey));
			settings.put(module.yKey, defaults.get(module.yKey));
			for (String key : module.sizeKeys) settings.put(key, defaults.get(key));
			changed();
			clearContextMenu();
		}).bounds(left, top + 44, menuWidth, 20).build()));
	}

	private void clearContextMenu() {
		for (Button button : contextButtons) removeWidget(button);
		contextButtons.clear();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (draggingModule == null) return super.mouseDragged(event, deltaX, deltaY);
		dragX += deltaX;
		dragY += deltaY;
		applyDrag(!minecraft.hasShiftDown());
		return true;
	}

	/** Re-solves the drag from its original pointer position, never its snapped result. */
	private void applyDrag(boolean snapping) {
		int snapDistance = snapping ? VarioConfig.positionSnapDistance : -1;
		if (draggingCorner == null) {
			drag(draggingModule, (int) Math.round(dragX), (int) Math.round(dragY), snapDistance);
		} else {
			resize(draggingModule, draggingCorner, dragX, dragY, snapDistance);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingModule == null) return super.mouseReleased(event);
		draggingModule = null;
		draggingCorner = null;
		resizeOrigin = null;
		trueDragBounds = null;
		VarioHudElement.stretchEnergyField = false;
		snapVerticalGuides = List.of();
		snapHorizontalGuides = List.of();
		return true;
	}

	private List<ModulePositionEditor.Bounds> moduleBounds() {
		// Every path that measures or drags a panel comes through here, so this is where the
		// editor tells the panels the GUI scale their text sizes are quantized against. It is
		// read afresh rather than cached: a resize must see the same scale the HUD is drawing
		// at, and the video settings can change it while this screen is closed.
		StatsPanel.guiScale(minecraft.getWindow().getGuiScale());
		return ModulePositionEditor.bounds(font, width, height);
	}

	private ModulePositionEditor.Bounds moduleAt(double x, double y,
			ModulePositionEditor.Module preferred) {
		return ModulePositionEditor.at(moduleBounds(), x, y, preferred);
	}

	/** The settings section this page has selected. */
	private static String selectedGroup(int page) {
		List<String> groups = ConfigOptions.groups(page);
		return groups.get(Math.min(sections.getOrDefault(page, 0), groups.size() - 1));
	}

	/**
	 * The module the arrow keys move, which is the one whose settings are on screen.
	 *
	 * <p>A page carrying several modules — Flight Stats, one per panel — gives each of them a
	 * section of its own, so the selection follows the section the settings screen last opened.
	 * A page carrying one leaves its module's group null, and it stays selected whichever
	 * section is showing; the speedometers, whose extra sections are one bar or needle each,
	 * are that case.
	 *
	 * <p>Most sections are neither: a page opens on its general section, and the layout editor
	 * is reached from a section without a module as often as from one with it. So a section that
	 * names no module selects the page's first, rather than leaving the arrow keys with nothing
	 * to move.
	 */
	private ModulePositionEditor.Module selectedModule() {
		String group = selectedGroup(page);
		ModulePositionEditor.Module first = null;
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page != page) continue;
			if (module.group == null || module.group.equals(group)) return module;
			if (first == null) first = module;
		}
		return first;
	}

	/** Selects this module, so that the arrow keys and the settings button both follow it. */
	private void select(ModulePositionEditor.Module module) {
		boolean wrongPage = page != module.page;
		boolean wrongSection = module.group != null
				&& !module.group.equals(selectedGroup(module.page));
		if (!wrongPage && !wrongSection) return;
		page = module.page;
		if (wrongSection) {
			sections.put(module.page, ConfigOptions.groups(module.page).indexOf(module.group));
		}
		rebuildWidgets();
	}

	/** A module's name for a tooltip: its page, and its group where it has one. */
	private static Component moduleName(ModulePositionEditor.Module module) {
		Component name = text("page." + module.page);
		return module.group == null ? name
				: name.copy().append(": ").append(text("group." + module.group));
	}

	private void move(ModulePositionEditor.Module module, int dx, int dy) {
		if (!ModulePositionEditor.nudge(module, dx, dy, settings, renderedBounds(module),
				width, height)) {
			return;
		}
		changed();
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

	private void drag(ModulePositionEditor.Module module, int x, int y, int snapDistance) {
		ModulePositionEditor.Bounds moving = null;
		List<ModulePositionEditor.Bounds> bounds = moduleBounds();
		for (ModulePositionEditor.Bounds candidate : bounds) {
			if (candidate.module() == module) moving = candidate;
		}
		if (moving == null) return;
		trueDragBounds = new ModulePositionEditor.Bounds(module,
				Math.clamp(x, 0, Math.max(0, width - moving.width())),
				Math.clamp(y, 0, Math.max(0, height - moving.height())),
				moving.width(), moving.height());
		ModulePositionEditor.Snap snap = ModulePositionEditor.snap(module, x, y,
				moving.width(), moving.height(), bounds, width, height,
				VarioConfig.positionMargin, snapDistance);
		snapVerticalGuides = snap.verticalGuides();
		snapHorizontalGuides = snap.horizontalGuides();
		ModulePositionEditor.Position snapped = snap.position();
		String nextX = Integer.toString(snapped.x());
		String nextY = Integer.toString(snapped.y());
		if (nextX.equals(settings.get(module.xKey)) && nextY.equals(settings.get(module.yKey))) return;
		settings.put(module.xKey, nextX);
		settings.put(module.yKey, nextY);
		changed();
	}

	/**
	 * Scales the module so that the grabbed corner follows the pointer and the corner opposite
	 * it stays where it is.
	 *
	 * <p>Every event is solved from the drag's immutable origin. The live module is then measured
	 * after applying the winning setting, so the pinned corner survives layout rounding without
	 * letting one frame's rounded result perturb the next frame's snap decision.
	 */
	private void resize(ModulePositionEditor.Module module, ModulePositionEditor.Corner corner,
			double pointerX, double pointerY, int snapDistance) {
		if (resizeOrigin == null) return;
		List<ModulePositionEditor.Bounds> bounds = moduleBounds();
		List<ModulePositionEditor.Resize> resizes = new ArrayList<>();
		int trueWidth = resizeOrigin.width();
		int trueHeight = resizeOrigin.height();
		// A module with two settings takes them one at a time. Each is solved against the same
		// immutable origin and answers only the axis it grows. Stats panels establish width first;
		// their text size is then limited to what that width can contain, preventing the
		// content minimum from pushing the horizontal edge away from the pointer.
		//
		// Both of those limits are read from this drag rather than from the panel as it is
		// currently drawn. That is the whole of what keeps the answer a function of where the
		// pointer is: see ModulePositionEditor#narrowestWidth.
		int settledWidth = resizeOrigin.width();
		for (String key : module.sizeKeys) {
			ConfigOptions.Option size = option(key);
			ModulePositionEditor.Growth growth = ModulePositionEditor.growth(key);
			double maximum = size.max() / size.factor();
			double minimum = size.min() / size.factor();
			double step = size.integral() ? 1 : 0;
			if (module.panel != null && key.equals(module.panel.widthKey())) {
				minimum = ModulePositionEditor.narrowestWidth(module.panel, minimum, maximum);
			}
			if (module.panel != null && key.equals(module.panel.textSizeKey())) {
				// The text size counts in the sizes the font is drawn at, so the drag lands on
				// one of those and never between two.
				step = ModulePositionEditor.textSizeStep();
				minimum = Math.max(minimum, ModulePositionEditor.smallestTextSize());
				maximum = ModulePositionEditor.largestTextSize(module.panel, settledWidth,
						minimum, maximum);
			}
			maximum = Math.max(minimum, maximum);
			ModulePositionEditor.Resize resize = ModulePositionEditor.resizeWithMarkers(
					corner, resizeOrigin, resizeOriginValues.get(key),
					new ModulePositionEditor.Sizing(growth, minimum, maximum, step),
					pointerX, pointerY, bounds, width, height,
					VarioConfig.positionMargin, snapDistance);
			resizes.add(resize);
			if (module.panel != null && key.equals(module.panel.widthKey())) {
				settledWidth = (int) Math.round(resize.value());
			}
			if (growth.width() > 0) trueWidth = resize.trueBounds().width();
			if (growth.height() > 0) trueHeight = resize.trueBounds().height();
			String next = size.format(resize.value());
			if (!next.equals(settings.get(key))) {
				settings.put(key, next);
				changed();
			}
		}
		ModulePositionEditor.Position truePosition = ModulePositionEditor.anchored(corner,
				resizeOrigin, trueWidth, trueHeight);
		trueDragBounds = new ModulePositionEditor.Bounds(module,
				Math.clamp(truePosition.x(), 0, Math.max(0, width - trueWidth)),
				Math.clamp(truePosition.y(), 0, Math.max(0, height - trueHeight)),
				trueWidth, trueHeight);
		ModulePositionEditor.Bounds after = renderedBounds(module);
		if (after == null) return;
		ModulePositionEditor.Position position = ModulePositionEditor.anchored(corner, resizeOrigin,
				after.width(), after.height());
		int x = Math.clamp(position.x(), 0, Math.max(0, width - after.width()));
		int y = Math.clamp(position.y(), 0, Math.max(0, height - after.height()));
		String nextX = Integer.toString(x);
		String nextY = Integer.toString(y);
		if (!nextX.equals(settings.get(module.xKey))
				|| !nextY.equals(settings.get(module.yKey))) {
			settings.put(module.xKey, nextX);
			settings.put(module.yKey, nextY);
			changed();
		}
		// Asked of the module as it ended up, so a guide is drawn only where an edge is
		// genuinely on it. The others have not moved, so the rests they offer are unchanged.
		ModulePositionEditor.Bounds landed =
				new ModulePositionEditor.Bounds(module, x, y, after.width(), after.height());
		List<ModulePositionEditor.Guide> vertical = new ArrayList<>();
		List<ModulePositionEditor.Guide> horizontal = new ArrayList<>();
		for (ModulePositionEditor.Resize resize : resizes) {
			ModulePositionEditor.Guides guides =
					ModulePositionEditor.resizeGuides(resize, landed, corner);
			vertical.addAll(guides.vertical());
			horizontal.addAll(guides.horizontal());
		}
		snapVerticalGuides = List.copyOf(vertical);
		snapHorizontalGuides = List.copyOf(horizontal);
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
	 * Writes the edits made since the last write, if the values agree with each other.
	 *
	 * <p>Once per tick rather than once per edit, because a drag or a held arrow key changes a
	 * value far faster than a file wants replacing; and again as the screen closes, so that the
	 * last edit is on disk before the layout is out of sight. A set of values that do not agree
	 * with each other is simply not written yet — the error under Close says why — and it
	 * becomes writable again as soon as they do.
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
			graphics.fill(0, 0, width, toolbarBottom, 0xA0101014);
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
		if (minecraft.level != null && draggingModule == null && !overControl(mouseX, mouseY)) {
			ModulePositionEditor.Bounds hovered = moduleAt(mouseX, mouseY, selectedModule());
			if (hovered == null) return;
			int tooltipWidth = Math.max(40, Math.min(240, width - 24));
			graphics.setTooltipForNextFrame(font,
					font.split(text("positionEditor.tooltip",
							moduleName(hovered.module())), tooltipWidth), mouseX, mouseY);
		}
	}

	private void drawPositionEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ModulePositionEditor.Bounds hovered = overControl(mouseX, mouseY)
				? null : moduleAt(mouseX, mouseY,
						draggingModule == null ? selectedModule() : draggingModule);
		ModulePositionEditor.Corner grip = hovered == null ? null
				: ModulePositionEditor.grip(hovered, mouseX, mouseY);
		for (ModulePositionEditor.Bounds bounds : moduleBounds()) {
			boolean isHovered = hovered != null && bounds.module() == hovered.module();
			boolean isDragging = bounds.module() == draggingModule;
			boolean isSelected = bounds.module() == selectedModule();
			if (!isHovered && !isDragging && !isSelected) {
				graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0x60FFFFFF);
				continue;
			}
			ModulePositionEditor.Corner active = isDragging ? draggingCorner
					: isHovered ? grip : null;
			graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xFFFFFFFF);
			drawGrips(graphics, bounds, active);
		}
		if (draggingModule == null) return;
		drawSnapGuides(graphics);
		if (trueDragBounds == null) return;
		ModulePositionEditor.Bounds rendered = renderedBounds(draggingModule);
		if (rendered != null && !trueDragBounds.equals(rendered)) {
			graphics.outline(trueDragBounds.x(), trueDragBounds.y(), trueDragBounds.width(),
					trueDragBounds.height(), 0xA0FFFFFF);
		}
	}

	/**
	 * Thickened corners, marking where a module can be taken hold of to resize it. The
	 * {@code active} corner is the one the pointer has found, or the one being dragged, and is
	 * drawn longer and thicker. Color and outline stay unchanged, leaving size as the only
	 * indication that the corner has been found.
	 */
	private static void drawGrips(GuiGraphicsExtractor graphics, ModulePositionEditor.Bounds bounds,
			ModulePositionEditor.Corner active) {
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
			graphics.fill(x, row, x + reach, row + thickness, 0xFFFFFFFF);
			graphics.fill(column, y, column + thickness, y + reach, 0xFFFFFFFF);
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
			int x = Math.clamp(guide.strokeCoordinate(), 0, Math.max(0, width - 1));
			graphics.fill(x, guide.from(), x + 1, guide.to(), color);
		}
		for (ModulePositionEditor.Guide guide : snapHorizontalGuides) {
			int y = Math.clamp(guide.strokeCoordinate(), 0, Math.max(0, height - 1));
			graphics.fill(guide.from(), y, guide.to(), y + 1, color);
		}
	}

	/** Exact position and size values, kept out of the ordinary settings screen. */
	private final class ExactGeometryScreen extends Screen {
		private final ModulePositionEditor.Module module;
		private final Map<String, String> opening = new HashMap<>();
		private final List<ConfigOptions.Option> specs = new ArrayList<>();
		private final List<EditBox> boxes = new ArrayList<>();
		private int left;
		private int top;

		ExactGeometryScreen(ModulePositionEditor.Module module) {
			super(text("layout.exactGeometryTitle", moduleName(module)));
			this.module = module;
			List<String> keys = new ArrayList<>();
			keys.add(module.xKey);
			keys.add(module.yKey);
			keys.addAll(module.sizeKeys);
			for (String key : keys) {
				ConfigOptions.Option spec = option(key);
				specs.add(spec);
				opening.put(key, settings.get(key));
			}
		}

		@Override
		protected void init() {
			boxes.clear();
			int contentWidth = Math.min(320, width - 24);
			left = (width - contentWidth) / 2;
			top = Math.max(42, (height - specs.size() * 42 - 48) / 2);
			for (int i = 0; i < specs.size(); i++) {
				ConfigOptions.Option spec = specs.get(i);
				EditBox box = new EditBox(font, left, top + i * 42 + 12, contentWidth, 20,
						text(spec.key()));
				box.setValue(settings.get(spec.key()));
				box.setResponder(value -> {
					String previous = settings.put(spec.key(), value);
					if (ConfigOptions.error(settings) == null) {
						changed();
					} else {
						settings.put(spec.key(), previous);
					}
				});
				boxes.add(addRenderableWidget(box));
			}
			int buttonsY = top + specs.size() * 42 + 4;
			addRenderableWidget(Button.builder(text("colorPicker.done"), button -> finish())
					.bounds(left, buttonsY, contentWidth / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(text("colorPicker.cancel"), button -> cancel())
					.bounds(left + contentWidth / 2 + 2, buttonsY, contentWidth / 2 - 2, 20).build());
		}

		private void finish() {
			flush();
			minecraft.gui.setScreen(HudLayoutScreen.this);
		}

		private void cancel() {
			settings.putAll(opening);
			ConfigOptions.applyIfValid(settings);
			minecraft.gui.setScreen(HudLayoutScreen.this);
		}

		@Override public void onClose() { cancel(); }

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float delta) {
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			graphics.centeredText(font, title, width / 2, top - 24, 0xFFFFFFFF);
			for (int i = 0; i < specs.size(); i++) {
				graphics.text(font, text(specs.get(i).key()), left, top + i * 42, 0xFFFFFFFF);
			}
		}
	}

}

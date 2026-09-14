package jealoustone.elytravario.config;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.StateManager;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.string.StringControllerElement;
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.ElytraVarioClient;
import jealoustone.elytravario.VarioInstrument;
import jealoustone.elytravario.hud.StatsPanel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** YACL-backed settings. All controls preview immediately; Cancel restores the opening values. */
public final class VarioConfigScreen extends YACLScreen {
	/** What this screen goes back to. {@link YACLScreen}'s own copy of it is private. */
	private final Screen parentScreen;
	/** Every setting as this screen has it, which is what Save writes. */
	private final Map<String, String> values;
	private final Map<String, Option<?>> options;
	private final int initialPage;
	private boolean selectedInitialPage;
	private KeyBindController.Element capturingKey;

	private VarioConfigScreen(YetAnotherConfigLib yacl, Screen parent, int initialPage,
			Map<String, String> values, Map<String, Option<?>> options) {
		super(yacl, parent);
		this.parentScreen = parent;
		this.initialPage = initialPage;
		this.values = values;
		this.options = options;
	}

	public static Screen create(Screen parent) {
		return create(parent, 0);
	}

	static Screen create(Screen parent, int initialPage) {
		Map<String, String> values = ConfigOptions.snapshot();
		Map<String, Option<?>> options = new LinkedHashMap<>();
		YetAnotherConfigLib.Builder yacl = YetAnotherConfigLib.createBuilder()
				.title(text("title"))
				.save(() -> save(values));
		for (int page = 0; page < 7; page++) {
			yacl.category(category(page, values, options));
		}
		setAvailability(options);
		return new VarioConfigScreen(yacl.build(), parent, initialPage, values, options);
	}

	/** The screen this one returns to, which the layout editor reopens these settings behind. */
	Screen parentScreen() { return parentScreen; }

	@Override
	protected void init() {
		super.init();
		if (!selectedInitialPage) {
			tabNavigationBar.selectTab(Math.clamp(initialPage, 0, 6), false);
			selectedInitialPage = true;
		}
	}

	private static ConfigCategory category(int page, Map<String, String> values,
			Map<String, Option<?>> options) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder().name(text("page." + page));
		Map<String, OptionGroup.Builder> groups = new LinkedHashMap<>();
		category.option(layoutOption(pageModule(page)));

		if (page == 0) {
			category.option(keyBinding("visibilityKey", ElytraVarioClient.visibilityKey()));
			for (String key : new String[] {"enabled", "hudGlidingOnly"}) {
				Option<?> option = option(spec(key), values);
				options.put(key, option);
				category.option(option);
			}
			category.option(keyBinding("settingsKey", ElytraVarioClient.settingsKey()));
			category.option(ButtonOption.createBuilder()
					.name(text("keyBindings"))
					.text(text("keyBindings.edit"))
					.description(OptionDescription.of(text("keyBindings.tooltip")))
					.action((screen, option) -> {
						Minecraft minecraft = Minecraft.getInstance();
						minecraft.gui.setScreen(new KeyBindsScreen(screen, minecraft.options));
					}).build());
		} else {
			addInstrumentKeyBinding(category, page);
		}
		for (ConfigOptions.Option spec : ConfigOptions.all()) {
			if (spec.page() != page || geometry(spec.key())
					|| page == 0 && (spec.key().equals("enabled")
							|| spec.key().equals("hudGlidingOnly"))) continue;
			Option<?> option = option(spec, values);
			options.put(spec.key(), option);
			if (spec.group() == null) {
				category.option(option);
			} else {
				OptionGroup.Builder group = groups.computeIfAbsent(spec.group(), id -> {
					OptionGroup.Builder builder = OptionGroup.createBuilder()
							.name(text("group." + id)).collapsed(false);
					ModulePositionEditor.Module module = module(page, id);
					if (module != null) builder.option(layoutOption(module));
					return builder;
				});
				group.option(option);
			}
		}
		for (OptionGroup.Builder group : groups.values()) category.group(group.build());
		return category.build();
	}

	private static void addInstrumentKeyBinding(ConfigCategory.Builder category, int page) {
		for (VarioInstrument instrument : VarioInstrument.values()) {
			if (spec(instrument.showKey()).page() == page) {
				category.option(keyBinding("toggleKey", instrument.key()));
				return;
			}
		}
	}

	private static Option<InputConstants.Key> keyBinding(String label, KeyMapping mapping) {
		return Option.<InputConstants.Key>createBuilder()
				.name(text(label))
				.description(OptionDescription.of(text(label + ".tooltip")))
				.available(mapping != null)
				.stateManager(new KeyBindState(mapping))
				.customController(KeyBindController::new)
				.build();
	}

	/**
	 * A key bind's state, which is the game's rather than this screen's.
	 *
	 * <p>A bind is written to options.txt the moment it is set, because the vanilla Controls
	 * screen edits the same mapping: a bind held back until Save would be silently reverted by a
	 * Cancel there. It follows that it is never an unsaved change. Reporting one would leave the
	 * screen dirty over an edit that is already on disk — which disables Edit layout and Escape
	 * — and Cancel would then undo a bind the game has already taken. The value is read back
	 * from the mapping for the same reason: the Controls screen may have moved it since.
	 */
	private static final class KeyBindState implements StateManager<InputConstants.Key> {
		/** Null only if the screen is somehow open before client init registered the keys. */
		private final KeyMapping mapping;
		private StateListener<InputConstants.Key> listener = StateListener.noop();

		KeyBindState(KeyMapping mapping) {
			this.mapping = mapping;
		}

		@Override
		public InputConstants.Key get() {
			return mapping == null ? InputConstants.UNKNOWN
					: InputConstants.getKey(mapping.saveString());
		}

		@Override
		public void set(InputConstants.Key key) {
			if (mapping == null) return;
			InputConstants.Key previous = get();
			if (previous.equals(key)) return;
			mapping.setKey(key);
			KeyMapping.resetMapping();
			Minecraft.getInstance().options.save();
			listener.onStateChange(previous, key);
		}

		@Override public void apply() { }
		@Override public void sync() { }
		@Override public boolean isSynced() { return true; }

		@Override
		public boolean isDefault() {
			return mapping == null || get().equals(mapping.getDefaultKey());
		}

		@Override
		public void resetToDefault(ResetAction action) {
			if (mapping != null) set(mapping.getDefaultKey());
		}

		@Override
		public void addListener(StateListener<InputConstants.Key> listener) {
			this.listener = this.listener.andThen(listener);
		}
	}

	void capture(KeyBindController.Element element) {
		if (capturingKey != null && capturingKey != element) capturingKey.cancelCapture();
		capturingKey = element;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturingKey != null) {
			capturingKey.accept(event.key() == GLFW.GLFW_KEY_ESCAPE
					? InputConstants.UNKNOWN : InputConstants.getKey(event));
			capturingKey = null;
			return true;
		}
		if (super.keyPressed(event)) return true;
		// An instrument's key and this screen's switch for it are the same setting, so the keys
		// keep working while the settings are open and the switch moves to match. The key that
		// opened the settings closes them again, the way Escape does — a bind you press to look
		// at the HUD settings is one you press again to get back to flying.
		//
		// Unlike Escape, they all yield to a field being typed into. A bind is a plain letter by
		// default, and a letter meant for a number field must reach the field; offering the event
		// to the widgets first is not enough on its own, since a field takes its ordinary
		// characters through charTyped and so refuses this event, hence the explicit check.
		if (typing(getFocused())) return false;
		boolean handled = ElytraVarioClient.toggleVisibilityIfMatches(event);
		handled |= VarioInstrument.toggleMatching(event);
		if (handled) adoptVisibility();
		KeyMapping settingsKey = ElytraVarioClient.settingsKey();
		if (settingsKey != null && settingsKey.matches(event)) {
			onClose();
			return true;
		}
		return handled;
	}

	/** Whether the focus path ends in a field that is taking typed input. */
	private static boolean typing(GuiEventListener focused) {
		if (focused instanceof EditBox box) return box.canConsumeInput();
		// YACL's own number and text controls are not EditBoxes, and take their characters
		// through charTyped just the same.
		if (focused instanceof StringControllerElement) return true;
		if (focused instanceof ContainerEventHandler container) return typing(container.getFocused());
		return false;
	}

	/**
	 * Pulls a visibility flipped by its key into the switches that show it.
	 *
	 * <p>The key has already written the file, so none of this is an edit the screen is holding:
	 * the switches move and the screen stays clean.
	 */
	private void adoptVisibility() {
		Map<String, String> live = ConfigOptions.snapshot();
		adopt("enabled", live);
		for (VarioInstrument instrument : VarioInstrument.values()) {
			adopt(instrument.showKey(), live);
		}
	}

	@SuppressWarnings("unchecked")
	private void adopt(String key, Map<String, String> live) {
		Option<?> option = options.get(key);
		// Every visibility setting is a switch, so its state is the boolean one.
		if (option != null) {
			((LivePreviewStateManager<Boolean>) option.stateManager())
					.adopt(Boolean.parseBoolean(live.get(key)));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (capturingKey != null) {
			capturingKey.accept(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			capturingKey = null;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	private static ButtonOption layoutOption(ModulePositionEditor.Module module) {
		return ButtonOption.createBuilder()
				.name(text("layout.title"))
				.text(text("layout.edit"))
				.description(OptionDescription.of(text("layout.tooltip")))
				.available(Minecraft.getInstance().level != null)
				.action((screen, option) -> {
					if (!screen.shouldCloseOnEsc()) return;
					Minecraft.getInstance().gui.setScreen(new HudLayoutScreen(screen, module));
				})
				.build();
	}

	/**
	 * The module a page's own layout button opens: the one the whole page is, or — where the
	 * page is divided into a module per group, as Flight Stats is — the first of them.
	 *
	 * <p>Null for the pages that have no module at all, whose button opens the editor on
	 * whatever it was last showing, there being nothing here to point it at.
	 */
	private static ModulePositionEditor.Module pageModule(int page) {
		ModulePositionEditor.Module first = null;
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page != page) continue;
			if (module.group == null) return module;
			if (first == null) first = module;
		}
		return first;
	}

	private static ModulePositionEditor.Module module(int page, String group) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page == page && group.equals(module.group)) return module;
		}
		return null;
	}

	/**
	 * Keeps every option available only while the settings it depends on are switched on.
	 *
	 * <p>The pass is guarded against itself. Making an option unavailable is an event YACL
	 * delivers even from inside another one, and every option this pass reaches would otherwise
	 * start a pass of its own: unticking the master switch nested some hundred and forty deep,
	 * each level rescanning all of them. One pass answers for the lot, since it reads pending
	 * values and restores any the library reverted, so a nested request is recorded and served
	 * by a single repeat once the running pass is done.
	 */
	private static void setAvailability(Map<String, Option<?>> options) {
		Runnable refresh = new Runnable() {
			private boolean running;
			private boolean requested;

			@Override
			public void run() {
				if (running) {
					requested = true;
					return;
				}
				running = true;
				try {
					do {
						requested = false;
						refreshAvailability(options);
					} while (requested);
				} finally {
					running = false;
				}
			}
		};
		for (Option<?> option : options.values()) {
			option.addEventListener((changed, event) -> refresh.run());
		}
		refresh.run();
	}

	private static void refreshAvailability(Map<String, Option<?>> options) {
		boolean global = pendingBoolean(options, "enabled");
		boolean ladder = pendingBoolean(options, "showLadder");
		boolean markers = pendingBoolean(options, "showLadderMarkers");
		for (ConfigOptions.Option spec : ConfigOptions.all()) {
			Option<?> option = options.get(spec.key());
			if (option == null) continue;
			boolean available;
			if (spec.key().equals("enabled") || spec.key().equals("positionMargin")
					|| spec.key().equals("positionSnapDistance")) {
				available = true;
			} else if (spec.page() == 0) {
				available = global;
			} else if (isInstrumentShowKey(spec.key())) {
				available = global;
			} else if (spec.page() == 1 && sharedLadderSetting(spec.key())) {
				available = global && (ladder || markers);
			} else {
				available = global && pendingBoolean(options, instrumentShowKey(spec.page()));
			}
			available &= localAvailable(spec, options);
			setAvailablePreservingValue(option, available);
		}
	}

	private static boolean isInstrumentShowKey(String key) {
		for (VarioInstrument instrument : VarioInstrument.values()) {
			if (key.equals(instrument.showKey())) return true;
		}
		return false;
	}

	private static String instrumentShowKey(int page) {
		for (VarioInstrument instrument : VarioInstrument.values()) {
			if (spec(instrument.showKey()).page() == page) return instrument.showKey();
		}
		throw new IllegalArgumentException("No instrument for page " + page);
	}

	private static <T> void setAvailablePreservingValue(Option<T> option, boolean available) {
		if (option.available() == available) return;
		T pending = option.pendingValue();
		option.setAvailable(available);
		if (!available && !pending.equals(option.pendingValue())) option.requestSet(pending);
	}

	private static boolean sharedLadderSetting(String key) {
		return key.equals("ladderCenterGap") || key.equals("ladderBandFractionUp")
				|| key.equals("ladderBandFractionDown") || key.equals("ladderFadeFraction");
	}

	private static boolean localAvailable(ConfigOptions.Option spec, Map<String, Option<?>> options) {
		String key = spec.key();
		String parent = switch (key) {
			case "ladderFineLength", "ladderFineStepDegrees", "ladderFineRangeDegrees" -> "showFineTicks";
			case "lookaheadPitchColor", "lookaheadTicks" -> "showLookaheadPitch";
			case "holdPitchColor" -> "showHoldPitch";
			case "optimalPitchColor" -> "showOptimalPitch";
			case "maxHorizontalSpeedPitchColor" -> "showMaxHorizontalSpeedPitch";
			case "minimumFallSpeedPitchColor" -> "showMinimumFallSpeedPitch";
			case "zeroPitchColor" -> "showZeroPitch";
			case "flightPathColor" -> "showFlightPath";
			case "chartTrailTicks", "trailColor" -> "showTrail";
			case "chartFieldZeroColor", "chartFieldGainColor", "chartFieldLossColor", "chartFieldScale" -> "showEnergyField";
			case "cursorXzColor" -> "showHorizontalCursor";
			case "horizontalAccelerationArrowColor" -> "showHorizontalAccelerationArrow";
			case "cursorForwardColor" -> "showForwardCursor";
			case "forwardAccelerationArrowColor" -> "showForwardAccelerationArrow";
			case "barSpeedoTotalColor" -> "showBarSpeedoTotal";
			case "barSpeedoHorizontalColor" -> "showBarSpeedoHorizontal";
			case "barSpeedoVerticalColor" -> "showBarSpeedoVertical";
			case "dialSpeedoTotalColor" -> "showDialSpeedoTotal";
			case "dialSpeedoHorizontalColor" -> "showDialSpeedoHorizontal";
			case "dialSpeedoVerticalColor" -> "showDialSpeedoVertical";
			default -> null;
		};
		if (parent != null && !pendingBoolean(options, parent)) return false;
		// A panel's own group names it, which is cheaper and plainer than searching the schema
		// for the options that share it.
		StatsPanel panel = spec.group() == null ? null : StatsPanel.byGroup(spec.group());
		if (panel != null && !key.equals(panel.showKey())) {
			if (!pendingBoolean(options, panel.showKey())) return false;
			boolean hasRows = panel.rowKeys().stream()
					.anyMatch(row -> pendingBoolean(options, row));
			if ((key.equals(panel.opacityKey()) || key.equals(panel.borderKey())) && !hasRows) {
				return false;
			}
			if (key.equals("energyReference")
					&& !pendingBoolean(options, "showPotentialEnergy")
					&& !pendingBoolean(options, "showTotalEnergy")) return false;
			return true;
		}
		if (speedometerCommonEffect(key, "barSpeedo")) {
			return pendingBoolean(options, "showBarSpeedoTotal")
					|| pendingBoolean(options, "showBarSpeedoHorizontal")
					|| pendingBoolean(options, "showBarSpeedoVertical");
		}
		if (speedometerCommonEffect(key, "dialSpeedo")) {
			return pendingBoolean(options, "showDialSpeedoTotal")
					|| pendingBoolean(options, "showDialSpeedoHorizontal")
					|| pendingBoolean(options, "showDialSpeedoVertical");
		}
		return true;
	}

	private static boolean speedometerCommonEffect(String key, String prefix) {
		return key.equals("show" + capitalize(prefix) + "Acceleration")
				|| key.equals("show" + capitalize(prefix) + "MaxHorizontalSpeedMarkers")
				|| key.equals("show" + capitalize(prefix) + "TerminalVelocityMarkers")
				|| key.equals(prefix + "PeggedColor");
	}

	private static String capitalize(String value) {
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static boolean pendingBoolean(Map<String, Option<?>> options, String key) {
		Option<?> option = options.get(key);
		return option == null || Boolean.TRUE.equals(option.pendingValue());
	}

	private static ConfigOptions.Option spec(String key) {
		for (ConfigOptions.Option option : ConfigOptions.all()) {
			if (key.equals(option.key())) return option;
		}
		throw new IllegalArgumentException(key);
	}

	private static Option<?> option(ConfigOptions.Option spec, Map<String, String> values) {
		if (spec.toggle()) return Option.<Boolean>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(new LivePreviewStateManager<>(
						(Boolean) spec.parse(spec.defaultValue()),
						() -> Boolean.parseBoolean(values.get(spec.key())),
						value -> set(spec, values, value)))
				.controller(TickBoxControllerBuilder::create).build();

		if (spec.color()) return Option.<Color>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(new LivePreviewStateManager<>(
						new Color((Integer) spec.parse(spec.defaultValue()), true),
						() -> new Color((Integer) spec.parse(values.get(spec.key())), true),
						value -> set(spec, values, value.getRGB())))
				.controller(option -> ColorControllerBuilder.create(option)
						.allowAlpha(!spec.opaque())).build();

		if (spec.choices() > 0) {
			List<Integer> choices = new ArrayList<>();
			for (int i = 0; i < spec.choices(); i++) choices.add(i);
			return Option.<Integer>createBuilder()
					.name(text(spec.key())).description(description(spec))
					.stateManager(new LivePreviewStateManager<>(
							(Integer) spec.parse(spec.defaultValue()),
							() -> (Integer) spec.parse(values.get(spec.key())),
							value -> set(spec, values, value)))
					.controller(option -> CyclingListControllerBuilder.create(option)
							.values(choices).formatValue(value -> text(spec.key() + "." + value)))
					.build();
		}

		if (spec.integral() && spec.factor() == 1) return Option.<Integer>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(new LivePreviewStateManager<>(
						Integer.parseInt(spec.defaultValue()),
						() -> Integer.parseInt(values.get(spec.key())),
						value -> setDisplayed(spec, values, value.doubleValue())))
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range((int) spec.min(), (int) spec.max()).step(1)).build();

		double defaultValue = Double.parseDouble(spec.defaultValue());
		if (exactField(spec.key())) return Option.<Double>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(new LivePreviewStateManager<>(defaultValue,
						() -> Double.parseDouble(values.get(spec.key())),
						value -> setDisplayed(spec, values, value)))
				.controller(option -> DoubleFieldControllerBuilder.create(option)
						.range(spec.min(), spec.max())).build();

		return Option.<Double>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(new LivePreviewStateManager<>(defaultValue,
						() -> Double.parseDouble(values.get(spec.key())),
						value -> setDisplayed(spec, values, value)))
				.controller(option -> DoubleSliderControllerBuilder.create(option)
						.range(spec.min(), spec.max()).step(step(spec))).build();
	}

	private static OptionDescription description(ConfigOptions.Option spec) {
		return OptionDescription.of(text(spec.key() + ".tooltip"));
	}

	private static void setDisplayed(ConfigOptions.Option spec, Map<String, String> values,
			double displayed) {
		set(spec, values, spec.parse(Double.toString(displayed)));
	}

	/**
	 * Records what a control now says, and previews it where the whole set is consistent.
	 *
	 * <p>The value is kept whether or not it can be applied, so that what is on screen is what
	 * Save will write. A pair of values can disagree — an axis maximum typed below its minimum —
	 * without either control being wrong on its own, and dropping the number just typed would
	 * leave the screen showing one value and saving another. The HUD keeps the last consistent
	 * settings until the disagreement is resolved, and {@link #finishOrSave} says so.
	 */
	private static void set(ConfigOptions.Option spec, Map<String, String> values, Object value) {
		values.put(spec.key(), spec.format(value));
		ConfigOptions.applyIfValid(values);
	}

	/**
	 * Refuses to save a set of values no config file may hold, naming the reason on the button.
	 *
	 * <p>Geometry is merged first because the layout editor owns it, and because the checks
	 * span both: the graph's pixel size is the editor's and the speeds it covers are this
	 * screen's.
	 */
	@Override
	public void finishOrSave() {
		mergeGeometry(values, ConfigOptions.snapshot());
		String error = ConfigOptions.error(values);
		if (error != null) {
			setSaveButtonMessage(text("saveBlocked").copy().withStyle(ChatFormatting.RED),
					text(error));
			return;
		}
		super.finishOrSave();
	}

	private static double step(ConfigOptions.Option spec) {
		if (spec.factor() == 0.05) return 0.05;
		if (spec.max() <= 1) return 0.01;
		if (spec.key().equals("ladderFineRangeDegrees")) return 0.5;
		return 1;
	}

	private static boolean exactField(String key) {
		return key.equals("chartMinVxz") || key.equals("chartMaxVxz")
				|| key.equals("chartMinVy") || key.equals("chartMaxVy")
				|| key.equals("chartFieldScale");
	}

	static boolean geometry(String key) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (key.equals(module.xKey) || key.equals(module.yKey) || module.sizeKeys.contains(key)) {
				return true;
			}
		}
		return false;
	}

	private static void mergeGeometry(Map<String, String> destination,
			Map<String, String> source) {
		for (ConfigOptions.Option option : ConfigOptions.all()) {
			if (geometry(option.key())) destination.put(option.key(), source.get(option.key()));
		}
	}

	private static void save(Map<String, String> values) {
		try {
			ConfigStore.save(values);
		} catch (IOException | RuntimeException e) {
			ElytraVario.LOGGER.error("Could not save Elytra Vario settings", e);
		}
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable("config.elytra-vario." + key, arguments);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) super.extractBackground(graphics, mouseX, mouseY, delta);
	}
}

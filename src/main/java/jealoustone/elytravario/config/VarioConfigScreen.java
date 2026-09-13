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
import jealoustone.elytravario.ElytraVario;
import jealoustone.elytravario.ElytraVarioClient;
import jealoustone.elytravario.VarioInstrument;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** YACL-backed settings. All controls preview immediately; Cancel restores the opening values. */
public final class VarioConfigScreen extends YACLScreen {
	private final int initialPage;
	private boolean selectedInitialPage;
	private KeyBindController.Element capturingKey;

	private VarioConfigScreen(YetAnotherConfigLib yacl, Screen parent, int initialPage) {
		super(yacl, parent);
		this.initialPage = initialPage;
	}

	public static Screen create(Screen parent) {
		return create(parent, 0);
	}

	static Screen create(Screen parent, int initialPage) {
		Map<String, String> values = ConfigOptions.snapshot();
		Map<String, Option<?>> options = new LinkedHashMap<>();
		YetAnotherConfigLib.Builder yacl = YetAnotherConfigLib.createBuilder()
				.title(text("title"))
				.save(() -> {
					mergeGeometry(values, ConfigOptions.snapshot());
					save(values);
				});
		for (int page = 0; page < 7; page++) {
			yacl.category(category(page, values, options));
		}
		setAvailability(options);
		return new VarioConfigScreen(yacl.build(), parent, initialPage);
	}

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
		addKeyBindings(category, page);

		if (page == 0) {
			category.option(ButtonOption.createBuilder()
					.name(text("keyBindings"))
					.text(text("keyBindings.edit"))
					.description(OptionDescription.of(text("keyBindings.tooltip")))
					.action((screen, option) -> {
						Minecraft minecraft = Minecraft.getInstance();
						minecraft.setScreen(new KeyBindsScreen(screen, minecraft.options));
					}).build());
		}
		for (ConfigOptions.Option spec : ConfigOptions.all()) {
			if (spec.page() != page || geometry(spec.key())) continue;
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

	private static void addKeyBindings(ConfigCategory.Builder category, int page) {
		if (page == 0) {
			category.option(keyBinding("visibilityKey", ElytraVarioClient.visibilityKey()));
			category.option(keyBinding("settingsKey", ElytraVarioClient.settingsKey()));
			return;
		}
		for (VarioInstrument instrument : VarioInstrument.values()) {
			if (spec(instrument.showKey()).page() == page) {
				category.option(keyBinding("toggleKey", instrument.key()));
				return;
			}
		}
	}

	private static Option<InputConstants.Key> keyBinding(String label, KeyMapping mapping) {
		InputConstants.Key current = mapping == null
				? InputConstants.UNKNOWN : InputConstants.getKey(mapping.saveString());
		InputConstants.Key defaultKey = mapping == null ? InputConstants.UNKNOWN : mapping.getDefaultKey();
		return Option.<InputConstants.Key>createBuilder()
				.name(text(label))
				.description(OptionDescription.of(text(label + ".tooltip")))
				.available(mapping != null)
				.stateManager(StateManager.createInstant(defaultKey, () -> current,
						key -> bind(mapping, key)))
				.customController(KeyBindController::new)
				.build();
	}

	private static void bind(KeyMapping mapping, InputConstants.Key key) {
		if (mapping == null) return;
		mapping.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
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
		return super.keyPressed(event);
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

	private static ModulePositionEditor.Module pageModule(int page) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page == page && module.group == null) return module;
		}
		return null;
	}

	private static ModulePositionEditor.Module module(int page, String group) {
		for (ModulePositionEditor.Module module : ModulePositionEditor.Module.values()) {
			if (module.page == page && group.equals(module.group)) return module;
		}
		return null;
	}

	private static void setAvailability(Map<String, Option<?>> options) {
		Runnable refresh = () -> refreshAvailability(options);
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
			available &= localAvailable(spec.key(), options);
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

	private static boolean localAvailable(String key, Map<String, Option<?>> options) {
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
		for (jealoustone.elytravario.hud.StatsPanel panel
				: jealoustone.elytravario.hud.StatsPanel.values()) {
			if (!key.equals(panel.showKey()) && !geometry(key)
					&& ConfigOptions.all().stream().anyMatch(spec -> spec.key().equals(key)
							&& panel.group().equals(spec.group()))) {
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
				.stateManager(StateManager.createInstant(
						(Boolean) spec.parse(spec.defaultValue()),
						() -> Boolean.parseBoolean(values.get(spec.key())),
						value -> set(spec, values, value)))
				.controller(TickBoxControllerBuilder::create).build();

		if (spec.color()) return Option.<Color>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(StateManager.createInstant(
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
					.stateManager(StateManager.createInstant(
							(Integer) spec.parse(spec.defaultValue()),
							() -> (Integer) spec.parse(values.get(spec.key())),
							value -> set(spec, values, value)))
					.controller(option -> CyclingListControllerBuilder.create(option)
							.values(choices).formatValue(value -> text(spec.key() + "." + value)))
					.build();
		}

		if (spec.integral() && spec.factor() == 1) return Option.<Integer>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(StateManager.createInstant(
						Integer.parseInt(spec.defaultValue()),
						() -> Integer.parseInt(values.get(spec.key())),
						value -> setDisplayed(spec, values, value.doubleValue())))
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range((int) spec.min(), (int) spec.max()).step(1)).build();

		double defaultValue = Double.parseDouble(spec.defaultValue());
		if (exactField(spec.key())) return Option.<Double>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(StateManager.createInstant(defaultValue,
						() -> Double.parseDouble(values.get(spec.key())),
						value -> setDisplayed(spec, values, value)))
				.controller(option -> DoubleFieldControllerBuilder.create(option)
						.range(spec.min(), spec.max())).build();

		return Option.<Double>createBuilder()
				.name(text(spec.key())).description(description(spec))
				.stateManager(StateManager.createInstant(defaultValue,
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

	private static void set(ConfigOptions.Option spec, Map<String, String> values, Object value) {
		String previous = values.put(spec.key(), spec.format(value));
		if (ConfigOptions.error(values) == null) {
			ConfigOptions.apply(values);
		} else {
			values.put(spec.key(), previous);
		}
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
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) super.renderBackground(graphics, mouseX, mouseY, delta);
	}
}

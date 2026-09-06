package jealoustone.elytravario;

import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.mojang.blaze3d.platform.InputConstants;

import jealoustone.elytravario.config.ConfigOptions;
import jealoustone.elytravario.config.ConfigStore;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;

/**
 * The instruments that can be shown or hidden as a whole, each with a key that toggles it.
 *
 * <p>The markers on the ladder are one instrument here rather than seven, matching the settings
 * screen: the markers page has a single switch above its per-marker subpages, and a key per bug
 * or reference would be seven binds for a set that is read as one overlay.
 *
 * <p>The key flips exactly the setting the screen's own switch flips, and saves. That it writes
 * the config file rather than holding a runtime override is what makes it a toggle rather than a
 * mode: there is one place the answer lives, the settings screen shows what the key did, and
 * nothing has to decide what a keypress means for an instrument that was already off. Persisting
 * is also the honest reading of the action — a HUD you switched off is one you want to stay off.
 */
public enum VarioInstrument {
	LADDER("ladder", "showLadder", "ladderGlidingOnly",
			() -> VarioConfig.showLadder, () -> VarioConfig.ladderGlidingOnly),
	MARKERS("markers", "showMarkers", "markersGlidingOnly",
			() -> VarioConfig.showMarkers, () -> VarioConfig.markersGlidingOnly),
	CHART("chart", "showChart", "chartGlidingOnly",
			() -> VarioConfig.showChart, () -> VarioConfig.chartGlidingOnly),
	STATS("stats", "showStats", "statsGlidingOnly",
			() -> VarioConfig.showStats, () -> VarioConfig.statsGlidingOnly),
	BAR_SPEEDOMETER("bar_speedometer", "showBarSpeedo", "barSpeedoGlidingOnly",
			() -> VarioConfig.showBarSpeedo, () -> VarioConfig.barSpeedoGlidingOnly),
	DIAL_SPEEDOMETER("dial_speedometer", "showDialSpeedo", "dialSpeedoGlidingOnly",
			() -> VarioConfig.showDialSpeedo, () -> VarioConfig.dialSpeedoGlidingOnly);

	private final String id;
	/**
	 * The config keys of this instrument's two visibility settings. The first is what the key
	 * toggles and what the settings screen puts above the rest of the page; the second is where
	 * the screen anchors the rebinding row. Each names the field the matching supplier reads,
	 * which nothing checks, so the pairs are declared a line apart.
	 */
	private final String showKey;
	private final String glidingOnlyKey;
	private final BooleanSupplier shown;
	private final BooleanSupplier glidingOnly;
	private KeyMapping key;

	VarioInstrument(String id, String showKey, String glidingOnlyKey,
			BooleanSupplier shown, BooleanSupplier glidingOnly) {
		this.id = id;
		this.showKey = showKey;
		this.glidingOnlyKey = glidingOnlyKey;
		this.shown = shown;
		this.glidingOnly = glidingOnly;
	}

	/**
	 * All instruments, unbound by default. Unbound because default binds are keys taken off a
	 * keyboard that already has a mod key on it, for an action most flights never need; the
	 * settings screen puts the binding control on the page of the instrument it toggles, so
	 * anyone who does want one finds it beside the switch it flips.
	 */
	public static void registerAll(KeyMapping.Category category) {
		for (VarioInstrument instrument : values()) {
			instrument.key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
					"key.elytra-vario.toggle." + instrument.id,
					InputConstants.Type.KEYSYM,
					InputConstants.UNKNOWN.getValue(),
					category));
		}
	}

	/** Drains every toggle's queued presses. Does nothing before {@link #registerAll} has run. */
	public static void tickAll() {
		for (VarioInstrument instrument : values()) {
			if (instrument.key == null) continue;
			while (instrument.key.consumeClick()) instrument.toggle();
		}
	}

	/** The instrument either of whose visibility settings has this key, or null for the rest. */
	public static VarioInstrument byOptionKey(String key) {
		for (VarioInstrument instrument : values()) {
			if (instrument.showKey.equals(key) || instrument.glidingOnlyKey.equals(key)) return instrument;
		}
		return null;
	}

	/** The two settings the config screen shows above the rest of this instrument's page. */
	public String showKey() { return showKey; }

	public String glidingOnlyKey() { return glidingOnlyKey; }

	/** Null until {@link #registerAll} has run, which is once, during client init. */
	public KeyMapping key() { return key; }

	public boolean visible(boolean gliding) {
		return VarioConfig.visible(shown.getAsBoolean(), glidingOnly.getAsBoolean(), gliding);
	}

	/**
	 * Flips the instrument on or off and saves.
	 *
	 * <p>It goes through the settings schema rather than assigning the field, so that the write
	 * is validated and the file is replaced by exactly the code the settings screen uses. Reading a
	 * whole snapshot to change one entry is a keypress's worth of work, and it is what makes the
	 * saved file agree with the rest of the live settings rather than with whatever was on disk.
	 *
	 * <p>A failed write is logged and left: the toggle still applies for this session, which is
	 * the part the key was pressed for.
	 */
	private void toggle() {
		Map<String, String> values = ConfigOptions.snapshot();
		values.put(showKey, Boolean.toString(!shown.getAsBoolean()));
		ConfigOptions.apply(values);
		try {
			ConfigStore.save(values);
		} catch (IOException | RuntimeException e) {
			ElytraVario.LOGGER.warn("Could not save the {} toggle; it applies for this session only", id, e);
		}
	}
}

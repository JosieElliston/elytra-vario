package jealoustone.elytravario;

import java.util.function.IntSupplier;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;

/**
 * The five instruments that can be shown or hidden as a whole, each with a key that toggles it.
 *
 * <p>The bugs on the ladder are one instrument here rather than five, matching the settings
 * screen: the markers page has a single visibility switch above its per-marker subpages, and a
 * key per bug would be five binds for a set that is read as one overlay.
 *
 * <p><b>The toggle is a suppression on top of the configured visibility, not a change to it.</b>
 * Every visibility setting is a saved config value that the settings screen edits through a
 * draft; a key that wrote into that draft would mean a mid-flight keypress could be persisted by
 * a later Save of an unrelated color change, and would need somewhere to remember whether the
 * instrument had been on <em>always</em> or only <em>while gliding</em> before it was hidden.
 * A separate runtime flag has neither problem, at the price of one honest limitation: an
 * instrument whose configured visibility is already Hidden cannot be keyed back into view.
 *
 * <p>Being runtime-only also means the toggles are forgotten on quit, which is the behavior a
 * momentary declutter key wants: what comes back at launch is what the settings screen says.
 */
public enum VarioInstrument {
	LADDER("ladder", "ladderVisibility", () -> VarioConfig.ladderVisibility),
	MARKERS("markers", "markerVisibility", () -> VarioConfig.markerVisibility),
	CHART("chart", "chartVisibility", () -> VarioConfig.chartVisibility),
	STATS("stats", "statsVisibility", () -> VarioConfig.statsVisibility),
	SPEEDOMETER("speedometer", "speedoVisibility", () -> VarioConfig.speedoVisibility);

	private final String id;
	/**
	 * The config key of this instrument's visibility setting, which is how the settings screen
	 * finds the row to put the rebinding control under. It names the same field the supplier
	 * reads; nothing checks that, so the two are declared on one line each.
	 */
	private final String visibilityKey;
	private final IntSupplier visibility;
	private KeyMapping key;
	private boolean hidden;

	VarioInstrument(String id, String visibilityKey, IntSupplier visibility) {
		this.id = id;
		this.visibilityKey = visibilityKey;
		this.visibility = visibility;
	}

	/**
	 * All five, unbound by default. Unbound because five default binds is five keys taken off a
	 * keyboard that already has a mod key on it, for an action most flights never need; the
	 * settings screen puts the binding control in front of anyone who does want them.
	 */
	public static void registerAll(KeyMapping.Category category) {
		for (VarioInstrument instrument : values()) {
			instrument.key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
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
			while (instrument.key.consumeClick()) instrument.hidden = !instrument.hidden;
		}
	}

	/** The instrument whose visibility setting has this key, or null when it governs nothing. */
	public static VarioInstrument byVisibilityKey(String key) {
		for (VarioInstrument instrument : values()) {
			if (instrument.visibilityKey.equals(key)) return instrument;
		}
		return null;
	}

	public String id() { return id; }

	/** Null until {@link #registerAll} has run, which is once, during client init. */
	public KeyMapping key() { return key; }

	/** The configured visibility, with the toggle key's suppression applied. */
	public boolean visible(boolean gliding) {
		return !hidden && VarioConfig.visible(visibility.getAsInt(), gliding);
	}
}

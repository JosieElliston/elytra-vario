package jealoustone.elytravario;

import com.mojang.blaze3d.platform.InputConstants;

import jealoustone.elytravario.flight.FlightRecorder;
import jealoustone.elytravario.hud.PitchLadderElement;
import jealoustone.elytravario.hud.VarioHudElement;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;

import org.lwjgl.glfw.GLFW;

public class ElytraVarioClient implements ClientModInitializer {
	public static final FlightRecorder RECORDER = new FlightRecorder();

	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(ElytraVario.id("general"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.elytra-vario.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				VarioConfig.enabled = !VarioConfig.enabled;
			}

			LocalPlayer player = client.player;

			if (player == null) {
				// Altitude is measured from an arbitrary origin, so history from a previous
				// world would read as a huge bogus energy change.
				RECORDER.clear();
			} else {
				RECORDER.tick(player);
			}
		});

		// Attached before chat so chat messages draw over the readouts rather than under them.
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				ElytraVario.id("vario"),
				new VarioHudElement(RECORDER));

		// A separate element because it lives in a different place on screen and is governed
		// by its own toggle, even though both share the master switch and the recorder.
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				ElytraVario.id("pitch_ladder"),
				new PitchLadderElement(RECORDER));

		ElytraVario.LOGGER.info("Elytra Vario initialised");
	}
}

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
			} else if (!client.isPaused()) {
				// Client ticks keep firing while the game is paused — Minecraft.tick() is
				// called with no pause guard, and only the level's entities stop. The player
				// then does not move, so every tick spent in the menu would record a sample
				// with zero velocity, filling the ring buffer with a standstill that never
				// happened and dragging the rate readouts down with it.
				//
				// The buffer is held rather than cleared, so unpausing resumes from the sample
				// before the pause and the first velocity after it is one honest tick of
				// movement.
				//
				// isPaused only means the local integrated server is stopped, so this leaves
				// multiplayer and the tick commands alone: under /tick rate or /tick freeze the
				// world really is still running and the player really can still move.
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

		ElytraVario.LOGGER.info("Elytra Vario initialized");
	}
}

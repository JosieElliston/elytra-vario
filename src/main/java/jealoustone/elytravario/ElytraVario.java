package jealoustone.elytravario;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the mod. The actual entrypoint is {@link ElytraVarioClient};
 * this mod is client-only and has no server-side behaviour.
 */
public final class ElytraVario {
	public static final String MOD_ID = "elytra-vario";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ElytraVario() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

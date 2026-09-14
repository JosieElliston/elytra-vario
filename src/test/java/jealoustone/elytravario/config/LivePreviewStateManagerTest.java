package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.isxander.yacl3.api.StateManager;
import org.junit.jupiter.api.Test;

class LivePreviewStateManagerTest {
	@Test void resettingAfterSaveIsDirtyAgainstTheNewlySavedValue() {
		AtomicBoolean preview = new AtomicBoolean(true);
		LivePreviewStateManager<Boolean> state = new LivePreviewStateManager<>(true,
				preview::get, preview::set);

		state.set(false);
		state.apply();
		assertTrue(state.isSynced());

		state.resetToDefault(StateManager.ResetAction.BY_OPTION);
		assertFalse(state.isSynced());
		assertTrue(preview.get());
	}

	@Test void syncRestoresTheMostRecentlySavedPreview() {
		AtomicBoolean preview = new AtomicBoolean(true);
		LivePreviewStateManager<Boolean> state = new LivePreviewStateManager<>(true,
				preview::get, preview::set);

		state.set(false);
		state.apply();
		state.set(true);
		state.sync();

		assertTrue(state.isSynced());
		assertFalse(preview.get());
	}
}

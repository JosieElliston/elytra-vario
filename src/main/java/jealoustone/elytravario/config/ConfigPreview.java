package jealoustone.elytravario.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** An editing session: valid drafts preview immediately; confirmed discard restores the save point. */
final class ConfigPreview {
	private final Map<String, String> draft = ConfigOptions.snapshot();
	private Map<String, String> saved = new LinkedHashMap<>(draft);

	Map<String, String> draft() { return draft; }

	boolean hasUnsavedChanges() { return !draft.equals(saved); }

	void preview() {
		// Incomplete numeric input leaves the last valid preview on screen.
		if (ConfigOptions.error(draft) == null) ConfigOptions.apply(draft);
	}

	/** Called only after writing the file successfully. */
	void markSaved() {
		ConfigOptions.apply(draft);
		saved = new LinkedHashMap<>(draft);
	}

	void restore() { ConfigOptions.apply(saved); }
}

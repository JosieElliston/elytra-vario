package jealoustone.elytravario.config;

import static org.junit.jupiter.api.Assertions.*;
import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class ConfigPreviewTest {
	@Test void unsavedChangesIncludeInvalidInputAndClearAtTheSavePoint() {
		var original = ConfigOptions.snapshot();
		try {
			var preview = new ConfigPreview();
			assertFalse(preview.hasUnsavedChanges());
			String initial = preview.draft().get("chartMinVxz");
			preview.draft().put("chartMinVxz", "-");
			preview.preview();
			assertTrue(preview.hasUnsavedChanges());
			assertEquals(original, ConfigOptions.snapshot());
			preview.draft().put("chartMinVxz", initial);
			assertFalse(preview.hasUnsavedChanges());
			preview.draft().put("chartMinVxz", "-20");
			preview.preview();
			assertTrue(preview.hasUnsavedChanges());
			assertEquals(-1.0, VarioConfig.chartMinVxz);
			preview.markSaved();
			assertFalse(preview.hasUnsavedChanges());
			preview.draft().put("chartMinVxz", "-30");
			assertTrue(preview.hasUnsavedChanges());
		} finally { ConfigOptions.apply(original); }
	}

	@Test void editsPreviewAndClosingRestoresTheOpeningState() {
		var original = ConfigOptions.snapshot();
		try {
			var preview = new ConfigPreview();
			preview.draft().put("chartMinVxz", "-20");
			preview.preview();
			assertEquals(-1.0, VarioConfig.chartMinVxz);
			preview.restore();
			assertEquals(original, ConfigOptions.snapshot());
		} finally { ConfigOptions.apply(original); }
	}

	@Test void closingAfterAnotherEditKeepsTheMostRecentSave() {
		var original = ConfigOptions.snapshot();
		try {
			var preview = new ConfigPreview();
			preview.draft().put("chartMinVxz", "-20");
			preview.preview();
			preview.markSaved();
			preview.draft().put("chartMinVxz", "-30");
			preview.preview();
			assertEquals(-1.5, VarioConfig.chartMinVxz);
			preview.restore();
			assertEquals(-1.0, VarioConfig.chartMinVxz);
		} finally { ConfigOptions.apply(original); }
	}

	@Test void incompleteOrInvalidInputKeepsTheLastValidPreview() {
		var original = ConfigOptions.snapshot();
		try {
			var preview = new ConfigPreview();
			preview.draft().put("chartMinVxz", "-20");
			preview.preview();
			for (String invalid : new String[] {"-", "NaN", "80"}) {
				preview.draft().put("chartMinVxz", invalid);
				preview.preview();
				assertEquals(-1.0, VarioConfig.chartMinVxz);
			}
			preview.draft().put("chartMinVxz", "-30");
			preview.preview();
			assertEquals(-1.5, VarioConfig.chartMinVxz);
		} finally { ConfigOptions.apply(original); }
	}
}

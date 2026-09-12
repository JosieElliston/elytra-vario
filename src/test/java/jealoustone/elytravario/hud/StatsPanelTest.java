package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import jealoustone.elytravario.VarioConfig;
import org.junit.jupiter.api.Test;

class StatsPanelTest {
	@Test void everyPanelNamesTheSettingsItReads() {
		for (StatsPanel panel : StatsPanel.values()) {
			for (String key : List.of(panel.showKey(), panel.xKey(), panel.yKey(),
					panel.widthKey(), panel.heightKey(), panel.opacityKey(), panel.borderKey())) {
				// A key naming no field would reach the settings screen as a missing option
				// rather than as an error, so check it here where it is cheap.
				assertDoesNotThrow(() -> VarioConfig.class.getField(key), key);
			}
			for (String key : panel.rowKeys()) {
				assertDoesNotThrow(() -> VarioConfig.class.getField(key), key);
			}
		}
	}

	@Test void theSettingsAreLookedUpByTheKeysTheyAreNamedBy() {
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(panel, StatsPanel.byGroup(panel.group()));
			assertEquals(panel, StatsPanel.byWidthKey(panel.widthKey()));
			assertEquals(panel, StatsPanel.byHeightKey(panel.heightKey()));
			assertNull(StatsPanel.byWidthKey(panel.heightKey()));
			assertNull(StatsPanel.byHeightKey(panel.widthKey()));
		}
		assertNull(StatsPanel.byWidthKey("chartSize"));
	}

	/** Every row belongs to exactly one panel, so the split neither loses nor duplicates one. */
	@Test void theFourPanelsPartitionTheRows() {
		List<String> rows = new ArrayList<>();
		for (StatsPanel panel : StatsPanel.values()) rows.addAll(panel.rowKeys());
		assertEquals(12, rows.size());
		assertEquals(rows.size(), new LinkedHashSet<>(rows).size());
		// The default is every row on, so the count the layout uses is the list drawing reads.
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(panel.rowKeys().size(), panel.rows(), panel.name());
		}
	}

	@Test void theDefaultHeightsAreTheDefaultRowsAtTheirNaturalSize() {
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(panel.layoutHeight(), panel.height(), panel.name());
			assertEquals(1.0, panel.scale(), panel.name());
			assertEquals(panel.configuredWidth(), panel.layoutWidth(), panel.name());
		}
	}

	/** The default stack butts each panel onto the one above it, borders sharing a column. */
	@Test void theDefaultPositionsShareOneColumnOfBorder() {
		StatsPanel[] panels = StatsPanel.values();
		for (int i = 1; i < panels.length; i++) {
			assertEquals(panels[i - 1].y() + panels[i - 1].height() - 1, panels[i].y(),
					panels[i].name());
			assertEquals(panels[i - 1].x(), panels[i].x(), panels[i].name());
		}
	}

	@Test void widthAndHeightMoveIndependentlyAndPanelToPanel() {
		int width = VarioConfig.statsSpeedWidth;
		int height = VarioConfig.statsSpeedHeight;
		int otherWidth = VarioConfig.statsOtherWidth;
		try {
			// Narrowing spends the gap between the columns and leaves the text where it was.
			VarioConfig.statsSpeedWidth = 120;
			assertEquals(120, StatsPanel.SPEED.layoutWidth());

			// Doubling the height doubles the text size, so a width that has not changed buys
			// half the layout it did before.
			VarioConfig.statsSpeedWidth = 264;
			assertEquals(264, StatsPanel.SPEED.layoutWidth());
			VarioConfig.statsSpeedHeight = 2 * StatsPanel.SPEED.layoutHeight();
			assertEquals(132, StatsPanel.SPEED.layoutWidth());

			// And none of it reached the panel beside it, which is the point of the split.
			assertEquals(otherWidth, StatsPanel.OTHER.width());
		} finally {
			VarioConfig.statsSpeedWidth = width;
			VarioConfig.statsSpeedHeight = height;
			VarioConfig.statsOtherWidth = otherWidth;
		}
	}

	@Test void aPanelIsNeverDrawnNarrowerThanItsOwnRowsNeed() {
		int width = VarioConfig.statsOtherWidth;
		int height = VarioConfig.statsOtherHeight;
		try {
			VarioConfig.statsOtherWidth = 32;
			int minimum = StatsPanel.OTHER.minWidth();
			assertEquals(minimum, StatsPanel.OTHER.width());
			// The minimum is a layout width, so it grows with the text size the height sets.
			VarioConfig.statsOtherHeight = 3 * StatsPanel.OTHER.layoutHeight();
			assertEquals(3 * minimum, StatsPanel.OTHER.width());
		} finally {
			VarioConfig.statsOtherWidth = width;
			VarioConfig.statsOtherHeight = height;
		}
	}

	/** The narrow panel is genuinely narrower, which is the whole reason for the split. */
	@Test void thePitchAndGlideRowsNeedLessWidthThanTheSpeedRows() {
		assertTrue(StatsPanel.OTHER.minWidth() < StatsPanel.SPEED.minWidth());
		assertEquals(StatsPanel.SPEED.minWidth(), StatsPanel.ACCEL.minWidth());
	}

	@Test void aPanelWithEveryRowOffIsNotDrawn() {
		boolean pitch = VarioConfig.showPitch;
		boolean glide = VarioConfig.showGlideRatio;
		try {
			assertTrue(StatsPanel.OTHER.visible(true));
			VarioConfig.showPitch = false;
			assertTrue(StatsPanel.OTHER.visible(true));
			VarioConfig.showGlideRatio = false;
			assertEquals(0, StatsPanel.OTHER.rows());
			assertFalse(StatsPanel.OTHER.visible(true));
			// And its neighbours are unaffected.
			assertTrue(StatsPanel.SPEED.visible(true));
		} finally {
			VarioConfig.showPitch = pitch;
			VarioConfig.showGlideRatio = glide;
		}
	}

	@Test void aPanelSwitchedOffIsNotDrawnAndTheInstrumentSwitchGovernsThemAll() {
		boolean shown = VarioConfig.showStatsSpeed;
		boolean instrument = VarioConfig.showStats;
		try {
			VarioConfig.showStatsSpeed = false;
			assertFalse(StatsPanel.SPEED.visible(true));
			assertTrue(StatsPanel.OTHER.visible(true));
			VarioConfig.showStatsSpeed = true;
			VarioConfig.showStats = false;
			for (StatsPanel panel : StatsPanel.values()) {
				assertFalse(panel.visible(true), panel.name());
			}
		} finally {
			VarioConfig.showStatsSpeed = shown;
			VarioConfig.showStats = instrument;
		}
	}

	@Test void everyPanelIsFoundByItsGroup() {
		assertNotNull(StatsPanel.byGroup("statsEnergy"));
		assertNull(StatsPanel.byGroup("barSpeedoTotal"));
	}
}

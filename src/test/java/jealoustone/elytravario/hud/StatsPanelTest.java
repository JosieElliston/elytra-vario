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
	@Test
	void heightLimitIsTheInverseOfMinimumWidth() {
		int layoutHeight = StatsPanel.BOUNCE_DISTANCE.layoutHeight();

		assertEquals(layoutHeight, StatsPanel.BOUNCE_DISTANCE.maxHeightForWidth(110));
		assertEquals(layoutHeight - 1, StatsPanel.BOUNCE_DISTANCE.maxHeightForWidth(109));
		assertEquals(layoutHeight * 2, StatsPanel.BOUNCE_DISTANCE.maxHeightForWidth(220));
	}

	/**
	 * The floors the declared rows come to. They are here as numbers so that a change to a
	 * template, a label or the padding shows up as a specific pixel count rather than as a panel
	 * that quietly stopped being placeable where it used to be.
	 *
	 * <p>Three of them — 86, 116 and 106 — are the figures the panels carried before the floors
	 * were computed, when they had been measured through the font by hand. Acceleration is the
	 * one that moved, from a typed 116 to a measured 115: the superscript on {@code b/s²} is a
	 * pixel narrower than the digit a speed has in its place.
	 */
	@Test void eachFloorIsTheWidestRowThePanelDeclares() {
		assertEquals(86, StatsPanel.OTHER.minWidth());
		assertEquals(116, StatsPanel.SPEED.minWidth());
		assertEquals(115, StatsPanel.ACCEL.minWidth());
		assertEquals(106, StatsPanel.ENERGY.minWidth());
		assertEquals(150, StatsPanel.BOUNCE_VELOCITY.minWidth());
		assertEquals(110, StatsPanel.BOUNCE_DISTANCE.minWidth());
		assertEquals(98, StatsPanel.BOUNCE_TICKS.minWidth());
	}

	/** No panel is defaulted narrower than its own rows need, so none is drawn wider than placed. */
	@Test void theDefaultWidthIsTheWidestFloorAndEveryPanelShouldClearItsOwn() {
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(150, panel.configuredWidth(), panel.name());
			assertTrue(panel.minWidth() <= panel.configuredWidth(), panel.name());
		}
	}

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

	/** Every switch belongs to exactly one panel, so settings neither lose nor duplicate one. */
	@Test void thePanelsPartitionTheSwitches() {
		List<String> rows = new ArrayList<>();
		for (StatsPanel panel : StatsPanel.values()) rows.addAll(panel.rowKeys());
		assertEquals(19, rows.size());
		assertEquals(rows.size(), new LinkedHashSet<>(rows).size());
		assertEquals(4, StatsPanel.BOUNCE_VELOCITY.rows());
		assertEquals(4, StatsPanel.BOUNCE_DISTANCE.rows());
		assertEquals(2, StatsPanel.BOUNCE_TICKS.rows());
	}

	@Test void theDefaultHeightsAreTheDefaultRowsAtTheirNaturalSize() {
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(panel.layoutHeight(), panel.height(), panel.name());
			assertEquals(1.0, panel.scale(), panel.name());
			assertEquals(panel.configuredWidth(), panel.layoutWidth(), panel.name());
		}
	}

	/**
	 * The default layout is one vertical stack of all seven, in declaration order, each butted
	 * onto the one above it so their borders share a column.
	 */
	@Test void theDefaultPositionsAreOneStackSharingOneColumnOfBorder() {
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
			VarioConfig.statsSpeedWidth = 120;
			assertEquals(120, StatsPanel.SPEED.layoutWidth());
			VarioConfig.statsSpeedWidth = 264;
			VarioConfig.statsSpeedHeight = 2 * StatsPanel.SPEED.layoutHeight();
			assertEquals(132, StatsPanel.SPEED.layoutWidth());
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
			VarioConfig.statsOtherHeight = 3 * StatsPanel.OTHER.layoutHeight();
			assertEquals(3 * minimum, StatsPanel.OTHER.width());
		} finally {
			VarioConfig.statsOtherWidth = width;
			VarioConfig.statsOtherHeight = height;
		}
	}

	@Test void eachPanelKeepsItsOwnContentMinimum() {
		assertTrue(StatsPanel.OTHER.minWidth() < StatsPanel.ACCEL.minWidth());
		assertTrue(StatsPanel.ACCEL.minWidth() < StatsPanel.SPEED.minWidth());
		assertTrue(StatsPanel.SPEED.minWidth() < StatsPanel.BOUNCE_VELOCITY.minWidth());
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

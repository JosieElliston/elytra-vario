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
	/**
	 * The two bounds are exact inverses, which they were not when the height was the setting:
	 * a width holds exactly the sizes whose floors fit in it, with nothing lost to rounding in
	 * either direction.
	 */
	@Test
	void theTextSizeLimitIsTheInverseOfMinimumWidth() {
		assertEquals(1, StatsPanel.BOUNCE_DISTANCE.maxTextSizeForWidth(110));
		assertEquals(0, StatsPanel.BOUNCE_DISTANCE.maxTextSizeForWidth(109));
		assertEquals(2, StatsPanel.BOUNCE_DISTANCE.maxTextSizeForWidth(220));
		assertEquals(2, StatsPanel.BOUNCE_DISTANCE.maxTextSizeForWidth(229));

		for (StatsPanel panel : StatsPanel.values()) {
			for (int size = 1; size <= StatsPanel.MAX_TEXT_SIZE; size++) {
				int floor = panel.minWidth(size);
				assertEquals(size, panel.maxTextSizeForWidth(floor), panel.name());
				assertEquals(size - 1, panel.maxTextSizeForWidth(floor - 1), panel.name());
			}
		}
	}

	/**
	 * Switching a row off shortens the panel and leaves the letters where they were. When the
	 * height was the setting this was the other way about: the divisor shrank, the dividend did
	 * not, and a checkbox reading "show total speed" also enlarged every letter on the panel.
	 */
	@Test
	void switchingARowOffShortensThePanelRatherThanResizingItsText() {
		boolean total = VarioConfig.showTotalSpeed;
		int size = VarioConfig.statsSpeedTextSize;
		try {
			VarioConfig.statsSpeedTextSize = 2;
			int before = StatsPanel.SPEED.height();
			assertEquals(2.0, StatsPanel.SPEED.scale());

			VarioConfig.showTotalSpeed = false;

			assertEquals(2.0, StatsPanel.SPEED.scale());
			assertEquals(before - StatsPanel.LINE * 2, StatsPanel.SPEED.height());
			// And the width floor, which follows the text size, has not moved either.
			assertEquals(StatsPanel.SPEED.minWidth(2), StatsPanel.SPEED.minWidth());
		} finally {
			VarioConfig.showTotalSpeed = total;
			VarioConfig.statsSpeedTextSize = size;
		}
	}

	/** Every size the setting admits is a whole multiple, so no panel is ever interpolated. */
	@Test
	void everyReachableTextSizeIsOneTheFontIsDrawnAtLosslessly() {
		int size = VarioConfig.statsEnergyTextSize;
		try {
			for (int textSize = 1; textSize <= StatsPanel.MAX_TEXT_SIZE; textSize++) {
				VarioConfig.statsEnergyTextSize = textSize;
				assertEquals(textSize, StatsPanel.ENERGY.scale());
				assertEquals(Math.rint(StatsPanel.ENERGY.scale()), StatsPanel.ENERGY.scale());
				assertEquals(StatsPanel.ENERGY.layoutHeight() * textSize,
						StatsPanel.ENERGY.height());
			}
		} finally {
			VarioConfig.statsEnergyTextSize = size;
		}
	}

	/**
	 * The floors the declared rows come to. They are here as numbers so that a change to a
	 * template, a label or the padding shows up as a specific pixel count rather than as a panel
	 * that quietly stopped being placeable where it used to be.
	 *
	 * <p>Speed and acceleration are the two the units heading paid for: 116 and 115 when every
	 * row read {@code SPEED XYZ} against a figure carrying its own {@code b/s}, and 66 and 67 now
	 * that the unit is said once at the top and the rows are labelled {@code XYZ}. Acceleration
	 * is the wider of the two by a pixel, and for the opposite reason it used to be the narrower:
	 * its floor is its heading rather than its figures, and {@code ACCEL b/s²} carries the
	 * superscript that {@code SPEED b/s} does not. Energy moved from 106 to 116 across the two
	 * changes together: its figures gave up their {@code b} to the heading and its heading took
	 * the row back, and its {@code REL} column widened to the one width every figure column on
	 * every panel now reserves.
	 */
	@Test void eachFloorIsTheWidestRowThePanelDeclares() {
		assertEquals(86, StatsPanel.OTHER.minWidth());
		assertEquals(66, StatsPanel.SPEED.minWidth());
		assertEquals(67, StatsPanel.ACCEL.minWidth());
		assertEquals(116, StatsPanel.ENERGY.minWidth());
		assertEquals(150, StatsPanel.BOUNCE_VELOCITY.minWidth());
		assertEquals(110, StatsPanel.BOUNCE_DISTANCE.minWidth());
		assertEquals(118, StatsPanel.BOUNCE_TICKS.minWidth());
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
					panel.widthKey(), panel.textSizeKey(), panel.opacityKey(), panel.borderKey())) {
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
			assertEquals(panel, StatsPanel.byTextSizeKey(panel.textSizeKey()));
			assertNull(StatsPanel.byWidthKey(panel.textSizeKey()));
			assertNull(StatsPanel.byTextSizeKey(panel.widthKey()));
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

	@Test void everyPanelShipsAtTheFontsOwnSize() {
		for (StatsPanel panel : StatsPanel.values()) {
			assertEquals(1, panel.textSize(), panel.name());
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

	@Test void widthAndTextSizeMoveIndependentlyAndPanelToPanel() {
		int width = VarioConfig.statsSpeedWidth;
		int size = VarioConfig.statsSpeedTextSize;
		int otherWidth = VarioConfig.statsOtherWidth;
		try {
			VarioConfig.statsSpeedWidth = 120;
			assertEquals(120, StatsPanel.SPEED.layoutWidth());
			VarioConfig.statsSpeedWidth = 264;
			VarioConfig.statsSpeedTextSize = 2;
			assertEquals(132, StatsPanel.SPEED.layoutWidth());
			assertEquals(otherWidth, StatsPanel.OTHER.width());
		} finally {
			VarioConfig.statsSpeedWidth = width;
			VarioConfig.statsSpeedTextSize = size;
			VarioConfig.statsOtherWidth = otherWidth;
		}
	}

	@Test void aPanelIsNeverDrawnNarrowerThanItsOwnRowsNeed() {
		int width = VarioConfig.statsOtherWidth;
		int size = VarioConfig.statsOtherTextSize;
		try {
			VarioConfig.statsOtherWidth = 32;
			int minimum = StatsPanel.OTHER.minWidth();
			assertEquals(minimum, StatsPanel.OTHER.width());
			VarioConfig.statsOtherTextSize = 3;
			assertEquals(3 * minimum, StatsPanel.OTHER.width());
		} finally {
			VarioConfig.statsOtherWidth = width;
			VarioConfig.statsOtherTextSize = size;
		}
	}

	@Test void eachPanelKeepsItsOwnContentMinimum() {
		assertTrue(StatsPanel.SPEED.minWidth() < StatsPanel.ACCEL.minWidth());
		assertTrue(StatsPanel.ACCEL.minWidth() < StatsPanel.OTHER.minWidth());
		assertTrue(StatsPanel.OTHER.minWidth() < StatsPanel.ENERGY.minWidth());
		assertTrue(StatsPanel.ENERGY.minWidth() < StatsPanel.BOUNCE_TICKS.minWidth());
		assertTrue(StatsPanel.BOUNCE_TICKS.minWidth() < StatsPanel.BOUNCE_VELOCITY.minWidth());
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

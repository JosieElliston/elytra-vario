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
	 * The two bounds are exact inverses, counted in screen pixels per font pixel: a width holds
	 * exactly the sizes whose floors fit in it, with nothing lost to rounding in either
	 * direction.
	 */
	@Test
	void theTextSizeLimitIsTheInverseOfMinimumWidth() {
		int wasScale = StatsPanel.guiScale();
		try {
			StatsPanel.guiScale(1);
			assertEquals(1, StatsPanel.BOUNCE_DISTANCE.maxFontPixelsForWidth(110));
			assertEquals(0, StatsPanel.BOUNCE_DISTANCE.maxFontPixelsForWidth(109));
			assertEquals(2, StatsPanel.BOUNCE_DISTANCE.maxFontPixelsForWidth(220));

			// At a GUI scale of 4 the same 110 pixels of width hold four times the font pixels,
			// which is the same text at the same size -- the panel is measured in GUI pixels and
			// each of those is four screen pixels.
			StatsPanel.guiScale(4);
			assertEquals(4, StatsPanel.BOUNCE_DISTANCE.maxFontPixelsForWidth(110));
			assertEquals(1.0, StatsPanel.BOUNCE_DISTANCE.maxFontPixelsForWidth(110) / 4.0);

			for (int gui = 1; gui <= 4; gui++) {
				StatsPanel.guiScale(gui);
				for (StatsPanel panel : StatsPanel.values()) {
					for (int size = 1; size <= StatsPanel.MAX_TEXT_SIZE * gui; size++) {
						int floor = panel.minWidth(size);
						String at = panel.name() + " gui " + gui + " size " + size;
						assertEquals(size, panel.maxFontPixelsForWidth(floor), at);
						assertTrue(panel.maxFontPixelsForWidth(floor - 1) < size, at);
					}
				}
			}
		} finally {
			StatsPanel.guiScale(wasScale);
		}
	}

	/**
	 * The sizes a panel can be drawn at are the multiples of one over the GUI scale, because a
	 * glyph pixel covers the text size times the GUI scale screen pixels and that product is
	 * what has to be whole. Whole text sizes are only the special case of a GUI scale of one.
	 */
	@Test
	void theDrawableTextSizesAreTheMultiplesOfOneOverTheGuiScale() {
		double was = VarioConfig.statsEnergyTextSize;
		int wasScale = StatsPanel.guiScale();
		try {
			StatsPanel.guiScale(4);
			// A quarter, a half and three quarters are all exact here, and were unreachable
			// when the setting counted whole font sizes.
			for (double size : new double[] { 0.25, 0.5, 0.75, 1.0, 1.25 }) {
				VarioConfig.statsEnergyTextSize = size;
				assertEquals(size, StatsPanel.ENERGY.scale(), "" + size);
				assertEquals(Math.rint(size * 4), size * 4, 1e-9, "" + size);
			}
			// Anything between is quantized to the nearest size that is drawn exactly, so a
			// hand-edited file cannot ask for blurred text.
			VarioConfig.statsEnergyTextSize = 0.3;
			assertEquals(0.25, StatsPanel.ENERGY.scale());
			VarioConfig.statsEnergyTextSize = 1.3;
			assertEquals(1.25, StatsPanel.ENERGY.scale());

			// At a GUI scale of 1 there is nothing below the font's own size, and the drawable
			// sizes really are the whole numbers.
			StatsPanel.guiScale(1);
			VarioConfig.statsEnergyTextSize = 0.5;
			assertEquals(1.0, StatsPanel.ENERGY.scale());
			VarioConfig.statsEnergyTextSize = 1.5;
			assertEquals(2.0, StatsPanel.ENERGY.scale());

			// And at 3 they are thirds.
			StatsPanel.guiScale(3);
			VarioConfig.statsEnergyTextSize = 2.0 / 3;
			assertEquals(2, StatsPanel.ENERGY.fontPixels());
			assertEquals(2.0 / 3, StatsPanel.ENERGY.scale(), 1e-9);
		} finally {
			StatsPanel.guiScale(wasScale);
			VarioConfig.statsEnergyTextSize = was;
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
		double size = VarioConfig.statsSpeedTextSize;
		try {
			VarioConfig.statsSpeedTextSize = 2;
			int before = StatsPanel.SPEED.height();
			assertEquals(2.0, StatsPanel.SPEED.scale());

			VarioConfig.showTotalSpeed = false;

			assertEquals(2.0, StatsPanel.SPEED.scale());
			assertEquals(before - StatsPanel.LINE * 2, StatsPanel.SPEED.height());
			// And the width floor, which follows the text size, has not moved either.
			assertEquals(StatsPanel.SPEED.minWidth(StatsPanel.SPEED.fontPixels()),
					StatsPanel.SPEED.minWidth());
		} finally {
			VarioConfig.showTotalSpeed = total;
			VarioConfig.statsSpeedTextSize = size;
		}
	}

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
			assertEquals(1.0, panel.textSize(), panel.name());
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
		double size = VarioConfig.statsSpeedTextSize;
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
		double size = VarioConfig.statsOtherTextSize;
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

package jealoustone.elytravario.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CycleTrackerTest {
	private static final double GRAVITY = 0.08;

	/** Ticks spent on each leg of a synthetic cycle, climbing and then descending. */
	private static final int LEG = 5;

	private static final double TOLERANCE = 1e-9;

	/**
	 * A cycle whose apex is lower than the last one still latches its own apex.
	 *
	 * <p>The running best used to be taken from the previous boundary, which falls a tick
	 * past the previous apex and so sits barely below it. A cycle that gave altitude back
	 * therefore found that leftover sample higher than its own apex, latched it a second
	 * time, and reported the previous cycle's gain — every reading a cycle behind. The
	 * cycles here lose different amounts so that a stale reading cannot pass by looking like
	 * a correct one.
	 */
	@Test
	void reportsTheGainOfTheCycleThatJustClosed() {
		Pump pump = pump(new double[][] {
				{2.0, 2.5},
				{2.0, 3.5},
				{2.0, 2.25},
				{2.0, 1.5},
				{2.0, 3.0},
		});

		CycleTracker tracker = new CycleTracker();
		int cycle = 0;

		for (int t = 0; t < pump.samples.size(); t++) {
			tracker.update(pump.samples.get(t));

			// The apex latches on the first descending tick after it, one tick later.
			if (cycle < pump.apexes.size() && t == pump.apexes.get(cycle) + 1) {
				Sample apex = pump.samples.get(pump.apexes.get(cycle));
				assertEquals(apex.totalHeight(), tracker.peakTotalHeight(), TOLERANCE,
						"cycle " + cycle + " latched the wrong apex");

				if (cycle > 0) {
					Sample previous = pump.samples.get(pump.apexes.get(cycle - 1));
					assertEquals(apex.totalHeight() - previous.totalHeight(),
							tracker.lastCycleGain(), TOLERANCE,
							"cycle " + cycle + " reported the wrong gain");
				}

				cycle++;
			}
		}

		assertEquals(pump.apexes.size(), cycle, "not every apex was reached");
	}

	/** The same, for cycles that gain altitude, where the running best was never stale. */
	@Test
	void reportsTheGainOfAClimbingCycle() {
		Pump pump = pump(new double[][] {
				{3.0, 2.0},
				{4.0, 2.0},
				{2.5, 2.0},
		});

		CycleTracker tracker = new CycleTracker();

		for (int t = 0; t < pump.samples.size(); t++) {
			tracker.update(pump.samples.get(t));
		}

		Sample last = pump.samples.get(pump.apexes.get(pump.apexes.size() - 1));
		Sample previous = pump.samples.get(pump.apexes.get(pump.apexes.size() - 2));
		assertEquals(last.totalHeight() - previous.totalHeight(), tracker.lastCycleGain(), TOLERANCE);
	}

	private record Pump(List<Sample> samples, List<Integer> apexes) {
	}

	/**
	 * A sawtooth of altitude: each {@code {climb, descent}} pair is one cycle, spread over
	 * {@link #LEG} ticks per leg. Vertical speed follows from the altitude step, and is well
	 * clear of the tracker's deadbands at these sizes.
	 */
	private static Pump pump(double[][] cycles) {
		List<Sample> samples = new ArrayList<>();
		List<Integer> apexes = new ArrayList<>();
		double y = 100.0;

		for (double[] cycle : cycles) {
			double climb = cycle[0] / LEG;
			double descent = -cycle[1] / LEG;

			for (int i = 0; i < LEG; i++) {
				y += climb;
				samples.add(sample(y, climb));
			}

			apexes.add(samples.size() - 1);

			for (int i = 0; i < LEG; i++) {
				y += descent;
				samples.add(sample(y, descent));
			}
		}

		return new Pump(samples, apexes);
	}

	private static Sample sample(double y, double vy) {
		return new Sample(0.0, y, 0.0, 0.0, vy, 0.0, 0.0f, 0.0f, GRAVITY, true);
	}
}

package jealoustone.elytravario.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class BounceTrackerTest {
	@Test void beginningOnTheGroundProvidesTheFirstLaunchBaseline() {
		BounceTracker tracker = new BounceTracker();
		Sample touch = sample(10, true, false);
		Sample leave = sample(12, false, false);
		tracker.update(touch);
		tracker.update(leave);

		assertSame(touch, tracker.touch().sample());
		assertSame(leave, tracker.leave().sample());
		assertEquals(1, BounceTracker.ticks(tracker.touch(), tracker.leave()));
	}

	@Test void latchesTouchLeaveAndDeployWithConsecutiveIntervals() {
		BounceTracker tracker = new BounceTracker();
		Sample air = sample(0, false, false);
		Sample touch = sample(1, true, false);
		Sample ground = sample(2, true, false);
		Sample leave = sample(3, false, false);
		Sample airborne = sample(4, false, false);
		Sample deploy = sample(5, false, true);

		tracker.update(air);
		tracker.update(touch);
		tracker.update(ground);
		tracker.update(leave);
		tracker.update(airborne);
		tracker.update(deploy);

		assertSame(touch, tracker.touch().sample());
		assertSame(leave, tracker.leave().sample());
		assertSame(deploy, tracker.deploy().sample());
		assertEquals(2, BounceTracker.ticks(tracker.touch(), tracker.leave()));
		assertEquals(2, BounceTracker.ticks(tracker.leave(), tracker.deploy()));
	}

	@Test void aNewTouchClearsTheOldSequence() {
		BounceTracker tracker = new BounceTracker();
		tracker.update(sample(0, false, false));
		tracker.update(sample(1, true, false));
		tracker.update(sample(2, false, false));
		tracker.update(sample(3, false, true));
		tracker.update(sample(4, false, false));
		tracker.update(sample(5, true, false));

		assertNull(tracker.leave());
		assertNull(tracker.deploy());
	}

	private static Sample sample(double x, boolean grounded, boolean gliding) {
		return new Sample(x, 64, 0, 1, 0, 0, 0, 0, 0.08, gliding, grounded);
	}
}

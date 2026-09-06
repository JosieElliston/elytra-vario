package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudPositionTest {
	@Test void absolutePositionIsUnchangedWhenItFits() {
		assertEquals(new HudPosition(4, 6), HudPosition.clamp(4, 6, 100, 50, 640, 360));
	}

	@Test void positionsAndOversizedPanelsRemainOnscreen() {
		assertEquals(new HudPosition(0, 0), HudPosition.clamp(4, 6, 512, 512, 320, 240));
		assertEquals(new HudPosition(220, 0), HudPosition.clamp(4000, -4000, 100, 50, 320, 240));
	}
}

package jealoustone.elytravario.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudPositionTest {
	@Test void cornersUseInwardOffsetsAndCenterUsesSignedOffsets() {
		assertEquals(new HudPosition(4, 6), HudPosition.resolve(0, 4, 6, 100, 50, 640, 360));
		assertEquals(new HudPosition(536, 6), HudPosition.resolve(1, 4, 6, 100, 50, 640, 360));
		assertEquals(new HudPosition(4, 304), HudPosition.resolve(2, 4, 6, 100, 50, 640, 360));
		assertEquals(new HudPosition(536, 304), HudPosition.resolve(3, 4, 6, 100, 50, 640, 360));
		assertEquals(new HudPosition(274, 149), HudPosition.resolve(4, 4, -6, 100, 50, 640, 360));
	}

	@Test void resizesAndOversizedPanelsRemainAnchoredOnscreen() {
		assertEquals(new HudPosition(0, 0), HudPosition.resolve(3, 4, 6, 512, 512, 320, 240));
		assertEquals(new HudPosition(220, 0), HudPosition.resolve(0, 4000, -4000, 100, 50, 320, 240));
	}
}

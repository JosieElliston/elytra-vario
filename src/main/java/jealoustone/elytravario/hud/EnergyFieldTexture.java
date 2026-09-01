package jealoustone.elytravario.hud;

import jealoustone.elytravario.VarioConfig;
import jealoustone.elytravario.flight.EnergyField;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Paints an {@link EnergyField} into a texture and blits it behind the chart.
 *
 * <h2>Why a texture and not rectangles</h2>
 *
 * <p>The first version drew the field with {@code fill}, one rectangle per horizontal run of
 * equal colour. That needs the values quantised for anything to merge, and the quantisation
 * was the problem: at a coarse enough step to be worth doing — twenty-four levels a side,
 * which cut fourteen thousand rectangles to about three thousand — the ramp banded visibly.
 * At a fine enough step not to band, almost nothing merged: measured on the default domain,
 * an unquantised field still comes to eleven thousand runs, so the whole trick bought about
 * twenty per cent for a picture that looked worse.
 *
 * <p>So it is a texture. One quad a frame, no allocation per frame, no quantisation at all,
 * and the eight bits a channel that the screen has anyway are the only rounding left. It also
 * removes the awkward middle ground where a display concern — how many colours to allow — was
 * deciding how the physics results got stored.
 *
 * <p>{@link DynamicTexture} samples {@code NEAREST} and the blit is one texel to one GUI
 * pixel, so nothing is filtered or resampled on the way to the screen.
 *
 * <h2>What is rebuilt when</h2>
 *
 * <p>Three costs, in descending order and each with its own trigger. The field itself — the
 * expensive one, a third of a second — is rebuilt by {@link EnergyField} when the domain or
 * gravity changes. The pixels are repainted when the field instance or any colour setting
 * changes, which is a few milliseconds. The texture object is recreated only when the chart
 * changes size, because that is the only thing that invalidates the allocation. Everything
 * else is a blit.
 */
final class EnergyFieldTexture {
	private static DynamicTexture texture;
	private static EnergyField painted;
	private static int paintedZero;
	private static int paintedGain;
	private static int paintedLoss;
	private static double paintedScale;

	private EnergyFieldTexture() {
	}

	/** Draws the field over the chart's interior, which is exactly the texture's size. */
	static void blit(GuiGraphicsExtractor graphics, EnergyField field, int x, int y) {
		DynamicTexture current = prepare(field);
		graphics.blit(current.getTextureView(), current.getSampler(),
				x, y, x + field.width(), y + field.height(), 0.0f, 1.0f, 0.0f, 1.0f);
	}

	/** Returns a texture holding this field in the configured colours, repainting if needed. */
	private static DynamicTexture prepare(EnergyField field) {
		if (texture != null && painted == field
				&& paintedZero == VarioConfig.chartFieldZeroColor
				&& paintedGain == VarioConfig.chartFieldGainColor
				&& paintedLoss == VarioConfig.chartFieldLossColor
				&& paintedScale == VarioConfig.chartFieldScale) {
			return texture;
		}

		if (texture == null || texture.getPixels().getWidth() != field.width()
				|| texture.getPixels().getHeight() != field.height()) {
			// Closing frees the native buffer and the GPU texture. Only reached when the
			// chart's size changes, which needs a recompile, so at most once a session.
			if (texture != null) {
				texture.close();
			}

			texture = new DynamicTexture("elytra-vario energy field", field.width(),
					field.height(), false);
		}

		float[] gains = field.gains();
		int width = field.width();

		for (int i = 0; i < gains.length; i++) {
			texture.getPixels().setPixel(i % width, i / width, color(gains[i]));
		}

		texture.upload();

		painted = field;
		paintedZero = VarioConfig.chartFieldZeroColor;
		paintedGain = VarioConfig.chartFieldGainColor;
		paintedLoss = VarioConfig.chartFieldLossColor;
		paintedScale = VarioConfig.chartFieldScale;
		return texture;
	}

	/**
	 * The colour for one energy change, as ARGB.
	 *
	 * <p>The magnitude is compressed by {@code |x| / (|x| + chartFieldScale)} rather than
	 * clipped to a range. The field spans about four blocks/tick end to end while the
	 * interesting structure — the boundary and the glide band around it — lives in the first
	 * tenth of that, so a linear ramp would saturate almost everywhere and show nothing.
	 * Compression never clips, so the extremes stay distinguishable from the merely large, and
	 * it is smooth through zero, so the boundary is a clean seam rather than a step.
	 *
	 * <p>Interpolated in sRGB rather than in linear light. Linear light is the correct way to
	 * mix two lights, but this is not mixing light: it is laying out a scale, and on a ramp
	 * from near-black to a saturated colour the linear version spends most of its length near
	 * the dark end and arrives at the mid-tones desaturated. Plain sRGB keeps the hue and
	 * spaces the steps about as evenly as the eye reads them.
	 */
	private static int color(float energyChange) {
		float magnitude = Math.abs(energyChange);
		float t = magnitude / (magnitude + (float) VarioConfig.chartFieldScale);
		int end = energyChange < 0.0f
				? VarioConfig.chartFieldLossColor
				: VarioConfig.chartFieldGainColor;

		return mix(VarioConfig.chartFieldZeroColor, end, t);
	}

	/** Channel-wise interpolation between two ARGB colours, alpha included. */
	private static int mix(int from, int to, float t) {
		int argb = 0;

		for (int shift = 0; shift < 32; shift += 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			argb |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
		}

		return argb;
	}
}

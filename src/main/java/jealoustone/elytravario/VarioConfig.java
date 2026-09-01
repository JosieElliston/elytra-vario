package jealoustone.elytravario;

/**
 * Mutable in-memory settings. Nothing is persisted to disk yet, so these reset each launch.
 */
public final class VarioConfig {
	public static boolean enabled = true;
	public static boolean onlyWhileGliding = false;
	public static boolean showChart = true;

	/** Top-left corner of the HUD, in scaled GUI pixels. */
	public static int originX = 4;
	public static int originY = 4;

	/**
	 * Width of the readout panel. Values are right-aligned against this, so it has to be
	 * wide enough for the longest one ("0.000 b/t  0.0 b/s") plus its label.
	 */
	public static int panelWidth = 158;

	/** Ticks to average the variometer over. Raw per-tick deltas are far too noisy to read. */
	public static int varioWindow = 10;

	public static int chartSize = 100;
	public static int chartTrailTicks = 100;

	/**
	 * Chart bounds in blocks/tick. Defaults span roughly the region elytrasim plots, so the
	 * in-game chart and the sim's screenshots can be compared by eye.
	 */
	public static double chartMinVxz = 0.0;
	public static double chartMaxVxz = 3.0;
	public static double chartMinVy = -2.5;
	public static double chartMaxVy = 1.0;

	private VarioConfig() {
	}
}

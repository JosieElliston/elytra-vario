package jealoustone.elytravario;

/**
 * Runtime settings, loaded and saved by the Mod Menu configuration screen.
 */
public final class VarioConfig {
	public static boolean enabled = true;

	/**
	 * The delta-TE heatmap behind the chart: for every velocity the chart can show, the most
	 * energy one tick could gain from it. See
	 * {@link jealoustone.elytravario.flight.EnergyField}.
	 *
	 * <p>Costs about thirty milliseconds to build, once, and nothing afterwards until the
	 * chart's domain or gravity changes.
	 */
	public static boolean showEnergyField = true;

	/**
	 * Angle of attack, in its two forms: a row on the panel, and the flight path marker on
	 * the ladder, whose vertical gap from the crosshair is the same quantity drawn rather
	 * than printed. Both are off by default.
	 *
	 * <p>The code for both is kept and correct. Nothing in the pump-cycle research refers to
	 * angle of attack yet, so on instruments that are watched continuously they were clutter
	 * competing with readings actually being used — and whether it matters is an open
	 * question rather than a settled one.
	 *
	 * <p>Turning the marker back on also restores the only sideslip cue on the ladder; the
	 * chart's cyan cursor still shows sideslip either way.
	 *
	 * <p>The open question has since closed, and these two switches did not move. The dive's
	 * rule is read as the gap between the hold bug and the velocity bug, and that gap is angle
	 * of attack — so the ladder does show it by default now, as a distance between two marks.
	 * What is still off is the printed figure and the exact two-dimensional placement, neither
	 * of which the rule needs.
	 */
	public static boolean showAngleOfAttack = false;
	public static boolean showFlightPath = false;

	/**
	 * The optimal pitch bug: a pair of wedges in the ladder's center gap marking the pitch
	 * that would gain the most energy over the next tick.
	 *
	 * <p>Only ever drawn while actually gliding, since that is the only state the underlying
	 * physics describes. See {@link jealoustone.elytravario.flight.OptimalPitch}, and note
	 * that its answer is greedy: trustworthy while energy is being gained, and a shorter view
	 * than a pump cycle needs during the dive that pays for the climb.
	 *
	 * <p><b>Off by default.</b> Every phase it is right about is a phase another bug is right
	 * about too, and the phases it is wrong about are the ones being flown and studied — it
	 * parks on the horizon through the whole dive and pins to the nose-up stop entering the
	 * climb. That made it two more marks in the one band the useful bugs share. It is kept
	 * because it is the reading elytrasim plots and the heatmap colors, so it is the way to
	 * see those two on the ladder.
	 */
	public static boolean showOptimalPitch = false;

	/**
	 * The other three bugs, each marking a pitch some rule says to fly, all drawn in the same
	 * band of the center gap and told apart by color and by height.
	 *
	 * <p>Together with the one above they are the four myopic rules an optimised pump cycle
	 * turns out to obey piecewise, plus the reference they are read against:
	 *
	 * <ul>
	 * <li><b>Lookahead</b> — the constant pitch gaining the most energy over the next
	 *     {@code lookaheadTicks}. This is the <em>gain phase</em> rule, the climb out of the
	 *     flick, and it is the one to follow there; the one-tick bug is badly wrong through
	 *     that phase. See {@link jealoustone.elytravario.flight.OptimalPitch}.</li>
	 * <li><b>Hold</b> — the pitch that leaves the flight path angle where it is. This is the
	 *     <em>dive</em> rule, and it is parameter-free. See
	 *     {@link jealoustone.elytravario.flight.FlightPathHold}.</li>
	 * <li><b>Velocity</b> — where the player is actually going. Not advice, which is why it
	 *     is the one bug drawn in gray; it is there so the hold bug can be read against it,
	 *     the gap between the two being the angle of attack the hold is asking for.</li>
	 * </ul>
	 *
	 * <p><b>Neither rule bug pegs at the edge of the ladder; both leave through it.</b> Each
	 * governs one phase and sends its answer off the ladder in the phases it does not govern,
	 * where a mark held at the stop would read as a direction to keep going in and so invite
	 * flying a rule where it does not apply. The two that do peg are the two switched off by
	 * default, neither of which is a rule.
	 *
	 * <p>The velocity bug and the flight path marker are the same quantity twice. The marker
	 * is the honest two-dimensional version and also shows sideslip; the bug is its vertical
	 * component alone, in the center gap where the other three are, which is where it is
	 * wanted when the thing being read is a gap between two pitches.
	 */
	public static boolean showLookaheadPitch = true;
	public static boolean showHoldPitch = true;

	/**
	 * <b>Off by default</b>, unlike the two above.
	 *
	 * <p>It was added so the hold bug could be read against something, on the theory that the
	 * gap between them — the angle of attack the hold is asking for — is worth watching. In
	 * the air it is not: the hold bug is flown by putting the nose on it, and the gap is a
	 * fact about the answer rather than an input to flying it. So this is one more mark in a
	 * crowded band for a number nothing is done with, which is the same case that keeps
	 * {@code showAngleOfAttack} and {@code showFlightPath} off.
	 *
	 * <p>Turn it on to see that gap directly; it is about thirty degrees by the end of a dive,
	 * and it is the answer to "does holding the angle mean pointing along it", which it does
	 * not.
	 */
	public static boolean showVelocityPitch = false;

	/**
	 * How many ticks the lookahead bug holds a candidate pitch for before scoring it.
	 *
	 * <p>Twenty because that is what fits the gain phase of an optimised cycle: 1.1 degrees
	 * RMS against 17.9 for a single tick. The fit is sharp in the sense that sixteen and
	 * twenty-four are both three times worse, but flight is far less picky than the fit —
	 * every horizon from sixteen to twenty-four flies the cycle within a point of the best.
	 * Below about eight it stops being the gain rule at all.
	 *
	 * <p>Costs this many ticks of physics per candidate pitch, once per tick, so raising it
	 * is linear in something that is currently tens of microseconds.
	 */
	public static int lookaheadTicks = 20;

	/** Top-left corner of the HUD, in scaled GUI pixels. */
	public static int originX = 4;
	public static int originY = 4;

	/**
	 * Width of the readout panel. Values are right-aligned against this, so it has to be
	 * wide enough for the longest one plus its label.
	 */
	public static int panelWidth = 132;


	/**
	 * Chart size, in pixels per block/tick. Both the width and the height are derived from
	 * this and the axis ranges, so a pixel is always worth the same change in speed on both
	 * axes, and widening a range grows the chart rather than rescaling it.
	 */
	public static double chartScale = 34.0;
	public static int chartTrailTicks = 100;

	/**
	 * Chart bounds in blocks/<em>tick</em>, matching the units elytrasim plots in, so the
	 * in-game chart and the sim's screenshots can be compared directly. Axis labels are
	 * rendered in blocks/second like the rest of the HUD.
	 *
	 * <p>The horizontal axis starts slightly below zero so that the origin sits inside the
	 * chart rather than on its edge. Neither horizontal speed nor its look-projection is
	 * bounded by that lower edge; it is there for legibility.
	 *
	 * <p>The top of the vertical axis is set by what a working pump cycle actually reaches, not
	 * by what the physics allows. elytrasim's optimised three-hundred-tick cycle peaks at about
	 * 1.76 blocks/tick of climb, so two covers it with a little room; terminal velocity is
	 * nearer to three and a half, and reserving room for it would make the chart enormous to
	 * show a state nothing useful passes through.
	 *
	 * <p>A cursor that leaves the domain is clamped to the edge rather than dropped, so it
	 * stops being a reading and becomes a floor or a ceiling. There is no cue that this has
	 * happened. A domain that slid to keep the cursor inside would fix that, and is worth
	 * considering: the cost is that every distance on the chart would stop meaning a fixed
	 * change in speed, and that the heatmap would have to be rebuilt as it moved.
	 */
	public static double chartMinVxz = -0.5;
	public static double chartMaxVxz = 3.0;
	public static double chartMinVy = -1.5;
	public static double chartMaxVy = 2.0;

	/**
	 * The heatmap's ramp. Energy being lost runs from {@code chartFieldZeroColor} to
	 * {@code chartFieldLossColor}, energy being gained from the same zero to
	 * {@code chartFieldGainColor}, interpolated in sRGB.
	 *
	 * <p><b>Opaque, unlike everything else on the HUD.</b> The panels are translucent because
	 * they are chrome and the world behind them is worth seeing; this is data, and a
	 * translucent heatmap would make the same energy figure read as one color over the sky
	 * and another over the ground. A map whose colors depend on what is behind it is not a
	 * map.
	 *
	 * <p>Cool for loss, warm for gain, with a near-black at zero. Both arms carry real colour
	 * at their ends rather than fading into the panel: the losing side is most of the chart and
	 * most of a cycle, and a map that renders the common case as almost-background is telling
	 * you nothing about the part you spend the most time in.
	 * The pair is chosen against the rest of the chart rather than for its own sake: the trail
	 * is teal and the cursors are yellow and cyan, so the whole ramp keeps out of that arc and
	 * every mark stays legible over every part of the field. It is also, deliberately, an
	 * elytrasim scheme with elytrasim's two problems fixed — the sign flip is a black seam
	 * instead of two indistinguishable purples, and both arms climb in brightness as well as
	 * in color instead of starting saturated and dark.
	 *
	 * <p>For the panel's green-is-rising convention instead, set the gain color to something
	 * like {@code 0xFF389654} and the loss color to {@code 0xFF8C3A30}. That reads well and
	 * is not the default only because red and green are the one pair a color-blind eye
	 * cannot separate, and because green sits close enough to the trail's teal to blur it.
	 */
	public static int chartFieldZeroColor = 0xFF0C0D10;
	public static int chartFieldGainColor = 0xFF9E3692;
	public static int chartFieldLossColor = 0xFF4A70A8;

	/**
	 * The energy change, in blocks/tick, at which the heatmap's ramp is half way to saturated.
	 *
	 * <p>The ramp compresses rather than clips — {@code |x| / (|x| + this)} — because the
	 * field spans about four blocks/tick end to end while everything worth looking at happens
	 * in the first tenth of that. Lower values pull detail towards the boundary and flatten the
	 * extremes; higher values do the reverse. Note that this is a display setting only: it is
	 * read when the texture is painted and never touches the cached physics.
	 */
	public static double chartFieldScale = 0.4;

	/**
	 * Cursor colors, as ARGB. The two cursors sit on the same row of the chart and differ
	 * only in their horizontal position, so the gap between them is the sideslip. Set either
	 * to a gray such as {@code 0xFF6A7076} to demote it to a secondary reference.
	 */
	public static int cursorXzColor = 0xFFFFD633;
	public static int cursorForwardColor = 0xFF55CCFF;

	/**
	 * Pitch ladder spacing, in degrees. The rungs are placed by projection, so their spacing
	 * on screen is a tangent and not uniform; this is the angular step, not a pixel pitch.
	 */
	public static int ladderStepDegrees = 10;

	/**
	 * Finer ticks are drawn within {@code ladderFineRangeDegrees} of wherever the camera is
	 * pointing, fading out at the edge of that range, so there is extra resolution exactly
	 * where the eye already is without that detail cluttering the rest of the ladder.
	 */
	public static int ladderFineStepDegrees = 2;
	public static double ladderFineRangeDegrees = 12.0;

	/**
	 * How far above and below the center of the view the ladder reaches, as a fraction of
	 * half the view height. One is exactly the screen edge.
	 *
	 * <p>Asymmetric on purpose. Below the center the hotbar and the rest of the vanilla HUD
	 * want the room, but above it there is nothing, and stopping short there costs real
	 * range: in a pump cycle the horizon is exactly the mark being aimed at while looking a
	 * long way down, and it is the first thing a low ceiling clips.
	 *
	 * <p>There is a hard limit above this that no setting can lift. The horizon is only in
	 * the rendered view at all while looking down less than half the field of view — 35
	 * degrees at the default 70 — because beyond that it is genuinely off the top of the
	 * screen. A wider FOV is the only thing that extends it.
	 *
	 * <p>Fractions rather than pixel counts because the band is an <em>angle</em>: the
	 * projection scale is itself proportional to the view height, so a fixed pixel band would
	 * cover a different slice of sky at every GUI scale and resolution.
	 *
	 * <p>The rung lengths below are pixel counts for the opposite reason — they are sized
	 * against the labels, which are text and do not scale with the view.
	 */
	public static double ladderBandFractionUp = 0.97;
	public static double ladderBandFractionDown = 0.75;

	/**
	 * The outer fraction of the band over which marks fade out, so that they thin away at
	 * the edge instead of blinking off a pixel at a time as the view moves.
	 */
	public static double ladderFadeFraction = 0.18;

	/**
	 * Rung geometry, in scaled GUI pixels, measured out from the center of the screen. Each
	 * rung is drawn twice, mirrored about the center; {@code ladderCenterGap} is the
	 * half-width of the hole left in the middle so the ladder does not cross the crosshair.
	 *
	 * <p>Length and weight both carry the tier, so an angle can be read from the pattern
	 * without reading any digits: fine ticks are stubs, ten-degree rungs are short and
	 * dashed, twenty-degree rungs are solid and longer, and the datum lines are longest.
	 */
	public static int ladderCenterGap = 22;
	public static int ladderFineLength = 7;
	public static int ladderMinorLength = 12;
	public static int ladderMajorLength = 26;
	public static int ladderPrimeLength = 40;

	/**
	 * The horizon is drawn a little longer than the other prime rungs. It shares their weight,
	 * as it should — nothing about the horizon is stronger than plus or minus forty — but it
	 * is the datum the whole ladder is measured from, so it stays findable at a glance. Set to
	 * zero to make all three identical.
	 */
	public static int ladderHorizonExtra = 10;


	/**
	 * Ladder colors, as ARGB, ordered by tier. All are deliberately translucent: the ladder
	 * sits over the world rather than over a panel, and at full opacity it obscures more than
	 * it says. The fine ticks' alpha is the value at the center of the ladder, scaled down to
	 * nothing at the edge of the fine range.
	 *
	 * <p>The minor and fine tiers share an RGB, so that nothing but strength separates them
	 * and the ramp from prime down to fine is monotonic.
	 *
	 * <p>Nothing distinguishes above the horizon from below it. The sky, the ground and the
	 * labeled datum line already say which way up the world is.
	 */
	public static int ladderPrimeColor = 0xE0E8EAED;
	public static int ladderMajorColor = 0xB4C6CCD2;
	public static int ladderMinorColor = 0x9AB4BAC0;
	public static int ladderFineColor = 0x4AB4BAC0;

	/**
	 * Rung labels. Weak on purpose: the tiers are meant to be read as a pattern in the
	 * periphery, and the digits are there for when you look straight at them.
	 */
	public static int ladderLabelColor = 0xA0949AA0;

	/**
	 * Color of the flight path marker when it is pegged at the edge of the band, meaning
	 * the true flight path is off the ladder and the marker's position is a floor or ceiling
	 * rather than a reading.
	 */
	public static int flightPathPeggedColor = 0xFF6A7076;

	/**
	 * The optimal pitch bug's geometry, in scaled GUI pixels, and its color.
	 *
	 * <p>It lives entirely inside {@code ladderCenterGap}, which is the one band of the
	 * ladder nothing else ever draws in: rungs start at the gap's edge and labels sit beyond
	 * their outer ends, so a wedge in here can never collide with either, at any pitch and
	 * any tier. Everywhere further out does collide — just past the twenty-degree rungs the
	 * bug lands on the horizon in a steady glide and on the labels near ±20, and clearing the
	 * labels entirely puts it so far outboard that it stops reading as part of the ladder.
	 *
	 * <p>{@code ladderBugGap} is the clearance between the wedge's base and the inner end of
	 * the rungs; {@code ladderBugLength} is how far it tapers inwards from there, and
	 * {@code ladderBugRise} its half-height at the base. The apex points inwards, so the pair
	 * closes on the marked pitch like a caliper, and frames the crosshair when the pitch
	 * being flown is already the best one.
	 *
	 * <p>Magenta because the ladder is otherwise entirely gray and the chart has already
	 * spent yellow and cyan — and because elytrasim draws the same quantity in the same
	 * color. Alpha matches the datum rungs: it is one small mark and it has to be findable.
	 */
	public static int ladderBugGap = 2;
	public static int ladderBugLength = 8;
	public static int ladderBugRise = 2;
	public static int optimalPitchColor = 0xE0FF5AE0;

	/**
	 * The other three bugs share that geometry and differ only in rise, which is the second
	 * channel their identity is carried on.
	 *
	 * <p>Color alone would not be enough. All four bugs occupy one band — there is nowhere
	 * else on the ladder for them, the center gap being the only radius no rung or label ever
	 * reaches — so they overlap whenever the rules agree, and agreement is common. Ranking
	 * them by height makes an overlap nest instead of merge: the apexes coincide, the taller
	 * shoulders still show past the shorter ones, and the pile reads as a set of chevrons
	 * rather than as one mark of uncertain color. It also survives the peg, where all four
	 * take the same gray and color stops saying anything at all.
	 *
	 * <p>Which bug gets which height is a display choice tuned in flight, and it is worth being
	 * plain that it encodes no claim — the lookahead is the tallest and the hold one step under
	 * it because that is what reads well with both of them up, not because the ordering means
	 * anything. Only two things about these numbers matter structurally: that they are
	 * distinct, and that the flat pair of stubs is the velocity bug, which is the one mark of
	 * the four that is not advice.
	 *
	 * <p><b>{@code drawBugs} must draw them in descending order of rise</b>, since that is what
	 * makes an overlap nest rather than hide the taller bug. Changing the ranking here means
	 * reordering the calls there; nothing checks it.
	 *
	 * <p>A rise of zero is a single row: not a wedge, deliberately, since that bug is not
	 * advice.
	 */
	public static int ladderLookaheadRise = 6;
	public static int ladderHoldRise = 4;
	public static int ladderVelocityRise = 0;

	/**
	 * The other three bugs' colors.
	 *
	 * <p>Amber and green are picked the way the magenta was: away from the chart's yellow and
	 * cyan, away from each other, and readable against both sky and ground. The velocity bug
	 * is gray on purpose — it is the one mark of the four that is a reading rather than a
	 * target, and gray is what the rest of this ladder uses to say exactly that. It shares its
	 * RGB with the minor rungs.
	 */
	public static int lookaheadPitchColor = 0xE0F7A900;
	public static int holdPitchColor = 0xE000B533;
	public static int velocityPitchColor = 0xD0B4BAC0;

	// Per-instrument visibility: 0 = always, 1 = gliding, 2 = hidden.
	public static int ladderVisibility = 0;
	public static int markerVisibility = 0;
	public static int chartVisibility = 0;
	public static int statsVisibility = 0;
	public static boolean showLadderLabels = true;
	public static boolean showFineTicks = true;
	public static double ladderOpacity = 1.0;
	public static int flightPathColor = 0xFF55CCFF;
	public static int statsAnchor = 0;

	/**
	 * The graph's anchor. Zero to four are the five screen anchors the stats panel also uses;
	 * five to eight are {@code ATTACH_LEFT}, {@code ATTACH_RIGHT}, {@code ATTACH_ABOVE} and
	 * {@link jealoustone.elytravario.hud.HudPosition#ATTACH_BELOW}, which hang the graph off
	 * that side of the panel. Below is the default, and being attached is why {@code chartX}
	 * and {@code chartY} are zero.
	 *
	 * <p>Attaching is not a ninth place to put the graph so much as a statement that the two
	 * instruments are one block: the pair gets anchored and clamped together, so the anchor and
	 * its offsets move both. A side facing a screen edge pushes the panel in from that edge
	 * rather than sending the graph off it. See {@link jealoustone.elytravario.hud.HudLayout}.
	 */
	public static int chartAnchor = 8;

	/**
	 * The graph's offsets: measured inward from its anchor when it has one of its own, and a
	 * nudge away from the panel when it is attached. Zero in both cases, since an attachment
	 * that needs a hand-tuned offset to look attached is not one.
	 */
	public static int chartX = 0;
	public static int chartY = 0;
	public static boolean showTrail = true;
	public static int trailColor = 0xFF33CCAA;
	public static boolean showHorizontalCursor = true;
	public static boolean showForwardCursor = true;
	public static boolean showGrid = true;
	public static boolean showAxisLabels = true;
	public static boolean showPanelBorder = true;
	public static double panelOpacity = 176.0 / 255.0;
	public static double panelScale = 1.0;
	public static int energyReference = 2;
	public static int positiveColor = 0xFF66DD77;
	public static int negativeColor = 0xFFE2685F;
	public static boolean showPitch = true;
	public static boolean showHorizontalSpeed = true;
	public static boolean showTotalSpeed = true;
	public static boolean showVerticalSpeed = true;
	public static boolean showGlideRatio = true;
	public static boolean showKineticEnergy = true;
	public static boolean showPotentialEnergy = true;
	public static boolean showTotalEnergy = true;
	public static boolean showCycleGain = true;

	/**
	 * The speedometer: a semicircular dial reading zero at its left end and
	 * {@code speedoMaxSpeed} at its right, with a needle for each of the three speeds the
	 * stats panel prints.
	 *
	 * <p>It says nothing the panel does not already say, and that is the point of it. The
	 * panel is a set of digits that has to be read; the dial is an angle that can be caught in
	 * peripheral vision, and three angles side by side make the relationship between the
	 * speeds — how much of the total is horizontal, how much is being spent going up or down —
	 * a shape rather than three subtractions.
	 *
	 * <p><b>Vertical speed is drawn as a magnitude.</b> A scale that starts at zero cannot
	 * carry a sign, and vertical speed is negative through most of a dive, so the blue needle
	 * shows {@code |vy|} and nothing on the dial says which way. Nothing needs to: which way
	 * you are going is the most obvious fact in the view out of the window, and the readout
	 * panel's {@code SPEED Y} row prints the sign for when a figure is wanted.
	 *
	 * <p>The background is the half disc itself rather than a box around it. The instrument is
	 * round and its corners hold nothing, so a rectangle would be four wedges of dimmed world
	 * paying for no reading — which matters here more than on the other panels, this being the
	 * large one whose content does not fill its own bounds.
	 */
	public static int speedoVisibility = 0;
	public static int speedoAnchor = 2;
	public static int speedoX = 4;
	public static int speedoY = 4;

	/**
	 * Dial radius in scaled GUI pixels, and the speed at the right-hand end of the arc in
	 * blocks/tick.
	 *
	 * <p>Sixty-four pixels because this is an instrument read out of the corner of the eye
	 * rather than looked at, and a small dial is one whose needles have to be resolved before
	 * their angles can be. It is the largest thing on the HUD, and it can afford to be: the
	 * background is only the half disc, so what it costs the view is the shape and not a box.
	 *
	 * <p>Four blocks/tick is eighty blocks/second, which is a little past terminal velocity —
	 * an elytra settles near 3.5 — so the whole of ordinary flight lands inside the arc and
	 * the stops are somewhere the needle only reaches under a rocket or a very long dive. A
	 * scale chosen to fit the fastest state the physics allows would spend most of its length
	 * on speeds nothing passes through, which is the same argument that sets the velocity
	 * graph's vertical bound.
	 */
	public static int speedoRadius = 64;
	public static double speedoMaxSpeed = 4.0;

	/**
	 * Tick spacing in blocks/tick: labelled ticks every {@code speedoMajorStep}, plain ones
	 * every {@code speedoMinorStep}. Twenty and five blocks/second by default.
	 */
	public static double speedoMajorStep = 1.0;
	public static double speedoMinorStep = 0.25;

	/**
	 * The three needles. Each is independent, and each is told apart by <em>two</em> channels:
	 * a colour and a length.
	 *
	 * <p>The length is not decoration. All three needles turn about one hub, so whenever two
	 * speeds agree the needles lie exactly on top of one another — and they agree often, since
	 * total and horizontal speed are equal in level flight, which is most of a glide. Distinct
	 * lengths make that overlap read as one needle with a longer one behind it rather than as
	 * a single mark of uncertain colour. It is also what is left when colour has stopped
	 * saying anything, which happens twice over: at the stop, where a pegged needle takes the
	 * pegged grey whichever one it is, and for the red and green pair, which is the one pair a
	 * colour-blind eye cannot separate.
	 *
	 * <p>Total speed gets the longest needle because it is the largest of the three by
	 * construction, so the tips run outwards in the same order they run clockwise and the fan
	 * never reads as crossed. The reading itself is the angle, so length costs nothing.
	 */
	public static boolean showSpeedoTotal = true;
	public static boolean showSpeedoHorizontal = true;
	public static boolean showSpeedoVertical = true;
	public static int speedoTotalColor = 0xFFE0574B;
	public static int speedoHorizontalColor = 0xFF57C46A;
	public static int speedoVerticalColor = 0xFF4D8CFF;

	/**
	 * Colour of a needle whose speed is past the end of the scale.
	 *
	 * <p>These needles peg rather than leave, unlike the pitch ladder's rule bugs, and for the
	 * reason that instrument gives for the two marks that do peg there: a speed past the stop
	 * is a limit genuinely being exceeded, not advice being followed in the wrong phase. The
	 * grey is what says the position has stopped being a reading.
	 *
	 * <p>Lighter than the ladder's equivalent, which it otherwise matches. That mark sits over
	 * the world and this one sits on a panel with a grey of its own behind it, and the same
	 * value that reads as neutral over sky and ground disappears into this background.
	 */
	public static int speedoPeggedColor = 0xFF8C9298;

	public static boolean showSpeedoLabels = true;
	public static boolean showSpeedoBorder = true;
	public static double speedoOpacity = 0.45;

	public static boolean visible(int mode, boolean gliding) {
		return enabled && mode != 2 && (mode != 1 || gliding);
	}

	private VarioConfig() {
	}
}

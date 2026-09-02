# Design notes

Why Elytra Vario is built the way it is, and what Minecraft 26.2 does differently from what
the docs and the internet will tell you. For what the mod is and how to use it, see the
[README](README.md).

## Scope

Most of this *observes*: pitch, speeds, energies and the chart's cursors are all measurable
from the player entity, and none of them need a simulation. Two readings are not, and both ask
the same question — what would the next tick do at a pitch that is not being flown? The
optimal pitch bug asks it about the velocity the player actually has; the chart's heatmap asks
it about every velocity the chart can draw.

So the mod carries a copy of vanilla's `updateFallFlyingMovement` and a search over it. That
was a deliberate expansion, not a quiet one, and it is kept as small as it can be: forty lines
of vector arithmetic and a loop, in `ElytraPhysics` and `OptimalPitch`, with `EnergyField`
doing nothing but calling that loop in a grid. Nothing else in the mod depends on any of them.
The wider simulation work — longer horizons, dynamic programming over whole cycles, the
policies those produce — belongs to [elytrasim](https://github.com/HactarCE/elytrasim) and
stays there. What has been brought over is one tick of lookahead and nothing more.

**The physics is copied from bytecode, not written from the wiki.** Elytra motion is a chain
of single-precision operations whose rounding is load-bearing, so `ElytraPhysics` uses
vanilla's own `Mth` — whose `sin` and `cos` are a 65536-entry lookup table, not libm — and
keeps vanilla's float constants as floats, because `0.99F` widens to `0.9900000095367432` and
not to `0.99`. Where vanilla calls `Math.cos` rather than `Mth.cos`, as the lift term does,
so does this.

That is checkable without the game, and it was checked: for a velocity of
`(0, 0.065042850, 1.061452210)` the port and elytrasim agree on an energy change of
`-0.008830925` at a pitch of `2.204123497°` and `-0.008820132` at zero, to nine decimals.

## Measurement

**Energies are heights.** Dividing energy by gravity turns it into the altitude that energy is
worth. Potential energy then *is* altitude, kinetic energy is `v²/2g`, and a cycle's gain can
be read off directly in blocks. Absolute total energy is arbitrary — altitude has an arbitrary
origin — so differences are what matter, which is why `TE RATE` and `GAIN` exist.

**Blocks/tick internally, blocks/second on screen.** Vanilla physics works in blocks/tick, so
that is what is stored and what the chart domain is configured in, keeping numbers comparable
with elytrasim. Conversion happens only at the point of display.

**Velocity is measured from position change, not `getDeltaMovement`.** The latter is the
velocity the player is *trying* to have; gravity keeps it pointing downwards even while stood
still, because collision cancels the motion after the fact rather than by changing the vector.
The two agree in free flight and diverge only on contact. `getKnownMovement` is not an
alternative — for a directly controlled player it falls through to `getDeltaMovement`.

**Potential and total energy are shown from the apex, not from sea level.** Both are anchored
to an arbitrary datum — altitude zero is wherever the world says it is — so the absolute figure
answers no question anyone flying a cycle is asking. Measured down from the last apex they
answer the one that matters: how far below the top am I, and how much of that is coming back.
The raw height stays on the row, dimmed, because it is what F3 and a map agree with. Kinetic
energy is left alone; speed has no arbitrary origin, so its absolute value is already the
reading, and its value at the apex was dropped rather than kept for symmetry.

Both are coloured on the same green-and-red scale as the rate readouts, which for total energy
says the thing worth knowing at a glance: below the last apex is energy still owed, above it is
a cycle that has already paid for itself.

**Two-figure rows reserve fixed columns.** Each figure is right-aligned inside a width measured
from a template string, so a change in digit count cannot shove the figure beside it sideways.
Every value has a fixed number of decimals and a fixed suffix, so right-alignment also pins the
decimal point; the only motion left is a leading digit appearing, which is the least a changing
number can do. Templates rather than pixel constants so the columns follow the font.

**Nothing is recorded while the game is paused.** `Minecraft.tick()` is called with no pause
guard — only the level's entities stop — so client ticks keep arriving in the menu, and the
player's position stops changing. Every one of those ticks would otherwise record a sample with
zero velocity, filling the ring buffer with a standstill that never happened and dragging the
rate readouts to a false reading. The buffer is held rather than cleared, so unpausing resumes
from the sample before the pause and the first velocity after it is one honest tick of
movement. The guard is `isPaused`, which means specifically that the local integrated server is
stopped: multiplayer and the `/tick` commands are left alone, because under those the world
really is running and the player really can still move.

**One cycle clock, latched at the apex.** Energies are all latched from the same `Sample`, so
`KE + PE = TE` holds. Independent per-metric peak detectors do not work: kinetic energy crests
at the bottom of the dive and potential energy at the top, so the held figures would not sum.
The apex triggers on *entering* a descent rather than on having climbed first, so that walking
off a ledge closes a cycle too.

## The chart

**Its scale is one number.** Both chart dimensions derive from `chartScale` (pixels per
block/tick), so a pixel is worth the same change in speed on both axes whatever the domain is.
Widening a range grows the chart rather than rescaling it.

## The delta-TE heatmap

Behind the chart, every pixel is colored by the most total energy one tick could gain from
the velocity that pixel stands for — the optimal pitch bug's reading, evaluated everywhere
instead of only where the player is. The bug says which way to point now; the map says where
in velocity space energy can be made at all, which is the shape a pump cycle has to be flown
around. The feature that matters is the boundary between the signs: inside it a cycle can pay
for itself, outside it nothing can.

**It is two-dimensional because the physics is indifferent to heading.** Velocity has three
components and the search needs a yaw, which ought to make this a four-dimensional table. It
collapses because rotating the world about the vertical axis rotates every term with it, so
the field is computed once at yaw zero with the velocity laid along the look direction and is
then correct for every heading.

**What that misses is sideslip.** The horizontal axis is total horizontal speed and the field
assumes all of it is going where the nose points. In a turn some of it is not, and the reading
under the yellow cursor is optimistic by exactly the gap to the cyan one — which is the reason
both cursors are drawn.

**Negative horizontal speed is a real state, not padding.** The axis starts below zero so the
origin sits inside the chart, and the strip to the left of it means moving backwards relative
to the look direction. The physics has a definite answer there: the turning term hauls the
velocity round to face forwards, which is violent and expensive. It is drawn because it is
defined, not because it is common.

**The build is cached and it is not baked.** A cell costs 179 ticks of physics and the default
domain is 14,161 cells. That is about 30ms of arithmetic warm and **ten times that cold**,
because the first build is also the first few million interpreted executions of the physics —
so the honest figure is roughly a third of a second of freeze on the frame it first happens,
and nothing afterwards until gravity or the chart's domain changes. Gliding for a few seconds
first warms the same code through the optimal pitch bug and makes the build cheap.
Precomputing it at compile time would avoid the hitch and is deliberately not done: the domain
is meant to become adjustable, and a table that cannot follow the axes it is drawn against
would be worse than no table. The whole-degree sweep is also not refined here, unlike in the
bug: refinement is worth at most 0.00012 blocks/tick anywhere in the envelope, far below one
step of an eight-bit ramp, so it could not move a single pixel.

**Drawn as a texture, after an attempt at rectangles failed.** The first version drew the
field with `fill`, one rectangle per horizontal run of equal colour. Merging runs needs the
values quantised, and the quantisation was the problem. At a step coarse enough to be worth
doing — 24 levels a side, which cut 14,161 rectangles to about 3,000 — the ramp banded
visibly. At a step fine enough not to band, almost nothing merged: an unquantised field still
comes to 10,919 runs on the default domain, so the whole trick bought about 20% for a picture
that looked worse. Both ends of that trade are bad, so the field goes into a `DynamicTexture`
and is blitted as one quad: no per-frame allocation, no quantisation, and the eight bits a
channel the screen has anyway are the only rounding left. It also stops a display concern —
how many colours to allow — from deciding how the physics results are stored.

`DynamicTexture` samples `NEAREST` and the blit is one texel to one GUI pixel, so nothing is
filtered on the way to the screen. Three costs with three separate triggers: the field rebuilds
on a domain or gravity change, the pixels repaint on a colour or scale change, and the texture
object is reallocated only when the chart changes size.

### Its colors

**Opaque, unlike everything else on the HUD.** The panels are translucent because they are
chrome and the world behind them is worth seeing. This is data. A translucent heatmap would
make the same energy figure read as one color over the sky and another over the ground, and a
map whose colors depend on what is behind it is not a map. The rest of the chart — grid,
trail, both cursors — is drawn over the top, and the grid stays translucent on purpose, since
it is a reference rather than a border.

**Cool for loss, warm for gain, near-black at zero.** It is an elytrasim scheme with
elytrasim's two problems fixed. There, zero was two nearly identical
dark purples, so the one boundary worth seeing was invisible; here the sign flip is a black
seam that reads at a glance. There, both arms started saturated and dark and got muddier
before they got brighter; here each arm climbs in lightness and in chroma together.

Both arms also end in a real colour rather than fading into the panel. An early version had
the losing arm barely leave the background, on the theory that the common case should recede —
but the losing region is most of the chart and most of a cycle, and rendering the part you
spend the most time in as almost-nothing says nothing about it.

**The hues are chosen against the rest of the chart, not for their own sake.** The trail is
teal and the cursors are yellow and cyan, so the ramp keeps out of that arc entirely and every
mark stays legible over every part of the field. Green-for-gaining would match the panel's
`TE RATE`, and the two colors to swap in for it are in the config, but it is not the default:
red and green are the one pair a color-blind eye cannot separate, and green sits close enough
to the trail's teal to blur it.

**The magnitude compresses rather than clips.** `|x| / (|x| + chartFieldScale)`, with the
scale at 0.6 blocks/tick. The field spans about three and a half blocks/tick end to end while
everything worth looking at happens in the first tenth of that, so a linear ramp would
saturate almost everywhere and show nothing. Compression never clips, so the extremes stay
distinguishable from the merely large, and it is smooth through zero, so the boundary is a
clean seam and not a step.

**The palette interpolates in sRGB, not in linear light.** Linear light is the correct way to
mix two lights, but this is not mixing light — it is laying out a scale. On a ramp from
near-black to a saturated color the linear version spends most of its length near the dark end
and arrives at the mid-tones desaturated. Plain sRGB keeps the hue and spaces the steps about
as evenly as the eye reads them.

**No zero contour.** Drawing the boundary as an explicit line was tried and dropped: it is
already the strongest edge on the map, and a bright line over it hid the structure either side
of the thing it was pointing at.

### Where the axes stop

The top of the vertical axis is set by what a working cycle reaches, not by what the physics
allows. elytrasim's optimised 300-tick cycle peaks at about 1.76 blocks/tick of climb, so the
axis stops at 2.0; terminal velocity is nearer 3.5, and reserving room for it would make the
chart enormous in order to show a state nothing useful passes through.

A cursor outside the domain is clamped to the edge rather than dropped, so it silently stops
being a reading and becomes a floor or a ceiling, with no cue that it has happened. **A domain
that slid to keep the cursor inside would fix that, and is noted here as a possible future
feature rather than a plan.** The costs are real: distances on the chart would stop meaning a
fixed change in speed, which is the property the single `chartScale` exists to guarantee; the
heatmap would have to be rebuilt as the domain moved, which is a third of a second the first
time and 30ms after; and a map that moves under a cursor that is trying to stay still is a
different instrument from a fixed one, quite possibly a worse one.

## The pitch ladder

**It is conformal.** Every mark is projected through the same camera the world was drawn with,
so the rung labeled `-20` lies exactly along the ground that is twenty degrees above the
horizon. The ladder and the terrain move together, which is what makes it readable at a glance
rather than by being studied.

**Tiers do the reading, not the digits.** Length and weight both carry the tier, so an angle
can be recovered from the pattern alone in peripheral vision: faint stubs every two degrees
near the center, short rungs every ten, longer ones every twenty, and the datum lines — the
horizon and ±40 — longest and brightest. Strength runs strictly downhill across the four, and
the two weakest share an RGB so that nothing but alpha separates them. Only the twenties are
labeled, and faintly. A digit on every rung is the clutter this arrangement exists to avoid.

Every tier is solid. The ten-degree rungs were dashed at first, which made a third channel say
what length and strength had already said — and at that size the dashes mostly read as noise
rather than as a pattern.

**Nothing marks which side of the horizon a rung is on.** The sky, the ground and the labeled
datum line already say which way up the world is, so spending dash pattern or color on it
would be spending the ladder's only two visual channels on the one fact that never needs
saying.

**Detail follows the eye.** The fine ticks cover only the span the camera is pointing at and
fade to nothing at the edge of it, which buys resolution where it is being used without
paying for it across the whole ladder. Their pitches are absolute multiples of the fine step
rather than offsets from the camera, so they are real angles that the view slides across
instead of a scale that follows the head around.

**Rungs are labeled in raw Minecraft pitch**, agreeing in sign with the `PITCH` row, with F3
and with elytrasim: negative is above the horizon. That reads backwards for a spatial
instrument, and the aviation convention was considered and rejected — having the ladder
disagree in sign with the readout two inches away is worse than having it disagree with
aviation.

**The ladder is yaw-locked, and the marker is not.** A rung is the set of directions at one
pitch, which is a circle on the view sphere, and a circle projects to a conic — so a rung is
only truly straight where it crosses the center of the screen. Drawing straight horizontal
rungs symmetric about the center is exact in the middle and bows away from the truth towards
the ends; at the default rung length that error is well under a pixel. The flight path marker
has no such problem, because a point projects to a point: it is placed exactly on both axes,
which is why it can show sideslip honestly.

**The band is an angle, the rungs are pixels.** How far the ladder reaches above and below
center is configured as a fraction of the view height, because the projection scale is itself
proportional to that height — a fixed pixel band would cover a different slice of sky at every
GUI scale. Rung lengths stay pixel counts for the opposite reason: they are sized against the
labels, which are text and do not scale with the view.

**The band is asymmetric.** Below center the hotbar and the rest of the vanilla HUD want the
room; above it there is nothing, and stopping short costs real range, because in a pump cycle
the horizon is the mark being aimed at from a long way nose-down and it is the first thing a
low ceiling clips. Above that there is a limit no setting can lift: the horizon is only in the
rendered view while looking down less than half the field of view, so at the default 70 it is
gone past 35° regardless of what the ladder would like to draw.

**Marks fade at the edge of the band.** Without a taper a mark leaves by blinking off, which
in an instrument that is mostly watched peripherally reads as a flicker at the top of the
vision rather than as something departing. The fine ticks carry two independent fades that
multiply — one for leaving the span the camera is looking at, one for approaching the edge.

**No aspect ratio in the projection.** Minecraft's perspective matrix is built from a vertical
field of view and the viewport height, so the pixel scale is the same on both screen axes: a
direction `t` units of tangent off the camera axis lands `t * halfHeight / tan(fov / 2)` pixels
from the center. The GUI's orthographic projection covers the whole framebuffer, so that
half-height can be taken in scaled GUI pixels.

**Everything is referenced to the camera, not the player.** `Camera.xRot()` and the camera's
own basis vectors are what the world was actually drawn with, so the ladder stays glued to the
world in third person and in the mirrored front view, and it interpolates smoothly between
ticks instead of stepping at 20 Hz. The flight path marker is the exception that proves it:
velocity only exists per tick, so it is averaged over a few of them, since an un-smoothed
marker visibly jitters in a way the numeric readouts do not.

**Marks are drawn on a fractional pose.** Rounding each mark to a whole scaled pixel makes the
ladder climb the screen in visible steps, worst on the labels, whose glyphs jump as a block.
Each mark is therefore drawn on a pose translated by its own fractional part, which pushes the
quantisation down to the physical pixel the GUI scale is drawn at.

## The bugs

**Four of them, because the optimum is piecewise myopic.** An optimised 300-tick cycle divides
into a dive, a snap to level, a flick to near-vertical and a gain phase, and through the two
long phases the globally optimal pitch agrees with a simple rule of the current state — a
different rule in each. So the ladder carries a bug per rule rather than one cue pretending to
cover the whole cycle, plus a gray reference bug the dive's rule is read against. What it
deliberately does **not** carry is any indication of which phase you are in. The switch points
are learnable — tuning them independently rediscovers the optimum's own — but they are the open
part of the problem, and a display that guessed would be inventing the answer rather than
showing the evidence.

**They share one band and are ranked by height.** The center gap is the only radius on the
ladder that nothing else ever draws in, so all four have to live there, and they land on the
same rows whenever two rules agree — which is common. Colour alone would turn that into one
mark of indeterminate hue. Ranking them by rise and drawing tallest first makes an overlap
*nest* instead: the apexes coincide, the taller shoulders show past the shorter ones, and the
pile reads as a set of chevrons. Which bug gets which height is a display choice tuned in
flight and carries no claim; only two things about it matter structurally, that the heights are
distinct and that the flat pair of stubs is the velocity bug, the one mark of the four that is
not advice. It is also what is left when colour has been spent, since a pegged bug takes the
same gray whichever one it is. Nothing enforces the ordering — `drawBugs` draws in a fixed
sequence that has to be kept in step with the rises by hand.

**Neither rule bug pegs; both leave the ladder.** The original behaviour was to hold a bug at
the edge of the band and turn it gray, which reads as a direction to keep going in. That
reading is wrong for a rule that only governs one phase, and both of these do. The lookahead's
off-ladder answer through the dive is not a limit being approached but the *far mode of a
bimodal choice* — stay level against zoom now at 40–50° nose-up. The hold's is a steep
nose-down pitch it starts reporting once the dive is over and the rule has stopped applying.
Pegged, each would park at a stop for whole phases while inviting you to fly a rule exactly
where it is not the rule.

Leaving is also what each already does when its search returns nothing, which is the second
half of the argument: a bug that is not on the ladder now means one thing rather than two.

The peg survives as a per-bug argument to `drawBug` for the two switched-off bugs, where the
off-ladder answer genuinely is a limit — the one-tick bug's near-90° nose-down through a slow
descent, and the velocity bug's direction of travel, which cannot be anything but where you are
going. That per-bug shape is the honest one for something that was always described here as a
cheap courtesy rather than a necessity.

**Only the two rules are on by default.** The one-tick bug and the velocity bug are both
switched off, having been drawn and then flown. The one-tick bug is right only in phases some
other bug is also right in, and is conspicuously wrong in both phases that are actually being
studied. The velocity bug was there so the hold could be read against something, on the theory
that the gap between them is worth watching; it is not, because the hold is flown by putting
the nose on it and the gap is a property of the answer rather than an input to using it. Both
are kept behind switches rather than deleted — the one-tick reading is what elytrasim plots and
what the heatmap colours, and the gap is the demonstration that holding an angle is not
pointing along it.

### One tick of lookahead

**It is greedy, and greedy is only sometimes right.** The search tries every pitch, ticks the
physics once, and keeps whichever gains the most total energy — one tick of lookahead, which
is elytrasim's *immediate optimal pitch* exactly. Flying it every tick does not fly a good
cycle, because a one-tick horizon cannot see that giving energy away now buys more of it back
later. It agrees with the far-sighted answer while energy is being *gained*, and again
wherever it snaps to level; it is the dive that pays for the climb where the short view is
not to be trusted.

**Three regimes, and they look nothing alike.** In a steady glide the answer is exactly level
— not approximately, because the curve has a real cusp at zero where the nose-up term
switches on and straight off again. In a zoom climb it is a genuine interior optimum, tens of
degrees nose-up, moving as speed bleeds away, and this is the regime worth watching. In a
slow descent it runs to the stops near ninety nose-down, which is not a glitch: past about
eighty degrees the wing makes no lift, the flight is a free fall, and at low speed that gains
energy faster than gliding does.

**The sweep stops a degree short of vertical.** `Mth.cos` is a lookup table, and the index it
computes for −90° truncates to zero, so the cosine comes back as exactly `0.0` rather than
something merely tiny. The horizontal look length is then zero, every conversion and turning
term is skipped by its own `> 0` guard, and the flight goes ballistic — no wing at all. It is
real vanilla behavior and it is reachable, since the mouse pins pitch to exactly −90. But it
is a knife edge one hundredth of a degree wide that ties with a nose-straight-*down* dive to
within a millionth of a block, so searching it would let the cue jump between the top and the
bottom of the ladder on the last bits of a double. Excluding both bounds costs at most 0.0012
blocks/tick across the whole envelope, against cycle gains of a few tenths. Note the
asymmetry is the table's and not the model's: +90° returns 1.2e-16 and keeps its wing.

**The winning degree is refined by golden section, not by fitting a parabola.** The maximum
is often that cusp rather than a smooth peak — steep on the nose-up side, gentle on the
nose-down side — and a parabola through the three points around it lands a degree or two down
the shallow slope, which would nudge the cue off the horizon exactly where it belongs on it.
Sixteen steps narrow a two-degree bracket to a thousandth of a degree, which is what stops
the bug walking up the screen in whole-degree jumps.

**It searches at the real yaw.** elytrasim pins yaw to zero, because it is drawing a
two-dimensional velocity grid and has no sideslip to carry. Here the search runs on the real
three-dimensional velocity at the player's real yaw, which is what the game is about to do
anyway, so a turn's sideways speed is carried honestly rather than flattened into the forward
component.

**Once per tick, not once per frame.** The answer is a function of the tick's state, so it is
computed in the recorder alongside everything else. Recomputing it per frame would burn a few
hundred thousand physics steps a second arriving at the same number, and would let two HUD
elements disagree inside one frame.

**Strength does not track the margin.** Fading the bug when the correction is small would
hide it exactly while it is being followed; fading it when the correction is large would hide
it exactly when there is a long way to go and no way to snap to it. So it carries the same
band-edge taper the rungs do and nothing else.

**It pegs rather than leaving, and earns less there than anywhere else.** A whole regime — the
slow descent above — has its answer eighty-something degrees nose-down, far below anything the
band reaches. Held at the limit the bug takes the flight path marker's pegged gray, which
already means a direction to go rather than a place to be.

That is worth little, though, and it is worth being straight about why. Pitch clamps at ±90,
so the stops need no aiming: the mouse finds them on its own, and all a cue can add there is
which way to slam, which the situation already implies. The bug does its real work at interior
angles, where it is a mark to fly to and nothing else supplies one. The peg is kept because it
costs nothing and stays out of the way, not because it is load-bearing.

**It lives in the center gap**, which is the only radius that never meets a rung or a label:
rungs start at the gap's edge and labels sit beyond their outer ends. Everywhere further out
collides — just past the twenty-degree rungs it lands on the horizon in a steady glide and on
the labels near ±20, and clearing the labels entirely puts it so far outboard it stops reading
as part of the ladder. The wedges point inwards so the pair closes on the marked pitch like a
caliper, and frames the crosshair when the pitch being flown is already the best one.

### Twenty ticks of lookahead

**One tick does not fit the gain phase at all.** Measured against the optimised cycle, greedy
pins to the nose-up bound for the first seventeen ticks of the climb and is still forty degrees
off well after that — 17.9° RMS over the phase. Holding a constant pitch for twenty ticks and
scoring the energy at the end of them fits the same phase to **1.14°**. The fit is sharp:
sixteen and twenty-four ticks both land near 3.8°.

**Twenty is a compromise, not a constant, and it is not overfitted.** The horizon whose argmax
actually lands on the optimum starts near twenty entering the gain phase and walks down to
about twelve as the climb develops. Twelve is nose-up of the optimum at *every* gain tick and
thirty-seven nose-down at every one; everything between crosses somewhere and nothing outside
ever does, so the family {12…20} is exactly the set of horizons that are ever right. Fixed
twenty is the best single stand-in and costs about a degree. Re-optimising the whole cycle
against a family of objectives that trade climb for distance leaves the best horizon at 18–23
throughout, so this is not a number fitted to one trajectory.

**Flight is far less picky than the fit.** Re-tuning everything else per horizon, the cycle
flies at 82.9% of optimum on one tick, 96.4% on sixteen, 96.1% on twenty and 97.1% on
twenty-four. So sixteen to twenty-four is a plateau, and even the one-tick rule still flies —
badly — because the switch points compensate.

**The pitch is held constant across the horizon rather than re-optimised each tick.** That is
the point of the reading: it asks what a fixed attitude is worth, not what the best possible
flight from here is worth. The latter is a dynamic program and its answer is the whole cycle,
which is not something a bug can express.

**In the dive it goes bimodal.** Short horizons say stay level, long ones say zoom now around
40–50° nose-up, and the optimum is at neither. The bug will jump between the two answers while
diving. Nothing is done to damp that, because damping it would hide the one honest thing it
has to say there, which is that a lookahead cannot answer this question.

**It costs the horizon in physics ticks per candidate pitch** — measured at 65 µs a call
against 3 µs for the one-tick search, once per client tick. The two searches are memoised on
the recorder for the tick they are asked on, so the HUD's per-frame reads are free; that keeps
the once-per-tick property without the `flight` package having to read display settings, which
it deliberately does not do.

### The hold bug, and the dive

**The dive's rule never mentions energy.** It cannot: energy is being *spent* through the
descent, so every lookahead worth the name disagrees about how to spend it. What fits instead
is the pitch that leaves the flight path angle where it already is — at the optimal pitch, γ
after the tick equals γ before it to within a few tenths of a degree, for **0.73° RMS over 150
ticks with no parameter to tune**.

**The hold leaks, and the leak is the rule.** Flown, γ decays first-order from wherever the
dive is entered towards about 16.8° below the horizon, losing 4–5.5% of the remaining gap each
tick, and the optimum decays the same way. An *exact* hold is a worse rule: it keeps its entry
angle forever and loses height at 3.5 b/s. The asymptote is derivable rather than fitted — it
is the steady glide that maximises forward speed, pitch 53.35°, v_z 3.389, γ 16.58° — but it is
an asymptote and not a bound: steering γ straight at 16.58 instead of holding it is a much
weaker rule, 34° RMS, saturated against the nose-up stop for the first sixty ticks.

**Holding the angle is not pointing along it**, which is the obvious misreading and the reason
the gray velocity bug is drawn at all. By the end of a dive the nose sits about 30° *below* the
flight path — pitch 47° against γ 17° — so the two bugs are nowhere near each other, and the
gap between them is the angle of attack the hold is asking for.

**It is bisected on the residual's sign, not minimised on its magnitude.** The natural phrasing
— the pitch that moves γ least — is a trap: at low speed two separate pitches hold a given
angle, one nose-down and one nose-up, and a search over the magnitude oscillates between the
branches tick to tick. Flown, that scores worse than doing nothing. So the scan runs from the
nose-down stop towards nose-up and takes the first sign change, which is the nose-down root by
construction. Where the residual never changes sign there is no such pitch — a near-vertical
fall cannot be sustained by any attitude — and the search returns `NaN` and the bug is not
drawn, rather than picking the least bad degree.

**Nose-down the residual is smooth; nose-up it is a staircase.** Worth knowing because it
explains a four-thousand-fold difference in the residuals the bisection reports. Nose-down, the
look vector's horizontal part divides out of every term it appears in, so the only pitch left
in the tick is inside the lift term's `Math.cos`, which is libm; measured residuals at the root
are around 1e-7. Nose-up, the climb term calls `Mth.sin` directly, and that is a 65536-entry
table, so the residual steps by up to 5e-4 and the bisection converges on a step rather than a
root. That is a hundredth of a degree of pitch once the low gain has multiplied it back up —
two orders of magnitude finer than the ladder can draw, so nothing is done about it.

**The reading is low-gain, which makes it forgiving to fly and delicate to display.** A degree
of pitch moves the next tick's γ by about a fifteenth of a degree, so any wobble in the
measured velocity arrives at the answer multiplied by fifteen. It is therefore searched against
the same 4-tick averaged velocity the flight path marker uses, which costs it four ticks of
lag. Flying it, the error integrates over 190 ticks, which is the other half of the same fact.

**Open question.** The γ floor is non-monotonic across the family of cycles that trade climb
for distance: it peaks at 16.7–16.8° exactly at the pure-climb cycle, where the
fastest-steady-glide derivation predicts it, and falls off in *both* directions — 14.5° when
distance is weighted positively, 12.8° when weighted negatively. Weighting distance either way
pulling the floor down is counterintuitive, and it is the one place the derivation might be
luck rather than structure.

## Readouts that are off by default

**Angle of attack, in both its forms.** Real, correct, and once unused: the pump-cycle research
did not refer to it, so on instruments watched continuously the `AOA` row and the flight path
marker were both clutter competing with readings in use. Kept behind `showAngleOfAttack` and
`showFlightPath` rather than deleted, because whether angle of attack matters was an open
question rather than a settled one.

**That question has since been answered in one direction**, which is worth being honest about
rather than quietly leaving the switches where they were. The dive's rule is read as a gap
between the hold bug and the velocity bug, and that gap *is* angle of attack — so the ladder
now shows it by default after all, as the distance between a green mark and a gray one. What
the two switches still add over that is a printed figure and the exact two-dimensional
placement with its sideslip. Neither is needed to fly the dive rule, so neither has been turned
back on, but the rationale above no longer covers the quantity, only these two renderings of
it.

Turning the marker off costs the ladder its only sideslip cue, which is the one thing it
carried that the number did not. That is affordable because the chart shows sideslip too, as
the gap between its two cursors — and the chart is the instrument that turning is analyzed on
anyway. The design notes below still describe how the marker is placed, because the code is
still there and still correct.

## Minecraft 26.2 notes

26.2 moved several things. Verify against the actual jars rather than recalling — `javap` the
artifacts under `~/.gradle/caches/fabric-loom` and `~/.gradle/caches/modules-2` — because
several of these are recent renames:

- The HUD is registered through `HudElementRegistry`, and `HudElement` implements
  `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`. There is no `render(DrawContext…)`
  any more; the HUD builds a render state instead of issuing draw calls. The drawing
  primitives themselves (`text`, `fill`, `outline`, scissor, `pose()`) are unchanged.
- The keybinding module is `fabric-key-mapping-api-v1` (package `…client.keymapping.v1`), not
  the older `fabric-key-binding-api-v1`. `KeyMapping` takes a `KeyMapping.Category` object,
  whose label resolves to `key.category.<namespace>.<path>`.
- `Options.hideGui` no longer exists, and no replacement is needed: `Gui.extractRenderState`
  gates the entire `Hud` pass on a visibility flag, so HUD elements are simply never reached
  when the GUI is hidden.
- `Camera.getFov()` is public and returns the **vertical** field of view in degrees, already
  including the modifiers (sprinting, speed effects) that the options value does not. It is
  the number fed to the projection matrix, so it is the one to project against.
- `GameRenderer` has public `projectPointToScreen(Vec3)` and `projectHorizonToScreen()`. The
  latter is exactly `tan(cameraXRot) / tan(fov / 2)`, in units of half the screen height and
  positive upwards; the pitch ladder's rung formula is that same expression generalised to a
  non-zero rung pitch, and is checked against it. `projectPointToScreen` is not used, because
  it projects through the full matrix and so returns a flipped, finite, plausible-looking
  result for points behind the camera rather than an obvious one.
- `Camera` exposes `forwardVector()`, `upVector()` and `leftVector()`. `leftVector()` really
  is left: at yaw 0 the player faces `+Z` (south) and it returns `+X` (east).
- `Matrix3x2fStack` inherits `translate(float, float)` from `Matrix3x2f`, which is what makes
  sub-pixel placement of text and fills possible.
- `outline(x, y, width, height, color)` takes a size, whereas `fill` takes bounds.
  `horizontalLine`/`verticalLine` are inclusive on one end and exclusive on the other, so
  `fill` is used for gridlines to avoid an off-by-one.

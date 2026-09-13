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
origin — so differences are what matter, which is why apex differences and `GAIN` exist.

**Blocks/tick internally, blocks/second on screen.** Vanilla physics works in blocks/tick, so
that is what is stored internally, keeping numbers comparable with elytrasim. The config screen
accepts chart bounds in blocks/second and converts them to the internal units.

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

**Its size is one number.** `chartSize` is its exact width in pixels; the height follows the
ratio of the two domains, so a pixel is worth the same change in speed on both axes whatever
the domain is. Widening the vertical range grows the chart rather than rescaling that axis.

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

One exception, and it is the reason the blit takes a destination rectangle at all: a chart
being resized by a drag asks for a new size every pixel the pointer moves, and each of those
would be a rebuild. For the length of the drag the field it already has is stretched over the
chart instead. Only the scale changes under a drag, never the domain, so the stretched field
describes exactly the velocities the chart is drawing — it is wrong in resolution and in
nothing else, and nearest sampling showing its cells as blocks is the honest picture of that.
The exact field is built once, on release.

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
`GAIN`, and the two colors to swap in for it are in the config, but it is not the default:
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
fixed change in speed, which is the property the chart's locked aspect ratio exists to
guarantee; the heatmap would have to be rebuilt as the domain moved, which is a third of a
second the first time and 30ms after; and a map that moves under a cursor that is trying to stay
still is a different instrument from a fixed one, quite possibly a worse one.

## The speedometers

Both speedometers deliberately present the same three magnitudes in different visual forms.

### Bar speedometer

**It says nothing new, deliberately.** Total, horizontal, and vertical speed are already three
rows on the readout panel. The bars trade exact digits for shapes and aligned heights that can
be compared in peripheral vision. Their fixed order is `Y`, `XZ`, `XYZ`, matching the component
names used elsewhere in the HUD.

**Vertical speed is a magnitude.** A zero-based shared scale cannot carry its sign, so the Y bar
shows `|vy|`. Direction remains visible in the world and explicit in Flight Stats' signed
`SPEED Y` row.

**Reference markers are per component, not one line across unlike quantities.** The white
max-horizontal-speed set marks the steady +53.366° solution: 20.191 b/s Y, 67.776 b/s XZ, and
70.719 b/s XYZ. The gray terminal set marks straight-down steady state: 78.400 b/s Y and XYZ,
and zero XZ. Both sets are independently toggleable and use one-pixel horizontal lines so they
remain legible over a filled bar without competing with it.

**Bars peg rather than leave**, unlike ladder markers. A speed above full scale is still useful
as a limit being exceeded, so its bar reaches the top and turns gray to say its height is no
longer an exact reading.

**The panel is measured around what it shows.** Every part that can be switched off takes its
space with it: the scale labels' column and the half line of clearance the topmost label needs
above the plot, and each bar's slot. A panel with its labels off is narrower rather than
emptier. The bars keep their fixed order but not fixed positions; hiding one closes its gap,
because the panel is the readings shown side by side rather than a frame with three places in
it.

**Only the switch decides whether the labels are there.** The panel used to drop them on its
own once the major step was too fine for them to sit clear of one another, which made the
panel's width depend on its height: dragging the plot shorter made the label column vanish and
the whole panel jump sideways under the pointer. Labels that crowd at a fine step are the
step's problem and are visibly so, which is better than a panel that changes shape for reasons
the person resizing it cannot see.

### Dial speedometer

The semicircular dial trades aligned heights for angles that can be caught in peripheral
vision. Total, horizontal, and vertical speed use longest, middle, and shortest needles so
coincident readings remain distinguishable even when color does not. Vertical speed is again
`|vy|`, and a reading beyond full scale pegs at the right stop in gray.

Each needle's acceleration arrow begins at its tip and follows a concentric arc to the speed
projected one second ahead. The three radii keep the arrows separate just as the needle lengths
do, so they can use their matching needle colors instead of the bar chart's white overlay color.
The arc rides half its own stem inside the tip radius rather than centered on it, which puts the
stem's outer edge flush with the end of the needle; centered, half the stem hung past the tip
and the arrow read as overshooting the reading it starts from.

**The head ends in a snub, not a point.** Its two barbs run back and out from the corners of the
stem's own end face, and it is each stroke's leading *corner* that sits on a corner, so nothing
in the head reaches past where the stem stops. Barbs aimed at the stem's centerline instead —
the obvious construction — cross it and stand a third of a pixel proud of the nose. A third of a
pixel cannot be drawn: it rounds to whichever side of the centerline it happens to fall on, so
the arrow grew a one-pixel spike that changed sides with the angle and read as a point pulled
off-center. The nose is now exactly one stem wide, which is the smallest mark the arrow can end
in and still be the same object as its stem.

**Reference markers turn ninety degrees, not into arcs.** Both sets mark the same speeds they
mark on the bar chart, and for the same reason each is three marks rather than one: the
terminal state is 78.400 b/s XYZ and Y and zero XZ, which is not a single speed to draw a ring
at. What changes is the axis. On the bar chart the value axis is vertical, so a marker is a
horizontal line laid across its bar; here the value axis is the angle, so a marker is a radial
line laid across its needle's reach. The needle tips are a constant 0.18 of the radius apart,
which gives each component a lane, and a mark reaches a third of that gap either side of its
tip so it stays in its lane with daylight around it. The two sets never crowd each other: the
closest pair is the total needle's own, 70.719 against 78.400 b/s, seventeen pixels apart along
its lane at the default radius. The marks are drawn under the needles,
unlike the bar chart's: there a marker inside a filled bar would simply be gone, while here a
mark is hidden only at the instant its needle agrees with it, and an unbroken needle is the
quieter of the two.

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
velocity only exists per tick. The flight path marker, velocity pitch, and hold pitch use
the latest sampled velocity directly, without temporal smoothing.

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
ladder that nothing else ever draws in, so all three have to live there, and they land on the
same rows whenever two rules agree — which is common. Colour alone would turn that into one
mark of indeterminate hue. Ranking them by rise and drawing tallest first makes an overlap
*nest* instead: the apexes coincide, the taller shoulders show past the shorter ones, and the
pile reads as a set of chevrons. Which bug gets which height is a display choice tuned in
flight and carries no claim; the one thing about it that matters structurally is that the
heights are distinct. Nothing enforces the ordering — `drawBugs` draws in a fixed sequence that
has to be kept in step with the rises by hand.

**No bug pegs; each leaves the ladder.** The original behaviour was to hold a bug at the edge
of the band and turn it gray, which reads as a direction to keep going in. That reading is
wrong for a rule that only governs one phase, and both rules do. The lookahead's off-ladder
answer through the dive is not a limit being approached but the *far mode of a bimodal choice*
— stay level against zoom now at 40–50° nose-up. The hold's is a steep nose-down pitch it
starts reporting once the dive is over and the rule has stopped applying. Pegged, each would
park at a stop for whole phases while inviting you to fly a rule exactly where it is not the
rule.

Leaving is also what each already does when its search returns nothing, which is the second
half of the argument: a bug that is not on the ladder means one thing rather than two.

The one-tick bug went the same way, and the peg with it. Its off-ladder answer is closer to a
genuine limit — near-90° nose-down through a slow descent — but pitch clamps at ±90, so the
stops need no aiming and a mark there only names a direction the situation already implies. One
rule for every bug is worth more than a courtesy that was never load-bearing.

**Only the two rules are on by default.** The one-tick bug is switched off, having been drawn
and then flown: it is right only in phases some other bug is also right in, and is conspicuously
wrong in both phases that are actually being studied. It is kept behind a switch rather than
deleted, because the one-tick reading is what elytrasim plots and what the heatmap colours.

**A fourth bug, gray, marking where you are actually going, has been deleted.** It was there so
the hold could be read against something, on the theory that the gap between them is worth
watching; it is not, because the hold is flown by putting the nose on it and the gap is a
property of the answer rather than an input to using it. It was also the flight path marker's
quantity drawn a second time and drawn worse — one axis instead of two, no sideslip, read
against another bug instead of against the crosshair. `showFlightPath` is where that reading
lives.

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

**It leaves rather than pegging, and loses nothing by it.** A whole regime — the slow descent
above — has its answer eighty-something degrees nose-down, far below anything the band reaches,
so the bug is gone for the length of it. Pitch clamps at ±90, so the stops need no aiming: the
mouse finds them on its own, and all a cue held there can add is which way to slam, which the
situation already implies. The bug does its real work at interior angles, where it is a mark to
fly to and nothing else supplies one.

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

**Holding the angle is not pointing along it**, which is the obvious misreading and the thing
the flight path marker is worth turning on to see. By the end of a dive the nose sits about 30°
*below* the flight path — pitch 47° against γ 17° — so the green bug and the marker are nowhere
near each other, and the gap between them is the angle of attack the hold is asking for.

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
measured velocity arrives at the answer multiplied by fifteen. It is searched against the
latest sampled velocity, matching the flight path marker and velocity pitch without adding
smoothing lag.

**Open question.** The γ floor is non-monotonic across the family of cycles that trade climb
for distance: it peaks at 16.7–16.8° exactly at the pure-climb cycle, where the
fastest-steady-glide derivation predicts it, and falls off in *both* directions — 14.5° when
distance is weighted positively, 12.8° when weighted negatively. Weighting distance either way
pulling the floor down is counterintuitive, and it is the one place the derivation might be
luck rather than structure.

## Direction-of-travel reading

The flight-path marker is the sole direction-of-travel reading. Its vertical gap from the
crosshair shows angle of attack spatially, while its horizontal gap shows sideslip. The former
numeric `AOA` row expressed only the first of those dimensions, so it and its setting were
removed rather than maintaining two renderings of one reading. The marker defaults to on; the
chart also shows sideslip as the gap between its two cursors.

## Toggle keys

Each of the six instruments — pitch ladder, ladder markers, velocity graph, flight stats, bar
speedometer, and dial speedometer — has a key that switches it off and back on. A seventh
visibility key switches the master HUD setting, independently of the key that opens the settings
screen. All seven visibility keys are unbound by default. Not the individual
ladder markers: those are seven settings behind one switch on one page, and the markers are read
as one overlay, so seven more binds would buy nothing that switching the whole set off does not.
Unbound by default because seven keys is a lot to take off a keyboard that already has `V` on it
for an action most flights never need, and because the settings screen puts the binding control
directly beneath the pair of switches it flips, so anyone who wants one finds it there.

**Visibility was one three-way choice and is now two switches**, which is what makes the keys
work. Always / only while gliding / hidden puts *off* in the same setting as *when*, so a key
that toggles it has to remember which of the other two values to return to, and has nothing
sensible to do when the setting already reads hidden — the first press either does nothing or
silently overwrites a preference. Asking the two questions separately — is this instrument on,
and is it wanted only while gliding — removes both problems at once. The key flips one boolean,
and *only while gliding* is a preference that survives being switched off and on. It is also
less to explain: two checkboxes rather than a dropdown whose third value is not about the same
thing as its first two.

**The key writes the setting and saves it**, rather than holding a runtime override on top of
it. That is what makes it a toggle rather than a mode: there is one place the answer lives, the
settings screen shows what the key did, and a HUD you switched off stays off across a restart,
which is the honest reading of having switched it off. It goes through the same schema the
settings screen writes through, so the write is validated and the file is replaced by exactly
that code; a failed write is logged and the toggle still applies for the session.

The binds themselves are ordinary `KeyMapping`s registered with the game, so they are in the
vanilla Controls list like any other. Rebinding them from the mod's own screen is a convenience
on top of that, not a second store: the row sets the same mapping and writes `options.txt`
immediately. That is the only arrangement in which the two menus agree — a bind this screen held
back until later would be silently reverted by a Cancel in the vanilla one — and it is why the
toggle key row is the one row on a page that Reset does not touch.

Ordinary gameplay mappings are not queued while a screen owns keyboard input. The settings
screen therefore matches its own key events against all visibility mappings directly, just as it
does for its open/close key, so a module or the whole HUD can be toggled while its live preview is
being adjusted. Text fields retain ordinary characters. If mappings conflict, every matching
visibility action still fires, preserving vanilla's conflict behavior.

Config files written before the split still read: `ConfigStore` translates a retired
`*Visibility` value into the pair, since the alternative is that every hidden instrument quietly
comes back. The old key is not written back, so one save finishes the migration.

A key already bound elsewhere is shown in red with the conflicting binds named, and allowed.
Vanilla allows the clash too; what it does is fire both actions, which is occasionally what was
wanted, and refusing it here would mean this screen enforcing a rule the Controls screen does
not.

## Placing the modules

Seven modules carry a position: the velocity graph, the four flight stats panels, and the two
speedometers. Each is an absolute top-left in scaled GUI pixels, clamped so that it stays on
screen, and each can be set from its own page — or, for the stats panels, its own subpage — as a
pair of numbers, or moved in the world with the settings screen open. Clicking a module opens
its settings; dragging its middle moves it; dragging a corner resizes it; the arrow keys move
the selected module a pixel at a time.

**A page may carry more than one module, and then its subpage selector chooses between them.**
Flight Stats is the case: one subpage per panel, so the selection the arrow keys and the quiet
outline follow is whichever panel's settings are on screen, and clicking a panel in the world
moves the dropdown to it. A page carrying one module leaves its group unset and keeps that
module selected whatever subpage is showing, which is what the speedometers want — their
subpages are one bar or needle each, not one module each.

**Corners resize, edges do not.** Three of these modules are a single size setting — the
graph's width, the bar speedometer's plot height, the dial's radius — so there is no such thing
as a nonuniform resize to offer, and an edge would have nothing to drag that a corner does not
already drag. The stats panels are the exception, each with a width and a height of its own, and
a corner drags them separately: pull it sideways and only the width moves. That is what a pair
of edges would do one at a time, so it still does not need them.

**One setting, a pointer with two dimensions, so the answer is least squares.** The size chosen
is the one whose box comes closest to the box the pointer is asking for. Where both axes follow
the setting that is the pointer projected onto the box's diagonal, which is what dragging a
locked-aspect corner looks like anywhere else; for the bar speedometer, whose width is its bars
and labels rather than a setting, the same expression collapses to following the pointer
vertically and ignoring the rest.

**Two settings are solved one at a time.** A stats panel's width and height each answer one
pointer axis. Width is established first, then height is capped at the largest text size whose
content minimum fits inside that width. Inside that boundary the axes are orthogonal; at the
boundary the cap is the necessary coupling. Without it, pulling a corner mostly vertically
could enlarge the text first and then force the panel's far horizontal edge well past the
pointer merely to contain it.

**The box is affine in its settings, and the constant is measured rather than modelled.** A
slope — the graph's aspect ratio, one per axis for a stats panel, two for the dial's diameter —
plus the size the module is currently drawn at pins the whole relationship, so whatever the box
carries that the settings do not pay for, like the label column or the dial's rim, falls out as
the difference between them. After the setting is applied the module is measured again and the
pinned corner recomputed from that, rather than from what the arithmetic predicted: the primary
size is exact, but an aspect ratio can still leave the other dimension between pixels, and over
a long drag that rounding would otherwise walk the corner it is supposed to be holding still.

**A resize snaps to the rests a move snaps to, and to no others.** A module should come to rest
in the same places whether it was carried there or grown there; a rest that only one of the two
knows about is one nobody can predict. A move relates two whole boxes, so on each axis it offers,
against another module:

- near edges flush or far edges flush;
- the box set down a margin clear of the other, on whichever side it is approaching from;
- the box butted against the other, overlapping it by one pixel, on that same side;

and against the screen: the near edge a margin in or the far edge a margin in.

In general, snapping starts from the closest legal unsnapped transform requested by the pointer,
then analytically generates every reachable transform which satisfies one of those edge
relations. Its moving edge must be within the snap distance of the rest on the axis which offers
the relation, measured in rendered screen space. A free move, or a resize whose settings control
its dimensions independently, chooses the nearest candidate independently on each axis so that a
corner can take two alignments at once. A coupled size such as the graph's width or the dial's
radius instead chooses the candidate whose dragged corner is closest to the pointer in both
dimensions. After applying the answer, the editor measures the final integer bounds and draws
every target edge whose exact relation is satisfied by them.

A resize holds one edge still, so it can only take the rests its moving lines can reach, which is
that same list read line by line. The dragged edge takes the rest of its own kind — a right edge
on a right edge, a left edge on a left edge — the margin clearance and the butt on its own side,
and the screen margin on its own side. The pinned edge takes nothing, because it is not going
anywhere.

So an edge dragged rightwards rests flush on a right edge, a margin short of a left edge, or one
pixel past it, and never flush against a left edge: a move would not put two modules together
with two borders abreast either. Centers are deliberately not snap targets: when boxes share an
edge and a center, the edge is the single visible explanation for where the drag came to rest.

**The butt overlaps by one pixel rather than meeting at zero.** Every panelled module draws a
one-pixel border, so setting two of them down edge to edge puts two identical gray lines side by
side — a two-pixel rule that reads as a seam rather than as a division. Overlapped by one they
share a column, and a row or stack of butted panels is ruled exactly the way the single stats
panel used to rule between its own halves: one line, the same weight as the outline around the
pair. Nothing is lost to it. The shared column belongs to both borders, and a border is chrome
rather than a reading, so the only pixel either module gives up is one it was spending on saying
where it ends — which is what the shared line now says for both.

The butt and the margin are five pixels apart, so at the default snap distance of four there is
no position between them from which neither is reachable; the drag lands on whichever it is
nearer. This is what the four stats panels are stacked with by default, and it is offered
between any two modules and on either axis, not only between panels.

Only one answer can win, because one setting may place both moving edges. Every reachable rest
proposes a size, and the size whose resulting corner is closest to the mouse in both dimensions
chooses the answer. Every marker which independently produces that same answer appears with it;
constraints proposing another size are not carried into rendering. A rest the setting cannot
actually reach is passed over for one it can, which happens whenever a module's size comes in
steps, as the dial's diameter does. The winning markers are checked against the module as it ends
up rather than merely against the size that was aimed at, so a line appears only where its exact
flush, margin or butt relation is genuinely satisfied after layout rounding.

The calculation is always made from the box and setting at the start of the drag. Feeding the
previous frame's rounded box back into the next frame would make the arithmetic depend on which
rest won last, allowing nearly equivalent edge answers to trade places under a steady
pointer. Alongside the snapped answer, the calculation returns the unsnapped box requested by the
pointer. That box is the translucent true-position outline during a resize, just as the raw
position is during a move; the live module and its guides show where snapping put it.

**The grips say which drag is armed.** The four corners are drawn as thickened corners on the
outline, and the one under the pointer is drawn longer, thicker and white. At the same moment
the white hover outline — which means *this is what a drag would pick up and carry* — goes,
leaving the quieter outline of the module whose page is open. The white is not lost so much as
handed over: it moves from the box to the grip that has taken the drag over, so exactly one
thing on screen is white and it is always the thing the next click will act on. The grown grip
is drawn larger than the area it answers to, which is safe in the one direction that matters:
the pointer is inside the plain reach whenever the larger mark is showing, so the mark never
claims ground a click would not.

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

# old readme which has some stuff that i edited out but is relevant to the design

also this repo should be in american english.

# Elytra Vario

A client-side glide-computer HUD for elytra flight in Minecraft 26.2 (Fabric).

It reads the player's state and displays speeds, energies, a velocity-space chart and a pitch
ladder, so that pump cycles can be flown and tuned by instrument rather than by feel. Some of
the readings are worked out rather than measured: a set of bugs on the ladder marking where
each of an optimised cycle's rules says to point, and a heatmap behind the chart showing where
in velocity space energy can be made.

Client-only: it watches the player and draws, and never talks to the server or changes how
anything flies. It does not need to be installed on the other end.

## Building and running

Needs JDK 25.

```sh
./gradlew build       # compile and package into build/libs
./gradlew runClient   # launch a dev client
```

## Using it

`V` opens settings.

Open Mod Menu's configuration button for Elytra Vario. Seven pages separate Global, Pitch Ladder,
Ladder Markers, Velocity Graph, Flight Stats, Bar Speedometer, and Dial Speedometer. Global holds the
HUD master switch; each instrument’s
visibility control stays on its own page, and a page may divide into subpages under a selector —
one per marker on Ladder Markers, one per bar or needle on the speedometers, one per panel on
Flight Stats. Ladder-marker names identify the displayed quantities;
tooltips define their calculations and describe possible uses. Edits take effect in the HUD as
you make them and save themselves to `config/elytra-vario.json`, so there is nothing to confirm
on the way out: Close and Escape simply close. A half-typed number is held back from both the
HUD and the file until it reads as a number, with the reason shown under the Close button. In game, a
right-side settings panel leaves the HUD visible without blur. Each page has a reset, and less
common controls are under Advanced. The screen reopens on the page, subpage and scroll position
you left, with the Advanced switch as you left it, for the rest of the session.

Position is an absolute top-left in scaled GUI pixels for each of the seven placed modules,
clamped to the screen; the anchors and the graph-to-stats attachment this paragraph used to
describe are gone. With the settings screen open the modules are editable in the world: click
one to open its settings, drag its middle to move it, drag a corner to resize it, or use the
arrow keys for a pixel at a time. Moves and resizes both snap to the other modules and to the
screen, and the coordinate and size fields stay in step with whatever the drag does. See
*Placing the modules* above. Both horizontal and vertical chart bounds are editable, and each
Flight Stats panel carries a width and a height of its own. Visibility is independent for all
four instruments, each stats panel switches on and off on its own, and stats rows are
selectable. Energy rate has been removed; cycle gain and apex differences remain.

## The readout panels

**One panel per kind of reading, not one panel for all of them.** Flight Stats is four boxes —
*Other* (pitch, glide), *Speed*, *Acceleration*, *Energy* — each placed, sized and switched on
its own, each a module of the position editor, each a subpage of the Flight Stats page.

The reason is the width. A row wants the width its widest label and figure need, and one panel
had to give every row the same one, so the panel was as wide as its widest row and every
narrower row carried the difference as gap: `SPEED XYZ` against a speed is 106 pixels of
content, `GLIDE` against a ratio is 76, and thirty pixels of nothing sat between the label and
the figure on every attitude row. Split, the narrow panel can be narrow. What is shared is what
is genuinely shared: the instrument's switch — which is what the toggle key binds to — its
only-while-gliding companion, and the positive and negative colors, which are a palette rather
than a layout.

They are still meant to read as one instrument, which is what the editor's one-pixel butt is
for: stacked with their borders sharing a column, four panels look like one panel ruled into
sections, and that rule is the one the single panel drew between its own halves. The defaults
are exactly that stack, down the left, in the order the single panel read in and at the width
it had.

**Width and height are set separately, and the height is the one that sets the text size.** The
rows are laid out at a fixed line height and the panel is scaled to the height asked for, so it
is always exactly as tall as its rows need and never carries a gap at the bottom. The width then
buys one thing only: the distance between a label and the value right-aligned against the far
edge. That gap is a panel's only dead space, and a single size setting could not close it —
narrowing the panel shrank the reading along with it. The cost of the split is that switching a
row off no longer makes its panel shorter; that panel keeps the height it was given and draws
the rows that remain larger.

**A width narrower than the rows need is drawn at the width they need**, and each panel has its
own floor, where its own widest row has met itself. Every figure below was measured through the
font rather than guessed at, and each is a representative reading rather than the worst
imaginable one — a figure that runs longer than its column encroaches on its label, here as
anywhere else on these panels.

| Panel | Floor | Widest row |
| --- | --- | --- |
| Other | 86 | `GLIDE` at 28, a ratio like `-12.34 : 1` at 48, eight of padding, two over |
| Speed | 116 | `SPEED XYZ` at 52, `-78.40 b/s` — straight-down terminal velocity — at 54, eight, two |
| Acceleration | 116 | `ACCEL XYZ` measures the same 52, and an acceleration much the same as a speed |
| Energy | 106 | `TE` at 12, then the two columns: 38, a pad, 42, and the panel's padding either side |

Energy's floor is measured in the two-column mode, the widest of the three energy references
and the default, so that changing the reference never moves the floor under a width already set.
The grips stop at these, and a narrower width typed into the box is drawn at the minimum rather
than refused, so a reading never turns into an overlap.

| Row | Meaning |
| --- | --- |
| `PITCH` | Raw Minecraft pitch: **negative is looking up**. Matches F3 and elytrasim rather than the aviation convention. |
| `SPEED XZ` | Horizontal speed. |
| `SPEED XYZ` | Total speed. |
| `SPEED Y` | Vertical speed; negative descending. |
| `GLIDE` | Blocks forward per block down. Negative while climbing, where it reads as blocks forward per block *gained*. `--` only when level with speed, or stationary. |
| `KE` | Kinetic energy as a height: the altitude your speed is worth. |
| `PE` `TE` | Potential and total energy, **measured from the last apex**: how far below the top of the cycle you are, and how much of it is recoverable. Green means you are above the last apex, which for `TE` is a cycle that has already paid for itself. The dimmed figure to the left is the same height against the world's origin, which is what F3 and a map agree with. `--` until an apex has been seen. |
| `GAIN` | Total energy gained between the last two apexes: what the cycle was worth. |

## The chart

Horizontal speed against vertical speed. The **yellow** cursor is total horizontal speed; the
**cyan** cursor is horizontal speed projected onto the look direction. They coincide in
straight flight, and the gap between them is sideslip — the signal for analysing turns. The
trail is the last 100 ticks of total horizontal speed.

### The heatmap

Behind all of that, every pixel of the chart is colored by the most total energy one tick
could gain from the velocity that pixel stands for. It is the optimal pitch bug's number,
worked out for the whole chart instead of only for where you are.

| Color | Means |
| --- | --- |
| **Magenta** | Energy can be made here. The brighter, the more of it |
| **Blue** | Energy must be lost here, whatever you do with the nose |
| **The black seam between them** | Break-even, and the only line on the map worth learning |

Inside the magenta a cycle can pay for itself; outside it nothing can. Watch where the trail
crosses the seam rather than what color it happens to be sitting on — the map is terrain, and
a good cycle spends time on both sides of it.

The strip left of the origin is horizontal speed *backwards* relative to where you are looking.
It is a real state and the physics has a real answer there, so it is drawn, but you will not
be in it.

The map assumes all your horizontal speed is going where the nose points. In a turn it is not,
and the map is then optimistic by however far apart the two cursors are.

It costs about a third of a second to build, once, on the first frame it is drawn — and again
only if gravity or the chart's bounds change. After that it is free. Most of that is the JIT
seeing the physics for the first time, so gliding for a few seconds before opening the chart
makes it roughly ten times cheaper. Resizing the chart by dragging a corner stretches the map
it has for the length of the drag and builds the exact one when the mouse comes up, so a drag
costs one build rather than one per pixel; the map goes blocky while it is being dragged, which
is what a stretched map honestly looks like. Turn it off with `showEnergyField`.

## The pitch ladder

Drawn over the world and lined up with it, so a rung lies along the ground at the angle it is
labeled with. Rungs are tiered by length and weight, to be read from the pattern rather than
from the digits:

| Mark | Every | Looks like |
| --- | --- | --- |
| Datum | horizon and ±40° | Longest and brightest; the horizon is longer still |
| Major | 20° | Solid, medium length, labeled |
| Minor | 10° | Short, dimmer |
| Fine | 2° | Faint stubs, only near where you are looking, fading out with distance from it |

Every tier is solid. Length and strength run downhill together across the four, which is
enough to separate them at a glance without a dash pattern repeating what they already say.
Marks fade out as they approach the top and bottom of the ladder rather than blinking off.

Labels are in raw Minecraft pitch, matching the `PITCH` row: **negative is above the horizon**.
Nothing else marks which side of the horizon a rung is on — the sky and the ground already do.

## The bugs

A pair of wedges on either side of the crosshair marks a pitch to fly. The gap between the
crosshair and a bug is the correction; when there is none, its two wedges close around the
crosshair. They are the only advisory marks on the ladder and the only things on it that are
not gray, and they are drawn only while actually gliding.

There are three, because an optimised pump cycle turns out to be **piecewise myopic**: each
phase of it follows a simple rule of the current state, and the hard part is knowing when to
switch rules rather than what any rule is.

| Bug | Colour | Marks | Its phase |
| --- | --- | --- | --- |
| Lookahead | Amber | The constant pitch gaining the most energy over the next 20 ticks | **The gain phase** — the climb out of the flick, where most of a cycle's energy is made |
| Hold | Green | The pitch that leaves the flight path angle where it is | **The dive** — parameter-free, and it fits the whole descent to under a degree. Gone once the dive is over |
| Optimal pitch | Magenta | The same over one tick | *Off by default.* A gradient rather than a plan, and wrong through both phases above |

Two are on. The one-tick bug parks on the horizon through the whole dive and pins to the
nose-up stop entering the climb, so in the phases being flown it is two more marks saying
nothing; it is kept because it is the reading elytrasim plots and the heatmap colours.

Where you are actually going is the flight path marker's job and is not repeated here. A gray
bug for it was, until it turned out to be one reading drawn twice.

**Nothing tells you which rule the phase you are in calls for.** That switch is the open part
of the problem; a display that guessed at it would be inventing the answer rather than showing
the evidence.

They share one band of the ladder, since the center gap is the only place on it any of them
can go, so they overlap whenever two rules agree. They are ranked by height as well as colour
and drawn tallest first, so a pile nests into chevrons instead of merging into one mark of
uncertain colour. Which bug gets which height is tuned by eye and means nothing in itself.

**When an answer is further out than the band reaches, the bug leaves the ladder.** All three
do this, and the flight path marker with them. It matters most for the two rules: each governs
one phase and each sends its answer off the ladder during the phases it does not govern — the
amber one into a second mode 40–50° nose-up through the dive, the green one into a steep
nose-down answer once the dive is over. Held at the edge in gray they would read as *keep going
that way* for whole phases at a time, which is an invitation to fly a rule exactly where it is
not the rule. Gone says the one true thing instead, and it matches what each already does when
its search has no answer at all — so a bug that is not there means one thing rather than two.

The magenta one comes nearer to a real limit off the ladder — near-90° nose-down through a
slow descent — but pitch clamps at ±90, so the mouse finds the stop by itself. The bugs earn
their place at interior angles, where they are actual targets to fly to.

### Reading the two energy bugs

Both try every pitch, tick the physics forward holding it, and keep the best. The one-tick
version is elytrasim's *immediate optimal pitch* exactly.

| Regime | One tick says | Trust it? |
| --- | --- | --- |
| Steady glide | Exactly level, and it stays there | Yes — a real cusp, not a rounding |
| Zoom climb | Tens of degrees nose-up, moving as speed bleeds off | Yes — this is the regime it earns its place in |
| Slow descent | Near ninety nose-down, usually off the ladder and so not drawn | Yes, but it means *dive*, not *dive to exactly there* |
| The gain phase | Pinned to the nose-up stop, then 40° off for a while | **No** — this is what the 20-tick bug is for |
| The dive that pays for a climb | Whatever loses least right now | **No** — and neither is the 20-tick one; use the hold bug |

Twenty ticks is not a tuned magic number so much as the best single stand-in for a horizon
that really shortens as the climb develops, from about twenty at the start of the gain phase
towards about twelve by the end of it. Every horizon in that range is right somewhere in the
phase and none outside it ever is.

In the dive the two energy bugs are not merely imprecise but **bimodal** — short horizons say
stay level, long ones say zoom now around 40–50° nose-up — and the truth is at neither. Expect
the amber bug to jump between staying near the horizon and vanishing off the top of the ladder
while diving. That is the reading, not a glitch, and it is why the dive gets a rule that never
mentions energy.

### Reading the hold bug

Point at it and the direction you are travelling one tick from now is the direction you are
travelling now. Against an optimised 300-tick cycle it fits the optimal pitch through the
whole descent to **0.73° RMS with nothing to tune**.

It does not hold the angle exactly, and that is the point: flown, the flight path angle decays
towards about 16.6° below the horizon, losing a twentieth of the remaining gap each tick, and
the optimum decays the same way. An exact hold is a *worse* rule — it keeps its entry angle
forever and bleeds height. The floor it decays towards is the flight path angle of the steady
glide that maximises forward speed, which vanilla puts at 53° nose-down doing 3.39 blocks/tick.

**Holding the angle is not pointing along it.** By the end of a dive the nose sits about 30°
*below* the flight path, so the green bug and the flight path marker are nowhere near each
other, and the gap between them is the angle of attack the hold is asking for. Turning the
marker on is what makes that gap visible.

The bug disappears when no pitch holds the current angle at all — a near-vertical fall cannot
be sustained by any attitude — rather than picking the least bad degree.

## Marker switches

| Switch | Default | Effect |
| --- | --- | --- |
| `showOptimalPitch` | Off | The magenta one-tick bug on the ladder |
| `showFlightPath` | On | A winged circle showing where you are going rather than where you are looking. Its vertical gap from the crosshair is angle of attack, and its horizontal gap is sideslip. It is not drawn once it falls outside the ladder band or off the edge of the screen |

Sideslip remains readable without the flight-path marker from the gap between the chart's two
cursors.

## Known limitations

- Positioning is absolute pixel coordinates plus an in-world editor: click, drag to move,
  drag a corner to resize, arrow keys to nudge, with snapping to the other modules and to the
  screen. What it does not have is a way back to a default arrangement other than the page's
  own reset, and nothing stops two modules from being dragged on top of one another.
- At small GUI sizes — below roughly 400 scaled pixels wide — the ladder's left-hand labels
  reach into the readout panel. Nothing checks for the collision.
- The ladder does not turn. Once yaw matters, rungs become conic sections and straight ticks
  stop being correct.
- **Nothing decides which bug to follow.** The four rules fit four phases, the switch points
  are learnable, and the HUD does not attempt them. Reading it well means knowing which phase
  you are in.
- **The heatmap still sees one tick ahead and no further**, as does the magenta bug. They are a
  gradient, not a plan.
- **The 20-tick bug scores a pitch held constant for 20 ticks**, which is not what anybody
  flies. It answers "what is a fixed attitude worth from here", not "what is the best flight
  from here" — the latter is a dynamic program whose answer is the whole cycle.
- The hold bug is a low-gain reading: a degree of pitch moves the next tick's flight path angle
  by about a fifteenth of a degree, so it magnifies any wobble in the measured velocity by
  about fifteen on the way to the answer. It uses the latest sampled velocity without
  temporal smoothing.
- The heatmap is drawn whenever the chart is, including while walking around, where the elytra
  physics it describes does not apply. Turn on the graph's Only while gliding to suppress it there.
- Building the heatmap blocks the frame it happens on. It is one hitch of roughly a third of a
  second and then never again, and it is deliberately not spread across frames: a half-built
  map that disagreed with its own axes would be worse than a stutter. Changing the graph's
  scale pays it again, once, which is why a resize drag stretches the old map until it ends
  rather than paying it on every pixel.
- **A cursor outside the chart's bounds is clamped to the edge, with no cue that it has
  happened.** The vertical axis reaches 40 b/s of climb, which covers a good pump cycle's peak
  of about 35, but a rocket will go past it. A domain that slid to keep the cursor inside is a
  possible future feature; see [DESIGN.md](DESIGN.md) for what it would cost.
- The bugs read plain gravity where vanilla reads its *effective* gravity. The two differ only
  under Slow Falling while descending, where the cue will be slightly wrong; every energy
  readout on the panel makes the same simplification, so at least they agree with each other.
- **The horizon leaves the screen when you look down past half your FOV** — 35° at the default
  70, 45° at FOV 90. That is not the ladder clipping it; at that attitude the horizon is not
  in the rendered view at all, so nothing can draw it there. The ladder reaches to 97% of the
  way to the top edge, so it shows everything that is actually on screen. A wider FOV is the
  only thing that extends the range.
- The teleport guard is crude: any movement over 10 blocks in a tick drops the history. Ender
  pearls under that distance slip through, and vanilla can exceed it. Deliberately left alone.
- The mixin config is present but empty — nothing has needed a mixin so far.

## Design notes

Why it is built this way, and the Minecraft 26.2 API differences worth knowing about, are in
[DESIGN.md](DESIGN.md).

## License

CC0-1.0.

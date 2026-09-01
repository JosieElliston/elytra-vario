# Design notes

Why Elytra Vario is built the way it is, and what Minecraft 26.2 does differently from what
the docs and the internet will tell you. For what the mod is and how to use it, see the
[README](README.md).

## Scope

The mod only ever *observes*. Every readout is measurable from the player entity, so there is
no physics here: no port of `update_fall_flying_movement`, no optimal-pitch search, no energy
flow field. That work belongs to [elytrasim](https://github.com/HactarCE/elytrasim), and the
two are kept apart deliberately. Predictive features — a flight director, an optimal-pitch
cue, a flow-field background — are the only ones that would need the simulation, and pulling
it in would be a deliberate expansion rather than a quiet one.

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

**One cycle clock, latched at the apex.** Energies are all latched from the same `Sample`, so
`KE + PE = TE` holds. Independent per-metric peak detectors do not work: kinetic energy crests
at the bottom of the dive and potential energy at the top, so the held figures would not sum.
The apex triggers on *entering* a descent rather than on having climbed first, so that walking
off a ledge closes a cycle too.

## The chart

**Its scale is one number.** Both chart dimensions derive from `chartScale` (pixels per
block/tick), so a pixel is worth the same change in speed on both axes whatever the domain is.
Widening a range grows the chart rather than rescaling it.

## The pitch ladder

**It is conformal.** Every mark is projected through the same camera the world was drawn with,
so the rung labelled `-20` lies exactly along the ground that is twenty degrees above the
horizon. The ladder and the terrain move together, which is what makes it readable at a glance
rather than by being studied.

**Tiers do the reading, not the digits.** Length and weight both carry the tier, so an angle
can be recovered from the pattern alone in peripheral vision: faint stubs every two degrees
near the centre, short rungs every ten, longer ones every twenty, and the datum lines — the
horizon and ±40 — longest and brightest. Strength runs strictly downhill across the four, and
the two weakest share an RGB so that nothing but alpha separates them. Only the twenties are
labelled, and faintly. A digit on every rung is the clutter this arrangement exists to avoid.

Every tier is solid. The ten-degree rungs were dashed at first, which made a third channel say
what length and strength had already said — and at that size the dashes mostly read as noise
rather than as a pattern.

**Nothing marks which side of the horizon a rung is on.** The sky, the ground and the labelled
datum line already say which way up the world is, so spending dash pattern or colour on it
would be spending the ladder's only two visual channels on the one fact that never needs
saying.

**Detail follows the eye.** The fine ticks cover only the span the camera is pointing at and
fade to nothing at the edge of it, which buys resolution where it is being used without
paying for it across the whole ladder. Their pitches are absolute multiples of the fine step
rather than offsets from the camera, so they are real angles that the view slides across
instead of a scale that follows the head around.

**Rungs are labelled in raw Minecraft pitch**, agreeing in sign with the `PITCH` row, with F3
and with elytrasim: negative is above the horizon. That reads backwards for a spatial
instrument, and the aviation convention was considered and rejected — having the ladder
disagree in sign with the readout two inches away is worse than having it disagree with
aviation.

**The ladder is yaw-locked, and the marker is not.** A rung is the set of directions at one
pitch, which is a circle on the view sphere, and a circle projects to a conic — so a rung is
only truly straight where it crosses the centre of the screen. Drawing straight horizontal
rungs symmetric about the centre is exact in the middle and bows away from the truth towards
the ends; at the default rung length that error is well under a pixel. The flight path marker
has no such problem, because a point projects to a point: it is placed exactly on both axes,
which is why it can show sideslip honestly.

**The band is an angle, the rungs are pixels.** How far the ladder reaches above and below
centre is configured as a fraction of the view height, because the projection scale is itself
proportional to that height — a fixed pixel band would cover a different slice of sky at every
GUI scale. Rung lengths stay pixel counts for the opposite reason: they are sized against the
labels, which are text and do not scale with the view.

**The band is asymmetric.** Below centre the hotbar and the rest of the vanilla HUD want the
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
from the centre. The GUI's orthographic projection covers the whole framebuffer, so that
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

## Readouts that are off by default

**Angle of attack, in both its forms.** Real, correct, and unused: the pump-cycle research
does not refer to it, so on instruments watched continuously the `AOA` row and the flight path
marker were both clutter competing with readings in use. Kept behind `showAngleOfAttack` and
`showFlightPath` rather than deleted, because whether angle of attack matters is an open
question rather than a settled one.

Turning the marker off costs the ladder its only sideslip cue, which is the one thing it
carried that the number did not. That is affordable because the chart shows sideslip too, as
the gap between its two cursors — and the chart is the instrument that turning is analysed on
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
- `outline(x, y, width, height, colour)` takes a size, whereas `fill` takes bounds.
  `horizontalLine`/`verticalLine` are inclusive on one end and exclusive on the other, so
  `fill` is used for gridlines to avoid an off-by-one.

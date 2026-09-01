# Elytra Vario

A client-side glide-computer HUD for elytra flight in Minecraft 26.2 (Fabric).

It reads the player's state and displays speeds, energies, and a velocity-space chart, so
that pump cycles can be flown and tuned by instrument rather than by feel.

## Status

Working: the readout panel, the velocity-space chart with two cursors, per-cycle energy
tracking, the pitch ladder with its flight path marker, and a `V` keybind that toggles the
whole HUD.

The mod only ever *observes*. It does not simulate elytra physics — that belongs to the
separate [elytrasim](https://github.com/HactarCE/elytrasim) project, and the two are kept
apart deliberately. Predictive features (flight director, optimal-pitch cue, the energy
flow-field background) would need the simulation and are not attempted here.

## Building and running

Needs JDK 25. Temurin 25 is installed on the development machine and registered with
`/usr/libexec/java_home`, so no `JAVA_HOME` juggling is required.

```sh
./gradlew build       # compile and package
./gradlew runClient   # launch a dev client
```

## Readouts

| Row | Meaning |
| --- | --- |
| `PITCH` | Raw Minecraft pitch: **negative is looking up**. Matches F3 and elytrasim rather than the aviation convention. |
| `SPEED XZ` | Horizontal speed. |
| `SPEED XYZ` | Total speed. |
| `SPEED Y` | Vertical speed; negative descending. |
| `GLIDE` | Blocks forward per block down. Negative while climbing, where it reads as blocks forward per block *gained*. `--` only when level with speed, or stationary. |
| `AOA` | Angle of attack: how far the nose sits above the flight path, which is the vertical gap between the crosshair and the ladder's flight path marker. Positive means looking above where you are going — the normal state in a glide, since holding the look level still descends. Signed the aviation way round even though both terms it is built from use Minecraft's convention; subtracting flips the sense. |
| `KE` `PE` `TE` | Kinetic, potential and total energy, as heights. The dimmed figure beside each is its value at the last apex. |
| `TE RATE` | How fast total energy is changing, averaged over 10 ticks. Says whether the cycle is net gaining, independent of whether you happen to be climbing right now. |
| `GAIN` | Total energy gained between the last two apexes: what the cycle was worth. |

The chart plots horizontal speed against vertical speed. The **yellow** cursor is total
horizontal speed; the **cyan** cursor is horizontal speed projected onto the look direction.
They coincide in straight flight, and the gap between them is sideslip — the signal for
analysing turns. The trail is the last 100 ticks of total horizontal speed.

## Pitch ladder

A conformal attitude indicator drawn over the world: every mark is projected through the same
camera the world was drawn with, so the rung labelled `-10` lies exactly along the ground that
is ten degrees above the horizon. The ladder and the terrain move together, which is what
makes it readable without looking at it directly.

Rungs are labelled in **raw Minecraft pitch**, agreeing in sign with the `PITCH` row, with F3
and with elytrasim: negative is above the horizon. Because that reads backwards for a spatial
instrument, direction is carried by the drawing instead — rungs above the horizon are solid,
rungs below it are dashed, and the horizon itself is longer and brighter.

The **flight path marker** is the winged circle: where the player is actually going, as
against the crosshair's where they are looking. The vertical gap between the two is the angle
of attack and the horizontal gap is sideslip, the same quantity the chart's cyan cursor
measures — hence the shared colour. It goes grey when pegged at the edge of the band, where
its position is a limit rather than a reading.

## Design decisions

**The ladder is yaw-locked, and the marker is not.** A rung is the set of directions at one
pitch, which is a circle on the view sphere, and a circle projects to a conic — so a rung is
only truly straight where it crosses the centre of the screen. Drawing straight horizontal
rungs symmetric about the centre is exact in the middle and bows away from the truth towards
the ends; at the default rung length that error is well under a pixel. The flight path marker
has no such problem, because a point projects to a point: it is placed exactly on both axes,
which is why it can show sideslip honestly.

**No aspect ratio in the projection.** Minecraft's perspective matrix is built from a vertical
field of view and the viewport height, so the pixel scale is the same on both screen axes: a
direction `t` units of tangent off the camera axis lands `t * halfHeight / tan(fov / 2)` pixels
from the centre. The GUI's orthographic projection covers the whole framebuffer, so that
half-height can be taken in scaled GUI pixels.

**The ladder's band is an angle, its rungs are pixels.** How far the ladder reaches above and
below centre is configured as a fraction of the view height, because the projection scale is
itself proportional to that height — a fixed pixel band would cover a different slice of sky
at every GUI scale. Rung lengths stay pixel counts for the opposite reason: they are sized
against the labels, which are text and do not scale with the view.

**Everything is referenced to the camera, not the player.** `Camera.xRot()` and the camera's
own basis vectors are what the world was actually drawn with, so the ladder stays glued to the
world in third person and in the mirrored front view, and it interpolates smoothly between
ticks instead of stepping at 20 Hz. The flight path marker is the exception that proves it:
velocity only exists per tick, so it is averaged over a few of them, since an un-smoothed
marker visibly jitters in a way the numeric readouts do not.

**Energies are heights.** Dividing energy by gravity turns it into the altitude that energy
is worth. Potential energy then *is* altitude, kinetic energy is `v²/2g`, and a cycle's gain
can be read off directly in blocks. Absolute total energy is arbitrary — altitude has an
arbitrary origin — so differences are what matter, which is why `TE RATE` and `GAIN` exist.

**Blocks/tick internally, blocks/second on screen.** Vanilla physics works in blocks/tick, so
that is what is stored and what the chart domain is configured in, keeping numbers comparable
with elytrasim. Conversion happens only at the point of display.

**Velocity is measured from position change, not `getDeltaMovement`.** The latter is the
velocity the player is *trying* to have; gravity keeps it pointing downwards even while stood
still, because collision cancels the motion after the fact rather than by changing the
vector. The two agree in free flight and diverge only on contact. `getKnownMovement` is not an
alternative — for a directly controlled player it falls through to `getDeltaMovement`.

**One cycle clock, latched at the apex.** Energies are all latched from the same `Sample`, so
`KE + PE = TE` holds. Independent per-metric peak detectors do not work: kinetic energy crests
at the bottom of the dive and potential energy at the top, so the held figures would not sum.
The apex triggers on *entering* a descent rather than on having climbed first, so that walking
off a ledge closes a cycle too.

**The chart's scale is one number.** Both chart dimensions derive from `chartScale` (pixels per
block/tick), so a pixel is worth the same change in speed on both axes whatever the domain is.
Widening a range grows the chart rather than rescaling it.

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
- `outline(x, y, width, height, colour)` takes a size, whereas `fill` takes bounds.
  `horizontalLine`/`verticalLine` are inclusive on one end and exclusive on the other, so
  `fill` is used for gridlines to avoid an off-by-one.

## Not done yet

- **Config does not persist.** Everything in `VarioConfig` is in-memory and resets each launch.
- No config screen; values are edited in source.
- The mixin config is present but empty — nothing has needed a mixin so far.
- The teleport guard is crude: any movement over 10 blocks in a tick drops the history. Ender
  pearls under that distance slip through, and vanilla can exceed it. Deliberately left alone.
- The ladder follows the same `onlyWhileGliding` switch as the panel, which defaults to off,
  so it is drawn while walking around too. It is more intrusive there than a corner panel is.
- At small GUI sizes — below roughly 400 scaled pixels wide — the ladder's left-hand labels
  reach into the readout panel. Nothing checks for the collision.
- The ladder does not turn. Once yaw matters, rungs become conic sections and straight ticks
  stop being correct; see the yaw-lock note under design decisions.

## License

CC0-1.0.

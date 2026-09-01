# Elytra Vario

A client-side glide-computer HUD for elytra flight in Minecraft 26.2 (Fabric).

It reads the player's state and displays speeds, energies, and a velocity-space chart, so
that pump cycles can be flown and tuned by instrument rather than by feel.

## Status

Working: the readout panel, the velocity-space chart with two cursors, per-cycle energy
tracking, and a `V` keybind that toggles the whole HUD.

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
| `KE` `PE` `TE` | Kinetic, potential and total energy, as heights. The dimmed figure beside each is its value at the last apex. |
| `TE RATE` | How fast total energy is changing, averaged over 10 ticks. Says whether the cycle is net gaining, independent of whether you happen to be climbing right now. |
| `GAIN` | Total energy gained between the last two apexes: what the cycle was worth. |

The chart plots horizontal speed against vertical speed. The **yellow** cursor is total
horizontal speed; the **cyan** cursor is horizontal speed projected onto the look direction.
They coincide in straight flight, and the gap between them is sideslip — the signal for
analysing turns. The trail is the last 100 ticks of total horizontal speed.

## Design decisions

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
- `outline(x, y, width, height, colour)` takes a size, whereas `fill` takes bounds.
  `horizontalLine`/`verticalLine` are inclusive on one end and exclusive on the other, so
  `fill` is used for gridlines to avoid an off-by-one.

## Not done yet

- **Config does not persist.** Everything in `VarioConfig` is in-memory and resets each launch.
- No config screen; values are edited in source.
- The mixin config is present but empty — nothing has needed a mixin so far.
- The teleport guard is crude: any movement over 10 blocks in a tick drops the history. Ender
  pearls under that distance slip through, and vanilla can exceed it. Deliberately left alone.

## Next: the pitch HUD

The intended next piece is a pitch ladder / attitude indicator, since elytra lift depends
directly on pitch and small changes matter a lot.

Known starting points from the original design discussion:

- **Start yaw-locked.** This postpones the "conic problem": with yaw locked the marks stay
  symmetric about centre, and a straight tick is wrong only in how it bows towards the edges.
  Once yaw is free the marks become conic sections and straight ticks stop being correct.
- Keep displaying **raw Minecraft pitch** (negative up) for consistency with F3, elytrasim and
  the existing panel, unless there is a reason to switch the whole HUD at once.
- Worth considering alongside it: angle of attack, the divergence between look direction and
  actual velocity direction, which is already half-computed for the chart's cyan cursor.

## License

CC0-1.0.

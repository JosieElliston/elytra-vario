# Elytra Vario

A client-side glide-computer HUD for elytra flight in Minecraft 26.2 (Fabric).

It reads the player's state and displays speeds, energies, a velocity-space chart and a pitch
ladder, so that pump cycles can be flown and tuned by instrument rather than by feel.

Client-only: it observes the player and draws, and never touches physics or talks to the
server. It does not need to be installed on the other end.

## Building and running

Needs JDK 25.

```sh
./gradlew build       # compile and package into build/libs
./gradlew runClient   # launch a dev client
```

## Using it

`V` toggles the whole HUD.

Everything else is edited in `VarioConfig`, which is plain static fields with a comment on
each — panel position and width, chart bounds and scale, ladder spacing, lengths and colours,
and switches for the chart, the ladder, the flight path marker and the angle of attack row. There is no config screen
and no config file, so changes mean a recompile and are lost on restart.

## The readout panel

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

An `AOA` row — angle of attack, how far the nose sits above the flight path — is implemented
but **off by default**, since nothing in the pump-cycle work refers to it yet. Set
`showAngleOfAttack` to bring it back; the panel resizes itself around it.

## The chart

Horizontal speed against vertical speed. The **yellow** cursor is total horizontal speed; the
**cyan** cursor is horizontal speed projected onto the look direction. They coincide in
straight flight, and the gap between them is sideslip — the signal for analysing turns. The
trail is the last 100 ticks of total horizontal speed.

## The pitch ladder

Drawn over the world and lined up with it, so a rung lies along the ground at the angle it is
labelled with. Rungs are tiered by length and weight, to be read from the pattern rather than
from the digits:

| Mark | Every | Looks like |
| --- | --- | --- |
| Datum | horizon and ±40° | Longest and brightest; the horizon is longer still |
| Major | 20° | Solid, medium length, labelled |
| Minor | 10° | Short, dimmer |
| Fine | 2° | Faint stubs, only near where you are looking, fading out with distance from it |

Every tier is solid. Length and strength run downhill together across the four, which is
enough to separate them at a glance without a dash pattern repeating what they already say.
Marks fade out as they approach the top and bottom of the ladder rather than blinking off.

Labels are in raw Minecraft pitch, matching the `PITCH` row: **negative is above the horizon**.
Nothing else marks which side of the horizon a rung is on — the sky and the ground already do.

The winged circle is the **flight path marker**: where you are actually going, as against the
crosshair's where you are looking. The vertical gap between the two is angle of attack and the
horizontal gap is sideslip, which is why it shares its colour with the chart's cyan cursor. It
turns grey when pegged at the edge of the ladder, where its position is a limit rather than a
reading.

## Known limitations

- **Config does not persist**, and there is no config screen; values are edited in source.
- The ladder follows the same `onlyWhileGliding` switch as the panel, which defaults to off,
  so it is drawn while walking around too. It is more intrusive there than a corner panel is.
- At small GUI sizes — below roughly 400 scaled pixels wide — the ladder's left-hand labels
  reach into the readout panel. Nothing checks for the collision.
- The ladder does not turn. Once yaw matters, rungs become conic sections and straight ticks
  stop being correct.
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

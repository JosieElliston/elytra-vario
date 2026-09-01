# Elytra Vario

A client-side glide-computer HUD for elytra flight in Minecraft 26.2 (Fabric).

It reads the player's state and displays speeds, energies, a velocity-space chart and a pitch
ladder, so that pump cycles can be flown and tuned by instrument rather than by feel. Two of
the readings are worked out rather than measured: an optimal pitch bug on the ladder, and a
heatmap behind the chart showing where in velocity space energy can be made.

Client-only: it watches the player and draws, and never talks to the server or changes how
anything flies. It does not need to be installed on the other end.

## Building and running

Needs JDK 25.

```sh
./gradlew build       # compile and package into build/libs
./gradlew runClient   # launch a dev client
```

## Using it

`V` toggles the whole HUD.

Everything else is edited in `VarioConfig`, which is plain static fields with a comment on
each — panel position and width, chart bounds and scale, ladder spacing, lengths and colors,
and switches for the chart, the heatmap, the ladder, the optimal pitch bug, the flight path
marker and the angle of attack row. There is no config screen and no config file, so changes mean a recompile
and are lost on restart.

## The readout panel

| Row | Meaning |
| --- | --- |
| `PITCH` | Raw Minecraft pitch: **negative is looking up**. Matches F3 and elytrasim rather than the aviation convention. |
| `SPEED XZ` | Horizontal speed. |
| `SPEED XYZ` | Total speed. |
| `SPEED Y` | Vertical speed; negative descending. |
| `GLIDE` | Blocks forward per block down. Negative while climbing, where it reads as blocks forward per block *gained*. `--` only when level with speed, or stationary. |
| `KE` | Kinetic energy as a height: the altitude your speed is worth. |
| `PE` `TE` | Potential and total energy, **measured from the last apex**: how far below the top of the cycle you are, and how much of it is recoverable. Green means you are above the last apex, which for `TE` is a cycle that has already paid for itself. The dimmed figure to the left is the same height against the world's origin, which is what F3 and a map agree with. `--` until an apex has been seen. |
| `TE RATE` | How fast total energy is changing, averaged over 10 ticks. Says whether the cycle is net gaining, independent of whether you happen to be climbing right now. |
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
makes it roughly ten times cheaper. Turn it off with `showEnergyField`.

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

## The optimal pitch bug

A magenta wedge on each side of the crosshair, marking the pitch that would gain the most
total energy over the next tick. It is the only advisory mark on the ladder and the only one
that is not gray. The gap between the crosshair and the bug is the correction; when there is
none, the two wedges close around the crosshair.

Drawn only while actually gliding. When the answer is further from the ladder's reach than the
band goes it is held at the edge and turns gray, which means *keep going that way*, not *stop
here*. Do not expect much of it there: pitch clamps at ±90, so the mouse finds the stop by
itself and the gray wedge only names a direction you could have guessed. The bug earns its
place at interior angles, where it is an actual target to fly to.

It is worth knowing what the number is before flying it:

| Regime | What it says | Trust it? |
| --- | --- | --- |
| Steady glide | Exactly level, and it stays there | Yes — a real cusp, not a rounding |
| Zoom climb | Tens of degrees nose-up, moving as speed bleeds off | Yes — this is the regime it earns its place in |
| Slow descent | Near ninety nose-down, usually pegged gray | Yes, but it means *dive*, not *dive to exactly there* |
| The dive that pays for a climb | Whatever loses least right now | **No** — one tick of lookahead is too short a view |

The search is greedy: it tries every pitch, ticks the physics once, and keeps the best. That
is the same *immediate optimal pitch* elytrasim plots, and it agrees with the far-sighted
answer whenever energy is being gained, and wherever it snaps to level. Flying it every tick
is not a strategy and will not fly a good cycle.

## Off by default

Angle of attack — how far the nose sits above the flight path — is built and correct, in two
forms, and both are switched off. Nothing in the pump-cycle work refers to it yet, and on
instruments watched continuously they were clutter competing with readings actually in use.

| Switch | Brings back |
| --- | --- |
| `showAngleOfAttack` | An `AOA` row on the panel, which resizes itself around it |
| `showFlightPath` | The flight path marker: a winged circle on the ladder marking where you are actually going, as against the crosshair's where you are looking. Its vertical gap from the crosshair is angle of attack drawn rather than printed, and its horizontal gap is sideslip. Grays out when pegged at the edge of the ladder |

Sideslip is still readable without the marker, from the gap between the chart's two cursors.

## Known limitations

- **Config does not persist**, and there is no config screen; values are edited in source.
- The ladder follows the same `onlyWhileGliding` switch as the panel, which defaults to off,
  so it is drawn while walking around too. It is more intrusive there than a corner panel is.
- At small GUI sizes — below roughly 400 scaled pixels wide — the ladder's left-hand labels
  reach into the readout panel. Nothing checks for the collision.
- The ladder does not turn. Once yaw matters, rungs become conic sections and straight ticks
  stop being correct.
- **The optimal pitch bug and the heatmap both see one tick ahead and no further.** They are a
  gradient, not a plan, and they are silent about the part of a cycle where you are meant to be
  spending energy.
- The heatmap is drawn whenever the chart is, including while walking around, where the elytra
  physics it describes does not apply. `onlyWhileGliding` suppresses the whole HUD if that
  matters.
- Building the heatmap blocks the frame it happens on. It is one hitch of roughly a third of a
  second and then never again, and it is deliberately not spread across frames: a half-built
  map that disagreed with its own axes would be worse than a stutter.
- **A cursor outside the chart's bounds is clamped to the edge, with no cue that it has
  happened.** The vertical axis reaches 40 b/s of climb, which covers a good pump cycle's peak
  of about 35, but a rocket will go past it. A domain that slid to keep the cursor inside is a
  possible future feature; see [DESIGN.md](DESIGN.md) for what it would cost.
- The bug reads plain gravity where vanilla reads its *effective* gravity. The two differ only
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

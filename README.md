# Elytra Vario

A client-side HUD for elytra flight in Minecraft 1.21.11 (Fabric), with utilities for flying a pump cycle. Open settings with `V`.

## Pitch ladder

In the center of the screen is a ladder of pitch marks. It has two sliding bugs (triangles) marking important pitches:

- green: the pitch at which the flight path angle is preserved. follow this during the dive phase.
- amber: the pitch that maximizes total energy gain over the next 20 ticks if you were to hold that constant angle. follow this during the gain phase.

## Readout panel

We detect the apex of the previous cycle and display some metrics as a difference from that point. Energy uses unit mass and is divided by gravity, so has units of blocks (of height).

| Row | Meaning |
| --- | --- |
| `PITCH` | pitch; note negative is up |
| `SPEED XZ` | horizontal speed |
| `SPEED XYZ` | total speed |
| `SPEED Y` | vertical speed |
| `GLIDE` | blocks forward per block down |
| `KE` | kinetic energy |
| `PE` | potential energy |
| `TE` | total energy |
| `GAIN` | total energy gained between the last two apexes |

## Chart

Horizontal speed against vertical speed. The yellow cursor is total horizontal speed, and has a 100 tick trail. The cyan cursor is horizontal speed projected onto the look direction. They agree during straight flight.

The heatmap is colored by the most total energy you can gain in one tick from that velocity.

## Speedometer

A half disc in the bottom-left corner, reading 0 b/s at its left end and 80 b/s at its right,
a little past terminal velocity. The background is the shape itself rather than a box around
it, so the corners are left alone. It carries three needles, each of which can be turned off:

| Needle | Speed | Length |
| --- | --- | --- |
| red | total, `sqrt(vx² + vy² + vz²)` | longest |
| green | horizontal, `sqrt(vx² + vz²)` | middle |
| blue | vertical, `|vy|` | shortest |

Nothing here is new — the same three figures are rows on the readout panel. The dial is for
reading them without looking: three angles about one hub make the split between horizontal and
vertical speed a shape rather than a subtraction. The lengths differ because two needles
coincide whenever two speeds agree, which is most of a glide, and a shorter needle on top of a
longer one still reads as two.

**The blue needle is a magnitude.** A scale starting at zero cannot carry a sign, and nothing
on the dial says which way — which way you are going is obvious out of the window, and the
panel's `SPEED Y` row prints the sign when a figure is wanted.

A needle past 80 b/s is held at the stop and turns gray, so a position that is a limit does not
read as a speed.

## Configuration

Press **V** in game to open settings — or again, like Escape, to close them — or, with [Mod Menu](https://modrinth.com/mod/modmenu) installed, open **Mods → Elytra Vario → Configure**.
The six pages are **Global**, **Pitch Ladder**, **Markers**, **Velocity Graph**, **Flight Stats**,
and **Speedometer**.
Global contains the HUD master switch and the settings key itself. Each instrument’s own
switches are on its own page: one for whether it is shown at all, one for whether it is wanted
only while gliding, and a key bind that flips the first of them in flight. The five toggle keys
start unbound. Every one of these binds, the settings key included, can be set on its own page
here or in the vanilla Controls screen.
Hover over a marker control for its mathematical definition and usage notes. Advanced
settings include lookahead duration and detailed ladder geometry.

The graph either takes a screen anchor of its own
or attaches to the left, right, top or bottom of the stats panel; an attached pair is anchored
and moved as one block. Both horizontal and vertical axis bounds are editable in blocks per
second. Stats rows can be hidden individually, and potential/total
energy can show absolute values, changes since the last apex, or both. The speedometer takes a
screen anchor of its own, and its radius, full-scale speed and tick spacing are all editable.

Valid edits preview immediately on the HUD. In game, settings sit on the right with the world
and HUD visible behind them. **Save** writes `config/elytra-vario.json` and keeps settings open.
**Close** or Escape asks for confirmation before discarding unsaved changes. Choose
**Save and exit** to save and close, or **Keep editing** (or Escape in the popup) to keep
the draft and preview. **Discard changes**
restores the last save, or the opening state if nothing was saved. **Reset page** also previews immediately, including advanced settings.
Mod Menu is optional; the settings key works without it.

## Building this backport

This branch targets Minecraft 1.21.11 with Fabric API 0.141.3+1.21.11 and Fabric Loader 0.19.3 or later. Run `./gradlew build` with JDK 25; the mod targets Java 21 for Minecraft 1.21.11. The installable jar is `build/libs/elytra-vario-1.2.0+mc1.21.11.jar`.

This is a one-off backport from the 26.2 version. `ElytraPhysics.java` is retained unchanged from that version.

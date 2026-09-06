# Elytra Vario

A client-side HUD for elytra flight in Minecraft 26.2 (Fabric), with utilities for flying a pump cycle. Open settings with `V`.

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

## Configuration

Press **V** in game to open settings, or, with [Mod Menu](https://modrinth.com/mod/modmenu) installed, open **Mods → Elytra Vario → Configure**.
The five pages are **Global**, **Pitch Ladder**, **Markers**, **Velocity Graph**, and **Flight Stats**.
Global contains the HUD master switch. Each instrument’s visibility control is on its own page.
Hover over a marker control for its mathematical definition and usage notes. Advanced
settings include lookahead duration and detailed ladder geometry.

Each instrument has independent visibility. The graph either takes a screen anchor of its own
or attaches to the left, right, top or bottom of the stats panel; an attached pair is anchored
and moved as one block. Both horizontal and vertical axis bounds are editable in blocks per
second. Stats rows can be hidden individually, and potential/total
energy can show absolute values, changes since the last apex, or both.

Valid edits preview immediately on the HUD. In game, settings sit on the right with the world
and HUD visible behind them. **Save** writes `config/elytra-vario.json` and keeps settings open.
**Close** or Escape asks for confirmation before discarding unsaved changes. Choose
**Save and exit** to save and close, or **Keep editing** (or Escape in the popup) to keep
the draft and preview. **Discard changes**
restores the last save, or the opening state if nothing was saved. **Reset page** also previews immediately, including advanced settings.
Mod Menu is optional; the settings key works without it.

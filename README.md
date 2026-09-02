# Elytra Vario

A client-side HUD for elytra flight in Minecraft 26.2 (Fabric), with utilities for flying a pump cycle. Toggle with `V`.

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
| `TE RATE` | total energy change rate |
| `GAIN` | total energy gained between the last two apexes |

## Chart

Horizontal speed against vertical speed. The yellow cursor is total horizontal speed, and has a 100 tick trail. The cyan cursor is horizontal speed projected onto the look direction. They agree during straight flight.

The heatmap is colored by the most total energy you can gain in one tick from that velocity.

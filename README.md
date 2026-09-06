# Elytra Vario

A client-side HUD for elytra flight and [pump cycles](https://www.youtube.com/watch?v=-HS3_UA7GM8) in Minecraft 1.21.11 (Fabric). Press `V` to open settings.

## Pitch ladder

The pitch ladder shows pitches fixed to their position in the world.

## Markers

There are five markers you can show inside the pitch ladder

- flight path: the current velocity
- one-tick optimal: the pitch that maximizes your energy gain on the next tick
- lookahead optimal: the pitch that maximizes your energy gain if you were to hold it for 20 ticks
- flight-path hold: the pitch that preserves your velocity direction
- max horizontal speed: the pitch that maximizes steady-state horizontal speed
- min fall speed: the pitch that minimizes steady-state vertical speed
- zero: this happens to be the pitch that maximizes the steady-state glide ratio

| reference            |    pitch | horizontal speed | vertical speed | glide ratio |
| -------------------- | -------: | ---------------: | -------------: | ----------: |
| max horizontal speed | +53.366° |       67.776 b/s |    -20.191 b/s |   3.357 : 1 |
| min fall speed       | -13.233° |        8.736 b/s |     -1.416 b/s |   6.170 : 1 |
| max glide ratio      |       0° |       30.203 b/s |     -2.990 b/s |  10.102 : 1 |

## Velocity Graph

Plots horizontal against vertical speed. The yellow cursor shows total horizontal speed with a 100-tick trail. The cyan cursor shows horizontal speed projected on to the look direction. They coincide in straight flight.

The heatmap shows the maximum one-tick total-energy gain for each velocity.

## Flight Stats

Some metrics are shown relative to the previous cycle's apex. Energy assumes unit mass and is divided by gravity, giving units of blocks of height.

| Row         | Meaning                                         |
| ----------- | ----------------------------------------------- |
| `PITCH`     | pitch; negative is up                           |
| `SPEED XZ`  | horizontal speed                                |
| `SPEED XYZ` | total speed                                     |
| `SPEED Y`   | vertical speed                                  |
| `GLIDE`     | blocks forward per block down                   |
| `KE`        | kinetic energy                                  |
| `PE`        | potential energy                                |
| `TE`        | total energy                                    |
| `GAIN`      | total energy gained between the last two apexes |

## Speedometer

A speedometer ranging from 0 to 80 b/s. Three needles:

| Needle | Speed                          | Length   |
| ------ | ------------------------------ | -------- |
| red    | total, `sqrt(vx² + vy² + vz²)` | longest  |
| green  | horizontal, `sqrt(vx² + vz²)`  | middle   |
| blue   | vertical, `\|vy\|`             | shortest |

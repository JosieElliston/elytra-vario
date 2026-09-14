# Elytra Vario

A client-side HUD for elytra flight and [pump cycles](https://www.youtube.com/watch?v=-HS3_UA7GM8) in Minecraft 1.21.11 (Fabric). Press `V` to open settings.

## Pitch ladder

The pitch ladder shows pitches fixed to their position in the world.

## Ladder Markers

There are several ladder markers you can show:

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

Plots horizontal against vertical speed. The yellow cursor shows total horizontal speed with a 100-tick trail. The cyan cursor shows horizontal speed projected on to the look direction. They coincide in straight flight. Matching arrows project each cursor's measured acceleration one second forward.

The heatmap shows the maximum one-tick total-energy gain for each velocity.

## Flight Stats

Seven panels, each placed, sized and switched on its own. Some metrics are shown relative to the previous cycle's apex. Energy assumes unit mass and is divided by gravity, giving units of blocks of height.

Every panel except Other starts with a heading: what its figures are measured in, and — where it has more than one column — what those columns are. So a unit is written once per panel rather than once per row, and the rows are labelled by what actually tells them apart. Other keeps full row labels because pitch and glide ratio have no unit in common.

| Panel             | Heading      | Row     | Meaning                                          |
| ----------------- | ------------ | ------- | ------------------------------------------------ |
| Other             | —            | `PITCH` | pitch; negative is up                            |
| Other             | —            | `GLIDE` | blocks forward per block down; negative climbing |
| Speed             | `SPEED b/s`  | `Y`     | vertical speed                                   |
| Speed             | `SPEED b/s`  | `XZ`    | horizontal speed                                 |
| Speed             | `SPEED b/s`  | `XYZ`   | total speed                                      |
| Acceleration      | `ACCEL b/s²` | `Y`     | rate of change of vertical speed                 |
| Acceleration      | `ACCEL b/s²` | `XZ`    | rate of change of horizontal speed               |
| Acceleration      | `ACCEL b/s²` | `XYZ`   | rate of change of total speed                    |
| Energy            | `ENERGY b`   | `KE`    | kinetic energy                                   |
| Energy            | `ENERGY b`   | `PE`    | potential energy                                 |
| Energy            | `ENERGY b`   | `TE`    | total energy                                     |
| Energy            | `ENERGY b`   | `GAIN`  | total energy gained between the last two apexes  |
| E-bounce velocity | `VEL b/s`    | `Y`     | vertical speed at touch, leave and deploy        |
| E-bounce velocity | `VEL b/s`    | `XZ`    | horizontal speed at each of those                |
| E-bounce velocity | `VEL b/s`    | `XYZ`   | total speed at each of those                     |
| E-bounce delta    | `DELTA b`    | `Y`     | height gained or lost over each interval         |
| E-bounce delta    | `DELTA b`    | `XZ`    | ground track distance over each interval         |
| E-bounce delta    | `DELTA b`    | `XYZ`   | straight-line distance over each interval        |
| E-bounce ticks    | —            | `TICKS` | elapsed ticks over each interval                 |

The three e-bounce panels read as columns rather than rows. The velocity matrix has one column per event — `T` touch, `L` leave, `D` deploy. The other two measure an interval against the event before it, so they have no touch column and their headings name the subtraction: `L-T` and `D-L`.

The Energy panel names its two columns as well, since they are different kinds of thing: `ABS` is the height against the world's origin and `REL` the height against the last apex. Kinetic energy sits under `ABS`, the cycle gain under `REL`, and potential and total energy fill both.

A reading that can go negative always shows its sign. Signed rates and deltas are colored by sign; pitch and glide ratio remain neutral. A magnitude does neither.

Each panel has its own width, so the two-row Other panel need not be as wide as the speed rows. By default they are stacked flush down the left, each overlapping the one above it by a pixel so that their borders share a column and the seven read as one panel ruled into sections.

## Bar Speedometer

A bar chart ranging from 0 to 80 b/s:

| Bar   | Speed                          |
| ----- | ------------------------------ |
| `Y`   | vertical magnitude, `\|vy\|`   |
| `XZ`  | horizontal, `sqrt(vx² + vz²)`  |
| `XYZ` | total, `sqrt(vx² + vy² + vz²)` |

Arrows for acceleration.

White markers show the speeds at the steady-state max-horizontal-speed glide:
20.191 b/s Y, 67.776 b/s XZ, and 70.719 b/s XYZ. Optional gray markers show straight-down terminal velocity: 78.400 b/s Y and XYZ, and zero XZ.

## Dial Speedometer

A semicircular dial ranging from 0 to 80 b/s. Its three needles use both color and length so
coincident readings remain distinguishable:

| Needle | Speed                          | Length   |
| ------ | ------------------------------ | -------- |
| red    | total, `sqrt(vx² + vy² + vz²)` | longest  |
| green  | horizontal, `sqrt(vx² + vz²)`  | middle   |
| blue   | vertical magnitude, `\|vy\|`   | shortest |

Matching-color concentric arrows at the needle tips project each speed's measured acceleration one second forward.

Reference markers, one per needle, are short radial marks across that needle's own tip, so a needle reaching its mark is at that speed. White markers show the steady-state max-horizontal-speed glide: 70.719 b/s total, 67.776 b/s horizontal, and 20.191 b/s vertical. Optional gray markers show straight-down terminal velocity: 78.400 b/s total and vertical, and zero horizontal.

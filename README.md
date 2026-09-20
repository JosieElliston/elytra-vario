# Elytra Vario

Elytra Vario is a client-side HUD for elytra flight and
[pump cycles](https://www.youtube.com/watch?v=-HS3_UA7GM8) in Minecraft 1.21.11 (Fabric).
Press `V` to open settings.

## Pitch Ladder

The pitch ladder shows world-relative pitch using minor, major, datum, horizon, and fine-pitch
rungs.

## Ladder Markers

Dynamic markers show the current flight path, the pitch that preserves it for the next tick,
and the pitches that maximize energy gain over one tick or a 20-tick lookahead. Fixed references
show minimum fall speed, zero pitch and best glide, and maximum horizontal speed.

| Reference | Pitch | Horizontal | Vertical | Glide ratio |
| --- | ---: | ---: | ---: | ---: |
| Maximum horizontal speed | +53.366° | 67.776 b/s | -20.191 b/s | 3.357 : 1 |
| Minimum fall speed | -13.233° | 8.736 b/s | -1.416 b/s | 6.170 : 1 |
| Best glide | 0° | 30.203 b/s | -2.990 b/s | 10.102 : 1 |

## Velocity Graph

The velocity graph plots horizontal speed against vertical speed. It includes total-horizontal
and look-direction cursors, acceleration arrows, and a velocity trail. Its heatmap shows the
maximum one-tick total-energy gain available at each velocity.

## Flight Stats

Flight Stats uses seven independently configurable panels. Other shows pitch and glide ratio;
Speed and Acceleration show vertical (`Y`), horizontal (`XZ`), and total (`XYZ`) values; Energy
shows kinetic (`KE`), potential (`PE`), total (`TE`), and cycle gain (`GAIN`). Absolute energy
uses the world's origin, while relative energy uses the last detected apex.

Three e-bounce panels compare touch (`T`), leave (`L`), and deploy (`D`). They show velocity at
each event, displacement over `L-T` and `D-L`, and elapsed ticks for those intervals. Signed rates
and deltas show their sign and sign color; magnitudes remain neutral.

## Bar Speedometer

The bar speedometer shows vertical magnitude (`Y`), horizontal speed (`XZ`), and total speed
(`XYZ`) from 0 to 80 b/s by default. Acceleration arrows project each reading one second forward.
Optional references mark maximum-horizontal-speed flight and straight-down terminal velocity.

## Dial Speedometer

The dial speedometer shows the same three speeds on a semicircular 0–80 b/s dial. Needle color
and length distinguish overlapping readings, and acceleration arrows project each reading one
second forward. It supports the same optional speed references as the bar speedometer.

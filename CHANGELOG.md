# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

A backport branch tracks this one for Minecraft 1.21.11, versioned with a `+mc1.21.11`
build identifier and released feature for feature; see its own changelog.

## [Unreleased]

## [1.1.0] - 2026-09-05

### Added

- **A toggle key per instrument** — pitch ladder, markers, velocity graph, flight stats and
  speedometer — bindable on the instrument's own settings page or in the vanilla Controls
  screen. All five start unbound. A press flips exactly the switch the settings screen shows
  and saves it, so an instrument switched off in flight stays off across a restart. A key
  already bound elsewhere is shown in red with the conflicting binds named, and allowed, as
  vanilla allows it.
- **Marker subpages**: the markers page is now one subpage per marker, chosen from a dropdown,
  so each marker's switch, color and settings sit together instead of in one long list.

### Changed

- **Per-instrument visibility is two switches rather than one three-way choice.** Whether the
  instrument is shown at all is now separate from whether it is wanted only while gliding, and
  the second is kept while the first is off, so it is still set when the instrument comes back.
  This is what makes a toggle key work: it flips one setting, with no question of which of
  three values to return to.
- Each page's own switches sit above its subpage selector, so a page's settings are never
  buried inside a subpage.
- **Markers leave the ladder rather than pegging at its edge.** A marker whose answer is off
  the ladder is simply not drawn, which is what each already did when its rule had no answer at
  all — so a marker that is not on the ladder means one thing rather than two. The flight-path
  marker leaves horizontally too, at the screen edge, rather than sliding along it. The
  speedometer's needles still peg: a speed past the stop is a limit genuinely being exceeded.
- Marker controls carry their mathematical definition and usage notes in their tooltips.

### Removed

- **The velocity bug.** It was the flight-path marker's reading drawn a second time and drawn
  worse — one axis instead of two, no sideslip, and read against another marker rather than
  against the crosshair. Where you are going is the flight-path marker's job alone, so the
  ladder is three bugs, each one a rule.
- `flightPathPeggedColor`, which went with the pegging behavior.

### Migration

- A `config/elytra-vario.json` written by 1.0.0 still reads: the retired three-way
  `*Visibility` settings are translated into their two switches on load, so an instrument you
  had hidden stays hidden. The old keys are not written back, so one save finishes it.

## [1.0.0] - 2026-09-05

First release, for Minecraft 26.2 on Fabric. Client-side only.

### Added

- **Pitch ladder** across the center of the screen, with a fading band, minor, major and prime
  rungs, and an emphasized horizon.
- **Markers** that slide along the ladder: the flight-path hold pitch to fly during the dive,
  the 20-tick lookahead optimal pitch to fly during the gain, the one-tick optimal pitch, and
  the velocity bug. Alongside them the flight-path marker, whose offset from the crosshair
  reads as flight-path angle and sideslip. A marker whose answer runs past the end of the
  ladder is held at the edge in gray.
- **Flight stats panel**, reading pitch, horizontal, total and vertical speed, glide ratio, and
  kinetic, potential and total energy. Energy uses unit mass and is divided by gravity, so it
  reads in blocks of height. Potential and total energy can show an absolute value, the change
  since the last apex, or both; `GAIN` reports the energy won between the last two apexes.
- **Velocity graph** of horizontal against vertical speed, with a 100-tick trail, a cursor for
  total horizontal speed and one for horizontal speed along the look direction, and a heatmap
  colored by the most total energy obtainable in one tick from each velocity.
- **Speedometer**: a half-disc dial in the bottom-left corner carrying three needles — total,
  horizontal and vertical speed — at three lengths, so that coincident needles still read as
  two. Past full scale a needle is held at the stop and turns gray.
- **Settings screen**, opened with `V` or through Mod Menu, with a page per instrument and live
  preview of every valid edit on the HUD behind it. Save writes `config/elytra-vario.json`;
  closing with unsaved changes asks first, and each page can be reset on its own. Advanced rows
  are collapsed by default.
- **Per-instrument visibility**, as a choice of always, only while gliding, or hidden. A master
  switch on the Global page covers the whole HUD.
- Screen anchoring for the stats panel, graph and speedometer. The graph can instead attach to
  an edge of the stats panel, and an attached pair anchors and moves as one block.

[Unreleased]: https://github.com/JosieElliston/elytra-vario/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JosieElliston/elytra-vario/releases/tag/v1.0.0

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

A backport branch tracks this one for Minecraft 1.21.11, versioned with a `+mc1.21.11`
build identifier and released feature for feature; see its own changelog.

## [Unreleased]

### Added

- Modules resize by dragging a corner in the in-world position editor. The corner opposite the
  one you take hold of stays where it is, so the module grows away from the pointer rather than
  jumping. A grip is the outer five pixels of a corner, and never more than a third of the
  module's shorter side, so a small module keeps a middle to pick it up by.

  Corners and not edges, because none of these modules has a nonuniform resize to offer. Each
  one's box is a function of a single setting — the velocity graph's scale, the flight stats
  panel's scale, the bar speedometer's plot height, the dial's radius — so an edge would have
  nothing to drag that a corner does not. Where both axes follow that setting the corner tracks
  the pointer down the box's diagonal, which is what dragging a locked-aspect corner looks like
  anywhere else. The bar speedometer is the exception worth knowing about: its width is its bars
  and its scale labels rather than a setting, so its corners follow the pointer vertically and
  ignore the rest. A drag cannot push a module off the screen past its pinned corner, and stops
  at each setting's own range.

  A resize snaps to the same rests a move does: flush with another module's edge or center, a
  margin clear of it, or against the screen's margins or center, with the same guides drawn.
  Only one edge can win, since both edges of a corner are the same number, so the nearer rest
  takes it; and a rest the setting cannot actually reach — the dial's diameter comes in steps of
  two, so half its widths do not exist — is passed over for one it can. The guides are drawn
  from the module as it ends up rather than from the size that was aimed at, so a line appears
  only where an edge genuinely lies on it.

  The module under the pointer draws its four grips as thickened corners on the outline it
  already had, and the grip the pointer has found is drawn longer and thicker than the others.
  At the same moment the white hover outline goes, because that outline means the module is
  what a drag would pick up and carry, and over a grip it no longer is. The grown grip is drawn
  larger than the area it answers to, which is safe in the direction that matters: the pointer
  is inside the plain reach whenever the larger mark is showing.

  A resize writes the same setting the module's page does, so its box follows the drag, and that
  box's tooltip now says the corners are there. The new size is measured off the module after
  the setting has been applied rather than predicted from it, so a scale that lands between
  pixels cannot walk the pinned corner sideways over a long drag.

### Changed

- The velocity graph keeps the heatmap it has while a resize drag is in progress, stretching it
  over the graph, and builds the exact one when the mouse comes up. A rebuild costs around 30ms
  at the default size and grows with the area, and a drag asks for a new size every pixel it
  moves, so rebuilding as it went made a drag into a slideshow. The map goes blocky under the
  drag and sharp again on release; only the scale changes while dragging, never the domain, so
  the stretched map describes exactly the velocities the graph is drawing and is wrong in
  nothing but resolution.
- The bar speedometer's scale labels are now shown or hidden by their switch alone. The panel
  used to drop them on its own once the major tick spacing was too fine for them to sit clear of
  one another, which meant the panel's width depended on its height: dragging the plot shorter
  made the label column vanish and the whole panel jump sideways under the pointer. Labels that
  crowd at a fine step are the step's problem and are visibly so, which is better than a panel
  that changes shape for reasons the person resizing it cannot see.
- The velocity chart's default scale is now 37.714286 pixels per block/tick rather than 34, so
  the default chart is exactly as wide as the flight stats panel stacked above it: 132 pixels
  instead of 119. Nothing about the picture changes — the domain is the same and a pixel is
  still worth the same change in speed on both axes — it is only drawn larger. Both axes span
  3.5 b/tick, so the chart stays square and gained the same 13 pixels in height, and the
  heatmap behind it now covers about a fifth more area to build.

### Fixed

- The dial speedometer's acceleration arrows sat centered on their needle's tip radius, so
  half the stem's width hung past the end of the needle. The arc now rides half a stem
  further in, flush with the tip.
- The dial speedometer's acceleration arrowheads came to a point by crossing both barbs
  past the end of the stem, which rounded to a one-pixel spike on whichever side of the stem
  it fell and read as off-center. The head now ends in a snub one stem wide, with no part of
  it reaching past the nose.

## [1.6.0] - 2026-09-12

### Added

- Reference markers on the dial speedometer, matching the bar speedometer's two sets: white
  marks at the +53.366° max-horizontal-speed glide — 70.719 b/s total, 67.776 horizontal, and
  20.191 vertical — and gray marks at straight-down terminal velocity — 78.400 b/s total and
  vertical, and zero horizontal. Each is a short radial mark across its own needle's tip, drawn
  only for needles that are shown. The white set defaults to on and the gray set to off, as
  they do on the bar speedometer.

### Changed

- The bar speedometer's panel is now measured around what it actually shows. Turning off the
  scale labels takes their column and the half line of clearance above the plot with them,
  fourteen pixels narrower and four shorter, and turning off one of the three bars closes its
  gap rather than leaving a hole in a panel of unchanged size. The margin to the right of the
  bars is now the same four pixels as the one to the left of the labels, where it used to be
  eleven.
- The selection and hover outlines in the position editor now stroke inside a module's
  bounds rather than one pixel outside them. The outline used to add a pixel on every side,
  so a module appeared to grow as the pointer crossed it and modules that were in fact flush
  with each other looked a pixel out of true while you were placing them.
- The cycle boundary is now exactly where vertical speed changes sign, rather than a
  deadband either side of zero. The rule is one the vertical speed readout shows happening.
  A stretch that only flattens out without ever climbing no longer counts as an apex, so on
  a flight that is steadily sinking the cycle readouts hold the last real apex rather than
  reporting a cycle per near-level moment.
- A latched apex no longer expires. The cycle readouts used to blank after thirty seconds
  without a new apex; they now hold the last one until another is reached, which is what a
  flight with no peak in it has to report. Teleports and dimension changes still clear them.

### Fixed

- The `GAIN` readout, and the apex the `PE` and `TE` readouts measure against, were a whole
  cycle behind whenever a cycle ended lower than it started: the apex was the highest sample
  since the previous boundary, which fell a tick past the previous apex, so that leftover
  sample outranked the new, lower apex and latched again.

## [1.5.0] - 2026-09-07

### Added

- Added a separate, unbound-by-default global key binding that toggles and saves the entire
  Elytra Vario HUD without opening the settings screen.

### Changed

- The flight-path direction-of-travel marker now defaults to on.
- Instrument toggle keys and the global HUD visibility key now work while the settings screen
  is open, as the open/close settings key already did.

### Removed

- The numeric `AOA` Flight Stats row and its setting. The flight-path marker shows the same
  vertical angle spatially and additionally shows sideslip.

## [1.4.0] - 2026-09-06

### Added

- Restored the semicircular three-needle dial as a separate Dial Speedometer, with its own
  settings page, toggle key, position, scale, colors, and appearance controls.
- Added matching-color concentric acceleration arrows at the dial needle tips. Each projects
  its speed's measured acceleration one second forward around the dial.

### Changed

- Renamed the three-bar speed chart to Bar Speedometer throughout the HUD settings and code.
- Renamed the bar speedometer's key-mapping ID as well; existing bindings to the former generic
  speedometer ID must be reassigned.
- Clarified the speed-bar reference-marker setting names and corrected the README's marker
  count from five to seven.

## [1.3.0] - 2026-09-06

### Added

- Toggleable signed horizontal, total, and vertical acceleration readouts in Flight Stats,
  displayed in blocks per second squared below the speed readouts.
- Toggleable acceleration arrows for the horizontal and look-projected cursors on the velocity
  graph. Each starts at its cursor, projects the measured acceleration for one second, and
  defaults to its cursor's color.
- Toggleable white acceleration arrows centered on the Y, XZ, and XYZ speedometer bars. Each
  starts at the current speed and projects its measured acceleration for one second; its head
  flattens into a stable horizontal mark as acceleration approaches zero.
- Toggleable white reference markers on the speed bars for the Y, XZ, and XYZ speeds at the
  +53.366° max-horizontal-speed glide, plus toggleable gray straight-down terminal-velocity
  markers, which default to off.
- An in-world position editor for Flight Stats, the Velocity Graph, and the Speedometer. Click a
  module to open its settings, drag it to move it, or use the arrow keys for one-pixel changes;
  the exact absolute coordinate fields remain editable. This replaces screen anchors and
  graph-to-stats attachment. Dragging snaps module edges and centers to one another and to the
  screen, with global margin and snap-distance settings; arrow keys and exact input bypass it.
- Three optional constant pitch markers for the fastest steady horizontal glide, minimum steady
  fall rate, and zero pitch / best steady glide ratio. They are independent of player state,
  use neutral ladder colors, and default to on.
- Speedometer bar subpages for total, horizontal, and vertical speed, keeping each bar's
  visibility and color controls together below a selector, with shared chart settings above it.
  Changing the selected bar preserves the list's scroll position.

### Changed

- The glide-ratio readout now sits directly below pitch and above the speed readouts.
- The velocity graph's default position moves down with the taller default Flight Stats panel.
- The semicircular three-needle speedometer is now a three-bar Y/XZ/XYZ chart for faster
  comparison at a glance. Existing speedometer position, scale, visibility, and color settings
  remain compatible; chart height replaces dial radius.
- The speed chart is narrower and taller by default, uses a lighter 25% background, and shows
  only its major 20 b/s gridlines.
- Settings now save themselves. Every edit is written as you make it, so the Save button, the
  Saved notice, and the unsaved-changes prompt on the way out are all gone; the screen has one
  Close button. A half-typed number is still held back from the HUD and the file until it reads
  as a number.
- The settings screen now reopens on the page and subpage you left, scrolled to where you left
  it and with Advanced as you left it, for the rest of the game session. Closing it to watch the
  HUD no longer costs you your place.
- Color settings now open a picker with red, green, blue, and, where supported, opacity
  sliders, a live swatch, and reversible live HUD preview. Opaque heatmap colors omit the
  opacity control, and the on-disk hexadecimal format remains compatible.
- The default absolute layout places Flight Stats and the Speedometer beside one another, with
  the Velocity Graph below them.

## [1.2.0] - 2026-09-06

### Added

- **The settings key is rebindable on the Global page**, beside the HUD master switch, the way
  each instrument's toggle key sits beside its own switches. It was already in the vanilla
  Controls screen and still is; either screen sets it.

### Changed

- The settings key closes the settings screen as well as opening it, as Escape does. It yields
  to a text field being typed into, so a bind on a plain letter still reaches a number or color
  box that has focus.
- The Global page's master switch now sits above the list, with the settings key below it, so
  the page has the shape every instrument page has. Nothing is left in its list, so the list is
  no longer drawn there.

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

[Unreleased]: https://github.com/JosieElliston/elytra-vario/compare/v1.6.0...HEAD
[1.6.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JosieElliston/elytra-vario/releases/tag/v1.0.0

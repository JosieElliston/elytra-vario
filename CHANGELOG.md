# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This is the Minecraft 1.21.11 backport branch. Versions carry a `+mc1.21.11` build
identifier and track the 26.2 branch feature for feature; the differences are rendering
and key-registration APIs, and the release lines its dependencies have for this game
version -- ModMenu 17.0.0 and YACL 3.8.1 rather than 20.0.1 and 3.9.6. Behavior matches,
with one exception: YACL 3.8.1 predates the cursor API the 26.2 branch asks a pointing
hand from over the inline key binds, so they are left with the arrow the rest of the
screen uses.

## [Unreleased]

### Added

- Modules resize by dragging a corner in the in-world position editor. The corner opposite the
  one you take hold of stays where it is, so the module grows away from the pointer rather than
  jumping. A grip is the outer five pixels of a corner, and never more than a third of the
  module's shorter side, so a small module keeps a middle to pick it up by.

  Corners and not edges. Three of these modules are a function of a single setting — the velocity
  graph's width, the bar speedometer's plot height, the dial's radius — so an edge would have
  nothing to drag that a corner does not. Each flight stats panel's width and text size are
  settings of their own, and its corner solves them separately: pulled sideways, only the width
  moves, which is what a pair of edges would do one at a time. Where both axes follow one setting
  the corner tracks the pointer down the box's diagonal, which is what dragging a locked-aspect
  corner looks like anywhere else. The bar speedometer is the exception worth knowing about: its
  width is its bars and its scale labels rather than a setting, so its corners follow the pointer
  vertically and ignore the rest. A drag cannot push a module off the screen past its pinned
  corner, and stops at each setting's own range.

  A resize snaps to the rests a move snaps to and to no others, with the same guides drawn: a
  module should come to rest in the same places whether it was carried there or grown there. A
  move relates two whole boxes by aligning like edges or setting one down a margin clear of the
  other, so a resize takes that same list read line by line. The dragged edge takes the rest of
  its own kind and the margin clearance on its own side; the pinned edge takes nothing, since it
  does not move. An edge dragged rightwards therefore rests flush on a right edge or a margin
  short of a left edge, and never flush against the left edge itself, because a move would not
  shove two modules together either.

  Only one answer can win, since one setting may place both moving edges. Every reachable rest
  proposes a size, and the size whose resulting corner is closest to the mouse in both dimensions
  chooses the answer. A module carrying two settings is solved a setting at a time and so has an
  answer for each; that is exact rather than approximate, because the two are orthogonal and each
  solve falls entirely on the axis its own setting grows. Every marker that independently
  produces that same answer appears with it; constraints proposing another size do not. A rest
  the setting cannot actually reach — the dial's diameter comes in steps of two, so half its
  widths do not exist — is passed over for one it can. The guides are drawn from the module as it
  ends up rather than from the size that was aimed at, so a line appears only where an edge
  genuinely lies on it.

  The module under the pointer draws its four grips as thickened corners on the outline it
  already had, and the grip the pointer has found is drawn longer, thicker and white. At the
  same moment the white hover outline goes, because that outline means the module is what a drag
  would pick up and carry, and over a grip it no longer is: the white moves from the box to the
  grip that has taken the drag over, so there is one white thing on screen at a time and it is
  always what the next click will act on. The grown grip is drawn larger than the area it
  answers to, which is safe in the direction that matters: the pointer is inside the plain reach
  whenever the larger mark is showing.

  A resize writes the same setting the module's page does, so its box follows the drag, and that
  box's tooltip now says the corners are there. Its pinned corner is placed from the module's
  actual post-layout size, so a secondary dimension rounded from the module's aspect ratio
  cannot walk that corner sideways over a long drag.

- Flight Stats' original twelve rows are now four panels — **Other** (pitch, glide), **Speed**,
  **Acceleration** and **Energy** — each placed, sized, and switched on or off on its own, in
  place of the single panel that carried them. Each has its own subpage under a selector on the
  Flight Stats page; the instrument's switch, its only-while-gliding companion, its toggle key,
  and the positive and negative colors stay shared above that selector, since the switch is what
  the key binds to and the colors are a palette rather than a layout. Every row means exactly what
  it meant and is still switched on or off individually; it has only moved subpage.

  **The widths are independent, which is the point.** A row wants the width its widest label and
  figure need, so one shared width had to satisfy the widest row on the panel: `SPEED XYZ`
  against a speed is 106 pixels of content, `GLIDE` against a ratio is 76, and the thirty
  between them was dead gap on every attitude row. The narrow panel can now be narrow. Each
  panel keeps its own floor, below which its own columns would meet.

- Modules snap butted together as well as a margin apart, overlapping by one pixel so that their
  two borders land on one column and the pair reads as a single panel with a rule between it.
  Edge to edge would put two identical gray lines side by side — a two-pixel seam rather than a
  division — and the pixel each module gives up is one it was spending on saying where it ends,
  which the shared line now says for both. Moves and corner resizes both offer it, as they offer
  every other rest, and the guide is drawn on the shared column. It is what the stats panels are
  stacked with by default, and it is available between any two modules and on either axis.

- Three e-bounce panels read the last bounce as a matrix: **velocity** with a column per event —
  `T` touch, `L` leave, `D` deploy — over `Y`, `XZ` and `XYZ` rows, and **position delta** and
  **elapsed ticks** over the intervals between those events. An interval is only ever the
  difference against the event before it, so those two have no touch column and their headings
  name the subtraction, `L-T` and `D-L`. All three right-align onto the same column edges, so at
  equal widths the two interval columns sit under the velocity matrix's `L` and `D`.

  The two three-row matrices carry the same three quantities the Speed panel does and in the
  same order: the signed vertical component, then the two magnitudes. A single world axis is not
  among them — `X` alone says which way the world happens to be oriented rather than which way
  the bounce went, and `XZ` is the rotation-independent quantity that replaces it. So the top row
  of the velocity matrix is vertical speed at each event, and the top row of the position matrix
  is the height gained or lost over each interval.

  Only that top row is colored by its sign. The two under it are a speed and a distance, and the
  elapsed ticks are a count forwards from the earlier event, so none of the three can be
  negative and coloring them would report them positive on every frame — the same rule the Speed
  panel already followed, where vertical speed is colored and the two magnitudes beside it are
  not.

  **A panel's width floor is now computed from the rows it draws** rather than typed in beside
  it. A row costs the panel's padding either side, its label, two pixels so that at the floor the
  label and the figure beside it are still two separate words, its own leftmost figure, and a pad
  and a full reserved column for every column to the right of it; the floor is the widest row.
  The glyph advances that runs on are Minecraft's default font written out in `LayoutWidths`,
  pinned by the strings the original panels were measured through the font by — which the
  computation reproduces exactly for three of the four, acceleration moving from a typed 116 to a
  measured 115 because the superscript on `b/s²` is a pixel narrower than the digit a speed has
  in its place.

- Every paired pitch-ladder marker now has its own pixel-exact shape. **Inset** leaves clearance
  from the ladder, **length** carries the marker inwards to its point, and **horizontal pixels per
  step** sets the staircase cut from that point: one pixel makes the square 90-degree point, a
  larger step makes a sharper one, and zero makes a one-row reference line. The last partial
  step is kept at the marker's outside edge, so changing a length does not blunt its point.

  These settings belong to each marker rather than to the ladder as a whole. Two markers may
  therefore use different insets as well as different shapes and colors, while the defaults
  align all seven at one outside edge. Taller shapes are drawn first and shorter ones over them,
  making a customized overlap nest without relying on the order the markers happen to be
  computed in.

  Each marker page has a compact pixel preview beside the controls. A preview square is one GUI
  pixel at the current GUI scale, so the editor shows the exact staircase the HUD will draw rather
  than a smoothed approximation.

- Ladder markers have two independent shadow switches: one for the four dynamic markers — flight
  path, flight-path hold, one-tick optimal and lookahead optimal — and one for the three static
  pitch references. Both default to on. The shadow is a restrained one-pixel down-right offset
  composited behind the ladder, while the marker itself remains in front of it.

### Changed

- Every settings page is now cut into collapsible sections, and every page opens on the same
  four rows: the HUD layout button, the key that toggles what the page configures, the switch
  that toggles it, and its only-while-gliding companion. A page's own switch is what most
  visits are for, so it is always in the same place rather than wherever its page happened to
  list it.

  Pitch Ladder and Velocity Graph were the pages this is for, each a single undivided list.
  Pitch Ladder now reads *Band* — the center gap, the upper and lower extents and the edge
  fade, which are the four the ladder markers are drawn against as well — then *Rungs and
  labels*, then *Fine ticks*. Velocity Graph reads *Axes*, *Velocity trail*, *Energy heatmap*,
  and a section per cursor, each cursor kept with the acceleration arrow that projects it, since
  the two are one reading in two parts.

  The other pages gained the sections that were missing around what they already had. Ladder
  Markers puts the two shadow switches in a *Shadows* section above its per-marker ones, Flight
  Stats puts the positive and negative colors in *Value colors* above its panels, and the
  speedometers follow their bars or needles with *Scale*, *Overlays* and *Panel* — all of which
  previously sat in a headingless run above the very sections they belong beside, because an
  unsectioned setting is drawn above every heading on its page whatever order it was written
  in. Global keeps the settings key and the way through to the vanilla Controls list in *Keys*,
  and the position editor's margin and snap distance in *Layout editor*.

  Nothing was added, removed or renamed; the settings themselves and their saved names are
  unchanged.
- Ladder marker settings are ordered by what their marks mean on the HUD: flight path,
  flight-path hold, one-tick optimal, lookahead optimal, minimum fall speed, zero/best glide, and
  maximum horizontal speed. Their descriptions now say directly what each mark reports — in
  particular, the flight-path marker is the direction of the current velocity relative to the
  camera — without repeating assumptions common to every calculation in the project.
- The three advisory wedges now default to one compact 2:1 staircase. One-tick optimal is hot red,
  lookahead optimal remains amber, and flight-path hold is blue-violet; the two energy choices
  therefore stay in one warm family while the kinematic hold remains distinct from the
  flight-path marker's sky blue. The fixed references retain their line shapes but now differ in
  opacity as well as pitch. A config saved before marker-shape settings existed deliberately
  adopts this complete new appearance, including colors, instead of carrying the retired look
  into the new shape system; subsequent edits persist normally.
- The default Flight Stats stack and both speedometers now begin below the in-world layout
  editor's header instead of letting their top row sit behind it.
- The default Flight Stats layout is one vertical stack of all seven panels down the left edge,
  in the order the single panel read in with the three matrices under it, every one of them 150
  wide so the stack has one right edge as well as one left. The matrices previously sat in a
  second column beside the other four at a width of 200. 150 is the widest of the seven floors,
  which makes it the narrowest width the whole stack can be drawn at. Existing configs name their
  own positions and are untouched.

- Every module's background now defaults to a quarter opacity. The flight stats panels came in
  at 0.69 and the dial speedometer at 0.45, against the bar speedometer's 0.25; a HUD out of the
  box therefore showed three different grays over the same world. The bar speedometer's value is
  the one kept because it is the most transparent of the three, and these panels are worth seeing
  through. Each module's opacity remains its own setting.

  An existing config is reset to the quarter as well, for the flight stats panels and the dial
  speedometer both, rather than left at what it said. Every config file states a value for every
  setting, so a file written before this records 0.69 and 0.45 whether or not anyone chose those
  numbers, and respecting them would have confined the new default to fresh installs — the one
  place the three grays were never seen. Set the opacity again if you had picked one on purpose.
  The bar speedometer is untouched, being already at the quarter.

  The dial's setting is renamed from `dialSpeedoOpacity` to `dialSpeedoBackgroundOpacity`, which
  is what carries that reset: a value saved under the old name cannot be told from one a player
  chose under it, so only a new name resets an existing file once and then leaves the setting
  alone. A hand-edited config should use the new name; the old one is ignored and is gone after
  the next save.
- Drag snapping no longer uses module or screen center lines. Moves and corner resizes now snap
  only to edges, removing the competing middle guide when boxes are already edge-aligned.
- Each Flight Stats panel is sized by a width in pixels and a text size that move independently,
  replacing the single panel's one size setting and the advanced content width behind it. The
  height is neither: the rows are laid out at a fixed line height and drawn at the size asked
  for, so the panel is exactly as tall as its rows need and cannot be left with a gap at the
  bottom. The width then buys one thing only, the distance between a label and the value
  right-aligned against the far edge — the panel's only dead space, and something the single
  setting could not close without shrinking the reading along with it. A width narrower than the
  rows need at the current text size is drawn at that minimum rather than refused, so a panel
  never becomes an overlap.
- **A Flight Stats panel's text size snaps to the sizes its font is actually drawn at, and
  sizes that would be interpolated cannot be drawn at all.** Minecraft's font is a bitmap and
  its atlas is sampled `NEAREST`, so nothing is ever blended — what goes wrong at a size like
  1.3× is rounding, not blurring: each glyph pixel claims whichever screen pixels are nearest,
  so some strokes come out two pixels wide and their neighbours one, and the same letter is a
  different shape in different words.

  A glyph pixel covers the text size times your GUI scale, and it is that *product* that has to
  be whole. Your GUI scale is already a whole number, so the sizes that survive are the
  multiples of one over it — quarters at GUI scale 4, thirds at 3, halves at 2, and only whole
  numbers at 1. **The smallest text there is is one screen pixel per font pixel**, which is a
  quarter of the font's nominal size at GUI scale 4.

  The setting itself is a plain multiplier that knows nothing about your GUI scale, because you
  can change that under a config that is already saved. It is snapped to a drawable size where
  the panel is drawn instead, so the same file stays sharp at every GUI scale and a hand-edited
  one cannot ask for blurred text.

  Sizes run from a sixteenth to 8. Eight is where the width setting runs out — the widest
  panel's rows need 150 pixels at size one, and the width may be set to 1200.

  This is also what makes two panels agree. Butted into a stack they are meant to read as one
  instrument, and nothing says otherwise like two sections of it set in different sizes; two
  panels showing the same number are set to the same size exactly, whatever their row counts.

- Switching a Flight Stats row off now shortens its panel instead of enlarging its text. The
  height used to be the setting and the text size the quotient, so turning off a row shrank the
  divisor and left the dividend where it was — a checkbox reading *show total speed* also made
  every letter on the panel bigger. It now does the one thing it says.
- A Flight Stats panel from before the split migrates to four panels at the text size it was
  drawn at, stacked from where it sat with their borders sharing a column, so a migrated HUD
  reads at the size and in the order it read in. Each takes the size *its own* rows want, which
  is not the height the retired panel would have given them: three of the four draw a units
  heading it never had, and measuring without it cost them a fifth of their text size on the way
  across. The size carries over exactly, fraction and all, and is read back off whichever
  dimension the old file actually stated rather than off one derived from the other — deriving
  it rounded a panel up to the next whole pixel and then divided that rounding into its text
  size.
- Every figure column on every Flight Stats panel now reserves the same width, so a stack of
  panels butted together at one width has one grid of columns rather than several that nearly
  agree. A column is placed by measuring back from the panel's right edge, so the three separate
  templates agreed on the rightmost column — every panel aligns its last column onto its own
  right edge whatever the template says — and disagreed on every column left of it, which put
  the Energy panel's `ABS` column two pixels off the e-bounce matrices' left column. The
  accident that makes one width enough is that seven glyphs and one stop measure the same
  whether they are spent on three digits and two decimals or on four and one, so a two-decimal
  speed and a one-decimal altitude want the same column.
- A stats panel's rows are now scaled by one factor on both axes, and its background is filled on
  the exact box instead. The width setting buys unscaled space to the right of the figures, as it
  always did, but it no longer stretches the glyphs sideways to reach the box's corner: the two
  axes were scaled separately, by a fraction of a pixel that differed with every width, so a
  panel's text was a slightly different shape at every width it was given.
- Every Flight Stats panel now has a heading row saying what its figures are measured in, and
  its rows are labelled by what tells them apart rather than by the panel they are on. `SPEED Y`,
  `SPEED XZ` and `SPEED XYZ` against figures each carrying their own `b/s` are now `SPEED b/s`
  over `Y`, `XZ` and `XYZ`; Acceleration and Energy likewise, under `ACCEL b/s²` and `ENERGY b`.
  This is what the e-bounce matrices have done since they arrived, under `VEL b/s` and
  `DELTA b`, and it was the only place a unit was written once rather than once a row.

  A unit is a fact about a panel rather than about any row of it, and writing it per row cost
  twice over: the suffix on every figure, and a label repeating `SPEED` three times to introduce
  the one letter that actually differed. The heading costs a row and gives back three labels'
  worth of width — Speed's minimum width falls from 116 to 66 — so the three panels are a row
  taller by default and can be placed in half the width. *Other* keeps its per-row labels,
  because a pitch in degrees and a dimensionless ratio have no unit in common for a heading to
  state.

  Energy's heading also names its two columns, `ABS` for the height against the world's origin
  and `REL` for the height against the last apex, since they are the one pair of columns on any
  panel that are different kinds of thing. Kinetic energy now sits under `ABS` and the cycle's
  gain under `REL`, each under the heading that describes it, where both used to be drawn
  against the panel's right edge whatever the column there meant. The names appear only in the
  mode that draws both columns.

  On the elapsed-ticks matrix, `TICKS` moves down a row. It names the figures, not the `L-T` and
  `D-L` above them, and every other label on every panel is drawn beside the figures it names.
- Every Flight Stats row that can go negative now writes its sign explicitly, and every row that
  cannot writes none. `PITCH`, `GLIDE` and the muted absolute figure beside `PE` and `TE` were
  the three that did not: all can go either way about a datum — nose up or nose down about
  level, a negative glide ratio is a climb rather than a shallower descent, and a world has
  floors below zero — and a leading minus that only appears half the time is easy to read past.
  The absolute figure stays muted, since muting says how loudly a figure asks to be read rather
  than which conventions it is written in.
- Velocity Graph and Flight Stats now expose their exact integer pixel widths instead of
  floating-point scale factors, matching the speedometers' pixel size settings. Existing scale
  settings migrate to the widths they rendered at, and resizing now changes a whole-pixel size.
- A resize now shows the same translucent true-position outline as a move. The live module still
  shows the snapped result; the outline shows the unsnapped box the mouse is requesting, making
  both the captured alignment and the distance needed to pull free explicit.
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
- The velocity chart's default width is now 132 pixels rather than 119, exactly matching the
  flight stats panel stacked above it. Nothing about the picture changes — the domain is the
  same and a pixel is still worth the same change in speed on both axes — it is only drawn
  larger. Both axes span 3.5 b/tick, so the chart stays square and gained the same 13 pixels in
  height, and the heatmap behind it now covers about a fifth more area to build.

### Fixed

- Overlapping ladder-marker shadows now form one union at the strongest contributing opacity
  instead of darkening where two copies stack. That union excludes every marker's foreground,
  so a shadow cannot show through a translucent fill, and it keeps the same fractional screen
  position as the marker instead of snapping independently to Minecraft GUI pixels.
- Marker shadows are now submitted before the ladder's rungs and pitch labels, preventing a
  shadow from being painted over text or a rung that should cover it. Pitch labels keep their
  own useful text shadow, but first write the foreground glyph to depth; the shifted shadow is
  therefore blocked only where it would otherwise show through the label itself.
- A Flight Stats panel's corner resize is now a function of where the pointer is rather than of
  how it got there. Both bounds on its two size settings were read off the panel as it stood:
  the width could not be dragged below the rows' width at the panel's *current* text size, and
  the text size could not be dragged past what the panel's *current* width holds. That closed a
  loop — this event's width depended on the last event's size, and this event's size on this
  event's width — and the loop has fixed points a drag cannot leave. A panel sitting exactly on
  its content floor could not get narrower, because the size it had demanded that width, and
  could not get larger, because the width it had forbade that size, so it stood still under a
  pointer asking for something else and then unwound in a rush once the pointer crossed into
  something the loop admitted.

  The width's floor is now the panel's rows at the smallest text size its setting allows, which
  is a constant, and the size's cap is taken from the width the same event just settled on.
  Nothing is given up: the size is still capped so the settled width holds the rows, so the
  panel is still never drawn wider than the drag placed it.

- Resizing a Flight Stats panel vertically no longer makes its horizontal edge run away from
  the pointer. A stats resize now establishes its width first and limits text growth to the
  largest size that width can contain.
- Resize snaps at a margin, a shared-border butt, or a screen margin now draw every target edge
  that produced the winning size. Their guides were previously discarded because validation
  compared the resized edge with the target edge instead of with its offset resting coordinate.
- Position-editor module tooltips now disappear during move and resize drags, so they do not
  cover modules and alignment guides to the right of the pointer.
- Resize candidates are now evaluated from the immutable geometry at the start of the drag and
  ranked by the resulting corner's full two-dimensional distance from the mouse. Recomputing
  from each previous rounded result could make equivalent edge alignments trade places from
  frame to frame, while comparing only the offering line could choose the farther result on
  modules such as the two-to-one dial.
- Resize snapping now carries the constraints that produced its winning size through to guide
  rendering. The renderer previously rediscovered every line the rounded result happened to
  touch, which could show markers whose constraints had proposed different sizes. Multiple
  markers now appear together only when each independently gives the winning answer.
- The dial speedometer's acceleration arrows sat centered on their needle's tip radius, so
  half the stem's width hung past the end of the needle. The arc now rides half a stem
  further in, flush with the tip.
- The dial speedometer's acceleration arrowheads came to a point by crossing both barbs
  past the end of the stem, which rounded to a one-pixel spike on whichever side of the stem
  it fell and read as off-center. The head now ends in a snub one stem wide, with no part of
  it reaching past the nose.

## [1.6.0+mc1.21.11] - 2026-09-12

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

## [1.5.0+mc1.21.11] - 2026-09-07

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

## [1.4.0+mc1.21.11] - 2026-09-06

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

## [1.3.0+mc1.21.11] - 2026-09-06

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

## [1.2.0+mc1.21.11] - 2026-09-06

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

## [1.1.0+mc1.21.11] - 2026-09-05

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

## [1.0.0+mc1.21.11] - 2026-09-05

First 1.21.11 build, backported from the 26.2 branch. Client-side only.

### Added

- **Pitch ladder** across the center of the screen, with a fading band, minor, major and prime
  rungs, and an emphasized horizon.
- **Markers** that slide along the ladder: the flight-path hold pitch to fly during the dive,
  the 20-tick lookahead optimal pitch to fly during the gain, the one-tick optimal pitch, and
  the flight-path marker, whose offset from the crosshair reads as flight-path angle and
  sideslip.
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
- Screen anchoring for the stats panel, graph and speedometer. The graph can instead attach to
  an edge of the stats panel, and an attached pair anchors and moves as one block.

[Unreleased]: https://github.com/JosieElliston/elytra-vario/compare/v1.6.0+mc1.21.11...mc/1.21.11
[1.6.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.5.0+mc1.21.11...v1.6.0+mc1.21.11
[1.5.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.4.0+mc1.21.11...v1.5.0+mc1.21.11
[1.4.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.3.0+mc1.21.11...v1.4.0+mc1.21.11
[1.3.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.2.0+mc1.21.11...v1.3.0+mc1.21.11
[1.2.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.1.0+mc1.21.11...v1.2.0+mc1.21.11
[1.1.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/compare/v1.0.0+mc1.21.11...v1.1.0+mc1.21.11
[1.0.0+mc1.21.11]: https://github.com/JosieElliston/elytra-vario/releases/tag/v1.0.0+mc1.21.11

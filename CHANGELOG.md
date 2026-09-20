# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The Minecraft 1.21.11 backport uses a `+mc1.21.11` build identifier and has its own changelog.

## [Unreleased]

### Added

- Corner resizing in the HUD layout editor, with snapping, alignment guides, size limits, and
  fixed opposite corners.
- Independent Other, Speed, Acceleration, and Energy panels in Flight Stats.
- Snap points that join module borders into a shared one-pixel divider.
- E-bounce velocity, displacement, and elapsed-tick matrices for touch, leave, and deploy events.
- Per-marker inset, length, step, and pixel-preview controls for pitch-ladder markers.
- Separate shadow controls for dynamic and static ladder markers.

### Changed

- Organized every settings page into collapsible sections with layout, key, visibility, and
  gliding controls first.
- Reordered ladder-marker settings and clarified their descriptions.
- Updated default marker shapes, colors, and opacity. Configs from before marker-shape settings
  use the new defaults.
- Stacked all seven default Flight Stats panels at a width of 150 pixels.
- Standardized module backgrounds at 25% opacity. Existing Flight Stats and dial settings reset
  once; the dial key is now `dialSpeedoBackgroundOpacity`.
- Removed module and screen center lines from drag snapping.
- Gave each Flight Stats panel independent pixel width and text size. Height now follows visible
  rows, and text size snaps to the nearest sharp bitmap-font scale.
- Migrated the former combined Flight Stats panel into four panels while preserving its rendered
  text size and position.
- Standardized Flight Stats headings, units, signs, figure columns, and two-axis text scaling.
- Replaced Velocity Graph and Flight Stats scale settings with integer pixel widths; existing
  values migrate to their rendered widths.
- Added an unsnapped outline during resize drags.
- Deferred velocity-heatmap regeneration until resizing ends; the existing heatmap stretches
  during the drag.
- Made bar-speedometer scale-label visibility depend only on its setting.
- Increased the default velocity-graph size from 119 to 132 pixels.

### Fixed

- Combined overlapping ladder-marker shadows without increasing their opacity or showing them
  through marker fills.
- Rendered marker shadows behind ladder rungs and labels while preserving label shadows.
- Removed feedback loops and pointer drift when resizing Flight Stats panels.
- Restored all applicable guides for snapped resizes.
- Hid module tooltips during layout drags.
- Made resize snapping deterministic and limited guides to constraints that produced the result.
- Aligned dial acceleration arrows with needle tips.
- Replaced off-center pointed dial arrowheads with centered flat tips.

## [1.6.0] - 2026-09-12

### Added

- Optional max-horizontal-speed and terminal-velocity reference markers on the dial speedometer.

### Changed

- Sized the bar-speedometer panel around its visible bars and labels.
- Drew layout-editor outlines inside module bounds.
- Detected cycle boundaries at vertical-speed sign changes instead of using a deadband.
- Kept the last detected apex until another apex, teleport, or dimension change.

### Fixed

- Corrected cycle gain and relative-energy values after cycles ending below their starting apex.

## [1.5.0] - 2026-09-07

### Added

- An unbound global key for toggling and saving HUD visibility.

### Changed

- Enabled the flight-path marker by default.
- Allowed visibility keys to work while the settings screen is open.

### Removed

- The redundant numeric `AOA` Flight Stats row and setting.

## [1.4.0] - 2026-09-06

### Added

- A separate three-needle Dial Speedometer with its own settings, key, layout, and appearance.
- Dial acceleration arrows that project measured acceleration one second forward.

### Changed

- Renamed the three-bar chart to Bar Speedometer.
- Renamed its key-mapping ID; bindings using the old ID must be reassigned.
- Clarified bar reference-marker names and corrected their count in the README.

## [1.3.0] - 2026-09-06

### Added

- Signed acceleration rows in Flight Stats.
- Acceleration arrows on velocity-graph cursors and speedometer bars.
- Max-horizontal-speed and terminal-velocity markers on speedometer bars.
- An in-world position editor with dragging, arrow-key movement, exact coordinates, and snapping.
- Fixed pitch references for maximum horizontal speed, minimum fall speed, and best glide.
- Per-bar speedometer settings pages.

### Changed

- Moved glide ratio below pitch in Flight Stats.
- Moved the default velocity graph below the taller Flight Stats panel.
- Replaced the dial speedometer with a Y/XZ/XYZ bar chart while migrating compatible settings.
- Updated the speedometer's default size, background opacity, and grid.
- Saved valid settings immediately and removed the Save workflow.
- Preserved the settings page, subpage, scroll position, and Advanced state during the session.
- Added color pickers with live preview.
- Updated the default HUD layout.

## [1.2.0] - 2026-09-06

### Added

- A rebindable settings key on the Global page.

### Changed

- Made the settings key close the screen unless a text field is active.
- Moved the Global master switch and settings key above the empty settings list.

## [1.1.0] - 2026-09-05

### Added

- Unbound toggle keys for each instrument, with conflict warnings and saved visibility.
- A settings subpage for each ladder marker.

### Changed

- Replaced each three-state visibility control with separate enabled and gliding-only switches.
- Moved instrument visibility controls above subpage selectors.
- Hid off-ladder markers instead of pinning them to the edge.
- Added definitions and usage notes to marker tooltips.

### Removed

- The redundant velocity bug.
- `flightPathPeggedColor`.

### Migration

- Converted 1.0.0 `*Visibility` values to the new visibility switches on load.

## [1.0.0] - 2026-09-05

First release for Minecraft 26.2 on Fabric. Client-side only.

### Added

- A pitch ladder with fading rungs and an emphasized horizon.
- Dynamic pitch and flight-path markers.
- Flight Stats for attitude, speed, glide ratio, and energy.
- A velocity graph with two cursors, a trail, and an energy heatmap.
- A three-needle dial speedometer.
- Per-instrument settings with live preview and JSON persistence.
- Per-instrument and global visibility controls.
- Screen anchoring and graph-to-stats attachment.

[Unreleased]: https://github.com/JosieElliston/elytra-vario/compare/v1.6.0...HEAD
[1.6.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/JosieElliston/elytra-vario/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JosieElliston/elytra-vario/releases/tag/v1.0.0

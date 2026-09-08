# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0] — unreleased

First version.

### The app
- Personal catalogue of items and tasks, with fuzzy matching on add so near-duplicates do
  not accumulate, usage counters, and a "not used in over a year" sort.
- Trips as deep-copied snapshots from two built-in templates, a past trip, or nothing.
- A deterministic wizard: accommodation, nights and activities compose a list from
  inspectable rules, and every generated item can say which rule put it there.
- Quantity rules resolved against trip length once, at creation, and frozen.
- Three item states — to buy, to pack, packed — with to-buy shown as blocking rather than
  folded into progress, and a shopping view across all active trips.
- Tasks alongside items, with their own affordance, no bag and no quantity.
- The trip list groups by section, bag or traveller, items are assigned to a traveller
  from the row itself, and order is changed by dragging a handle in an explicit reorder
  mode — never by a stray touch while packing.
- Items are added to a trip after it exists, from a sheet that stays open across a burst
  of them, offers what you most often carry and is not already on this trip, and matches
  against the catalogue as you type so a near-duplicate does not sneak in.
- Search inside a trip, diacritic-insensitive, so "punjac" finds "punjač".
- Return mode on a separate axis from packing, with a left-behind counter written back to
  the catalogue when a trip is closed.
- Optional photo per catalogue item through the Photo Picker, copied into private storage,
  downscaled, and stripped of EXIF.
- PDF export whose checkboxes are real AcroForm fields, on a narrow page sized for a phone
  screen or on A4 for printing.
- Full JSON export and import; additive, never deleting, matched by UUID.
- Home-screen widget and a launcher shortcut.
- Serbian (Latin) and English, with an in-app language override.

### Deliberately not built
- Weather, destination databases, accounts, cloud sync, real-time collaboration.
- Per-bag weight estimates. The feature only switches on after roughly ninety hand-entered
  numbers and then shows an estimate with a disclaimer.
- Pre-trip reminder notifications. It was the only thing in the app that would have needed
  a runtime permission.

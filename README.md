# Stow

An offline packing checklist for Android, built around a personal catalogue of the things
you actually own. It resolves quantities from trip length by explicit rules, tracks items
through three states rather than two, and has a second mode for the other half of the
problem — what you leave behind in the rented apartment.

Built for one person's own travelling. Not published to any store.

<!-- screenshots: fastlane/metadata/android/en-US/images/phoneScreenshots/ -->

## Four things that make it not generic

**Zero network capability, enforced by the build.** The merged manifest contains no
`<uses-permission>` at all — not `INTERNET`, not `ACCESS_NETWORK_STATE`, not one. A Gradle
task parses the *merged* manifest, the one that actually ships, and fails the build if any
permission appears. It has already earned its keep: it caught `androidx.glance` pulling in
WorkManager, which declares `ACCESS_NETWORK_STATE`. The widget is plain `RemoteViews` now.

**A catalogue, not a list of strings.** Three layers, and the distinction between them is
the architecture: the **catalogue** is what you own, **templates** are blueprints that
reference it live, and **trips are snapshots** — deep-copied at creation, so editing the
catalogue never mutates a trip that already exists. See [ARCHITECTURE.md](ARCHITECTURE.md).

**Generation you can inspect and correct.** A short questionnaire composes a list from
rule-based fragments. Every generated item records which rule put it there, and a "why is
this here?" affordance shows it. The competitors do this probabilistically and get it
wrong in ways you can neither see nor fix.

**A PDF writer written from scratch.** Trips export as PDFs whose checkboxes are real
AcroForm fields — send one to whoever you travel with and they tick it in any reader. The
writer is a separate pure-Kotlin module with no third-party PDF dependency. The page comes
in two sizes: a narrow one that fits a phone screen at the reader's default zoom, so the
list reads without panning, and A4 for printing.

## Design

One aesthetic, executed fully: a travel document, but a soft one — a luggage tag and a
ticket rather than a customs form. Cool grey-green paper, ink, one accent, and no sharp
corner anywhere.

The four item states are distinguishable **without relying on colour**, because shape
carries the meaning: a square is an object, a circle is a task, a dotted outline is
something to buy, and filled means done. "Packed" has no colour at all — it is an
ink-filled box, like a form ticked with a pen. Packing and returning modes differ by
value inversion, not hue, so the difference survives greyscale and colour blindness.

Typography is Rubik (its brief is *slightly rounded corners*) and Nunito. Both ship inside
the APK — the app has no network to fetch them — and both are verified to cover
`č ć š ž đ Č Ć Š Ž Đ` by a script, not by eye. There is no monospace: figures line up
through `tabular-nums` instead.

## Build

```bash
./gradlew assembleRelease
```

JDK 17+, Android SDK 37. No signing config is required for a debug build.

```bash
./gradlew check        # unit tests, plus the no-permission manifest check
```

## Dependencies, and why each one is here

| Dependency | Why |
|---|---|
| Jetpack Compose + Material 3 | The UI toolkit. Material 3 supplies the colour-scheme plumbing; the palette itself is fixed and dynamic colour is off. |
| Room | The database, with exported schemas committed and asserted against the code. |
| DataStore | Four settings and a seed version. |
| navigation-compose | Type-safe routes between eight destinations. |
| kotlinx-serialization | JSON export/import and the seed file. |

Deliberately absent: no dependency injection framework (the graph is one database, one
DataStore and four repositories — it fits in `AppContainer`); no `kotlinx-datetime` or
desugaring (`minSdk 26` is exactly where `java.time` is native); no PDF library; no image
loader (every photo is already local and downscaled to 1024 px, so loading one is "read a
small JPEG"); no `androidx.exifinterface` (the platform one reads from a stream from API
24); no Glance (it pulls in WorkManager, which declares `ACCESS_NETWORK_STATE` — the
widget is plain `RemoteViews`); no analytics, crash reporting, Firebase or Play Services.

## Tests

`./gradlew check` runs 72 unit tests and the manifest check. Three of them are worth
pointing at:

The PDF tests parse the **emitted bytes**, not the writer's internals — they walk the xref
table to confirm every offset points at the object it claims, assert each checkbox has both
appearance states, and check that Serbian text never leaves as a Latin-1 literal. A PDF is
correct exactly when what was written can be read back.

The seed tests assert that every item key the wizard rules mention actually exists in the
seed. A rule pointing at a missing key silently does nothing, so "beach" would ship without
a swimsuit and nobody would find out until the airport.

The schema test compares the committed schema against the database the code actually
creates. There are no migrations yet — the schema is at version 1 — but a schema file that
has drifted from the code is a migration written against a document that lies, and that is
cheaper to prevent than to debug.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
Bundled fonts are under the SIL Open Font License 1.1.

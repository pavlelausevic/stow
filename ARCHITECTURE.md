# Architecture

## Three layers, and why the split is the point

```
CATALOGUE ──referenced by──> TEMPLATES ──deep-copied into──> TRIPS
(what you own)               (blueprints)                    (snapshots)
```

**Catalogue** — `CatalogItemEntity`. The single source of truth for a thing you own: its
name, default bag, default quantity rule, suggested section, photo, and the counters
`timesUsed` / `timesLeftBehind`. Items are archived, never hard-deleted, because trips
reference them.

**Templates** — `TemplateEntity` / `TemplateSectionEntity` / `TemplateEntryEntity`. A
template holds sections, and each entry *points at* a catalogue row. The link is live:
rename something in the catalogue and every template shows the new name immediately. An
entry may override the bag or the quantity rule, and nothing else.

**Trips** — `TripEntity` / `TripSectionEntity` / `TripItemEntity`. A trip is a **snapshot**.
At creation the template is deep-copied and denormalised: `TripItem.title` is a copy of the
catalogue name, not a foreign key to it. `catalogItemId` survives only for provenance and
statistics, and is nullable with `ON DELETE SET NULL`, so archiving a catalogue item can
never take a trip row down with it.

A snapshot is a snapshot of *content*, not of language. `TripSection.seedKey` and
`TripItem.seedKey` carry the seed key across the deep copy, and switching the app language
rewrites exactly those rows — a hundred and forty seeded items are the same object under
two names and were never the user's words. A row he typed has no key and is never
rewritten; there is no translator here, only two lists.

The reason for the split is that a packing list is a historical record as much as a plan.
If you rename "Punjač za telefon" to "GaN punjač" in March, the trip you took in January
should still say what you actually packed.

## `packStatus` and `isReturned` are separate axes

They are two fields, not one state machine, and merging them was never on the table.

Packing and returning are different journeys over the same list. `packStatus` moves
`TO_BUY → TO_PACK → PACKED` on the way out; `isReturned` is a fresh boolean on the way
home. If they were one enum, the question *"what did I fail to bring home?"* would be
unanswerable — a `PACKED` item that never came back and a `PACKED` item that did would be
the same value.

`TO_BUY` exists for the same reason. Sunscreen used up, plasters expired, no adapter yet:
that is a pre-trip errand, not an unpacked item, and conflating the two is why people keep
a second list in another app. Trip progress therefore counts only `TO_PACK` and `PACKED`;
`TO_BUY` items are surfaced separately and never folded into the denominator.

### What the return list contains

The specification said the return list "starts fully unchecked" but not what fills it.
Filling it with everything is wrong: an item still marked `TO_BUY` was never bought, and
one still `TO_PACK` was never packed. **You cannot leave behind what you never brought.**

`ReturnList.build` therefore includes:

- everything from `RETURN`-phase sections (these are prompts like "cables behind the TV",
  which are never packed in the first place), and
- from `PACKING` / `BOTH` sections, only items with `packStatus == PACKED`, with tasks
  excluded — "water the plants" is not something you carry home.

Anything left over goes into a dimmed "not taken" group: visible, so nothing disappears
without explanation, but outside the denominator.

## The quantity rule engine

```kotlin
sealed interface QuantityRule {
    data class Fixed(val count: Int)
    data class PerNights(val perNights: Int, val plus: Int = 0, val cap: Int? = null)
    data object Unspecified
}
```

Rules are stored as four discrete columns (`ruleType`, `rulePer`, `rulePlus`, `ruleCap`),
not as a JSON blob: the set is closed and tiny, Room migrations over a JSON string are
painful, and columns allow the query *"every item with a per-night rule"*.

A rule is resolved against trip length **once**, at creation, and frozen into
`TripItem.quantityCount`. Socks are `PerNights(1, plus = 1, cap = 10)`; a passport is
`Fixed(1)`; salt is `Unspecified`. With no trip dates a `PerNights` rule resolves to
`null`, and the row shows its textual form instead of a number.

**The textual form is never stored.** The rule columns are frozen on the trip item and the
sentence is assembled from string resources at display time. Writing "1 per night, +1" into
the database would freeze the language too, so it would stay Serbian after the app was
switched to English. The same reasoning applies to `TripItem.ruleId`, which holds a stable
machine identifier such as `nights.gte5.rotation` rather than a human sentence.

## The wizard is rules, not a model

`WizardRules.rulesFor(answers)` maps accommodation, nights and activities onto an ordered
list of rules, each naming the sections and items it contributes. The same function feeds
both the preview shown *before* generation and the generation itself — a preview that was
a second implementation would eventually describe rules that do not run.

Trip length is a rule (`nights >= 5`, `nights >= 14`), not a separate template. The
original specification had four templates where the third and fourth were strict supersets
of the second; folding the difference into rules halved the seed content and made the rule
engine visibly do the work it exists for.

## Identity: `uuid` on every row

Every table carries `uuid` and `updatedAt` from version 1 of the schema, before there was
any import code to use them. An auto-increment `id` is local and means something different
after an import on another device, so additive import needs a stable row identity, and
retrofitting one later would mean a migration through every table.

Import matches by UUID first, then by natural key. When a row matches by natural key but
the UUIDs differ, the local row **adopts the UUID from the backup** and the two are the
same row from then on. All of it runs in one transaction, and **import never deletes**.

## Migrations

The schema is at version 2. Version 1 shipped everything except the seed key on trip rows,
so the migration adds two nullable columns and backfills what it can: a trip item finds its
key through `catalogItemId`, since the catalogue row already carries it. Trip sections could
not be done in SQL — the migration tried matching their titles against the template, but the
template had already been relocalised, so nothing matched. That backfill lives in
`SeedRepository.relocalise` instead, where the seed file is in hand and a title can be
recognised in either language. Both schema files are committed and the schema test asserts
the current one against the database the code creates.

Since `allowBackup=false` and `dataExtractionRules` excludes everything, this JSON export
is the only backup that exists.

## The `:pdf` module

A separate pure-Kotlin JVM module with no dependencies and no Android types. That isolation
is deliberate: the writer emits bytes, so its tests parse the emitted bytes back and assert
field count, field names, page count and font embedding, without an emulator and without
being able to reach for an Android API by accident.

Checkboxes are real `/Widget` annotations with `/FT /Btn` and an explicit appearance
dictionary `/AP /N << /Off … /Yes … >>`. `/NeedAppearances` is set as well, but it is a
patch rather than the mechanism — PDFium, which backs Chrome and most Android readers,
ignores it. The font is embedded as `CIDFontType2` with `Identity-H` encoding because
WinAnsi cannot represent `č ć š ž đ`.

## No dependency injection framework

`AppContainer` constructs one database, one DataStore and four repositories. Repositories
take DAOs through their constructors, so tests build them directly against an in-memory
database without touching `Application`. Hilt would add a KSP round and a layer of
annotations around a graph that fits on one screen.

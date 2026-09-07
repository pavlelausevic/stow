# Build prompt v3: "Stow" — personal offline travel packing checklist (Android)

Copy everything below the line into Claude Code.

---

## 0. Context and goal

Build **Stow**, an Android app: an offline travel packing checklist built around a personal item catalogue, with deterministic presets, three-state item tracking, per-traveller assignment, a return-journey mode, and interactive PDF export.

Two things shape every decision:

1. **This app has exactly one user — the developer.** No store submission, no onboarding funnel, no growth loop, no analytics, no "what will reviewers think". Optimise for daily usefulness to one person who travels several times a year, usually with a partner, often to rented apartments.
2. **The repository is a public portfolio piece.** Architecture, code quality, commit history, README and design polish are the actual deliverable. Someone technical will read this repo to judge the developer.

These conflict less than they seem: a small app done properly beats a large app done partially.

Name: `Stow`. Package: `com.lausevic.stow`. The display name is a single string resource.

Work in phases (§14). Stop after each phase, summarise, wait for review.

---

## 1. Hard constraints

1. **No INTERNET permission.** Merged manifest must contain zero `android.permission.INTERNET` and zero `ACCESS_NETWORK_STATE`. Add a Gradle task that parses the merged manifest and fails the build if either appears; wire it into `check`.
2. **No network code, no analytics, no crash reporting, no ads, no Firebase, no Play Services, no payments.**
3. **No backup.** `android:allowBackup="false"`, `android:fullBackupContent="false"`, and a `dataExtractionRules` XML excluding everything for both `cloud-backup` and `device-transfer`.
4. **Near-zero runtime permissions.** File export goes through the Storage Access Framework; sharing through `FileProvider` + `ACTION_SEND`; photos through the Android Photo Picker (`ACTION_PICK_IMAGES` / `PickVisualMedia`), which requires **no** permission. A single optional `POST_NOTIFICATIONS` may be added in Phase 5 for the pre-trip reminder — requested only when the user enables that feature, never at launch.
5. **Permissively licensed dependencies only** (Apache-2.0, MIT, BSD). Keep the count deliberately low and justify each in the README.

### Meet F-Droid's technical bar without submitting

Free to comply with, given the above. Do: no proprietary libraries, no trackers, full source, builds with a plain `./gradlew assembleRelease`, and `fastlane/metadata/android/en-US/` + `.../sr/` with `short_description.txt`, `full_description.txt`, `changelogs/<versionCode>.txt` and `images/phoneScreenshots/`. The README sources its screenshots from these folders.

Do **not** set up reproducible builds, submit to fdroiddata, or add F-Droid build flavours. That is maintenance with no payoff here.

---

## 2. Technology

Native Android only. There is no second platform.

- Kotlin, **Jetpack Compose**, Material 3
- **Room** (KSP), exported schemas committed to the repo, migration tests from day one
- **DataStore** for settings
- `androidx.navigation:navigation-compose`, type-safe routes
- Hilt, or manual constructor injection if the graph stays trivially small — choose one, justify it in the README, do not mix
- `kotlinx-serialization-json`, `kotlinx-datetime` (or desugared `java.time`)
- `minSdk 26`, `targetSdk` current
- Unidirectional data flow: `ViewModel` exposes immutable `UiState` via `StateFlow`, events go up as sealed `UiEvent`, repository over DAO, no business logic in composables
- R8 full mode for release with a minimal, commented `proguard-rules.pro`

---

## 3. Data model

Three layers, and the distinction between them is the core architectural idea of the app — explain it in `ARCHITECTURE.md`.

- **Catalogue** — the things the developer owns. Single source of truth for names, default bag, default quantity, weight, photo.
- **Templates** — blueprints referencing catalogue entries. Live links: editing the catalogue updates templates.
- **Trips** — snapshots. Deep-copied at creation and denormalised, so editing the catalogue or a template never mutates a trip that already exists. A trip keeps a nullable `catalogItemId` for provenance and statistics only.

```kotlin
enum class ItemKind { ITEM, TASK }        // TASK = an action, not an object
enum class Bag { CHECKED, CARRY_ON, PERSONAL, UNASSIGNED }
enum class PackStatus { TO_BUY, TO_PACK, PACKED }
enum class SectionPhase { PACKING, RETURN, BOTH }
enum class TripMode { PACKING, RETURNING }
```

```
CatalogItem(
  id, name, kind, defaultBag, defaultQuantityRule,
  defaultSection: String?, weightGrams: Int?, photoPath: String?,
  note: String?, timesUsed: Int, lastUsedAt: Instant?, isArchived: Boolean
)

Traveller(id, name, colorSeed, sortOrder)

Template(id, name, description, isBuiltIn, sortOrder)
TemplateSection(id, templateId, title, phase: SectionPhase, sortOrder)
TemplateEntry(id, sectionId, catalogItemId, quantityRuleOverride?, bagOverride?, note?, sortOrder)

Trip(id, name, destination?, startDate?, endDate?, accommodation,
     sourceTemplateId?, mode: TripMode, createdAt, updatedAt, isArchived)
TripSection(id, tripId, title, phase: SectionPhase, sortOrder)
TripItem(
  id, tripSectionId, catalogItemId?,
  title, note, kind, quantity: String?, bag,
  packStatus: PackStatus,
  assigneeId: Long?,            // null = shared / unassigned
  isReturned: Boolean,          // separate axis from packStatus
  generatedByRule: String?,     // wizard provenance
  sortOrder
)
```

### Quantity rules

```kotlin
sealed interface QuantityRule {
    data class Fixed(val count: Int) : QuantityRule
    data class PerNights(val perNights: Int, val plus: Int = 0, val cap: Int? = null) : QuantityRule
    data object Unspecified : QuantityRule
}
```

Resolved against trip length at creation and frozen into `TripItem.quantity`. Socks are `PerNights(1, plus = 1, cap = 10)`; passport is `Fixed(1)`; salt is `Unspecified`. With no trip dates, a `PerNights` rule renders as its textual form instead of a number. Tasks always resolve to `Unspecified`.

### Two independent axes

`packStatus` and `isReturned` are deliberately separate fields, not one state machine. Packing and returning are different journeys over the same list, and merging them would make "what did I fail to bring home" unanswerable. Say so in `ARCHITECTURE.md`.

---

## 4. Features

Everything below is either standard in this category or a deliberate gap-filler. Implement all of it as original design — take the ideas, never a competitor's layout, wording, icons or assets.

### 4.1 Catalogue

The personal inventory. Add, edit, archive (never hard-delete — trips reference it). Each entry carries a default bag, default quantity rule, optional weight, optional photo, and a suggested section.

Consequences to build on:
- No typo duplicates ("Punjač za telefon" vs "punjač telefona"). Fuzzy-match on add and offer the existing entry.
- `timesUsed` / `lastUsedAt` replace any separate "learned items" table. When the user adds an item to a trip that is not in the source template, either link it to an existing catalogue entry or create one, then increment the counter.
- A "suggestions" row on the trip screen offering frequently-used catalogue items not currently on this trip, ranked by `timesUsed` for the same accommodation type. Local frequency counting only, no model.
- A catalogue screen sortable by "most used" and "not used in over a year" — the second one is how you find things you carry everywhere and never touch.

### 4.2 Three-state items

`TO_BUY → TO_PACK → PACKED`. Sunscreen is used up, plasters expired, you don't own an adapter yet. That is a pre-trip errand, not an unpacked item, and conflating them is why people keep a second list in another app.

- Tapping a row advances `TO_PACK → PACKED`. `TO_BUY` is set explicitly (long-press, swipe or edit sheet) and returns to `TO_PACK` when bought.
- A dedicated **Shopping** view: everything marked `TO_BUY` across all active trips, grouped sensibly, with its own PDF/text export so it can be taken to a shop.
- Trip progress counts only `TO_PACK` and `PACKED`; `TO_BUY` items are shown as blocking and surfaced separately, not silently folded into the denominator.

### 4.3 Tasks alongside items

`ItemKind.TASK` for things that are not objects: zatvoriti vodu, isprazniti frižider, zaliti biljke, izbaciti smeće, proveriti prozore, najaviti banci putovanje, ostaviti ključ komšiji, uključiti tajmer za svetlo, isključiti bojler.

Tasks have no bag and no quantity, render with a distinct affordance (not the same checkbox as an object), are excluded from weight and bag grouping, and appear in their own block at the end of exports.

### 4.4 Traveller assignment

A small `Traveller` table, seeded with two entries the user can rename. `TripItem.assigneeId` is nullable; null means shared or unassigned.

- Filter and group the trip by traveller.
- Bulk assign a whole section.
- **The point is the export**: generate a PDF containing only one traveller's items plus shared ones, and send it. This solves what competitors solve with accounts, servers and real-time sync — with a file.
- Shared items appear once, not duplicated per person.

### 4.5 Return mode

The other half of the problem, and nothing in this category addresses it. As often as you forget to pack something, you leave it behind — a charger in the socket behind the bed, laundry on the balcony, food in the fridge.

- `Trip.mode` toggles between `PACKING` and `RETURNING`, with a clear visual change to the whole screen so the mode is never ambiguous.
- In `RETURNING`, the list shows `isReturned` instead of `packStatus`, starts fully unchecked, and hides `TASK` items whose section phase is `PACKING`.
- Sections carry a `phase`. Seeded **"Pokupiti pre odlaska"** (`RETURN` phase) contains: Punjači iz utičnica; Punjač telefona pored kreveta; Veš sa terase ili sušilice; Kupaći kostim; Stvari iz kupatila; Dokumenta iz sefa; Hrana iz frižidera; Punjač laptopa; Adapter iz utičnice; Kablovi iza TV-a.
- A "left behind" summary at the end of the trip, written back to the catalogue as a note on the offending item — the charger you keep forgetting should eventually say so.

### 4.6 Photos

Optional photo per catalogue item via the Android Photo Picker (no permission). **Copy the bytes into app-private storage** (`filesDir/photos/<uuid>.jpg`) at pick time — the picker URI is a temporary grant and will not survive. Downscale to a sane max edge (about 1024px) and strip EXIF location before writing.

Use: distinguishing three near-identical chargers; remembering which case you used and how it was packed. Thumbnails in the catalogue and the item detail sheet; not in the trip list rows, which stay dense.

### 4.7 Weight — deliberately minimal

`weightGrams` is optional on catalogue items only. Show a rough per-bag total **only when at least half the items in that bag have a weight**, and label it as an estimate. Build no UI encouraging the user to weigh everything — they will not, and a half-populated weight feature is worse than none.

### 4.8 Core mechanics

- Multiple trips with progress; archive rather than delete.
- Create a trip from a built-in preset, a user template, a past trip (deep copy, all state reset), or empty.
- Sections and items: add, rename, drag to reorder, delete with undo snackbar.
- **Checking an item must never reorder the list.** Sorting checked items to the bottom is opt-in and applied only on an explicit "tidy" action, never live while tapping. This is a specific, recurring failure in competing apps.
- Filters: All / To buy / To pack / Packed. Group by section, bag or traveller. Search within the trip.
- Bulk: uncheck all, complete section, assign section.

### 4.9 Trip wizard — deterministic, no AI

A short questionnaire — accommodation (hotel / apartment / camping / family), nights, season, activities (beach, hiking, business, driving, gym, formal dinner), travellers — composing a list from rule-based fragments over the catalogue.

Every generated item records `generatedByRule`, and a "why is this here?" affordance shows it. The AI competitors do this probabilistically and get it wrong in ways the user cannot inspect or correct; explicit rules are the whole advantage, and hiding them would waste it.

### 4.10 Data

Full JSON export and import via SAF — catalogue, travellers, templates and trips, lossless round-trip, versioned schema, additive import with conflict resolution. This is the only backup that exists, given `allowBackup=false`. Make restoring genuinely reliable and test it.

### 4.11 Phase 5 extras

Home-screen widget with current trip progress and next unpacked items; optional reminder N days before `startDate`; launcher shortcut for quick add.

### Explicitly not building

Weather, destination databases, real-time collaboration, accounts, cloud sync, multi-destination segments, in-app storage of passport or insurance documents, ads, purchases.

---

## 5. Design

The app is small; the design is the portfolio. Read `/mnt/skills/public/frontend-design/SKILL.md` if available before starting UI work.

**Commit to one aesthetic and execute it fully.** Default Material 3 purple with stock icons reads as unfinished.

Suggested direction, to accept or replace with something better argued: *utilitarian travel document* — baggage tags, customs forms, manifest sheets. Flat, high contrast, monospaced numerals for counts and dates, generous whitespace, one strong accent. Restrained rather than playful.

Requirements regardless:
- Custom seed colour, fully generated M3 tonal palettes, light and dark. No dynamic colour — it would dissolve the identity you just built.
- The four item states (`TO_BUY`, `TO_PACK`, `PACKED`, plus returning) must be distinguishable **without relying on colour alone**.
- Packing and returning modes must be unmistakable at a glance.
- Intentional typography. **Verify the chosen font renders `č ć š ž đ Č Ć Š Ž Đ`** — many display faces do not.
- Tabular figures for all counts, quantities and dates.
- Considered motion: check animation, animated progress, container transform between trip list and detail. Respect `ANIMATOR_DURATION_SCALE` and reduce-motion.
- Every empty state designed and written — no centred grey "No items".
- Custom adaptive icon with a monochrome layer.
- Edge-to-edge, predictive back, correct insets.
- Accessibility: 48dp targets, content descriptions, TalkBack pass, layout survives 200% font scale, WCAG AA contrast both themes.
- Compose previews for every screen in both themes; screenshot tests.

---

## 6. Interactive PDF export

The one thing no competitor has, and the most interesting code in the repo. Treat it as the centrepiece.

Export a trip as a PDF whose checkboxes are real AcroForm fields — sendable to a travel companion, tickable in any reader, saveable.

- Self-contained PDF writer in a separate Gradle module (`:pdf`), pure Kotlin, byte output, no third-party PDF dependency. Independently testable; that isolation is part of what makes it worth showing.
- Embed a TrueType face covering Serbian diacritics (Noto Sans or DejaVu Sans) as `CIDFontType2` with `Identity-H` encoding. Mandatory — WinAnsi does not cover `č ć š ž đ`. Subset at build time if practical.
- One `/Widget` per item: `/FT /Btn`, `/AS /Off`, appearance dictionary `/N << /Off … /Yes … >>`, registered in `/AcroForm /Fields`, `/NeedAppearances true` as fallback.
- Export options dialog: which traveller (one person + shared, or everyone), which mode (packing or return list), include or exclude `TO_BUY` items, group by section or bag.
- Layout: header with trip name, destination, dates, accommodation and traveller; sections as headings; a row per item with checkbox, title, quantity and note; tasks in their own block at the end; shopping items in a separate block if included. Correct pagination and page footer.
- Delivery via `FileProvider` + `ACTION_SEND`, plus "Save to file" through the document picker.
- Tests: parse the emitted bytes back and assert field count, field names, page count and font embedding. Verify manually in Adobe Acrobat, Chrome's viewer, Apple Preview and one Android reader.

If AcroForm proves unreliable across readers after a genuine attempt, fall back to drawn empty squares and document the decision in the README. Do not ship a half-working form.

---

## 7. Localisation

Serbian (Latin) and English. English is the fallback; Serbian is what the developer uses.

- No hardcoded user-facing strings.
- In-app language override (System / Srpski / English) via per-app language and `locales_config.xml`.
- Correct Serbian plurals (`one`, `few`, `other`): `1 stavka / 2 stavke / 5 stavki`.
- Built-in catalogue and preset content is bilingual in the seed JSON (`{"sr": "...", "en": "..."}`), resolved at read time. User-created content is not translated.

---

## 8. Seed data

Ship as JSON in `assets/seed/`, applied on first launch, versioned via `seedVersion` in DataStore so later releases can extend it without touching user data. Serbian below is the source of truth; produce natural English yourself.

Seed the **catalogue** first, then templates referencing it. Give every catalogue entry a `kind`, `defaultBag`, `defaultQuantityRule` and `defaultSection`.

### Catalogue — shared core

**Dokumenta i novac** — Pasoš / lična karta; Vozačka dozvola; Viza; Putno osiguranje; Karte i rezervacije (offline PDF + kopija u cloudu); Platne kartice (najaviti banci putovanje); Keš; Sitniš za prvi dan

**Elektronika** — Telefon; Punjač za telefon; Kabl; Powerbank *(CARRY_ON, note: obavezno u ručnom prtljagu, do 100Wh)*; Adapter za struju (univerzalni); Laptop; Punjač za laptop; UTP kabl; HDMI kabl; USB C-C kabl; USB micro kabl; USB A-C kabl; Slušalice; Čitač memorijskih kartica

**Neseser** — Četkica za zube i punjač; Pasta za zube; Oralni tuš; Konac za zube; Šampon; Sapun; Dezodorans; Brijač i žileti; Detailer / trimer; Grickalica *(CHECKED)*; Turpija; Makazice *(CHECKED)*; Pinceta; Štapići za uši; Balzam za usne; Krema za sunce; Pantenol

**Apoteka** — Lekovi na recept + fotografija recepta; Lek protiv bolova; Lek protiv proliva; Antihistaminik; Tablete za grlo; Elektroliti; Probiotik; Flasteri

**Odeća** — Donji veš `PerNights(1, +1, cap 10)`; Čarape `PerNights(1, +1, cap 10)`; Majice `PerNights(1, cap 5)`; Pantalone / farmerke `PerNights(4)`; Topliji sloj (duks ili fleece); Lagana jakna otporna na kišu; Pidžama; Kupaći kostim

**Obuća** — Razgažene patike; Lepše cipele ili patike za veče; Papuče; Japanke

**Sitnice** — Kišobran ili kabanica; Naočare za sunce; Kesa za prljav veš; Sklopiva torba / ceger; Čepići za uši; Maska za spavanje; Jastuk za vrat; Kutija za hranu; Gumice za tegle

### Catalogue — tasks (`kind = TASK`)

**Pre polaska** — Zatvoriti vodu; Isključiti bojler; Isprazniti frižider; Izbaciti smeće; Zaliti biljke; Proveriti prozore; Najaviti banci putovanje; Ostaviti ključ komšiji; Uključiti tajmer za svetlo; Poneti punjač iz utičnice pored kreveta *(this one is an item, not a task — keep it in the return section)*

### Catalogue — return phase

**Pokupiti pre odlaska** (`SectionPhase.RETURN`) — Punjači iz utičnica; Punjač telefona pored kreveta; Punjač laptopa; Adapter iz utičnice; Kablovi iza TV-a; Veš sa terase ili sušilice; Kupaći kostim; Stvari iz kupatila; Dokumenta iz sefa; Hrana iz frižidera

### Templates

**1 — Hotel**: core + **Hotel — dodatno** (Produžni kabl sa više utičnica; Sklopiva kesa za dnevni izlazak; Katanac; Kutija za hranu za ostatke doručka) + Pre polaska + Pokupiti pre odlaska. Description: "Ne nosiš peškire, fen ni gel za tuširanje."

**2 — Apartman**: core, plus

- **Kuhinja i začini** — So; Biber; Aleva paprika; Origano; Vegeta; Kafa ili čaj; Džezva ili filter; Ulje u maloj flašici
- **Održavanje** — Truleks krpa; Pamučna krpa; Streč folija; Alu folija; Zip kesice; Sunđer za sudove; Deterdžent za sudove (u putnoj flašici); Kese za smeće; Mali kuhinjski nož *(CHECKED)*; Otvarač i vadičep *(CHECKED)*; Deterdžent za veš; Štipaljke; Kanap za sušenje

plus Pre polaska and Pokupiti pre odlaska.

**3 — Dugo putovanje (5–14 dana)**: core + Apartman + **Rotacija i održavanje** (Drugi par obuće za rotaciju; Sredstvo za mrlje; Mrežasta torba za veš; Set za šivenje; Rezervne pertle) + both phase sections.

**4 — Ultra dugo (2+ nedelje)**: all of the above, plus

- **Veš i pranje** — Prašak ili kapsule za veš; Sapun za ručno pranje; Konopac za sušenje. Donji veš and čarape switch to `Fixed(10)` with the note "plan pranja na pola puta — ne pakuj za 14 dana".
- **Elektronika — dugo** — Jedan GaN punjač 65W+ sa tri porta umesto pet punjača; eSIM ili lokalna SIM kartica
- **Zdravlje — dugo** — Lekovi za ceo period + 3–4 dana rezerve; Rezervne naočare; Dodatna sočiva i rastvor; Anti-žulj flasteri; Ulošci za cipele
- **Logistika** — Vakuum ili kompresione kese; Sklopivi ranac 15L; Rezervna kartica držana odvojeno od novčanika; Sklopiva flaša za vodu; Kopije dokumenata u cloudu; Jedna štampana kopija dokumenata

---

## 9. Settings

Language; theme; "sort checked to bottom" toggle; default grouping (section / bag / traveller); manage travellers; manage catalogue; export and import data; reminder settings (Phase 5); "View source on GitHub" opening `https://github.com/<USERNAME>/stow` via `ACTION_VIEW` (no INTERNET permission, no WebView); open-source licences; version and build number.

---

## 10. Licence and repository

**Apache License 2.0.** `LICENSE`, `NOTICE`, SPDX header in every source file, generated third-party licence list in Settings.

The repo is the portfolio:
- README: what and why in a paragraph; screenshots light and dark; the four things that make it non-generic (zero network capability, catalogue-backed model, deterministic inspectable generation, self-written interactive PDF); architecture diagram; build instructions; and an honest "built for personal use, not published to any store" line.
- `ARCHITECTURE.md`: the catalogue / template / trip-snapshot split, why `packStatus` and `isReturned` are separate axes, the quantity rule engine, the `:pdf` module.
- Conventional commits, one logical change each, no "wip" or "fix" in history.
- GitHub Actions: build, unit tests, detekt, ktlint, and the no-INTERNET manifest check on every push.
- Issue templates and a short CONTRIBUTING stating the project is personal and PRs may not be merged. Honest beats aspirational.

---

## 11. Quality bar

- Unit tests: quantity rule resolution across trip lengths and missing dates; template→trip deep copy; catalogue archive with live trip references; seeding idempotency; sort-order integrity after reorder; JSON round-trip; wizard rule composition; state transitions across both axes; PDF structure.
- Room migration tests with committed schemas.
- Compose UI tests for the core flow: wizard → trip → mark to-buy → pack → switch to return mode → export.
- Screenshot tests in both themes.
- detekt + ktlint clean; no suppression without a comment explaining why.
- No `TODO`, no commented-out code, no unused resources in delivered work.

---

## 12. Phases

**Phase 1 — Foundation.** Skeleton, manifest hardening, no-INTERNET Gradle check, the **complete Room schema including catalogue, travellers, item kinds, both state axes and section phases**, migrations, design system (colour, type, motion, components) with previews, Settings, licence and repo scaffolding. Runs with an empty trip list and already looks finished.

The schema must be complete in this phase. Retrofitting the catalogue later would mean rewriting every screen.

**Phase 2 — Catalogue and trips.** Seeding, catalogue CRUD with fuzzy-match on add, travellers, trip and template CRUD, sections, items, reordering, quantity rules, three-state items, tasks, assignment, filters and grouping, shopping view, JSON export/import.

**Phase 3 — Return mode and photos.** Mode toggle, phase-aware sections, `isReturned` tracking, left-behind summary written back to the catalogue, photo picking with private-storage copy and downscaling.

**Phase 4 — PDF.** The `:pdf` module, the export options dialog, delivery, tests.

**Phase 5 — Extras.** Widget, reminder notification, launcher shortcut.

Start with Phase 1. First propose the module structure, the full Room schema, and the design direction (palette, type scale, one annotated screen mock) and wait for approval before writing the rest.

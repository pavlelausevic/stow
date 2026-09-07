// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.model

/** Predmet ili radnja. Zadaci nemaju torbu ni količinu i crtaju se krugom, ne kvadratom. */
enum class ItemKind { ITEM, TASK }

enum class Bag { CHECKED, CARRY_ON, PERSONAL, UNASSIGNED }

/**
 * Tri stanja, ne dva.
 *
 * `TO_BUY` postoji zato što je "krema za sunce je potrošena" predputna obaveza, a ne
 * nespakovana stavka. Spajanje tih dvaju je razlog zbog kog ljudi drže drugu listu u
 * drugoj aplikaciji.
 */
enum class PackStatus { TO_BUY, TO_PACK, PACKED }

/** Da li sekcija pripada pakovanju, povratku, ili oba. */
enum class SectionPhase { PACKING, RETURN, BOTH }

enum class TripMode { PACKING, RETURNING }

enum class Accommodation { HOTEL, APARTMENT, CAMPING, FAMILY }

enum class TripActivity { BEACH, HIKING, BUSINESS, DRIVING, GYM, FORMAL }

enum class GroupBy { SECTION, BAG, TRAVELLER }

enum class ItemFilter { ALL, TO_BUY, TO_PACK, PACKED }

enum class CatalogSort { MOST_USED, UNUSED_OVER_YEAR, ALPHABETICAL }

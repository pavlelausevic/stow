// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import rs.lausevic.stow.data.db.CatalogDao
import rs.lausevic.stow.data.db.TripDao
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.QuantityRuleColumns
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripActivity
import rs.lausevic.stow.domain.WizardRules

/**
 * Čarobnjak: odgovori → pravila → lista.
 *
 * Ista funkcija `WizardRules.rulesFor` hrani i pregled u čarobnjaku i samo generisanje.
 * Pregled ne sme da bude druga implementacija istog, inače prikazuje pravila koja se
 * neće primeniti — a poenta ovog ekrana je upravo to da se pravila mogu proveriti.
 *
 * Svaka generisana stavka nosi `ruleId`, pa "zašto je ovo ovde?" ima šta da pokaže.
 */
class TripComposer(
    private val tripDao: TripDao,
    private val catalogDao: CatalogDao,
    private val seedRepository: SeedRepository,
    private val tripRepository: TripRepository,
) {

    data class Request(
        val name: String,
        val destination: String?,
        val startDate: Long?,
        val endDate: Long?,
        val accommodation: Accommodation,
        val activities: Set<TripActivity>,
    )

    suspend fun compose(request: Request): Long {
        val nights = TripRepository.nightsBetween(request.startDate, request.endDate)
        val answers = WizardRules.Answers(request.accommodation, nights, request.activities)
        val rules = WizardRules.rulesFor(answers)
        val overrides = WizardRules.quantityOverrides(rules)

        val seed = seedRepository.read()
        val sectionsByKey = seed.sections.associateBy { it.key }
        val itemsByKey = seed.items.associateBy { it.key }
        val catalog = catalogDao.allActive().filter { it.seedKey != null }.associateBy { it.seedKey!! }

        val tripId = tripDao.insert(
            TripEntity(
                name = request.name.trim(),
                destination = request.destination?.takeIf { it.isNotBlank() },
                startDate = request.startDate,
                endDate = request.endDate,
                accommodation = request.accommodation,
            ),
        )

        // Sekcija se pravi jednom, ma koliko pravila je tražilo; stavka koju je dovelo
        // pravilo pamti KOJE pravilo, pa duplikat nikad ne nastaje.
        val sectionIds = linkedMapOf<String, Long>()
        val placed = mutableSetOf<String>()
        var sectionOrder = 0

        suspend fun sectionFor(key: String): Long? {
            sectionIds[key]?.let { return it }
            val section = sectionsByKey[key] ?: return null
            val id = tripDao.insertSection(
                TripSectionEntity(
                    tripId = tripId,
                    title = if (SeedRepository.isSerbian()) section.sr else section.en,
                    seedKey = section.key,
                    phase = runCatching { SectionPhase.valueOf(section.phase) }
                        .getOrDefault(SectionPhase.PACKING),
                    sortOrder = sectionOrder++,
                ),
            )
            sectionIds[key] = id
            return id
        }

        suspend fun place(itemKey: String, ruleId: String) {
            if (itemKey in placed) return
            val seedItem = itemsByKey[itemKey] ?: return
            val catalogItem = catalog[itemKey] ?: return
            val sectionId = sectionFor(seedItem.section) ?: return

            val rule = overrides[itemKey]?.let { QuantityRuleColumns.from(it) }
                ?: QuantityRuleColumns(
                    catalogItem.ruleType,
                    catalogItem.rulePer,
                    catalogItem.rulePlus,
                    catalogItem.ruleCap,
                )

            tripDao.insertItem(
                tripRepository.buildItem(
                    sectionId = sectionId,
                    catalogItemId = catalogItem.id,
                    title = catalogItem.name,
                    note = catalogItem.note,
                    kind = catalogItem.kind,
                    rule = rule,
                    nights = nights,
                    bag = catalogItem.defaultBag,
                    ruleId = ruleId,
                    ruleArgs = nights?.let { """{"nights":$it}""" },
                    sortOrder = tripDao.nextItemOrder(sectionId),
                    seedKey = catalogItem.seedKey,
                ),
            )
            catalogDao.recordUse(catalogItem.id)
            placed += itemKey
        }

        rules.forEach { rule ->
            rule.sections.forEach { sectionKey ->
                sectionFor(sectionKey)
                seed.items
                    .filter { it.section == sectionKey && it.core }
                    .forEach { place(it.key, rule.id) }
            }
            rule.items.forEach { place(it, rule.id) }
        }

        return tripId
    }

    /** Pregled pre generisanja: ista pravila, bez upisa. */
    fun preview(
        accommodation: Accommodation,
        nights: Int?,
        activities: Set<TripActivity>,
    ): List<WizardRules.Rule> =
        WizardRules.rulesFor(WizardRules.Answers(accommodation, nights, activities))
}

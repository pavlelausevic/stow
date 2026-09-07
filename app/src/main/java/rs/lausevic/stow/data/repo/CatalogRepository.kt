// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import kotlinx.coroutines.flow.Flow
import rs.lausevic.stow.data.db.CatalogDao
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.TravellerDao
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.CatalogSort
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.QuantityRuleColumns
import rs.lausevic.stow.domain.TextMatching
import java.time.Instant
import java.time.temporal.ChronoUnit

class CatalogRepository(
    private val catalogDao: CatalogDao,
    private val travellerDao: TravellerDao,
) {

    fun observe(sort: CatalogSort): Flow<List<CatalogItemEntity>> = when (sort) {
        CatalogSort.MOST_USED -> catalogDao.observeByUsage()
        CatalogSort.ALPHABETICAL -> catalogDao.observeActive()
        CatalogSort.UNUSED_OVER_YEAR -> catalogDao.observeUnusedSince(oneYearAgo())
    }

    fun observeAll(): Flow<List<CatalogItemEntity>> = catalogDao.observeAll()
    fun observeById(id: Long): Flow<CatalogItemEntity?> = catalogDao.observeById(id)
    fun observeItemCount(): Flow<Int> = catalogDao.observeItemCount()
    fun observeTaskCount(): Flow<Int> = catalogDao.observeTaskCount()
    fun observeSuggestions(tripId: Long): Flow<List<CatalogItemEntity>> =
        catalogDao.observeSuggestions(tripId)

    suspend fun byId(id: Long): CatalogItemEntity? = catalogDao.byId(id)

    /**
     * Postojeće stavke koje liče na uneti naziv, od najbliže.
     *
     * Poziva se PRE upisa, da se ponudi postojeća stavka umesto pravljenja bliskog
     * duplikata — bez toga `timesUsed` s vremenom ne znači ništa, jer je razbijen
     * na tri varijante istog naziva.
     */
    suspend fun similarTo(name: String): List<CatalogItemEntity> {
        val active = catalogDao.allActive()
        val ids = TextMatching.suggestions(name, active.map { it.id to it.normalizedName })
        return ids.mapNotNull { id -> active.firstOrNull { it.id == id } }
    }

    suspend fun add(
        name: String,
        kind: ItemKind,
        bag: Bag,
        rule: QuantityRule,
        section: String?,
        note: String?,
        photoPath: String? = null,
    ): Long {
        val columns = QuantityRuleColumns.from(rule)
        return catalogDao.insert(
            CatalogItemEntity(
                name = name.trim(),
                normalizedName = TextMatching.normalize(name),
                kind = kind,
                defaultBag = if (kind == ItemKind.TASK) Bag.UNASSIGNED else bag,
                ruleType = columns.type,
                rulePer = columns.per,
                rulePlus = columns.plus,
                ruleCap = columns.cap,
                defaultSection = section,
                note = note?.takeIf { it.isNotBlank() },
                photoPath = photoPath,
            ),
        )
    }

    /**
     * Izmena briše `seedKey`: od trenutka kada je korisnik dirao stavku, naziv je njegov
     * i ne sme više da se prevodi pri promeni jezika.
     */
    suspend fun update(
        item: CatalogItemEntity,
        name: String = item.name,
        kind: ItemKind = item.kind,
        bag: Bag = item.defaultBag,
        rule: QuantityRule? = null,
        section: String? = item.defaultSection,
        note: String? = item.note,
        photoPath: String? = item.photoPath,
    ) {
        val columns = rule?.let { QuantityRuleColumns.from(it) }
        catalogDao.update(
            item.copy(
                name = name.trim(),
                normalizedName = TextMatching.normalize(name),
                kind = kind,
                defaultBag = if (kind == ItemKind.TASK) Bag.UNASSIGNED else bag,
                ruleType = columns?.type ?: item.ruleType,
                rulePer = columns?.per ?: item.rulePer,
                rulePlus = columns?.plus ?: item.rulePlus,
                ruleCap = columns?.cap ?: item.ruleCap,
                defaultSection = section,
                note = note?.takeIf { it.isNotBlank() },
                photoPath = photoPath,
                seedKey = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setArchived(id: Long, archived: Boolean) = catalogDao.setArchived(id, archived)

    suspend fun recordUse(id: Long) = catalogDao.recordUse(id)

    // --- putnici ---

    fun observeTravellers(): Flow<List<TravellerEntity>> = travellerDao.observeAll()

    suspend fun travellers(): List<TravellerEntity> = travellerDao.all()

    suspend fun addTraveller(name: String): Long {
        val order = travellerDao.all().size
        return travellerDao.insert(TravellerEntity(name = name.trim(), sortOrder = order))
    }

    suspend fun renameTraveller(traveller: TravellerEntity, name: String) =
        travellerDao.update(traveller.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))

    suspend fun deleteTraveller(id: Long) = travellerDao.delete(id)

    private fun oneYearAgo(): Long =
        Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()
}

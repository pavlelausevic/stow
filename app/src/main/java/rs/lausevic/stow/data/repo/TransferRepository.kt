// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rs.lausevic.stow.data.db.CatalogDao
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.StowDatabase
import rs.lausevic.stow.data.db.TemplateDao
import rs.lausevic.stow.data.db.TemplateEntity
import rs.lausevic.stow.data.db.TemplateEntryEntity
import rs.lausevic.stow.data.db.TemplateSectionEntity
import rs.lausevic.stow.data.db.TravellerDao
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.db.TripDao
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity

/**
 * Puni JSON izvoz i uvoz. Pošto je `allowBackup=false`, ovo je JEDINA kopija koja postoji,
 * pa mora da radi pouzdano — i zato se testira povratnim putem (izvoz → uvoz → izvoz).
 *
 * Uvoz je aditivan i **nikad ne briše**. Poklapanje ide prvo po UUID-u, pa po prirodnom
 * ključu; ako se red poklopi po ključu a UUID-i se razlikuju, lokalni red PREUZIMA UUID
 * iz kopije i od tog trenutka su isti red zauvek. Sve u jednoj transakciji.
 */
class TransferRepository(
    private val catalogDao: CatalogDao,
    private val travellerDao: TravellerDao,
    private val templateDao: TemplateDao,
    private val tripDao: TripDao,
    private val database: StowDatabase,
) {

    data class ImportResult(val added: Int, val updated: Int)

    class BadFormat(message: String) : Exception(message)
    class TooNew(val schema: Int) : Exception("schema $schema")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): String {
        // Sve se ucitava jednom i indeksira po id-u: bez ovoga bi svaki red radio
        // svoj upit i izvoz kataloga od dvesta stavki bi postao kvadratan.
        val catalog = catalogDao.all()
        val travellers = travellerDao.all()
        val templates = templateDao.all()
        val templateSections = templateDao.allSections()
        val templateEntries = templateDao.allEntries()
        val trips = tripDao.all()
        val tripSections = tripDao.allSections()
        val tripItems = tripDao.allItems()

        val catalogUuid = catalog.associate { it.id to it.uuid }
        val travellerUuid = travellers.associate { it.id to it.uuid }
        val templateUuid = templates.associate { it.id to it.uuid }
        val templateSectionUuid = templateSections.associate { it.id to it.uuid }
        val tripUuid = trips.associate { it.id to it.uuid }
        val tripSectionUuid = tripSections.associate { it.id to it.uuid }

        val backup = Backup(
            schema = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            catalog = catalog.map(CatalogItemEntity::toDto),
            travellers = travellers.map(TravellerEntity::toDto),
            templates = templates.map(TemplateEntity::toDto),
            templateSections = templateSections.map { section ->
                TemplateSectionDto(
                    uuid = section.uuid,
                    templateUuid = templateUuid[section.templateId].orEmpty(),
                    title = section.title,
                    seedKey = section.seedKey,
                    phase = section.phase.name,
                    sortOrder = section.sortOrder,
                    updatedAt = section.updatedAt,
                )
            },
            templateEntries = templateEntries.map { entry ->
                TemplateEntryDto(
                    uuid = entry.uuid,
                    sectionUuid = templateSectionUuid[entry.sectionId].orEmpty(),
                    catalogUuid = catalogUuid[entry.catalogItemId].orEmpty(),
                    bagOverride = entry.bagOverride?.name,
                    ruleTypeOverride = entry.ruleTypeOverride?.name,
                    rulePerOverride = entry.rulePerOverride,
                    rulePlusOverride = entry.rulePlusOverride,
                    ruleCapOverride = entry.ruleCapOverride,
                    note = entry.note,
                    sortOrder = entry.sortOrder,
                    updatedAt = entry.updatedAt,
                )
            },
            trips = trips.map(TripEntity::toDto),
            tripSections = tripSections.map { section ->
                TripSectionDto(
                    uuid = section.uuid,
                    tripUuid = tripUuid[section.tripId].orEmpty(),
                    title = section.title,
                    phase = section.phase.name,
                    sortOrder = section.sortOrder,
                    updatedAt = section.updatedAt,
                )
            },
            tripItems = tripItems.map { item ->
                TripItemDto(
                    uuid = item.uuid,
                    sectionUuid = tripSectionUuid[item.tripSectionId].orEmpty(),
                    catalogUuid = item.catalogItemId?.let { catalogUuid[it] },
                    assigneeUuid = item.assigneeId?.let { travellerUuid[it] },
                    title = item.title,
                    note = item.note,
                    kind = item.kind.name,
                    quantityCount = item.quantityCount,
                    ruleType = item.ruleType.name,
                    rulePer = item.rulePer,
                    rulePlus = item.rulePlus,
                    ruleCap = item.ruleCap,
                    bag = item.bag.name,
                    packStatus = item.packStatus.name,
                    isReturned = item.isReturned,
                    ruleId = item.ruleId,
                    ruleArgs = item.ruleArgs,
                    sortOrder = item.sortOrder,
                    updatedAt = item.updatedAt,
                )
            },
        )
        return json.encodeToString(Backup.serializer(), backup)
    }

    suspend fun import(text: String): ImportResult {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BadFormat(e.message ?: "unparseable")
        }
        if (backup.magic != MAGIC) throw BadFormat("magic")
        if (backup.schema > SCHEMA_VERSION) throw TooNew(backup.schema)

        var added = 0
        var updated = 0

        database.withTransaction {
            val catalogByUuid = mutableMapOf<String, Long>()
            backup.catalog.forEach { dto ->
                val existing = catalogDao.byUuid(dto.uuid)
                    ?: catalogDao.allByNormalizedName(dto.normalizedName)
                        // Prirodni kljuc je naziv PLUS seedKey. Dve seed stavke sa
                        // razlicitim kljucevima su razlicite stvari ma koliko se isto
                        // zvale, i spajanje bi tiho pojelo jednu od njih.
                        .firstOrNull { it.seedKey == dto.seedKey }
                if (existing == null) {
                    catalogByUuid[dto.uuid] = catalogDao.insert(dto.toEntity())
                    added++
                } else {
                    /*
                     * Poklopili se po prirodnom kljucu a UUID-i se razlikuju.
                     *
                     * IDENTITET se preuzima uvek, bez obzira koja je strana novija:
                     * lokalni red uzima UUID iz kopije i od sada su isti red zauvek.
                     * Da se usvajanje vezalo za `updatedAt`, dva reda bi ostala razdvojena
                     * i sledeci uvoz bi napravio duplikat.
                     *
                     * SADRZAJ ide za novijim `updatedAt` — lokalna izmena novija od kopije
                     * se ne gazi.
                     */
                    val newer = dto.updatedAt >= existing.updatedAt
                    val merged = if (newer) dto.toEntity() else existing
                    catalogDao.update(merged.copy(id = existing.id, uuid = dto.uuid))
                    if (newer) updated++
                    catalogByUuid[dto.uuid] = existing.id
                }
            }

            val travellerByUuid = mutableMapOf<String, Long>()
            backup.travellers.forEach { dto ->
                val existing = travellerDao.byUuid(dto.uuid) ?: travellerDao.byName(dto.name)
                if (existing == null) {
                    travellerByUuid[dto.uuid] = travellerDao.insert(dto.toEntity())
                    added++
                } else {
                    val newer = dto.updatedAt >= existing.updatedAt
                    val merged = if (newer) dto.toEntity() else existing
                    travellerDao.update(merged.copy(id = existing.id, uuid = dto.uuid))
                    if (newer) updated++
                    travellerByUuid[dto.uuid] = existing.id
                }
            }

            val templateByUuid = mutableMapOf<String, Long>()
            backup.templates.forEach { dto ->
                val existing = templateDao.byUuid(dto.uuid)
                if (existing == null) {
                    templateByUuid[dto.uuid] = templateDao.insert(dto.toEntity())
                    added++
                } else {
                    templateByUuid[dto.uuid] = existing.id
                }
            }

            val templateSectionByUuid = mutableMapOf<String, Long>()
            backup.templateSections.forEach { dto ->
                val templateId = templateByUuid[dto.templateUuid] ?: return@forEach
                val existing = templateDao.sectionByUuid(dto.uuid)
                if (existing == null) {
                    templateSectionByUuid[dto.uuid] =
                        templateDao.insertSection(dto.toEntity(templateId))
                    added++
                } else {
                    templateSectionByUuid[dto.uuid] = existing.id
                }
            }

            backup.templateEntries.forEach { dto ->
                val sectionId = templateSectionByUuid[dto.sectionUuid] ?: return@forEach
                val catalogId = catalogByUuid[dto.catalogUuid] ?: return@forEach
                if (templateDao.entryByUuid(dto.uuid) == null) {
                    templateDao.insertEntry(dto.toEntity(sectionId, catalogId))
                    added++
                }
            }

            val tripByUuid = mutableMapOf<String, Long>()
            backup.trips.forEach { dto ->
                val existing = tripDao.byUuid(dto.uuid)
                if (existing == null) {
                    tripByUuid[dto.uuid] = tripDao.insert(dto.toEntity())
                    added++
                } else {
                    if (dto.updatedAt >= existing.updatedAt) {
                        tripDao.update(dto.toEntity().copy(id = existing.id))
                        updated++
                    }
                    tripByUuid[dto.uuid] = existing.id
                }
            }

            val tripSectionByUuid = mutableMapOf<String, Long>()
            backup.tripSections.forEach { dto ->
                val tripId = tripByUuid[dto.tripUuid] ?: return@forEach
                val existing = tripDao.sectionByUuid(dto.uuid)
                if (existing == null) {
                    tripSectionByUuid[dto.uuid] = tripDao.insertSection(dto.toEntity(tripId))
                    added++
                } else {
                    tripSectionByUuid[dto.uuid] = existing.id
                }
            }

            backup.tripItems.forEach { dto ->
                val sectionId = tripSectionByUuid[dto.sectionUuid] ?: return@forEach
                val catalogId = dto.catalogUuid?.let { catalogByUuid[it] }
                val assigneeId = dto.assigneeUuid?.let { travellerByUuid[it] }
                val existing = tripDao.itemByUuid(dto.uuid)
                if (existing == null) {
                    tripDao.insertItem(dto.toEntity(sectionId, catalogId, assigneeId))
                    added++
                } else if (dto.updatedAt >= existing.updatedAt) {
                    tripDao.updateItem(
                        dto.toEntity(sectionId, catalogId, assigneeId).copy(id = existing.id),
                    )
                    updated++
                }
            }
        }

        return ImportResult(added, updated)
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAGIC = "stow-backup"
    }
}

@Serializable
data class Backup(
    val magic: String = TransferRepository.MAGIC,
    val schema: Int,
    val exportedAt: Long,
    val catalog: List<CatalogDto> = emptyList(),
    val travellers: List<TravellerDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val templateSections: List<TemplateSectionDto> = emptyList(),
    val templateEntries: List<TemplateEntryDto> = emptyList(),
    val trips: List<TripDto> = emptyList(),
    val tripSections: List<TripSectionDto> = emptyList(),
    val tripItems: List<TripItemDto> = emptyList(),
)

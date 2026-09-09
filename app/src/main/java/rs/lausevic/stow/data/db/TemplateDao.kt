// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM template ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM template ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<TemplateEntity>

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun byId(id: Long): TemplateEntity?

    @Query("SELECT * FROM template WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): TemplateEntity?

    @Query("SELECT * FROM template WHERE seedKey = :seedKey LIMIT 1")
    suspend fun bySeedKey(seedKey: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM template")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun delete(id: Long)

    // --- sekcije ---

    @Query("SELECT * FROM template_section WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun sections(templateId: Long): List<TemplateSectionEntity>

    @Query("SELECT * FROM template_section WHERE templateId = :templateId ORDER BY sortOrder")
    fun observeSections(templateId: Long): Flow<List<TemplateSectionEntity>>

    @Query("SELECT * FROM template_section")
    suspend fun allSections(): List<TemplateSectionEntity>

    @Query("SELECT * FROM template_section WHERE uuid = :uuid")
    suspend fun sectionByUuid(uuid: String): TemplateSectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSection(section: TemplateSectionEntity): Long

    @Update
    suspend fun updateSection(section: TemplateSectionEntity)

    @Query("DELETE FROM template_section WHERE id = :id")
    suspend fun deleteSection(id: Long)

    // --- stavke ---

    @Query("SELECT * FROM template_entry WHERE sectionId = :sectionId ORDER BY sortOrder")
    suspend fun entries(sectionId: Long): List<TemplateEntryEntity>

    @Query("SELECT * FROM template_entry")
    suspend fun allEntries(): List<TemplateEntryEntity>

    @Query("SELECT * FROM template_entry WHERE uuid = :uuid")
    suspend fun entryByUuid(uuid: String): TemplateEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: TemplateEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<TemplateEntryEntity>): List<Long>

    @Update
    suspend fun updateEntry(entry: TemplateEntryEntity)

    @Query("DELETE FROM template_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /**
     * Ceo šablon u jednom upitu, spojen sa katalogom.
     *
     * Živa veza: naziv i podrazumevane vrednosti dolaze IZ kataloga, ne iz kopije u
     * šablonu. Izmena kataloga se vidi u šablonu odmah — to je razlika između šablona
     * i putovanja, koje je snimak.
     */
    @Query(
        """
        SELECT s.id AS sectionId, s.title AS sectionTitle, s.phase AS sectionPhase,
               s.seedKey AS sectionSeedKey,
               s.sortOrder AS sectionOrder,
               e.id AS entryId, e.sortOrder AS entryOrder, e.note AS entryNote,
               e.bagOverride AS bagOverride, e.ruleTypeOverride AS ruleTypeOverride,
               e.rulePerOverride AS rulePerOverride, e.rulePlusOverride AS rulePlusOverride,
               e.ruleCapOverride AS ruleCapOverride,
               c.id AS catalogItemId, c.name AS catalogName, c.kind AS catalogKind,
               c.defaultBag AS catalogBag, c.note AS catalogNote, c.seedKey AS catalogSeedKey,
               c.ruleType AS catalogRuleType, c.rulePer AS catalogRulePer,
               c.rulePlus AS catalogRulePlus, c.ruleCap AS catalogRuleCap
        FROM template_section s
        JOIN template_entry e ON e.sectionId = s.id
        JOIN catalog_item c ON c.id = e.catalogItemId
        WHERE s.templateId = :templateId AND c.isArchived = 0
        ORDER BY s.sortOrder, e.sortOrder
        """,
    )
    suspend fun resolvedEntries(templateId: Long): List<ResolvedTemplateEntry>
}

/** Red šablona sa već razrešenim vrednostima iz kataloga. Ulaz u duboko kopiranje. */
data class ResolvedTemplateEntry(
    val sectionId: Long,
    val sectionTitle: String,
    val sectionPhase: String,
    val sectionSeedKey: String?,
    val sectionOrder: Int,
    val entryId: Long,
    val entryOrder: Int,
    val entryNote: String?,
    val bagOverride: String?,
    val ruleTypeOverride: String?,
    val rulePerOverride: Int?,
    val rulePlusOverride: Int?,
    val ruleCapOverride: Int?,
    val catalogItemId: Long,
    val catalogName: String,
    val catalogKind: String,
    val catalogBag: String,
    val catalogNote: String?,
    val catalogSeedKey: String?,
    val catalogRuleType: String,
    val catalogRulePer: Int?,
    val catalogRulePlus: Int?,
    val catalogRuleCap: Int?,
)

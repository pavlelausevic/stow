// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import kotlinx.coroutines.flow.Flow
import rs.lausevic.stow.data.db.CatalogDao
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.ShoppingRow
import rs.lausevic.stow.data.db.TemplateDao
import rs.lausevic.stow.data.db.TemplateEntity
import rs.lausevic.stow.data.db.TripDao
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripProgressByTrip
import rs.lausevic.stow.data.db.TripProgressRow
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.QuantityRule.Companion.resolve
import rs.lausevic.stow.data.model.QuantityRuleColumns
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode

class TripRepository(
    private val tripDao: TripDao,
    private val templateDao: TemplateDao,
    private val catalogDao: CatalogDao,
) {

    fun observeActive(): Flow<List<TripEntity>> = tripDao.observeActive()
    fun observeAll(): Flow<List<TripEntity>> = tripDao.observeAll()
    fun observeById(id: Long): Flow<TripEntity?> = tripDao.observeById(id)
    fun observeSections(tripId: Long): Flow<List<TripSectionEntity>> = tripDao.observeSections(tripId)
    fun observeItems(tripId: Long): Flow<List<TripItemEntity>> = tripDao.observeItems(tripId)
    fun observeProgress(tripId: Long): Flow<TripProgressRow?> = tripDao.observeProgress(tripId)
    fun observeAllProgress(): Flow<List<TripProgressByTrip>> = tripDao.observeAllProgress()
    fun observeShoppingList(): Flow<List<ShoppingRow>> = tripDao.observeShoppingList()
    fun observeActiveCount(): Flow<Int> = tripDao.observeActiveCount()
    fun observeArchivedCount(): Flow<Int> = tripDao.observeArchivedCount()
    fun observeTemplates(): Flow<List<TemplateEntity>> = templateDao.observeAll()

    suspend fun byId(id: Long): TripEntity? = tripDao.byId(id)
    suspend fun current(): TripEntity? = tripDao.current()
    suspend fun items(tripId: Long): List<TripItemEntity> = tripDao.items(tripId)
    suspend fun sections(tripId: Long): List<TripSectionEntity> = tripDao.sections(tripId)
    suspend fun templates(): List<TemplateEntity> = templateDao.all()

    /**
     * Duboko kopiranje šablona u putovanje.
     *
     * Snimak: `title`, `bag` i pravilo se KOPIRAJU, ne referenciraju. `catalogItemId`
     * ostaje samo radi porekla i statistike. Zato kasnija izmena kataloga ne dira
     * putovanje koje već postoji — a to je cela svrha troslojnog modela.
     */
    suspend fun createFromTemplate(
        templateId: Long,
        name: String,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
        accommodation: rs.lausevic.stow.data.model.Accommodation,
    ): Long {
        val nights = nightsBetween(startDate, endDate)
        val tripId = tripDao.insert(
            TripEntity(
                name = name.trim(),
                destination = destination?.takeIf { it.isNotBlank() },
                startDate = startDate,
                endDate = endDate,
                accommodation = accommodation,
                sourceTemplateId = templateId,
            ),
        )

        val resolved = templateDao.resolvedEntries(templateId)
        var sectionOrder = 0
        var currentSectionId: Long? = null
        var lastTemplateSection = -1L
        var itemOrder = 0

        resolved.forEach { row ->
            if (row.sectionId != lastTemplateSection) {
                currentSectionId = tripDao.insertSection(
                    TripSectionEntity(
                        tripId = tripId,
                        title = row.sectionTitle,
                        phase = runCatching { SectionPhase.valueOf(row.sectionPhase) }
                            .getOrDefault(SectionPhase.PACKING),
                        sortOrder = sectionOrder++,
                    ),
                )
                lastTemplateSection = row.sectionId
                itemOrder = 0
            }

            val kind = runCatching { ItemKind.valueOf(row.catalogKind) }.getOrDefault(ItemKind.ITEM)
            val rule = QuantityRuleColumns(
                type = runCatching {
                    rs.lausevic.stow.data.model.RuleType.valueOf(
                        row.ruleTypeOverride ?: row.catalogRuleType,
                    )
                }.getOrDefault(rs.lausevic.stow.data.model.RuleType.UNSPECIFIED),
                per = row.rulePerOverride ?: row.catalogRulePer,
                plus = row.rulePlusOverride ?: row.catalogRulePlus,
                cap = row.ruleCapOverride ?: row.catalogRuleCap,
            )

            tripDao.insertItem(
                buildItem(
                    sectionId = currentSectionId ?: return@forEach,
                    catalogItemId = row.catalogItemId,
                    title = row.catalogName,
                    note = row.entryNote ?: row.catalogNote,
                    kind = kind,
                    rule = rule,
                    nights = nights,
                    bag = runCatching { Bag.valueOf(row.bagOverride ?: row.catalogBag) }
                        .getOrDefault(Bag.UNASSIGNED),
                    ruleId = null,
                    sortOrder = itemOrder++,
                ),
            )
            catalogDao.recordUse(row.catalogItemId)
        }

        return tripId
    }

    /** Kopija ranijeg putovanja: ista struktura, sva stanja resetovana. */
    suspend fun createFromTrip(
        sourceTripId: Long,
        name: String,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
    ): Long {
        val source = tripDao.byId(sourceTripId) ?: error("Trip $sourceTripId not found")
        val tripId = tripDao.insert(
            TripEntity(
                name = name.trim(),
                destination = destination?.takeIf { it.isNotBlank() },
                startDate = startDate,
                endDate = endDate,
                accommodation = source.accommodation,
                sourceTemplateId = source.sourceTemplateId,
            ),
        )

        val nights = nightsBetween(startDate, endDate)
        tripDao.sections(sourceTripId).forEach { section ->
            val newSectionId = tripDao.insertSection(
                TripSectionEntity(
                    tripId = tripId,
                    title = section.title,
                    phase = section.phase,
                    sortOrder = section.sortOrder,
                ),
            )
            tripDao.itemsInSection(section.id).forEach { item ->
                val rule = QuantityRuleColumns(item.ruleType, item.rulePer, item.rulePlus, item.ruleCap)
                tripDao.insertItem(
                    buildItem(
                        sectionId = newSectionId,
                        catalogItemId = item.catalogItemId,
                        title = item.title,
                        note = item.note,
                        kind = item.kind,
                        rule = rule,
                        nights = nights,
                        bag = item.bag,
                        ruleId = item.ruleId,
                        ruleArgs = item.ruleArgs,
                        sortOrder = item.sortOrder,
                        assigneeId = item.assigneeId,
                    ),
                )
                item.catalogItemId?.let { catalogDao.recordUse(it) }
            }
        }
        return tripId
    }

    suspend fun createEmpty(
        name: String,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
        accommodation: rs.lausevic.stow.data.model.Accommodation,
    ): Long = tripDao.insert(
        TripEntity(
            name = name.trim(),
            destination = destination?.takeIf { it.isNotBlank() },
            startDate = startDate,
            endDate = endDate,
            accommodation = accommodation,
        ),
    )

    internal suspend fun buildItem(
        sectionId: Long,
        catalogItemId: Long?,
        title: String,
        note: String?,
        kind: ItemKind,
        rule: QuantityRuleColumns,
        nights: Int?,
        bag: Bag,
        ruleId: String?,
        ruleArgs: String? = null,
        sortOrder: Int,
        assigneeId: Long? = null,
    ): TripItemEntity {
        // Zadatak nema torbu ni količinu — nije predmet.
        val effectiveRule = if (kind == ItemKind.TASK) {
            QuantityRuleColumns.from(QuantityRule.Unspecified)
        } else {
            rule
        }
        return TripItemEntity(
            tripSectionId = sectionId,
            catalogItemId = catalogItemId,
            title = title,
            note = note,
            kind = kind,
            quantityCount = effectiveRule.toRule().resolve(nights),
            ruleType = effectiveRule.type,
            rulePer = effectiveRule.per,
            rulePlus = effectiveRule.plus,
            ruleCap = effectiveRule.cap,
            bag = if (kind == ItemKind.TASK) Bag.UNASSIGNED else bag,
            ruleId = ruleId,
            ruleArgs = ruleArgs,
            sortOrder = sortOrder,
            assigneeId = assigneeId,
        )
    }


    /**
     * Dodavanje stavke na već napravljeno putovanje.
     *
     * Ide na **dno** svoje sekcije, nikad na vrh: lista koja se pomeri kad joj nešto
     * dodaš je isto pomeranje pod prstom protiv kojeg cela aplikacija stoji.
     *
     * Ako stavka dolazi iz kataloga, sa sobom nosi torbu, napomenu i pravilo količine —
     * pravilo se razrešava **sada**, prema dužini ovog putovanja, i tu se zamrzne, isto
     * kao pri pravljenju putovanja. Slobodan unos nema pravilo i nema katalošku vezu.
     */
    suspend fun addItem(
        sectionId: Long,
        catalogItem: CatalogItemEntity?,
        title: String,
        kind: ItemKind,
        nights: Int?,
    ) {
        val order = (tripDao.itemsInSection(sectionId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val rule = catalogItem?.let {
            QuantityRuleColumns(it.ruleType, it.rulePer, it.rulePlus, it.ruleCap)
        } ?: QuantityRuleColumns.from(QuantityRule.Unspecified)

        tripDao.insertItem(
            buildItem(
                sectionId = sectionId,
                catalogItemId = catalogItem?.id,
                title = catalogItem?.name ?: title.trim(),
                note = catalogItem?.note,
                kind = catalogItem?.kind ?: kind,
                rule = rule,
                nights = nights,
                bag = catalogItem?.defaultBag ?: Bag.UNASSIGNED,
                ruleId = null,
                sortOrder = order,
            ),
        )
        catalogItem?.let { catalogDao.recordUse(it.id) }
    }

    /** Predlozi za ovo putovanje: najkorišćenije iz kataloga što ovde još nije. */
    fun observeSuggestions(tripId: Long) = catalogDao.observeSuggestions(tripId)
    // --- izmene stanja ---

    /**
     * Tap prebacuje TO_PACK u PACKED i nazad. `TO_BUY` se postavlja eksplicitno
     * (dugi pritisak ili list izmene) i vraća se u `TO_PACK` kad je kupljeno — tap
     * na stavku za nabavku znači "kupio sam je", ne "spakovao sam je".
     */
    suspend fun toggle(item: TripItemEntity, returning: Boolean) {
        if (returning) {
            tripDao.setReturned(item.id, !item.isReturned)
            return
        }
        val next = when (item.packStatus) {
            PackStatus.TO_BUY -> PackStatus.TO_PACK
            PackStatus.TO_PACK -> PackStatus.PACKED
            PackStatus.PACKED -> PackStatus.TO_PACK
        }
        tripDao.setStatus(item.id, next.name)
    }

    suspend fun setStatus(itemId: Long, status: PackStatus) = tripDao.setStatus(itemId, status.name)

    suspend fun setReturned(itemId: Long, returned: Boolean) = tripDao.setReturned(itemId, returned)

    suspend fun setMode(tripId: Long, mode: TripMode) = tripDao.setMode(tripId, mode.name)

    suspend fun setArchived(tripId: Long, archived: Boolean) = tripDao.setArchived(tripId, archived)

    suspend fun uncheckAll(tripId: Long) = tripDao.uncheckAll(tripId)

    suspend fun completeSection(sectionId: Long) = tripDao.completeSection(sectionId)

    /** Dodela putnika jednoj stavci. `null` = zajedničko. */
    suspend fun assignItem(itemId: Long, assigneeId: Long?) =
        tripDao.assignItem(itemId, assigneeId)

    suspend fun assignSection(sectionId: Long, assigneeId: Long?) =
        tripDao.assignSection(sectionId, assigneeId)

    suspend fun updateItem(item: TripItemEntity) =
        tripDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteItem(itemId: Long) = tripDao.deleteItem(itemId)

    suspend fun itemById(id: Long) = tripDao.itemById(id)

    suspend fun addSection(tripId: Long, title: String, phase: SectionPhase): Long =
        tripDao.insertSection(
            TripSectionEntity(
                tripId = tripId,
                title = title.trim(),
                phase = phase,
                sortOrder = tripDao.nextSectionOrder(tripId),
            ),
        )

    suspend fun renameSection(section: TripSectionEntity, title: String) =
        tripDao.updateSection(section.copy(title = title.trim(), updatedAt = System.currentTimeMillis()))

    suspend fun deleteSection(sectionId: Long) = tripDao.deleteSection(sectionId)

    suspend fun updateTrip(trip: TripEntity) =
        tripDao.update(trip.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteTrip(id: Long) = tripDao.delete(id)

    /** Preuređivanje: redni brojevi se prepisuju u celoj sekciji, da ne ostanu rupe. */
    suspend fun reorderItems(sectionId: Long, orderedIds: List<Long>) {
        val items = tripDao.itemsInSection(sectionId).associateBy { it.id }
        val now = System.currentTimeMillis()
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            items[id]?.copy(sortOrder = index, updatedAt = now)
        }
        tripDao.updateItems(updated)
    }

    suspend fun reorderSections(tripId: Long, orderedIds: List<Long>) {
        val sections = tripDao.sections(tripId).associateBy { it.id }
        val now = System.currentTimeMillis()
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            sections[id]?.copy(sortOrder = index, updatedAt = now)
        }
        tripDao.updateSections(updated)
    }

    /**
     * "Sredi" — spakovano na dno, jednom, na eksplicitnu radnju.
     *
     * Nikad u hodu: štikliranje koje pomera red pod prstom je konkretan, ponovljiv
     * propust konkurencije i jedan od razloga zašto ova aplikacija postoji.
     */
    suspend fun tidy(tripId: Long) {
        tripDao.sections(tripId).forEach { section ->
            val items = tripDao.itemsInSection(section.id)
            val sorted = items.sortedBy { it.packStatus == PackStatus.PACKED }
            reorderItems(section.id, sorted.map { it.id })
        }
    }

    /**
     * Zatvaranje putovanja: ono što je poneto a nije vraćeno upisuje se u katalog
     * kao brojač zaboravljanja — nikad preko korisnikove napomene.
     *
     * @return stavke koje su ostale iza tebe
     */
    suspend fun finishTrip(tripId: Long): List<TripItemEntity> {
        val leftBehind = tripDao.leftBehind(tripId)
        leftBehind.forEach { item -> item.catalogItemId?.let { catalogDao.recordLeftBehind(it) } }
        val now = System.currentTimeMillis()
        tripDao.setCompleted(tripId, now)
        tripDao.setArchived(tripId, true, now)
        return leftBehind
    }

    suspend fun leftBehindPreview(tripId: Long): List<TripItemEntity> = tripDao.leftBehind(tripId)

    companion object {
        /** Noći između dva epoch dana. Bez datuma nema broja — i to je tačan odgovor. */
        fun nightsBetween(startDay: Long?, endDay: Long?): Int? {
            if (startDay == null || endDay == null) return null
            val nights = (endDay - startDay).toInt()
            return if (nights > 0) nights else null
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import kotlinx.serialization.Serializable
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.TemplateEntity
import rs.lausevic.stow.data.db.TemplateEntryEntity
import rs.lausevic.stow.data.db.TemplateSectionEntity
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode

/*
 * Prenosni oblik. Veze se izražavaju UUID-ima, ne lokalnim `id`-jevima: `id` je
 * auto-increment i posle uvoza na drugom uređaju znači nešto sasvim drugo.
 *
 * Enumi putuju kao imena, pa dodavanje vrednosti u sredinu enuma ne pokvari stare kopije.
 */

@Serializable
data class CatalogDto(
    val uuid: String,
    val name: String,
    val normalizedName: String,
    val kind: String,
    val defaultBag: String,
    val ruleType: String,
    val rulePer: Int? = null,
    val rulePlus: Int? = null,
    val ruleCap: Int? = null,
    val defaultSection: String? = null,
    val photoPath: String? = null,
    val note: String? = null,
    val timesUsed: Int = 0,
    val lastUsedAt: Long? = null,
    val timesLeftBehind: Int = 0,
    val lastLeftBehindAt: Long? = null,
    val seedKey: String? = null,
    val isArchived: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class TravellerDto(
    val uuid: String,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Serializable
data class TemplateDto(
    val uuid: String,
    val name: String,
    val description: String? = null,
    val seedKey: String? = null,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Serializable
data class TemplateSectionDto(
    val uuid: String,
    val templateUuid: String,
    val title: String,
    val seedKey: String? = null,
    val phase: String,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Serializable
data class TemplateEntryDto(
    val uuid: String,
    val sectionUuid: String,
    val catalogUuid: String,
    val bagOverride: String? = null,
    val ruleTypeOverride: String? = null,
    val rulePerOverride: Int? = null,
    val rulePlusOverride: Int? = null,
    val ruleCapOverride: Int? = null,
    val note: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Serializable
data class TripDto(
    val uuid: String,
    val name: String,
    val destination: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val accommodation: String,
    val mode: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val isArchived: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class TripSectionDto(
    val uuid: String,
    val tripUuid: String,
    val title: String,
    val phase: String,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Serializable
data class TripItemDto(
    val uuid: String,
    val sectionUuid: String,
    val catalogUuid: String? = null,
    val assigneeUuid: String? = null,
    val title: String,
    val note: String? = null,
    val kind: String,
    val quantityCount: Int? = null,
    val ruleType: String,
    val rulePer: Int? = null,
    val rulePlus: Int? = null,
    val ruleCap: Int? = null,
    val bag: String,
    val packStatus: String,
    val isReturned: Boolean = false,
    val ruleId: String? = null,
    val ruleArgs: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

// --- entitet -> DTO ---

fun CatalogItemEntity.toDto() = CatalogDto(
    uuid = uuid,
    name = name,
    normalizedName = normalizedName,
    kind = kind.name,
    defaultBag = defaultBag.name,
    ruleType = ruleType.name,
    rulePer = rulePer,
    rulePlus = rulePlus,
    ruleCap = ruleCap,
    defaultSection = defaultSection,
    photoPath = photoPath,
    note = note,
    timesUsed = timesUsed,
    lastUsedAt = lastUsedAt,
    timesLeftBehind = timesLeftBehind,
    lastLeftBehindAt = lastLeftBehindAt,
    seedKey = seedKey,
    isArchived = isArchived,
    updatedAt = updatedAt,
)

fun TravellerEntity.toDto() = TravellerDto(uuid, name, sortOrder, updatedAt)

fun TemplateEntity.toDto() =
    TemplateDto(uuid, name, description, seedKey, isBuiltIn, sortOrder, updatedAt)

fun TripEntity.toDto() = TripDto(
    uuid = uuid,
    name = name,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    accommodation = accommodation.name,
    mode = mode.name,
    createdAt = createdAt,
    completedAt = completedAt,
    isArchived = isArchived,
    updatedAt = updatedAt,
)

// --- DTO -> entitet ---

fun CatalogDto.toEntity() = CatalogItemEntity(
    uuid = uuid,
    name = name,
    normalizedName = normalizedName,
    kind = enumOr(kind, ItemKind.ITEM),
    defaultBag = enumOr(defaultBag, Bag.UNASSIGNED),
    ruleType = enumOr(ruleType, RuleType.UNSPECIFIED),
    rulePer = rulePer,
    rulePlus = rulePlus,
    ruleCap = ruleCap,
    defaultSection = defaultSection,
    photoPath = photoPath,
    note = note,
    timesUsed = timesUsed,
    lastUsedAt = lastUsedAt,
    timesLeftBehind = timesLeftBehind,
    lastLeftBehindAt = lastLeftBehindAt,
    seedKey = seedKey,
    isArchived = isArchived,
    updatedAt = updatedAt,
)

fun TravellerDto.toEntity() =
    TravellerEntity(uuid = uuid, name = name, sortOrder = sortOrder, updatedAt = updatedAt)

fun TemplateDto.toEntity() = TemplateEntity(
    uuid = uuid,
    name = name,
    description = description,
    seedKey = seedKey,
    isBuiltIn = isBuiltIn,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun TemplateSectionDto.toEntity(templateId: Long) = TemplateSectionEntity(
    uuid = uuid,
    templateId = templateId,
    title = title,
    seedKey = seedKey,
    phase = enumOr(phase, SectionPhase.PACKING),
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun TemplateEntryDto.toEntity(sectionId: Long, catalogId: Long) = TemplateEntryEntity(
    uuid = uuid,
    sectionId = sectionId,
    catalogItemId = catalogId,
    bagOverride = bagOverride?.let { enumOrNull<Bag>(it) },
    ruleTypeOverride = ruleTypeOverride?.let { enumOrNull<RuleType>(it) },
    rulePerOverride = rulePerOverride,
    rulePlusOverride = rulePlusOverride,
    ruleCapOverride = ruleCapOverride,
    note = note,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun TripDto.toEntity() = TripEntity(
    uuid = uuid,
    name = name,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    accommodation = enumOr(accommodation, Accommodation.HOTEL),
    mode = enumOr(mode, TripMode.PACKING),
    createdAt = createdAt,
    completedAt = completedAt,
    isArchived = isArchived,
    updatedAt = updatedAt,
)

fun TripSectionDto.toEntity(tripId: Long) = TripSectionEntity(
    uuid = uuid,
    tripId = tripId,
    title = title,
    phase = enumOr(phase, SectionPhase.PACKING),
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun TripItemDto.toEntity(sectionId: Long, catalogId: Long?, assigneeId: Long?) = TripItemEntity(
    uuid = uuid,
    tripSectionId = sectionId,
    catalogItemId = catalogId,
    assigneeId = assigneeId,
    title = title,
    note = note,
    kind = enumOr(kind, ItemKind.ITEM),
    quantityCount = quantityCount,
    ruleType = enumOr(ruleType, RuleType.UNSPECIFIED),
    rulePer = rulePer,
    rulePlus = rulePlus,
    ruleCap = ruleCap,
    bag = enumOr(bag, Bag.UNASSIGNED),
    packStatus = enumOr(packStatus, PackStatus.TO_PACK),
    isReturned = isReturned,
    ruleId = ruleId,
    ruleArgs = ruleArgs,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

/** Nepoznata vrednost iz starije ili novije kopije ne sme da obori uvoz. */
private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

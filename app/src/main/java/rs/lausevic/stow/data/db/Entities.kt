// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode
import java.util.UUID

/*
 * Tri sloja, i razlika među njima je glavna arhitektonska ideja aplikacije:
 *
 *   KATALOG   — stvari koje posedujem. Jedini izvor istine za naziv, podrazumevanu
 *               torbu, količinu i fotografiju.
 *   ŠABLONI   — nacrti koji POKAZUJU na katalog. Živa veza: izmena kataloga menja šablon.
 *   PUTOVANJA — SNIMCI. Duboko kopirani i denormalizovani pri kreiranju, pa izmena
 *               kataloga ili šablona ne dira putovanje koje već postoji.
 *
 * Svaki red nosi `uuid` i `updatedAt`. Bez stabilnog identiteta reda aditivni uvoz ne
 * postoji — auto-increment `id` je lokalan i posle uvoza znači nešto drugo. Poklapanje
 * pri uvozu ide prvo po UUID-u, pa po prirodnom ključu.
 */

fun newUuid(): String = UUID.randomUUID().toString()

@Entity(
    tableName = "catalog_item",
    indices = [
        Index("uuid", unique = true),
        Index("normalizedName"),
        Index("isArchived"),
    ],
)
data class CatalogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    /** Naziv bez dijakritike i interpunkcije. Nosilac poklapanja sličnih pri dodavanju. */
    val normalizedName: String,
    val kind: ItemKind = ItemKind.ITEM,
    val defaultBag: Bag = Bag.UNASSIGNED,
    val ruleType: RuleType = RuleType.UNSPECIFIED,
    val rulePer: Int? = null,
    val rulePlus: Int? = null,
    val ruleCap: Int? = null,
    val defaultSection: String? = null,
    val photoPath: String? = null,
    val note: String? = null,
    val timesUsed: Int = 0,
    val lastUsedAt: Long? = null,
    /**
     * Koliko puta je stavka ostala iza tebe. Brojač, a NE napomena — napomena je
     * korisnikovo polje i ne sme da se gazi. Diže stavku na vrh povratne sekcije.
     */
    val timesLeftBehind: Int = 0,
    val lastLeftBehindAt: Long? = null,
    /**
     * Ključ u seed JSON-u dok je stavka netaknuta. Dok postoji, naziv se čita za tekući
     * jezik; čim korisnik izmeni stavku, briše se i naziv postaje njegov.
     */
    val seedKey: String? = null,
    val isArchived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "traveller", indices = [Index("uuid", unique = true)])
data class TravellerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "template", indices = [Index("uuid", unique = true)])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val description: String? = null,
    val seedKey: String? = null,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "template_section",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("uuid", unique = true), Index("templateId")],
)
data class TemplateSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val templateId: Long,
    val title: String,
    val seedKey: String? = null,
    val phase: SectionPhase = SectionPhase.PACKING,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "template_entry",
    foreignKeys = [
        ForeignKey(
            entity = TemplateSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("uuid", unique = true), Index("sectionId"), Index("catalogItemId")],
)
data class TemplateEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val sectionId: Long,
    val catalogItemId: Long,
    val ruleTypeOverride: RuleType? = null,
    val rulePerOverride: Int? = null,
    val rulePlusOverride: Int? = null,
    val ruleCapOverride: Int? = null,
    val bagOverride: Bag? = null,
    val note: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "trip", indices = [Index("uuid", unique = true), Index("isArchived")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val destination: String? = null,
    /** Epoch dan. `null` znači putovanje bez datuma — pravila po noći tada ostaju tekst. */
    val startDate: Long? = null,
    val endDate: Long? = null,
    val accommodation: Accommodation = Accommodation.HOTEL,
    val sourceTemplateId: Long? = null,
    val mode: TripMode = TripMode.PACKING,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Trenutak u kome je povratak proglašen završenim. Bez ove tačke aplikacija nikad ne
     * zna da je putovanje gotovo, pa se brojač zaboravljanja nikad ne uveća.
     */
    val completedAt: Long? = null,
    val isArchived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "trip_section",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("uuid", unique = true), Index("tripId")],
)
data class TripSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val tripId: Long,
    val title: String,
    val phase: SectionPhase = SectionPhase.PACKING,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Snimak jedne stavke na putovanju. Denormalizovan namerno: `title` je kopija, ne veza.
 *
 * `catalogItemId` postoji samo radi porekla i statistike i sme da bude `null` — arhivirana
 * ili obrisana stavka kataloga ne sme da odnese red sa sobom (otud `SET_NULL`).
 */
@Entity(
    tableName = "trip_item",
    foreignKeys = [
        ForeignKey(
            entity = TripSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripSectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TravellerEntity::class,
            parentColumns = ["id"],
            childColumns = ["assigneeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("uuid", unique = true),
        Index("tripSectionId"),
        Index("catalogItemId"),
        Index("assigneeId"),
        Index("packStatus"),
    ],
)
data class TripItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val tripSectionId: Long,
    val catalogItemId: Long? = null,
    val title: String,
    val note: String? = null,
    val kind: ItemKind = ItemKind.ITEM,
    /** Razrešen broj komada, ili `null` kad broj nema smisla. */
    val quantityCount: Int? = null,
    /*
     * Pravilo se zamrzava uz stavku, ali se NE upisuje kao tekst: tekst bi zamrznuo i
     * jezik, pa bi "1 po noći, +1" ostalo na srpskom i posle prebacivanja na engleski.
     * Ovako se ljudski oblik sastavlja iz resursa u trenutku prikaza.
     */
    val ruleType: RuleType = RuleType.UNSPECIFIED,
    val rulePer: Int? = null,
    val rulePlus: Int? = null,
    val ruleCap: Int? = null,
    val bag: Bag = Bag.UNASSIGNED,
    val packStatus: PackStatus = PackStatus.TO_PACK,
    /** `null` = zajedničko ili nedodeljeno. */
    val assigneeId: Long? = null,
    /**
     * Druga osa, namerno odvojena od `packStatus`. Pakovanje i povratak su dva različita
     * prolaza kroz istu listu; spajanje bi pitanje "šta nisam doneo kući" učinilo neodgovorivim.
     */
    val isReturned: Boolean = false,
    /** ID pravila čarobnjaka, npr. `nights.gte5.rotation`. Nikad rečenica — vidi napomenu gore. */
    val ruleId: String? = null,
    val ruleArgs: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import android.content.res.AssetManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rs.lausevic.stow.data.SettingsStore
import rs.lausevic.stow.data.db.CatalogDao
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.TemplateDao
import rs.lausevic.stow.data.db.TripDao
import rs.lausevic.stow.data.db.TemplateEntity
import rs.lausevic.stow.data.db.TemplateEntryEntity
import rs.lausevic.stow.data.db.TemplateSectionEntity
import rs.lausevic.stow.data.db.TravellerDao
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.domain.TextMatching
import java.util.Locale

@Serializable
data class SeedFile(
    val seedVersion: Int,
    val travellers: List<SeedTraveller> = emptyList(),
    val sections: List<SeedSection> = emptyList(),
    val items: List<SeedItem> = emptyList(),
    val templates: List<SeedTemplate> = emptyList(),
)

@Serializable
data class SeedTraveller(val key: String, val sr: String, val en: String)

@Serializable
data class SeedSection(val key: String, val sr: String, val en: String, val phase: String = "PACKING")

@Serializable
data class SeedItem(
    val key: String,
    val sr: String,
    val en: String,
    val section: String,
    val kind: String = "ITEM",
    val bag: String = "UNASSIGNED",
    val rule: SeedRule? = null,
    /** `false` znači: ulazi samo preko eksplicitnog pravila čarobnjaka, ne uz celu sekciju. */
    val core: Boolean = true,
    @SerialName("noteSr") val noteSr: String? = null,
    @SerialName("noteEn") val noteEn: String? = null,
)

@Serializable
data class SeedRule(val type: String, val per: Int? = null, val plus: Int? = null, val cap: Int? = null)

@Serializable
data class SeedTemplate(
    val key: String,
    val sr: String,
    val en: String,
    val descSr: String? = null,
    val descEn: String? = null,
    val sections: List<String> = emptyList(),
)

/**
 * Prvo pokretanje: katalog pre šablona, jer šabloni pokazuju na katalog.
 *
 * Idempotentno po ključu. `seedVersion` u DataStore-u pamti dokle se stiglo, pa kasnija
 * izdanja mogu da PROŠIRE seed bez diranja onoga što je korisnik u međuvremenu izmenio:
 * postojeći ključ se preskače, nikad ne prepisuje.
 */
class SeedRepository(
    private val assets: AssetManager,
    private val catalogDao: CatalogDao,
    private val travellerDao: TravellerDao,
    private val templateDao: TemplateDao,
    private val tripDao: TripDao,
    private val settings: SettingsStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun applyIfNeeded(): Boolean {
        val file = read()
        if (settings.currentSeedVersion() >= file.seedVersion) return false
        apply(file)
        settings.setSeedVersion(file.seedVersion)
        return true
    }

    /**
     * Prevodi nazive stavki koje korisnik NIJE dirao na tekuci jezik.
     *
     * Nazivi iz seed-a se razresavaju pri upisu, pa bi bez ovoga katalog zasejan na
     * engleskom zauvek ostao engleski i posle prebacivanja aplikacije na srpski.
     *
     * Dira se samo red koji jos nosi `seedKey`. Cim korisnik izmeni stavku, `seedKey` se
     * brise i naziv postaje njegov — tudji jezik mu vise ne moze prepisati ime.
     *
     * Od verzije 2 seme isto vazi i za **putovanja**: pre-setovana stavka je ista stvar
     * na oba jezika, pa nema razloga da lista zauvek ostane na jeziku na kom je
     * napravljena. Ono sto je korisnik sam dodao nema kljuc i ne dira se nikad.
     */
    suspend fun relocalise() {
        val file = read()
        val sections = file.sections.associateBy { it.key }
        val templates = file.templates.associateBy { it.key }

        file.items.forEach { seedItem ->
            val existing = catalogDao.bySeedKey(seedItem.key) ?: return@forEach
            val name = seedItem.resolve()
            val note = seedItem.resolveNote()
            val section = sections[seedItem.section]?.resolve()
            if (existing.name == name && existing.note == note && existing.defaultSection == section) {
                return@forEach
            }
            catalogDao.update(
                existing.copy(
                    name = name,
                    normalizedName = TextMatching.normalize(name),
                    note = note,
                    defaultSection = section,
                ),
            )
        }

        file.templates.forEach { seedTemplate ->
            val existing = templateDao.bySeedKey(seedTemplate.key) ?: return@forEach
            val name = seedTemplate.resolve()
            if (existing.name != name) {
                templateDao.update(existing.copy(name = name, description = seedTemplate.resolveDescription()))
            }
        }

        templateDao.allSections().forEach { section ->
            val key = section.seedKey ?: return@forEach
            val title = sections[key]?.resolve() ?: return@forEach
            if (section.title != title) templateDao.updateSection(section.copy(title = title))
        }

        // Putovanja napravljena pre verzije 2 nemaju `seedKey` na sekcijama: migracija ga
        // je tražila poklapanjem naslova sa šablonom, a šablon je do tada već bio
        // preveden, pa poklapanja nije ni bilo. Ovde se ključ traži po naslovu na **oba**
        // jezika, jednom, i posle toga sekcija ima svoj ključ zauvek.
        val sectionKeyByTitle = buildMap {
            file.sections.forEach { section ->
                put(section.sr, section.key)
                put(section.en, section.key)
            }
        }
        tripDao.allSections().forEach { section ->
            if (section.seedKey != null) return@forEach
            val key = sectionKeyByTitle[section.title] ?: return@forEach
            tripDao.updateSection(section.copy(seedKey = key))
        }

        // Putovanja se prevode samo tamo gde je naziv i dalje seed-ov. Stavka koju je
        // korisnik sam dodao nema ključ i ostaje kako ju je nazvao — automatskog
        // prevodioca nema, postoje samo dva spiska.
        val itemsByKey = file.items.associateBy { it.key }
        tripDao.allItems().forEach { item ->
            val seed = itemsByKey[item.seedKey ?: return@forEach] ?: return@forEach
            val name = seed.resolve()
            val note = seed.resolveNote()
            if (item.title == name && item.note == note) return@forEach
            tripDao.updateItem(item.copy(title = name, note = note))
        }

        tripDao.allSections().forEach { section ->
            val title = sections[section.seedKey ?: return@forEach]?.resolve() ?: return@forEach
            if (section.title != title) tripDao.updateSection(section.copy(title = title))
        }
        templates.size
    }

    fun read(): SeedFile =
        assets.open(SEED_PATH).bufferedReader().use { json.decodeFromString(SeedFile.serializer(), it.readText()) }

    /**
     * Vidljivo modulu radi testova: `applyIfNeeded` cita `seedVersion` iz DataStore-a,
     * koji je procesno globalan, pa test ne bi mogao da krene od praznog stanja.
     */
    internal suspend fun apply(file: SeedFile) {
        val sections = file.sections.associateBy { it.key }

        file.travellers.forEachIndexed { index, seed ->
            if (travellerDao.byName(seed.resolve()) == null) {
                travellerDao.insert(TravellerEntity(name = seed.resolve(), sortOrder = index))
            }
        }

        val catalogIds = mutableMapOf<String, Long>()
        file.items.forEach { item ->
            val existing = catalogDao.bySeedKey(item.key)
            if (existing != null) {
                catalogIds[item.key] = existing.id
                return@forEach
            }
            val name = item.resolve()
            val sectionTitle = sections[item.section]?.resolve()
            val rule = item.rule
            val id = catalogDao.insert(
                CatalogItemEntity(
                    name = name,
                    normalizedName = TextMatching.normalize(name),
                    kind = runCatching { ItemKind.valueOf(item.kind) }.getOrDefault(ItemKind.ITEM),
                    defaultBag = runCatching { Bag.valueOf(item.bag) }.getOrDefault(Bag.UNASSIGNED),
                    ruleType = rule?.let { runCatching { RuleType.valueOf(it.type) }.getOrNull() }
                        ?: RuleType.UNSPECIFIED,
                    rulePer = rule?.per,
                    rulePlus = rule?.plus,
                    ruleCap = rule?.cap,
                    defaultSection = sectionTitle,
                    note = item.resolveNote(),
                    seedKey = item.key,
                ),
            )
            catalogIds[item.key] = id
        }

        file.templates.forEachIndexed { templateIndex, template ->
            if (templateDao.bySeedKey(template.key) != null) return@forEachIndexed
            val templateId = templateDao.insert(
                TemplateEntity(
                    name = template.resolve(),
                    description = template.resolveDescription(),
                    seedKey = template.key,
                    isBuiltIn = true,
                    sortOrder = templateIndex,
                ),
            )

            template.sections.forEachIndexed { sectionIndex, sectionKey ->
                val section = sections[sectionKey] ?: return@forEachIndexed
                val sectionId = templateDao.insertSection(
                    TemplateSectionEntity(
                        templateId = templateId,
                        title = section.resolve(),
                        seedKey = section.key,
                        phase = runCatching { SectionPhase.valueOf(section.phase) }
                            .getOrDefault(SectionPhase.PACKING),
                        sortOrder = sectionIndex,
                    ),
                )

                // Samo `core` stavke ulaze uz sekciju. Ostale čekaju pravilo čarobnjaka —
                // inače bi kupaći kostim bio na svakom poslovnom putu u decembru.
                file.items
                    .filter { it.section == sectionKey && it.core }
                    .forEachIndexed { entryIndex, item ->
                        val catalogId = catalogIds[item.key] ?: return@forEachIndexed
                        templateDao.insertEntry(
                            TemplateEntryEntity(
                                sectionId = sectionId,
                                catalogItemId = catalogId,
                                sortOrder = entryIndex,
                            ),
                        )
                    }
            }
        }
    }

    companion object {
        const val SEED_PATH = "seed/seed.json"

        /** Srpski je jezik razvoja; sve što nije `sr` dobija engleski. */
        fun isSerbian(): Boolean = Locale.getDefault().language.lowercase(Locale.ROOT) == "sr"
    }
}

private fun SeedTraveller.resolve() = if (SeedRepository.isSerbian()) sr else en
private fun SeedSection.resolve() = if (SeedRepository.isSerbian()) sr else en
private fun SeedItem.resolve() = if (SeedRepository.isSerbian()) sr else en
private fun SeedItem.resolveNote() = if (SeedRepository.isSerbian()) noteSr else noteEn
private fun SeedTemplate.resolve() = if (SeedRepository.isSerbian()) sr else en
private fun SeedTemplate.resolveDescription() = if (SeedRepository.isSerbian()) descSr else descEn

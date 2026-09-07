// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.repo

import android.content.Context
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.domain.ReturnList
import rs.lausevic.stow.pdf.PackingListPdf
import rs.lausevic.stow.pdf.TrueTypeFont
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prevodi putovanje u ulaz za `:pdf` modul.
 *
 * Sav jezik i sve odluke o sadržaju su ovde; `:pdf` zna samo za redove, sekcije i kutije.
 * Zato pisac PDF-a ostaje čist Kotlin modul bez ijednog Android tipa i može da se testira
 * bez emulatora.
 */
class PdfExporter(private val context: Context) {

    enum class Grouping { SECTION, BAG }

    data class Options(
        /** `null` = svi putnici; inače taj putnik plus sve zajedničko. */
        val travellerId: Long? = null,
        val travellerName: String? = null,
        val returning: Boolean = false,
        val includeToBuy: Boolean = true,
        val grouping: Grouping = Grouping.SECTION,
    )

    fun build(
        trip: TripEntity,
        sections: List<TripSectionEntity>,
        items: List<TripItemEntity>,
        options: Options,
    ): PackingListPdf.Document {
        val phaseOf: (TripItemEntity) -> SectionPhase = { item ->
            sections.firstOrNull { it.id == item.tripSectionId }?.phase ?: SectionPhase.PACKING
        }

        val scoped = items
            // Zajednicke stavke se pojavljuju JEDNOM, ne po jednom za svakog putnika.
            .filter { options.travellerId == null || it.assigneeId == null || it.assigneeId == options.travellerId }
            .let { list ->
                if (options.returning) ReturnList.build(list, phaseOf).included else list
            }
            .filter { options.includeToBuy || it.packStatus != PackStatus.TO_BUY }

        // Zadaci idu u zaseban blok na kraj: nisu predmeti, nemaju torbu ni kolicinu,
        // i mesanje sa stvarima cini spisak neprohodnim.
        val tasks = scoped.filter { it.kind == ItemKind.TASK }
        val objects = scoped.filter { it.kind == ItemKind.ITEM }
        val toBuy = if (options.includeToBuy) objects.filter { it.packStatus == PackStatus.TO_BUY } else emptyList()
        val main = objects.filter { it.packStatus != PackStatus.TO_BUY }

        val grouped = when (options.grouping) {
            Grouping.SECTION -> sections
                .sortedBy { it.sortOrder }
                .mapNotNull { section ->
                    val rows = main.filter { it.tripSectionId == section.id }.sortedBy { it.sortOrder }
                    if (rows.isEmpty()) null else section.title to rows
                }
            Grouping.BAG -> Bag.entries.mapNotNull { bag ->
                val rows = main.filter { it.bag == bag }
                if (rows.isEmpty()) null else bagLabel(bag) to rows
            }
        }

        val pdfSections = buildList {
            grouped.forEach { (title, rows) -> add(PackingListPdf.Section(title, rows.map(::row))) }
            if (tasks.isNotEmpty()) {
                add(PackingListPdf.Section(context.getString(R.string.catalogue_kind_task), tasks.map(::row)))
            }
            if (toBuy.isNotEmpty()) {
                add(PackingListPdf.Section(context.getString(R.string.filter_to_buy), toBuy.map(::row)))
            }
        }

        return PackingListPdf.Document(
            title = trip.name,
            subtitleLines = subtitle(trip, options),
            stamp = context.getString(
                if (options.returning) R.string.trip_mode_returning else R.string.trip_mode_packing,
            ).split(" "),
            sections = pdfSections,
            footerLeft = "Stow · ${LocalDate.now().format(dayMonthYear)}",
            pageLabel = { page, total -> "$page / $total" },
        )
    }

    fun write(document: PackingListPdf.Document, fileName: String): File {
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        // Isto ime se prepisuje: cache nije arhiva, a dvadeset kopija iste liste
        // je smece koje korisnik nikad nece obrisati.
        val file = File(exports, fileName)
        file.writeBytes(PackingListPdf(loadFont(REGULAR), loadFont(SEMIBOLD)).render(document))
        return file
    }

    fun fileName(trip: TripEntity, options: Options): String {
        val slug = trip.name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "stow" }
        val suffix = if (options.returning) "povratak" else "pakovanje"
        return "$slug-$suffix.pdf"
    }

    private fun row(item: TripItemEntity) = PackingListPdf.Row(
        fieldName = "item.${item.id}",
        title = item.title,
        meta = metaLine(item),
        quantity = item.quantityCount?.takeIf { it > 1 }?.toString(),
        shape = when {
            item.kind == ItemKind.TASK -> PackingListPdf.BoxShape.CIRCLE
            item.packStatus == PackStatus.TO_BUY -> PackingListPdf.BoxShape.DOTTED
            else -> PackingListPdf.BoxShape.SQUARE
        },
    )

    private fun metaLine(item: TripItemEntity): String? {
        val parts = buildList {
            if (item.kind == ItemKind.ITEM && item.bag != Bag.UNASSIGNED) add(bagLabel(item.bag))
            if (item.ruleType == RuleType.PER_NIGHTS) {
                val per = item.rulePer ?: 1
                add(
                    buildString {
                        append(
                            if (per == 1) {
                                context.getString(R.string.quantity_per_nights, 1)
                            } else {
                                context.getString(R.string.quantity_per_nights_n, per)
                            },
                        )
                        item.rulePlus?.takeIf { it > 0 }
                            ?.let { append(context.getString(R.string.quantity_plus, it)) }
                        item.ruleCap?.let { append(context.getString(R.string.quantity_cap, it)) }
                    },
                )
            }
            item.note?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun subtitle(trip: TripEntity, options: Options): List<String> = buildList {
        val start = trip.startDate?.let { LocalDate.ofEpochDay(it) }
        val end = trip.endDate?.let { LocalDate.ofEpochDay(it) }
        if (start != null && end != null) {
            val nights = (end.toEpochDay() - start.toEpochDay()).toInt()
            add("${start.format(dayMonthYear)} – ${end.format(dayMonthYear)} · $nights")
        } else if (start != null) {
            add(start.format(dayMonthYear))
        }
        val who = options.travellerName?.let { context.getString(R.string.export_who_one, it) }
            ?: context.getString(R.string.traveller_everyone)
        val where = trip.destination
        add(listOfNotNull(where, who).joinToString(" · "))
    }

    private fun bagLabel(bag: Bag): String = context.getString(
        when (bag) {
            Bag.CHECKED -> R.string.bag_checked
            Bag.CARRY_ON -> R.string.bag_carry_on
            Bag.PERSONAL -> R.string.bag_personal
            Bag.UNASSIGNED -> R.string.bag_unassigned
        },
    )

    private fun loadFont(assetPath: String): TrueTypeFont =
        TrueTypeFont(context.assets.open(assetPath).use { it.readBytes() })

    private companion object {
        const val REGULAR = "fonts/rubik_regular.ttf"
        const val SEMIBOLD = "fonts/rubik_semibold.ttf"
        val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    }
}

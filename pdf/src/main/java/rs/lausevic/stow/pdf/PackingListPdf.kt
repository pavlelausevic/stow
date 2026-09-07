// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

/**
 * Lista pakovanja kao PDF sa **pravim poljima obrasca**.
 *
 * Ovo je jedina stvar koju konkurencija nema. Kvačice nisu nacrtani kvadrati nego
 * `/Widget` anotacije tipa `/Btn`: pošalješ fajl saputniku i on ga štiklira u bilo kom
 * čitaču, pa sačuva. Bez naloga, bez servera, bez sinhronizacije — fajl.
 *
 * Oblik i dalje nosi značenje, isto kao u aplikaciji: kvadrat je predmet, krug je
 * zadatak, tačkasti okvir je nabavka. Tako lista radi i odštampana crno-belo.
 */
class PackingListPdf(
    private val regular: TrueTypeFont,
    private val semibold: TrueTypeFont,
) {

    enum class BoxShape { SQUARE, CIRCLE, DOTTED }

    data class Row(
        /** Jedinstveno ime polja, npr. `item.42`. Duplikat bi spojio kvačice u čitaču. */
        val fieldName: String,
        val title: String,
        val meta: String? = null,
        val quantity: String? = null,
        val shape: BoxShape = BoxShape.SQUARE,
    )

    data class Section(val title: String, val rows: List<Row>)

    data class Document(
        val title: String,
        val subtitleLines: List<String>,
        val stamp: List<String>,
        val sections: List<Section>,
        val footerLeft: String,
        /** (strana, ukupno) → tekst; jezik pripada aplikaciji, ne piscu. */
        val pageLabel: (Int, Int) -> String,
    )

    fun render(document: Document): ByteArray {
        val pages = paginate(document)
        val writer = PdfWriter()

        val catalogId = writer.reserve()
        val pagesId = writer.reserve()
        val acroFormId = writer.reserve()
        val infoId = writer.reserve()

        val bodyFont = PdfFont(regular, "F1")
        val boldFont = PdfFont(semibold, "F2")

        val allText = collectText(document)
        val bodyRef = bodyFont.write(writer, regular.glyphsUsed(allText))
        val boldRef = boldFont.write(writer, semibold.glyphsUsed(allText))

        val appearances = writeAppearances(writer)

        val pageIds = pages.map { writer.reserve() }
        val fieldIds = mutableListOf<Int>()

        pages.forEachIndexed { index, page ->
            val pageId = pageIds[index]
            val contentId = writer.reserve()
            val widgets = page.rows.map { placed ->
                val id = writer.reserve()
                fieldIds += id
                id to placed
            }

            val content = drawPage(document, page, index, pages.size, bodyFont, boldFont)
            writer.writeStream(contentId, "", content.toByteArray(Charsets.ISO_8859_1))

            widgets.forEach { (id, placed) ->
                writeWidget(writer, id, pageId, placed, appearances)
            }

            writer.writeObject(
                pageId,
                """
                << /Type /Page /Parent $pagesId 0 R
                   /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT]
                   /Resources << /Font << /F1 $bodyRef 0 R /F2 $boldRef 0 R >>
                                 /ProcSet [/PDF /Text] >>
                   /Contents $contentId 0 R
                   /Annots [${widgets.joinToString(" ") { "${it.first} 0 R" }}] >>
                """.trimIndent(),
            )
        }

        writer.writeObject(
            pagesId,
            "<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] >>",
        )

        /*
         * /NeedAppearances je zakrpa, ne mehanizam: PDFium — koji stoji iza Chrome-a i
         * vecine Android citaca — ga ignorise. Pravi mehanizam je eksplicitan /AP /N
         * recnik po vidzetu, koji je gore i upisan. /DA i /DR su ovde jer bez njih
         * Acrobat prigovara na obrazac bez podrazumevanog izgleda.
         */
        writer.writeObject(
            acroFormId,
            """
            << /Fields [${fieldIds.joinToString(" ") { "$it 0 R" }}]
               /NeedAppearances true
               /DA (/F1 0 Tf 0 g)
               /DR << /Font << /F1 $bodyRef 0 R /F2 $boldRef 0 R >> >> >>
            """.trimIndent(),
        )

        writer.writeObject(
            catalogId,
            "<< /Type /Catalog /Pages $pagesId 0 R /AcroForm $acroFormId 0 R >>",
        )
        writer.writeObject(
            infoId,
            "<< /Title ${PdfWriter.literal(document.title)} /Producer (Stow) /Creator (Stow) >>",
        )

        return writer.finish(catalogId, infoId)
    }

    // --- prelom ---

    private data class PlacedRow(val row: Row, val y: Double)
    private data class PlacedSection(val title: String, val y: Double)
    private data class Page(val sections: List<PlacedSection>, val rows: List<PlacedRow>)

    private fun paginate(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var sections = mutableListOf<PlacedSection>()
        var rows = mutableListOf<PlacedRow>()
        var y = PAGE_HEIGHT - MARGIN - HEADER_HEIGHT

        fun newPage() {
            pages += Page(sections, rows)
            sections = mutableListOf()
            rows = mutableListOf()
            y = PAGE_HEIGHT - MARGIN - CONTINUATION_TOP
        }

        document.sections.forEach { section ->
            if (section.rows.isEmpty()) return@forEach
            // Zaglavlje sekcije sa jednim redom ispod na dnu strane je siroce: ili oba
            // idu na sledecu stranu, ili nijedno.
            if (y - SECTION_HEIGHT - ROW_HEIGHT < MARGIN + FOOTER_HEIGHT) newPage()

            sections += PlacedSection(section.title, y)
            y -= SECTION_HEIGHT

            section.rows.forEach { row ->
                if (y - ROW_HEIGHT < MARGIN + FOOTER_HEIGHT) newPage()
                rows += PlacedRow(row, y)
                y -= if (row.meta != null) ROW_HEIGHT_WITH_META else ROW_HEIGHT
            }
            y -= SECTION_GAP
        }

        pages += Page(sections, rows)
        return pages
    }

    // --- crtanje ---

    private fun drawPage(
        document: Document,
        page: Page,
        index: Int,
        total: Int,
        body: PdfFont,
        bold: PdfFont,
    ): String = buildString {
        val right = PAGE_WIDTH - MARGIN

        if (index == 0) {
            val titleTop = PAGE_HEIGHT - MARGIN - 24
            text(bold, document.title, MARGIN, titleTop, 22.0)
            var line = titleTop - 18
            document.subtitleLines.forEach {
                text(body, it, MARGIN, line, 9.0, GREY)
                line -= 12
            }

            val stampWidth = 96.0
            val stampHeight = 12.0 + document.stamp.size * 11.0
            val stampX = right - stampWidth
            val stampY = PAGE_HEIGHT - MARGIN - stampHeight
            roundedRect(stampX, stampY, stampWidth, stampHeight, 8.0, ACCENT, 1.2)
            document.stamp.forEachIndexed { i, stampLine ->
                val w = body.width(stampLine, 8.0)
                text(body, stampLine, stampX + (stampWidth - w) / 2, stampY + stampHeight - 14 - i * 11, 8.0, ACCENT)
            }

            val ruleY = PAGE_HEIGHT - MARGIN - HEADER_HEIGHT + 14
            rule(MARGIN, ruleY, right, 2.0, INK)
        } else {
            text(body, document.title, MARGIN, PAGE_HEIGHT - MARGIN - 10, 9.0, GREY)
            rule(MARGIN, PAGE_HEIGHT - MARGIN - CONTINUATION_TOP + 12, right, 1.0, LINE)
        }

        page.sections.forEach { section ->
            val label = section.title.uppercase()
            text(bold, label, MARGIN, section.y, 8.0, INK, charSpacing = 1.4)
            val labelEnd = MARGIN + bold.width(label, 8.0) + 1.4 * label.length + 8
            rule(labelEnd, section.y + 3, right, 1.0, LINE)
        }

        page.rows.forEach { placed ->
            val row = placed.row
            val boxY = placed.y - 1
            drawBox(row.shape, MARGIN, boxY)

            val textX = MARGIN + BOX_SIZE + 10
            val quantity = row.quantity
            val quantityWidth = quantity?.let { body.width(it, 10.0) } ?: 0.0
            val available = right - textX - quantityWidth - (if (quantity != null) 10.0 else 0.0)

            text(body, body.ellipsize(row.title, 10.5, available), textX, placed.y, 10.5)
            if (row.meta != null) {
                text(body, body.ellipsize(row.meta.uppercase(), 7.0, available), textX, placed.y - 9, 7.0, GREY, charSpacing = 0.9)
            }
            if (quantity != null) {
                text(body, quantity, right - quantityWidth, placed.y, 10.0, INK)
            }
            rule(MARGIN, placed.y - (if (row.meta != null) 15.0 else 7.0), right, 0.5, LINE)
        }

        val footerY = MARGIN + 10
        rule(MARGIN, footerY + 12, right, 1.0, LINE)
        text(body, document.footerLeft, MARGIN, footerY, 7.5, GREY, charSpacing = 0.7)
        val label = document.pageLabel(index + 1, total)
        text(body, label, right - body.width(label, 7.5) - 0.7 * label.length, footerY, 7.5, GREY, charSpacing = 0.7)
    }

    private fun StringBuilder.text(
        font: PdfFont,
        value: String,
        x: Double,
        y: Double,
        size: Double,
        colour: String = INK,
        charSpacing: Double = 0.0,
    ) {
        if (value.isEmpty()) return
        append("BT $colour rg /${font.resourceName} $size Tf ")
        if (charSpacing != 0.0) append("$charSpacing Tc ")
        append("1 0 0 1 $x $y Tm ")
        append(PdfWriter.glyphHex(font.font, value))
        append(" Tj ")
        if (charSpacing != 0.0) append("0 Tc ")
        append("ET\n")
    }

    private fun StringBuilder.rule(x1: Double, y: Double, x2: Double, width: Double, colour: String) {
        append("$colour RG $width w 1 J $x1 $y m $x2 $y l S\n")
    }

    private fun StringBuilder.roundedRect(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        r: Double,
        colour: String,
        lineWidth: Double,
    ) {
        val k = r * BEZIER_CIRCLE
        append("$colour RG $lineWidth w\n")
        append("${x + r} $y m\n")
        append("${x + w - r} $y l\n")
        append("${x + w - r + k} $y ${x + w} ${y + r - k} ${x + w} ${y + r} c\n")
        append("${x + w} ${y + h - r} l\n")
        append("${x + w} ${y + h - r + k} ${x + w - r + k} ${y + h} ${x + w - r} ${y + h} c\n")
        append("${x + r} ${y + h} l\n")
        append("${x + r - k} ${y + h} $x ${y + h - r + k} $x ${y + h - r} c\n")
        append("$x ${y + r} l\n")
        append("$x ${y + r - k} ${x + r - k} $y ${x + r} $y c\n")
        append("S\n")
    }

    /**
     * Kutija se crta u sadržaju strane, a vidžet se postavlja tačno preko nje.
     *
     * Nacrtani okvir je ono što se vidi u čitaču koji ne prikazuje polja obrasca i ono
     * što izlazi na štampač; vidžet je ono što se štiklira. Bez nacrtanog okvira
     * odštampana lista bi bila spisak bez kvadratića.
     */
    private fun StringBuilder.drawBox(shape: BoxShape, x: Double, y: Double) {
        when (shape) {
            BoxShape.SQUARE -> roundedRect(x, y, BOX_SIZE, BOX_SIZE, 3.5, INK, 1.1)
            BoxShape.CIRCLE -> roundedRect(x, y, BOX_SIZE, BOX_SIZE, BOX_SIZE / 2, INK, 1.1)
            BoxShape.DOTTED -> {
                append("$ALERT RG 1.1 w [0.1 3] 0 d 1 J\n")
                roundedRect(x, y, BOX_SIZE, BOX_SIZE, 3.5, ALERT, 1.1)
                append("[] 0 d\n")
            }
        }
    }

    // --- polja obrasca ---

    private data class Appearances(val off: Int, val on: Int)

    private fun writeAppearances(writer: PdfWriter): Appearances {
        val off = writer.reserve()
        val on = writer.reserve()
        val dict = "/Type /XObject /Subtype /Form /BBox [0 0 $BOX_SIZE $BOX_SIZE] " +
            "/Resources << /ProcSet [/PDF] >>"

        // Prazno stanje ne crta nista: okvir je vec u sadrzaju strane, pa bi drugi
        // okvir preko njega bio deblja linija na svakoj neoznacenoj stavci.
        writer.writeStream(off, dict, " ".toByteArray(Charsets.ISO_8859_1))

        val tick = buildString {
            append("$INK RG 1.6 w 1 J 1 j\n")
            append("2.6 6.2 m 5.0 3.6 l 9.6 9.4 l S\n")
        }
        writer.writeStream(on, dict, tick.toByteArray(Charsets.ISO_8859_1))
        return Appearances(off, on)
    }

    private fun writeWidget(
        writer: PdfWriter,
        id: Int,
        pageId: Int,
        placed: PlacedRow,
        appearances: Appearances,
    ) {
        val x = MARGIN
        val y = placed.y - 1
        writer.writeObject(
            id,
            """
            << /Type /Annot /Subtype /Widget /FT /Btn
               /T ${PdfWriter.literal(placed.row.fieldName)}
               /TU ${PdfWriter.literal(placed.row.title)}
               /Ff 0 /F 4
               /Rect [$x $y ${x + BOX_SIZE} ${y + BOX_SIZE}]
               /AS /Off /V /Off /DV /Off
               /MK << /BC [] /BG [] >>
               /DA (/F1 0 Tf 0 g)
               /AP << /N << /Off ${appearances.off} 0 R /Yes ${appearances.on} 0 R >> >>
               /P $pageId 0 R >>
            """.trimIndent(),
        )
    }

    private fun collectText(document: Document): List<String> = buildList {
        add(document.title)
        addAll(document.subtitleLines)
        addAll(document.stamp)
        add(document.footerLeft)
        add(document.pageLabel(1, 1))
        add(document.pageLabel(9, 9))
        document.sections.forEach { section ->
            add(section.title)
            add(section.title.uppercase())
            section.rows.forEach { row ->
                add(row.title)
                add("…")
                row.meta?.let { add(it); add(it.uppercase()) }
                row.quantity?.let(::add)
            }
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595.28
        const val PAGE_HEIGHT = 841.89
        const val MARGIN = 48.0
        const val HEADER_HEIGHT = 92.0
        const val CONTINUATION_TOP = 34.0
        const val FOOTER_HEIGHT = 30.0
        const val SECTION_HEIGHT = 22.0
        const val SECTION_GAP = 8.0
        const val ROW_HEIGHT = 19.0
        const val ROW_HEIGHT_WITH_META = 27.0
        const val BOX_SIZE = 12.0

        /** Kappa: koliko kontrolna tačka Bezijea mora da izađe da bi luk bio krug. */
        const val BEZIER_CIRCLE = 0.5523

        const val INK = "0.078 0.09 0.098"
        const val GREY = "0.42 0.45 0.44"
        const val LINE = "0.78 0.80 0.77"
        const val ACCENT = "0.149 0.259 0.561"
        const val ALERT = "0.647 0.251 0.165"
    }
}

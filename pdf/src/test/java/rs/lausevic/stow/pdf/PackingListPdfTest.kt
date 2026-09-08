// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Testovi PDF-a parsiraju **izlazne bajtove**, ne unutrašnje stanje pisca.
 *
 * To je jedina provera koja nešto znači: PDF je tačan ako se ono što je zapisano može
 * pročitati natrag. Zbog toga je `:pdf` i zaseban čist Kotlin modul — testovi rade bez
 * emulatora, a kod ne može slučajno da posegne za Android API-jem.
 */
class PackingListPdfTest {

    private val regular = TrueTypeFont(fontBytes("rubik_regular.ttf"))
    private val semibold = TrueTypeFont(fontBytes("rubik_semibold.ttf"))

    private fun fontBytes(name: String): ByteArray {
        val file = File("../app/src/main/assets/fonts/$name")
        assertTrue("font nije nađen: ${file.absolutePath}", file.exists())
        return file.readBytes()
    }

    private fun document(rowCount: Int = 6, sectionCount: Int = 2) = PackingListPdf.Document(
        title = "Split · apartman",
        subtitleLines = listOf("12–19 SEP 2026 · 7 NOĆI", "APARTMAN · MILICA + ZAJEDNIČKO"),
        stamp = listOf("Lista", "pakovanja"),
        sections = (1..sectionCount).map { s ->
            PackingListPdf.Section(
                title = "Sekcija $s",
                rows = (1..rowCount).map { r ->
                    PackingListPdf.Row(
                        fieldName = "item.$s.$r",
                        title = "Punjač za telefon $s-$r",
                        meta = if (r % 2 == 0) "PREDATI · 1 PO NOĆI, +1" else null,
                        quantity = if (r % 3 == 0) "8" else null,
                        shape = when (r % 3) {
                            0 -> PackingListPdf.BoxShape.CIRCLE
                            1 -> PackingListPdf.BoxShape.DOTTED
                            else -> PackingListPdf.BoxShape.SQUARE
                        },
                    )
                },
            )
        },
        footerLeft = "Stow · izvezeno 07 SEP 2026",
        pageLabel = { page, total -> "Strana $page / $total" },
    )

    private fun render(doc: PackingListPdf.Document, layout: PageLayout = PageLayout.A4): String =
        String(PackingListPdf(regular, semibold, layout).render(doc), Charsets.ISO_8859_1)

    @Test
    fun `emits a well formed pdf`() {
        val pdf = render(document())
        assertTrue("nedostaje zaglavlje", pdf.startsWith("%PDF-1.7"))
        assertTrue("nedostaje EOF", pdf.trimEnd().endsWith("%%EOF"))
        assertTrue("nedostaje xref", pdf.contains("\nxref\n"))
        assertTrue("nedostaje startxref", pdf.contains("startxref"))
        assertTrue("nedostaje katalog", pdf.contains("/Type /Catalog"))
    }

    @Test
    fun `xref offsets point at the objects they claim`() {
        val bytes = PackingListPdf(regular, semibold).render(document())
        val pdf = String(bytes, Charsets.ISO_8859_1)

        val startxref = pdf.substringAfterLast("startxref").trim().substringBefore("\n").trim().toInt()
        val xref = pdf.substring(startxref)
        assertTrue("startxref ne pokazuje na xref", xref.startsWith("xref"))

        val lines = xref.lines()
        val count = lines[1].trim().split(" ")[1].toInt()
        // Prvi unos je uvek slobodan (objekat 0), pa se preskace.
        for (id in 1 until count) {
            val entry = lines[1 + id + 1].trim()
            val offset = entry.split(" ")[0].toInt()
            val at = pdf.substring(offset, minOf(offset + 24, pdf.length))
            assertTrue("objekat $id nije na prijavljenoj poziciji: '$at'", at.startsWith("$id 0 obj"))
        }
    }

    @Test
    fun `every row becomes a checkbox field with a unique name`() {
        val doc = document(rowCount = 5, sectionCount = 3)
        val pdf = render(doc)

        val expected = doc.sections.sumOf { it.rows.size }
        val widgets = Regex("/Subtype /Widget").findAll(pdf).count()
        assertEquals("broj vidžeta", expected, widgets)

        val buttons = Regex("/FT /Btn").findAll(pdf).count()
        assertEquals("sva polja su dugmad", expected, buttons)

        val names = Regex("""/T \(([^)]+)\)""").findAll(pdf).map { it.groupValues[1] }.toList()
        assertEquals("broj imena polja", expected, names.size)
        assertEquals("imena polja moraju biti jedinstvena", names.size, names.toSet().size)

        val fields = Regex("""/Fields \[([^\]]*)\]""").find(pdf)!!.groupValues[1]
        assertEquals("svako polje je u /AcroForm /Fields", expected, Regex("""\d+ 0 R""").findAll(fields).count())
    }

    @Test
    fun `checkboxes carry an explicit appearance dictionary`() {
        val pdf = render(document())
        // /NeedAppearances je zakrpa; PDFium ga ignorise, pa /AP mora da postoji.
        assertTrue("nedostaje /NeedAppearances", pdf.contains("/NeedAppearances true"))
        val appearances = Regex("""/AP << /N << /Off \d+ 0 R /Yes \d+ 0 R >> >>""").findAll(pdf).count()
        assertEquals("svaki vidžet ima /AP /N sa oba stanja", 12, appearances)
        assertTrue("početno stanje mora biti /Off", pdf.contains("/AS /Off /V /Off"))
    }

    @Test
    fun `font is embedded as CIDFontType2 with Identity-H`() {
        val pdf = render(document())
        assertTrue(pdf.contains("/Subtype /Type0"))
        assertTrue(pdf.contains("/Encoding /Identity-H"))
        assertTrue(pdf.contains("/Subtype /CIDFontType2"))
        assertTrue(pdf.contains("/CIDToGIDMap /Identity"))
        assertTrue("font mora biti ugrađen, ne samo imenovan", pdf.contains("/FontFile2"))
        assertTrue("nedostaje /Length1 na ugrađenom fontu", pdf.contains("/Length1"))
        assertTrue("nedostaje ToUnicode — tekst se ne bi mogao kopirati", pdf.contains("/ToUnicode"))
    }

    @Test
    fun `serbian text is written as glyph ids, never as latin1 bytes`() {
        val pdf = render(
            document().copy(
                sections = listOf(
                    PackingListPdf.Section(
                        "Neseser",
                        listOf(PackingListPdf.Row("item.1", "Četkica za zube i punjač")),
                    ),
                ),
            ),
        )
        // Da je tekst pisan kao WinAnsi, kvacice bi bile izgubljene ili pogresno mapirane.
        // Sa Identity-H u toku sadrzaja postoje samo heks niske GID-ova.
        assertFalse("tekst ne sme da izađe kao literal", pdf.contains("(Četkica"))
        assertTrue("tekst mora biti heks niska glifova", Regex("<[0-9a-f]{8,}> Tj").containsMatchIn(pdf))

        val gid = regular.glyphId('č'.code)
        assertTrue("font nema glif za č", gid != 0)
        assertTrue(
            "GID za č se ne pojavljuje u toku sadržaja",
            pdf.contains(gid.toString(16).padStart(4, '0')),
        )
    }

    @Test
    fun `long lists paginate and every page is registered`() {
        val single = render(document(rowCount = 4, sectionCount = 1))
        assertEquals("kratka lista staje na jednu stranu", 1, Regex("/Type /Page[^s]").findAll(single).count())

        val long = render(document(rowCount = 30, sectionCount = 4))
        val pageObjects = Regex("/Type /Page[^s]").findAll(long).count()
        assertTrue("duga lista mora da se prelomi, dobijeno $pageObjects", pageObjects > 1)

        val count = Regex("""/Type /Pages /Count (\d+)""").find(long)!!.groupValues[1].toInt()
        assertEquals("/Count mora da odgovara broju strana", pageObjects, count)

        val kids = Regex("""/Kids \[([^\]]*)\]""").find(long)!!.groupValues[1]
        assertEquals("svaka strana mora biti u /Kids", pageObjects, Regex("""\d+ 0 R""").findAll(kids).count())
    }

    @Test
    fun `an empty list still produces a valid single page`() {
        val pdf = render(document().copy(sections = emptyList()))
        assertEquals(1, Regex("/Type /Page[^s]").findAll(pdf).count())
        assertEquals(0, Regex("/Subtype /Widget").findAll(pdf).count())
        assertTrue("prazan obrazac i dalje mora imati /Fields", pdf.contains("/Fields []"))
    }

    @Test
    fun `sections with no rows are skipped rather than left as empty headings`() {
        val doc = document().copy(
            sections = listOf(
                PackingListPdf.Section("Prazna", emptyList()),
                PackingListPdf.Section("Puna", listOf(PackingListPdf.Row("item.1", "Pasoš"))),
            ),
        )
        val pdf = render(doc)
        assertEquals(1, Regex("/Subtype /Widget").findAll(pdf).count())
    }

    @Test
    fun `the phone layout is a narrow page with a checkbox a finger can hit`() {
        val a4 = render(document())
        val phone = render(document(), PageLayout.PHONE)

        assertTrue("A4 mora ostati A4", a4.contains("/MediaBox [0 0 595.28 841.89]"))
        assertTrue("strana za telefon mora biti uska", phone.contains("/MediaBox [0 0 300.0 540.0]"))

        assertEquals("kutija na A4", 12.0, boxSide(a4), 0.001)
        assertEquals("kutija na telefonu", 15.0, boxSide(phone), 0.001)

        // Ako se /BBox izgleda ne poveca zajedno sa /Rect, kvacica ostaje nacrtana za
        // kutiju od 12 pt i sedi u donjem levom uglu vece kutije.
        assertTrue("/BBox mora da prati kutiju", a4.contains("/BBox [0 0 12.0 12.0]"))
        assertTrue("/BBox mora da prati kutiju", phone.contains("/BBox [0 0 15.0 15.0]"))
    }

    @Test
    fun `a phone page holds fewer rows, so the same list runs longer`() {
        val doc = document(rowCount = 10, sectionCount = 2)
        val a4Pages = pages(render(doc))
        val phonePages = pages(render(doc, PageLayout.PHONE))

        assertEquals("kontrola: ova lista staje na jednu A4 stranu", 1, a4Pages)
        assertTrue("na uskoj strani mora da se prelomi, dobijeno $phonePages", phonePages > a4Pages)
    }

    private fun pages(pdf: String) = Regex("/Type /Page[^s]").findAll(pdf).count()

    /** Stranica kutije iz prvog `/Rect` — vidžet je kvadrat, pa je dovoljna širina. */
    private fun boxSide(pdf: String): Double {
        val rect = Regex("""/Rect \[([\d.]+) ([\d.]+) ([\d.]+) ([\d.]+)\]""").find(pdf)!!
        return rect.groupValues[3].toDouble() - rect.groupValues[1].toDouble()
    }
}

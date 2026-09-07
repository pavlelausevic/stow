// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

/**
 * Ugrađuje TrueType font u PDF kao `CIDFontType2` sa `Identity-H` kodiranjem.
 *
 * Obavezno je, a ne stvar ukusa: `WinAnsi` i ostala standardna kodiranja nemaju
 * `č ć š ž đ`. Sa `Identity-H` u toku sadržaja stoje identifikatori glifova, pa pitanje
 * kodiranja nestaje — ako font ima glif, tekst se vidi.
 *
 * Font se ugrađuje ceo, bez podskupa. Instanca Rubik-a je oko 210 kB, što je za dokument
 * koji se šalje jednoj osobi sasvim prihvatljivo, a podskup bi tražio prepisivanje `loca`,
 * `glyf` i `hmtx` tabela — puno koda i puno načina da se PDF tiho pokvari.
 */
class PdfFont(
    val font: TrueTypeFont,
    /** Ime resursa u sadržaju strane: `/F1`, `/F2`. */
    val resourceName: String,
) {

    /** Širina teksta u tačkama pri datoj veličini slova. */
    fun width(text: String, size: Double): Double = font.stringWidth(text) * size / 1000.0

    /**
     * Skraćuje tekst na zadatu širinu i dodaje tri tačke.
     *
     * Odsečen tekst je greška, ali odsečen sa naznakom je bar iskren: red u PDF-u ne može
     * da se skroluje, pa naziv koji ne staje mora negde da se prekine.
     */
    fun ellipsize(text: String, size: Double, maxWidth: Double): String {
        if (width(text, size) <= maxWidth) return text
        val ellipsis = "…"
        val room = maxWidth - width(ellipsis, size)
        if (room <= 0) return ellipsis
        var end = text.length
        while (end > 0 && width(text.substring(0, end), size) > room) end--
        return text.substring(0, end).trimEnd() + ellipsis
    }

    /** Upisuje sve objekte fonta i vraća broj objekta na koji `/Font` treba da pokaže. */
    fun write(writer: PdfWriter, usedGlyphs: Set<Int>): Int {
        val fontFileId = writer.reserve()
        val descriptorId = writer.reserve()
        val descendantId = writer.reserve()
        val toUnicodeId = writer.reserve()
        val type0Id = writer.reserve()

        writer.writeStream(
            fontFileId,
            "/Length1 ${font.data.size}",
            font.data,
        )

        val name = font.postScriptName
        // Flags bit 3 (vrednost 4) = simbolicki font. Postavlja se kad se ne tvrdi
        // standardno kodiranje, sto je kod Identity-H uvek slucaj.
        writer.writeObject(
            descriptorId,
            """
            << /Type /FontDescriptor /FontName /$name /Flags 4
               /FontBBox [${font.xMin.scaled()} ${font.yMin.scaled()} ${font.xMax.scaled()} ${font.yMax.scaled()}]
               /ItalicAngle ${font.italicAngle}
               /Ascent ${font.ascender.scaled()} /Descent ${font.descender.scaled()}
               /CapHeight ${font.capHeight.scaled()} /StemV 80
               /FontFile2 $fontFileId 0 R >>
            """.trimIndent(),
        )

        writer.writeObject(
            descendantId,
            """
            << /Type /Font /Subtype /CIDFontType2 /BaseFont /$name
               /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>
               /FontDescriptor $descriptorId 0 R /DW 1000
               /W [${widthArray(usedGlyphs)}]
               /CIDToGIDMap /Identity >>
            """.trimIndent(),
        )

        // ToUnicode nije potreban da bi se tekst VIDEO, ali jeste da bi mogao da se
        // pretrazi i kopira iz citaca. Bez njega je izvezena lista slika teksta.
        writer.writeStream(
            toUnicodeId,
            "",
            toUnicodeCMap(usedGlyphs).toByteArray(Charsets.ISO_8859_1),
        )

        writer.writeObject(
            type0Id,
            """
            << /Type /Font /Subtype /Type0 /BaseFont /$name /Encoding /Identity-H
               /DescendantFonts [$descendantId 0 R] /ToUnicode $toUnicodeId 0 R >>
            """.trimIndent(),
        )

        return type0Id
    }

    private fun Int.scaled(): Int = this * 1000 / font.unitsPerEm

    private fun widthArray(usedGlyphs: Set<Int>): String =
        usedGlyphs.sorted().joinToString(" ") { gid -> "$gid [${font.advanceWidth(gid)}]" }

    /**
     * Obrnuto preslikavanje GID → Unicode, da čitač zna šta je koji glif bio.
     *
     * Gradi se iz istog `cmap`-a kojim se i pisalo: za svaki korišćeni GID traži se prva
     * kodna tačka koja na njega vodi.
     */
    private fun toUnicodeCMap(usedGlyphs: Set<Int>): String {
        val reverse = LinkedHashMap<Int, Int>()
        for (codePoint in 0x20..0x2FFF) {
            val gid = font.glyphId(codePoint)
            if (gid != 0 && gid in usedGlyphs && gid !in reverse) reverse[gid] = codePoint
        }

        val entries = reverse.entries.toList()
        return buildString {
            append(
                """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                /CMapName /Adobe-Identity-UCS def
                /CMapType 2 def
                1 begincodespacerange
                <0000> <FFFF>
                endcodespacerange

                """.trimIndent(),
            )
            append('\n')
            // Citaci odbijaju blokove duze od 100 unosa; specifikacija to i trazi.
            entries.chunked(100).forEach { chunk ->
                append("${chunk.size} beginbfchar\n")
                chunk.forEach { (gid, codePoint) ->
                    append("<${gid.toString(16).padStart(4, '0')}> ")
                    append("<${codePoint.toString(16).padStart(4, '0')}>\n")
                }
                append("endbfchar\n")
            }
            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
    }
}

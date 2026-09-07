// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

/**
 * Onoliko TrueType parsera koliko treba da se font ugradi u PDF kao `CIDFontType2`.
 *
 * Zašto uopšte: `Identity-H` kodiranje ne piše znakove nego **identifikatore glifova**,
 * pa pisac mora da zna preslikavanje Unicode → GID (`cmap`) i širinu svakog glifa
 * (`hmtx`). Bez toga se tekst ili ne vidi ili ispadne razmaknut nasumično.
 *
 * A `Identity-H` je obavezan jer `WinAnsi` nema `č ć š ž đ`. Nije stvar ukusa: latinica
 * sa kvačicama u standardnom PDF kodiranju jednostavno ne postoji.
 */
class TrueTypeFont(val data: ByteArray) {

    private val tables = mutableMapOf<String, Table>()

    data class Table(val offset: Int, val length: Int)

    val unitsPerEm: Int
    val numGlyphs: Int
    val xMin: Int
    val yMin: Int
    val xMax: Int
    val yMax: Int
    val ascender: Int
    val descender: Int
    val capHeight: Int
    val italicAngle: Double
    val postScriptName: String

    private val advanceWidths: IntArray
    private val cmap: Map<Int, Int>

    init {
        require(data.size > 12) { "not a font" }
        val numTables = u16(4)
        var p = 12
        repeat(numTables) {
            val tag = String(data, p, 4, Charsets.US_ASCII)
            tables[tag] = Table(i32(p + 8), i32(p + 12))
            p += 16
        }
        require(tables.containsKey("head")) { "no head table" }
        require(tables.containsKey("cmap")) { "no cmap table" }

        val head = tables.getValue("head").offset
        unitsPerEm = u16(head + 18)
        xMin = s16(head + 36)
        yMin = s16(head + 38)
        xMax = s16(head + 40)
        yMax = s16(head + 42)

        numGlyphs = u16(tables.getValue("maxp").offset + 4)

        val hhea = tables.getValue("hhea").offset
        val numberOfHMetrics = u16(hhea + 34)
        advanceWidths = readAdvanceWidths(numberOfHMetrics)

        val os2 = tables["OS/2"]?.offset
        ascender = os2?.let { s16(it + 68) }?.takeIf { it != 0 } ?: s16(hhea + 4)
        descender = os2?.let { s16(it + 70) }?.takeIf { it != 0 } ?: s16(hhea + 6)
        capHeight = os2?.takeIf { tables.getValue("OS/2").length >= 90 }?.let { s16(it + 88) }
            ?.takeIf { it != 0 } ?: (ascender * 7 / 10)

        italicAngle = tables["post"]?.let { fixed(it.offset + 4) } ?: 0.0
        postScriptName = readPostScriptName()

        cmap = readCmap()
    }

    /** GID za dati znak, ili GID 0 (.notdef) ako ga font nema. */
    fun glyphId(codePoint: Int): Int = cmap[codePoint] ?: 0

    /** Širina glifa u hiljaditim delovima ema — jedinica u kojoj PDF računa tekst. */
    fun advanceWidth(glyphId: Int): Int {
        if (glyphId < 0 || advanceWidths.isEmpty()) return 0
        val raw = advanceWidths[glyphId.coerceAtMost(advanceWidths.size - 1)]
        return raw * 1000 / unitsPerEm
    }

    /** Širina niske u hiljaditim delovima ema; množi se veličinom slova i deli sa 1000. */
    fun stringWidth(text: String): Int {
        var total = 0
        forEachGlyph(text) { gid -> total += advanceWidth(gid) }
        return total
    }

    /**
     * Prolazi kroz tekst po kodnim tačkama, ne po `Char`-ovima.
     *
     * `čćšžđ` staju u jedan `Char`, ali surogatni parovi ne — a kad bi se čitalo po
     * `Char`-u, emodži u nazivu stavke bi se raspao na dva besmislena glifa.
     */
    inline fun forEachGlyph(text: String, action: (Int) -> Unit) {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            action(glyphId(codePoint))
            i += Character.charCount(codePoint)
        }
    }

    /** Svi GID-ovi koje tekst koristi — ulaz u `W` niz širina. */
    fun glyphsUsed(texts: List<String>): Set<Int> {
        val used = sortedSetOf(0)
        texts.forEach { text -> forEachGlyph(text) { used += it } }
        return used
    }

    private fun readAdvanceWidths(numberOfHMetrics: Int): IntArray {
        val hmtx = tables["hmtx"] ?: return IntArray(0)
        val widths = IntArray(numGlyphs)
        var last = 0
        for (gid in 0 until numGlyphs) {
            if (gid < numberOfHMetrics) {
                last = u16(hmtx.offset + gid * 4)
            }
            widths[gid] = last
        }
        return widths
    }

    private fun readPostScriptName(): String {
        val name = tables["name"] ?: return "Embedded"
        val count = u16(name.offset + 2)
        val stringOffset = name.offset + u16(name.offset + 4)
        for (i in 0 until count) {
            val record = name.offset + 6 + i * 12
            if (u16(record + 6) == 6) { // nameID 6 = PostScript name
                val length = u16(record + 8)
                val offset = stringOffset + u16(record + 10)
                if (offset + length > data.size) continue
                val platform = u16(record)
                val raw = data.copyOfRange(offset, offset + length)
                val text = if (platform == 3) String(raw, Charsets.UTF_16BE) else String(raw, Charsets.US_ASCII)
                val cleaned = text.filter { it.isLetterOrDigit() || it == '-' || it == '+' }
                if (cleaned.isNotEmpty()) return cleaned
            }
        }
        return "Embedded"
    }

    /**
     * Traži Unicode podtabelu, format 4 ili 12.
     *
     * Redosled je namerno (3,10) pa (3,1) pa (0,x): format 12 pokriva i van BMP-a, a
     * format 4 je ono što skoro svaki latinični font zaista nosi.
     */
    private fun readCmap(): Map<Int, Int> {
        val cmapTable = tables.getValue("cmap").offset
        val numSubtables = u16(cmapTable + 2)
        var best = -1
        var bestScore = -1
        for (i in 0 until numSubtables) {
            val record = cmapTable + 4 + i * 8
            val platform = u16(record)
            val encoding = u16(record + 2)
            val offset = cmapTable + i32(record + 4)
            val score = when {
                platform == 3 && encoding == 10 -> 4
                platform == 3 && encoding == 1 -> 3
                platform == 0 -> 2
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
        }
        if (best < 0) return emptyMap()
        return when (u16(best)) {
            4 -> readCmapFormat4(best)
            12 -> readCmapFormat12(best)
            else -> emptyMap()
        }
    }

    private fun readCmapFormat4(offset: Int): Map<Int, Int> {
        val segCountX2 = u16(offset + 6)
        val segCount = segCountX2 / 2
        val endCodes = offset + 14
        val startCodes = endCodes + segCountX2 + 2
        val idDeltas = startCodes + segCountX2
        val idRangeOffsets = idDeltas + segCountX2

        val map = HashMap<Int, Int>(segCount * 8)
        for (segment in 0 until segCount) {
            val end = u16(endCodes + segment * 2)
            val start = u16(startCodes + segment * 2)
            if (start > end) continue
            val delta = s16(idDeltas + segment * 2)
            val rangeOffsetPos = idRangeOffsets + segment * 2
            val rangeOffset = u16(rangeOffsetPos)
            for (code in start..end) {
                if (code == 0xFFFF) continue
                val gid = if (rangeOffset == 0) {
                    (code + delta) and 0xFFFF
                } else {
                    val glyphPos = rangeOffsetPos + rangeOffset + (code - start) * 2
                    if (glyphPos + 1 >= data.size) continue
                    val raw = u16(glyphPos)
                    if (raw == 0) 0 else (raw + delta) and 0xFFFF
                }
                if (gid != 0) map[code] = gid
            }
        }
        return map
    }

    private fun readCmapFormat12(offset: Int): Map<Int, Int> {
        val numGroups = i32(offset + 12)
        val map = HashMap<Int, Int>(numGroups * 4)
        for (group in 0 until numGroups) {
            val record = offset + 16 + group * 12
            val start = i32(record)
            val end = i32(record + 4)
            val startGid = i32(record + 8)
            if (end - start > MAX_GROUP_SPAN) continue
            for (code in start..end) {
                map[code] = startGid + (code - start)
            }
        }
        return map
    }

    private fun u16(at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun s16(at: Int): Int = u16(at).let { if (it > 0x7FFF) it - 0x10000 else it }

    private fun i32(at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 24) or
            ((data[at + 1].toInt() and 0xFF) shl 16) or
            ((data[at + 2].toInt() and 0xFF) shl 8) or
            (data[at + 3].toInt() and 0xFF)

    private fun fixed(at: Int): Double = i32(at) / 65536.0

    private companion object {
        /** Zaštita od pokvarene tabele koja bi tvrdila raspon od milijardu znakova. */
        const val MAX_GROUP_SPAN = 0x10000
    }
}

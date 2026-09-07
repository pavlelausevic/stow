// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

import java.io.ByteArrayOutputStream

/**
 * Niski sloj: numerisani objekti, xref tabela, trailer.
 *
 * PDF je, u svojoj osnovi, spisak numerisanih objekata i tabela njihovih pozicija u
 * bajtovima. Zato ovaj pisac ne pravi stablo pa ga serijalizuje, nego upisuje objekat po
 * objekat i pamti gde je koji počeo — xref na kraju je doslovno taj spisak pozicija.
 */
class PdfWriter {

    private val out = ByteArrayOutputStream()
    private val offsets = mutableListOf<Int>()
    private var nextId = 1

    init {
        // PDF 1.7. Druga linija su namerno bajtovi > 127: tako alati koji pogađaju tip
        // fajla vide binarni sadržaj i ne pokušaju da ga prepišu kao tekst.
        writeRaw("%PDF-1.7\n")
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
    }

    /** Rezerviše broj objekta pre nego što se zna njegov sadržaj — za međusobne veze. */
    fun reserve(): Int = nextId++

    fun writeObject(id: Int, body: String) {
        recordOffset(id)
        writeRaw("$id 0 obj\n")
        writeRaw(body)
        writeRaw("\nendobj\n")
    }

    /** Objekat sa binarnim tokom: rečnik, pa `stream`, pa sirovi bajtovi. */
    fun writeStream(id: Int, dictionaryEntries: String, bytes: ByteArray) {
        recordOffset(id)
        writeRaw("$id 0 obj\n<< $dictionaryEntries /Length ${bytes.size} >>\nstream\n")
        out.write(bytes)
        writeRaw("\nendstream\nendobj\n")
    }

    fun finish(catalogId: Int, infoId: Int?): ByteArray {
        val xrefOffset = out.size()
        val count = nextId
        writeRaw("xref\n0 $count\n")
        writeRaw("0000000000 65535 f \n")
        for (id in 1 until count) {
            val offset = offsets.getOrElse(id - 1) { 0 }
            writeRaw(offset.toString().padStart(10, '0') + " 00000 n \n")
        }
        writeRaw("trailer\n<< /Size $count /Root $catalogId 0 R")
        if (infoId != null) writeRaw(" /Info $infoId 0 R")
        writeRaw(" >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun recordOffset(id: Int) {
        while (offsets.size < id) offsets.add(0)
        offsets[id - 1] = out.size()
    }

    private fun writeRaw(text: String) {
        // Latin-1, ne UTF-8: sintaksa PDF-a je bajtovska, a svaki tekst koji korisnik vidi
        // ionako izlazi kao heks niska glifova, ne kao znakovi u ovom toku.
        out.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    companion object {

        /** Niska u PDF rečniku — zagrade i obrnuta kosa crta se moraju izbeći. */
        fun literal(text: String): String = buildString {
            append('(')
            text.forEach { char ->
                when (char) {
                    '(', ')', '\\' -> append('\\').append(char)
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> if (char.code in 32..126) append(char) else append(' ')
                }
            }
            append(')')
        }

        /**
         * Tekst kao heks niska identifikatora glifova.
         *
         * Ovo je ono što `Identity-H` zaista znači: u toku sadržaja ne stoje slova nego
         * dvobajtni GID-ovi. Zato `č` radi — nije mu potreban prostor u nekom kodiranju,
         * samo glif u fontu.
         */
        fun glyphHex(font: TrueTypeFont, text: String): String = buildString {
            append('<')
            font.forEachGlyph(text) { gid ->
                append(gid.toString(16).padStart(4, '0'))
            }
            append('>')
        }
    }
}

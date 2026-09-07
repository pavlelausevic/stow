// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.lausevic.stow.domain.TextMatching

class TextMatchingTest {

    @Test
    fun `normalisation strips serbian diacritics`() {
        assertEquals("punjac za telefon", TextMatching.normalize("Punjač za telefon"))
        assertEquals("cetkica za zube", TextMatching.normalize("Četkica za zube"))
        assertEquals("dzezva", TextMatching.normalize("Džezva"))
        // đ je samostalno slovo, ne slovo sa znakom — Unicode dekompozicija ga ne rastavlja,
        // pa se resava posebno. Bez toga bi ostalo neizmenjeno i poklapanje bi propalo.
        assertEquals("deram", TextMatching.normalize("Đeram"))
        assertEquals("sanje", TextMatching.normalize("Šanje"))
    }

    @Test
    fun `normalisation collapses punctuation and spacing`() {
        assertEquals("usb c c kabl", TextMatching.normalize("USB  C-C   kabl"))
        assertEquals("so", TextMatching.normalize("  So.  "))
    }

    @Test
    fun `typing without diacritics still finds the entry`() {
        val existing = TextMatching.normalize("Punjač za telefon")
        assertTrue(TextMatching.isMatch(TextMatching.normalize("punjac za telefon"), existing))
    }

    @Test
    fun `word order does not defeat the match`() {
        val existing = TextMatching.normalize("Punjač za telefon")
        assertTrue(TextMatching.isMatch(TextMatching.normalize("telefon punjač za"), existing))
    }

    @Test
    fun `a single typo still matches`() {
        val existing = TextMatching.normalize("Dezodorans")
        assertTrue(TextMatching.isMatch(TextMatching.normalize("Dezodoras"), existing))
    }

    @Test
    fun `genuinely different things do not match`() {
        val charger = TextMatching.normalize("Punjač za telefon")
        assertFalse(TextMatching.isMatch(TextMatching.normalize("Pasoš"), charger))
        assertFalse(TextMatching.isMatch(TextMatching.normalize("Krema za sunce"), charger))
        assertFalse(TextMatching.isMatch(TextMatching.normalize("Adapter za struju"), charger))
    }

    @Test
    fun `suggestions come back closest first and are capped at three`() {
        val existing = listOf(
            1L to TextMatching.normalize("Punjač za telefon"),
            2L to TextMatching.normalize("Punjač telefona"),
            3L to TextMatching.normalize("Punjač za laptop"),
            4L to TextMatching.normalize("Punjač"),
            5L to TextMatching.normalize("Pasoš"),
        )
        val suggestions = TextMatching.suggestions("punjac za telefon", existing)
        assertEquals(1L, suggestions.first())
        assertTrue("pasoš nije sličan punjaču", 5L !in suggestions)
        assertTrue("najviše tri predloga", suggestions.size <= TextMatching.MAX_SUGGESTIONS)
    }

    @Test
    fun `an empty name suggests nothing`() {
        assertTrue(TextMatching.suggestions("   ", listOf(1L to "pasos")).isEmpty())
    }

    @Test
    fun `levenshtein is symmetric and zero for equal strings`() {
        assertEquals(0, TextMatching.levenshtein("carape", "carape"))
        assertEquals(
            TextMatching.levenshtein("carape", "capare"),
            TextMatching.levenshtein("capare", "carape"),
        )
        assertEquals(6, TextMatching.levenshtein("", "carape"))
    }
}

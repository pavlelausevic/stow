// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Poklapanje sličnih naziva pri dodavanju u katalog.
 *
 * Bez ovoga katalog za godinu dana ima "Punjač za telefon", "punjac telefona" i
 * "Telefonski punjač" kao tri stavke, pa `timesUsed` ni na jednoj ne znači ništa.
 *
 * Postupak je namerno određen do detalja, jer se testira:
 *   1. normalizacija — mala slova, skidanje dijakritike, sažimanje razmaka i interpunkcije
 *   2. poklapanje ako je Levenštajn sličnost >= 0.82 ILI preklapanje tokena >= 0.6
 *   3. najviše tri predloga, poređana po sličnosti
 *
 * Skidanje dijakritike je ono što spaja "punjač" i "punjac". Bez toga polovina
 * duplikata prođe nezapaženo, jer ljudi kucaju bez kvačica kad im se žuri.
 */
object TextMatching {

    const val LEVENSHTEIN_THRESHOLD = 0.82
    const val TOKEN_OVERLAP_THRESHOLD = 0.6
    const val MAX_SUGGESTIONS = 3

    private val nonAlphanumeric = Regex("[^\\p{IsAlphabetic}\\p{IsDigit}]+")

    /**
     * Naziv sveden na oblik po kome se poredi.
     *
     * `đ` i `Đ` se rešavaju posebno: to su samostalna slova, ne slovo sa znakom, pa ih
     * Unicode dekompozicija ne rastavlja i `\p{Mn}` ih ne dohvata.
     */
    fun normalize(raw: String): String {
        val lowered = raw.lowercase(Locale.ROOT).replace('đ', 'd')
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(nonAlphanumeric, " ").trim()
    }

    fun tokens(normalized: String): Set<String> =
        normalized.split(' ').filter { it.isNotBlank() }.toSet()

    /** Levenštajnovo rastojanje, dva reda umesto pune matrice. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** 1.0 za identične nizove, 0.0 za potpuno različite. */
    fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    /** Žakarov koeficijent nad tokenima — hvata "punjač telefona" i "telefonski punjač". */
    fun tokenOverlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val union = (a + b).size
        if (union == 0) return 0.0
        return a.intersect(b).size.toDouble() / union
    }

    fun isMatch(candidateNormalized: String, existingNormalized: String): Boolean {
        if (candidateNormalized == existingNormalized) return true
        if (similarity(candidateNormalized, existingNormalized) >= LEVENSHTEIN_THRESHOLD) return true
        return tokenOverlap(tokens(candidateNormalized), tokens(existingNormalized)) >=
            TOKEN_OVERLAP_THRESHOLD
    }

    /** Rezultat sličnosti kandidata prema jednoj postojećoj stavci; veće je bliže. */
    fun score(candidateNormalized: String, existingNormalized: String): Double = maxOf(
        similarity(candidateNormalized, existingNormalized),
        tokenOverlap(tokens(candidateNormalized), tokens(existingNormalized)),
    )

    /**
     * Do tri postojeća naziva koja liče na kandidata, od najbližeg.
     *
     * @param existing parovi (id, normalizovan naziv)
     */
    fun suggestions(candidate: String, existing: List<Pair<Long, String>>): List<Long> {
        val normalized = normalize(candidate)
        if (normalized.isEmpty()) return emptyList()
        return existing
            .filter { (_, other) -> isMatch(normalized, other) }
            .sortedByDescending { (_, other) -> score(normalized, other) }
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }
}

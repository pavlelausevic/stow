// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.model

/**
 * Koliko komada nečega ide na putovanje.
 *
 * Pravilo se razrešava u broj **jednom**, pri kreiranju putovanja, i zamrzava u snimak.
 * Kasnija promena kataloga ne dira postojeće putovanje — to je cela poenta troslojnog
 * modela (katalog / šablon / snimak).
 */
sealed interface QuantityRule {

    data class Fixed(val count: Int) : QuantityRule

    /**
     * `perNights` = na koliko noći ide jedan komad; `plus` = rezerva preko toga;
     * `cap` = gornja granica, jer za četrnaest noći ne pakuješ petnaest majica.
     */
    data class PerNights(val perNights: Int, val plus: Int = 0, val cap: Int? = null) : QuantityRule

    /** So, biber, kanap. Broj nema smisla. */
    data object Unspecified : QuantityRule

    companion object {
        /**
         * Broj komada za dato putovanje, ili `null` kada broj nema smisla —
         * bilo zato što je pravilo `Unspecified`, bilo zato što putovanje nema datume
         * pa se `PerNights` nema o šta osloniti.
         */
        fun QuantityRule.resolve(nights: Int?): Int? = when (this) {
            is Fixed -> count.coerceAtLeast(1)
            is Unspecified -> null
            is PerNights -> nights?.let { n ->
                val perNightsSafe = perNights.coerceAtLeast(1)
                val raw = Math.ceil(n.toDouble() / perNightsSafe).toInt() + plus
                val capped = cap?.let { minOf(raw, it) } ?: raw
                capped.coerceAtLeast(1)
            }
        }
    }
}

/**
 * Ravna forma pravila, onakva kakva stoji u bazi.
 *
 * Zasebne kolone, ne JSON blob: skup je zatvoren i sićušan, Room migracije preko
 * JSON stringa su bolne, a ovako je moguć i upit "sve stavke sa pravilom po noći".
 */
data class QuantityRuleColumns(
    val type: RuleType,
    val per: Int?,
    val plus: Int?,
    val cap: Int?,
) {
    fun toRule(): QuantityRule = when (type) {
        RuleType.FIXED -> QuantityRule.Fixed(per ?: 1)
        RuleType.PER_NIGHTS -> QuantityRule.PerNights(per ?: 1, plus ?: 0, cap)
        RuleType.UNSPECIFIED -> QuantityRule.Unspecified
    }

    companion object {
        fun from(rule: QuantityRule): QuantityRuleColumns = when (rule) {
            is QuantityRule.Fixed -> QuantityRuleColumns(RuleType.FIXED, rule.count, null, null)
            is QuantityRule.PerNights ->
                QuantityRuleColumns(RuleType.PER_NIGHTS, rule.perNights, rule.plus, rule.cap)
            is QuantityRule.Unspecified -> QuantityRuleColumns(RuleType.UNSPECIFIED, null, null, null)
        }
    }
}

enum class RuleType { FIXED, PER_NIGHTS, UNSPECIFIED }

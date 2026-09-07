// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.domain

import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.TripActivity

/**
 * Determinističko sastavljanje liste. Bez modela, bez verovatnoća.
 *
 * AI konkurencija ovo radi verovatnosno i greši na način koji korisnik ne može ni da
 * pregleda ni da ispravi. Eksplicitna pravila su ovde cela prednost, pa se prikazuju
 * PRE generisanja i uz svaku stavku stoji koje ju je pravilo dovelo.
 *
 * Pravila su i razlog zašto šablona ima dva, a ne četiri: "Dugo putovanje" i "Ultra dugo"
 * iz prvobitnog zahteva bili su strogi nadskupovi "Apartmana", pa se dužina putovanja
 * ovde rešava kao pravilo umesto da se seed sadržaj četvorostruko duplira.
 */
object WizardRules {

    /** ID pravila je mašinski i stabilan; rečenica uz njega dolazi iz resursa. */
    data class Rule(
        val id: String,
        /** Ključevi sekcija iz seed-a koje pravilo dodaje. */
        val sections: List<String> = emptyList(),
        /** Ključevi pojedinačnih stavki kataloga koje pravilo dodaje. */
        val items: List<String> = emptyList(),
        /** Izmene pravila količine, po ključu stavke. */
        val quantityOverrides: Map<String, QuantityRule> = emptyMap(),
    )

    data class Answers(
        val accommodation: Accommodation,
        val nights: Int?,
        val activities: Set<TripActivity>,
    )

    const val LONG_TRIP_NIGHTS = 5
    const val VERY_LONG_TRIP_NIGHTS = 14

    /**
     * Pravila koja se primenjuju na dati skup odgovora, redom kojim se primenjuju.
     * Ista funkcija hrani i pregled u čarobnjaku i samo generisanje — pregled ne sme
     * da bude druga implementacija istog, inače laže.
     */
    fun rulesFor(answers: Answers): List<Rule> = buildList {
        add(Rule(id = RULE_CORE, sections = CORE_SECTIONS))

        when (answers.accommodation) {
            Accommodation.HOTEL -> add(Rule(RULE_HOTEL, sections = listOf("hotel_extra")))
            Accommodation.APARTMENT -> add(
                Rule(RULE_APARTMENT, sections = listOf("kitchen", "upkeep")),
            )
            Accommodation.CAMPING -> add(Rule(RULE_CAMPING, sections = listOf("kitchen")))
            Accommodation.FAMILY -> Unit
        }

        val nights = answers.nights
        if (nights != null && nights >= LONG_TRIP_NIGHTS) {
            add(Rule(RULE_NIGHTS_GTE5, sections = listOf("rotation")))
        }
        if (nights != null && nights >= VERY_LONG_TRIP_NIGHTS) {
            add(
                Rule(
                    id = RULE_NIGHTS_GTE14,
                    sections = listOf("laundry", "electronics_long", "health_long", "logistics"),
                    // Preko dve nedelje se pere, a ne pakuje. Bez ovoga bi pravilo po noći
                    // tražilo petnaest pari čarapa, što niko ne nosi.
                    quantityOverrides = mapOf(
                        "underwear" to QuantityRule.Fixed(10),
                        "socks" to QuantityRule.Fixed(10),
                    ),
                ),
            )
        }

        answers.activities.forEach { activity ->
            when (activity) {
                TripActivity.BEACH -> add(
                    Rule(RULE_BEACH, items = listOf("swimsuit", "beach_towel", "flip_flops", "sunscreen")),
                )
                TripActivity.HIKING -> add(
                    Rule(RULE_HIKING, items = listOf("hiking_boots", "backpack_15l", "water_bottle", "blister_plasters")),
                )
                TripActivity.BUSINESS -> add(
                    Rule(RULE_BUSINESS, items = listOf("laptop", "laptop_charger", "utp_cable", "nicer_shoes")),
                )
                TripActivity.DRIVING -> add(
                    Rule(RULE_DRIVING, items = listOf("driving_licence", "car_charger", "sunglasses")),
                )
                TripActivity.GYM -> add(
                    Rule(RULE_GYM, items = listOf("trainers", "gym_shorts", "gym_towel")),
                )
                TripActivity.FORMAL -> add(
                    Rule(RULE_FORMAL, items = listOf("nicer_shoes", "trousers", "shirt")),
                )
            }
        }
    }

    /** Sve izmene količine iz primenjenih pravila, spljoštene; kasnije pravilo pobeđuje. */
    fun quantityOverrides(rules: List<Rule>): Map<String, QuantityRule> =
        rules.fold(mutableMapOf()) { acc, rule -> acc.apply { putAll(rule.quantityOverrides) } }

    const val RULE_CORE = "core"
    const val RULE_HOTEL = "accommodation.hotel"
    const val RULE_APARTMENT = "accommodation.apartment"
    const val RULE_CAMPING = "accommodation.camping"
    const val RULE_NIGHTS_GTE5 = "nights.gte5.rotation"
    const val RULE_NIGHTS_GTE14 = "nights.gte14.laundry"
    const val RULE_BEACH = "activity.beach"
    const val RULE_HIKING = "activity.hiking"
    const val RULE_BUSINESS = "activity.business"
    const val RULE_DRIVING = "activity.driving"
    const val RULE_GYM = "activity.gym"
    const val RULE_FORMAL = "activity.formal"

    private val CORE_SECTIONS = listOf(
        "documents", "electronics", "toiletries", "pharmacy",
        "clothing", "footwear", "small_things", "before_leaving", "collect_before_leaving",
    )
}

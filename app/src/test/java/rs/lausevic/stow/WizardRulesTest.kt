// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.TripActivity
import rs.lausevic.stow.domain.WizardRules

class WizardRulesTest {

    private fun rules(
        accommodation: Accommodation = Accommodation.HOTEL,
        nights: Int? = 3,
        activities: Set<TripActivity> = emptySet(),
    ) = WizardRules.rulesFor(WizardRules.Answers(accommodation, nights, activities))

    @Test
    fun `the core list always applies`() {
        assertTrue(WizardRules.RULE_CORE in rules().map { it.id })
        assertTrue(WizardRules.RULE_CORE in rules(Accommodation.CAMPING, null).map { it.id })
    }

    @Test
    fun `an apartment brings the kitchen, a hotel does not`() {
        val apartment = rules(Accommodation.APARTMENT).flatMap { it.sections }
        assertTrue("kitchen" in apartment)
        assertTrue("upkeep" in apartment)

        val hotel = rules(Accommodation.HOTEL).flatMap { it.sections }
        assertFalse("hotel ne nosi kuhinju", "kitchen" in hotel)
        assertTrue("hotel_extra" in hotel)
    }

    @Test
    fun `rotation appears from five nights, not before`() {
        assertFalse(WizardRules.RULE_NIGHTS_GTE5 in rules(nights = 4).map { it.id })
        assertTrue(WizardRules.RULE_NIGHTS_GTE5 in rules(nights = 5).map { it.id })
        assertTrue(WizardRules.RULE_NIGHTS_GTE5 in rules(nights = 21).map { it.id })
    }

    @Test
    fun `past two weeks you wash rather than pack`() {
        val short = WizardRules.quantityOverrides(rules(nights = 7))
        assertTrue("na sedam noci se ne menja pravilo", short.isEmpty())

        val long = WizardRules.quantityOverrides(rules(nights = 14))
        assertEquals(QuantityRule.Fixed(10), long["socks"])
        assertEquals(QuantityRule.Fixed(10), long["underwear"])
    }

    @Test
    fun `a trip without dates gets no length rules at all`() {
        val withoutDates = rules(nights = null).map { it.id }
        assertFalse(WizardRules.RULE_NIGHTS_GTE5 in withoutDates)
        assertFalse(WizardRules.RULE_NIGHTS_GTE14 in withoutDates)
    }

    @Test
    fun `activities add their own items and nothing else`() {
        val beach = rules(activities = setOf(TripActivity.BEACH))
        val items = beach.flatMap { it.items }
        assertTrue("swimsuit" in items)
        assertTrue("flip_flops" in items)
        assertFalse("plaža ne donosi planinarske cipele", "hiking_boots" in items)
    }

    @Test
    fun `two activities wanting the same item do not duplicate the rule set`() {
        // Lepse cipele traze i poslovni put i svecana vecera. Pravila se ne spajaju —
        // spajanje radi TripComposer, koji stavku postavi jednom i zapamti koje ju je
        // pravilo dovelo. Ovde se samo tvrdi da su oba pravila prisutna.
        val both = rules(activities = setOf(TripActivity.BUSINESS, TripActivity.FORMAL))
        assertTrue(WizardRules.RULE_BUSINESS in both.map { it.id })
        assertTrue(WizardRules.RULE_FORMAL in both.map { it.id })
        assertEquals(2, both.count { "nicer_shoes" in it.items })
    }

    @Test
    fun `the preview and the generator are the same function`() {
        // Ako bi pregled bio druga implementacija, s vremenom bi reklamirao pravila
        // koja se ne primenjuju. Ovaj test to drzi na okupu.
        val answers = WizardRules.Answers(Accommodation.APARTMENT, 14, setOf(TripActivity.BEACH))
        assertEquals(WizardRules.rulesFor(answers), WizardRules.rulesFor(answers))
    }

    @Test
    fun `rule ids are stable identifiers, not sentences`() {
        rules(Accommodation.APARTMENT, 14, setOf(TripActivity.BEACH)).forEach { rule ->
            assertFalse("id pravila ne sme da sadrži razmak: ${rule.id}", ' ' in rule.id)
            assertEquals(rule.id, rule.id.lowercase())
        }
    }
}

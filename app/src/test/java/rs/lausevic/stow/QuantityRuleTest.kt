// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.QuantityRule.Companion.resolve
import rs.lausevic.stow.data.model.QuantityRuleColumns

class QuantityRuleTest {

    @Test
    fun `fixed ignores trip length`() {
        val passport = QuantityRule.Fixed(1)
        assertEquals(1, passport.resolve(nights = 1))
        assertEquals(1, passport.resolve(nights = 30))
        assertEquals(1, passport.resolve(nights = null))
    }

    @Test
    fun `socks are one per night plus a spare`() {
        val socks = QuantityRule.PerNights(perNights = 1, plus = 1, cap = 10)
        assertEquals(2, socks.resolve(1))
        assertEquals(8, socks.resolve(7))
        assertEquals(10, socks.resolve(9))
    }

    @Test
    fun `the cap holds, because nobody packs fifteen pairs`() {
        val socks = QuantityRule.PerNights(perNights = 1, plus = 1, cap = 10)
        assertEquals(10, socks.resolve(14))
        assertEquals(10, socks.resolve(60))
    }

    @Test
    fun `a rule spanning several nights rounds up`() {
        // Jedne pantalone na cetiri noci: za pet noci trebaju dvoje, ne jedne.
        val trousers = QuantityRule.PerNights(perNights = 4)
        assertEquals(1, trousers.resolve(1))
        assertEquals(1, trousers.resolve(4))
        assertEquals(2, trousers.resolve(5))
        assertEquals(2, trousers.resolve(8))
        assertEquals(3, trousers.resolve(9))
    }

    @Test
    fun `without dates a per-night rule has no number, and that is the right answer`() {
        assertNull(QuantityRule.PerNights(1, plus = 1).resolve(nights = null))
        assertNull(QuantityRule.Unspecified.resolve(nights = 7))
    }

    @Test
    fun `a resolved quantity is never zero`() {
        assertEquals(1, QuantityRule.Fixed(0).resolve(7))
        assertEquals(1, QuantityRule.PerNights(perNights = 10).resolve(1))
    }

    @Test
    fun `rules survive the round trip through their columns`() {
        val rules = listOf(
            QuantityRule.Fixed(3),
            QuantityRule.PerNights(1, plus = 1, cap = 10),
            QuantityRule.PerNights(4),
            QuantityRule.Unspecified,
        )
        rules.forEach { rule ->
            assertEquals(rule, QuantityRuleColumns.from(rule).toRule())
        }
    }
}

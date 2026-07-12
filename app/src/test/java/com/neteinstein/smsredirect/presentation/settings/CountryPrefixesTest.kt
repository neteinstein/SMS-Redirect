package com.neteinstein.smsredirect.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class CountryPrefixesTest {
    @Test
    fun `allCountryPrefixes has no duplicate ISO codes`() {
        val isoCodes = allCountryPrefixes.map { it.isoCode }
        assertEquals(isoCodes.size, isoCodes.toSet().size)
    }

    @Test
    fun `allCountryPrefixes includes Portugal with dial code 351`() {
        val portugal = allCountryPrefixes.first { it.isoCode == "PT" }
        assertEquals("351", portugal.dialCode)
        assertEquals("+351", portugal.prefix)
    }

    @Test
    fun `every prefix is plus-prefixed`() {
        assertTrue(allCountryPrefixes.all { it.prefix.startsWith("+") })
    }

    @Test
    fun `countryPrefixOptions returns one option per country`() {
        val options = countryPrefixOptions(Locale.ENGLISH)
        assertEquals(allCountryPrefixes.size, options.size)
    }

    @Test
    fun `countryPrefixOptions is sorted alphabetically by localized label`() {
        val options = countryPrefixOptions(Locale.ENGLISH)
        val collator = Collator.getInstance(Locale.ENGLISH)
        val labels = options.map { it.second }
        val sortedLabels = labels.sortedWith { a, b -> collator.compare(a, b) }
        assertEquals(sortedLabels, labels)
    }

    @Test
    fun `countryPrefixOptions labels contain the localized country name and dial code`() {
        val options = countryPrefixOptions(Locale.ENGLISH)
        val portugalOption = options.first { it.first == "+351" && it.second.startsWith("Portugal") }
        assertEquals("+351", portugalOption.first)
        assertEquals("Portugal (+351)", portugalOption.second)
    }

    @Test
    fun `countryPrefixOptions adapts labels to the requested locale`() {
        val portugueseLocale = Locale.Builder().setLanguage("pt").build()
        val portuguese = countryPrefixOptions(portugueseLocale).first { it.first == "+49" }
        assertEquals("Alemanha (+49)", portuguese.second)
    }
}

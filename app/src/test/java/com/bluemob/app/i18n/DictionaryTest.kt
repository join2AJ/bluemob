package com.bluemob.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictionaryTest {
    private val hi = Dictionary.parse(File("src/main/assets/i18n/hi.tsv").readText())

    @Test fun exactPhrases() {
        assertEquals("SOS भेजें", hi.translate("Send SOS"))
        assertEquals("आस-पास", hi.translate("Nearby"))
    }

    @Test fun gapsCarryNamesAndNumbers() {
        assertEquals("उम्र 34", hi.translate("Age 34"))
        assertEquals("{} को सत्यापित करें".replace("{}", "Asha"), hi.translate("Verify Asha"))
        // Numbered gaps put words in Hindi order.
        assertEquals("लेवल 5 तक 120 XP", hi.translate("120 XP to level 5"))
    }

    @Test fun unknownTextStaysAsItIs() {
        assertEquals("Kedarkantha", hi.translate("Kedarkantha"))
        assertEquals("", hi.translate(""))
    }

    @Test fun everyLineIsWellFormed() {
        File("src/main/assets/i18n/hi.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val parts = line.split('\t')
            assertEquals("one tab: $line", 2, parts.size)
            val gaps = Regex("\\{\\}").findAll(parts[0]).count()
            val filled = Regex("\\{\\d*\\}").findAll(parts[1]).count()
            assertEquals("same gaps: $line", gaps, filled)
            assertTrue(parts[1].isNotBlank())
        }
    }
}

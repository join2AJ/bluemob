package com.bluemob.app.peaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrekRegionsTest {
    private fun first(q: String) = TrekRegions.search(q).firstOrNull()?.name

    @Test fun findsMisspellingsAndOtherNames() {
        assertEquals("Uttarakhand", first("uttrakhand"))
        assertEquals("Uttarakhand", first("Uttarakhand"))
        assertEquals("Uttarakhand", first("uttaranchal"))
        assertEquals("Hampta Pass", first("hamta"))
        assertEquals("Kedarkantha", first("kedar kantha"))
        assertEquals("Everest region, Nepal", first("ebc"))
        assertEquals("Triund", first("mcleodganj"))
    }

    @Test fun shortOrUnknownFindNothingWrong() {
        assertTrue(TrekRegions.search("x").isEmpty())
        assertTrue(TrekRegions.search("zzzzzzzz").isEmpty())
        assertEquals(1, TrekRegions.distance("uttrakhand", "uttarakhand"))
    }

    @Test fun popularIsComplete() = assertEquals(8, TrekRegions.POPULAR.size)

    @Test fun parsesPlaceSearch() {
        val json = """[{"lat":"30.0417","lon":"79.0894","category":"boundary","type":"administrative","name":"Uttarakhand","display_name":"Uttarakhand, India"},
            {"lat":"30.8590","lon":"79.0700","category":"natural","type":"peak","name":"Thalay Sagar","display_name":"Thalay Sagar, Uttarkashi, Uttarakhand, India","extratags":{"ele":"6904"}}]"""
        val f = PeakLibrary.parseNominatim(json)
        assertEquals(listOf("Uttarakhand", "Thalay Sagar"), f.map { it.name })
        assertTrue(f[1].isPeak); assertEquals(6904, f[1].eleM); assertEquals("Uttarakhand, India", f[1].area)
        assertTrue(PeakLibrary.nominatimUrl("uttra khand").contains("q=uttra+khand"))
    }
}

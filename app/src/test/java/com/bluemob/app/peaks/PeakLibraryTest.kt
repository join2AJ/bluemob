package com.bluemob.app.peaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakLibraryTest {
    private val kedar = PeakArea("Sankri", 31.07, 78.18, 1000, listOf(Peak("Kedarkantha", 31.0215, 78.1718, 3800, "osm:1"), Peak("Swargarohini", 31.09, 78.52, 6252, "osm:2")))
    private val manali = PeakArea("Manali", 32.24, 77.19, 2000, listOf(Peak("Hanuman Tibba", 32.33, 77.1, 5982, "osm:3")))

    @Test fun keepsSeveralAreasAndReplacesNearbyOnes() {
        var l = PeakLibrary().withArea(kedar).withArea(manali)
        assertEquals(2, l.areas.size)
        assertEquals(3, l.all.size)
        l = l.withArea(kedar.copy(name = "Kedarkantha base", at = 3000, lat = 31.05))
        assertEquals(listOf("Kedarkantha base", "Manali"), l.areas.map { it.name })
        assertEquals(3000, l.asSaved()!!.at)
    }

    @Test fun encodesAndDecodes() {
        val l = PeakLibrary().withArea(kedar).withMine(Peak("Ghost Peak", 30.5, 79.1, 5100, "mine:30.5,79.1"))
        assertEquals(l, PeakLibrary.decode(PeakLibrary.encode(l)))
        assertNull(PeakLibrary().asSaved())
    }

    @Test fun offlineSearchIncludesFamousPeaks() {
        val l = PeakLibrary().withArea(kedar)
        assertEquals("Swargarohini", l.searchOffline("swarga").single().name)
        assertTrue(l.searchOffline("everest").any { it.name == "Mount Everest" })
        assertTrue(l.searchOffline("k").isEmpty())
    }

    @Test fun queryIsSafe() {
        assertEquals("Hampta Pass", PeakLibrary.cleanQuery("  Hampta \"Pass\".* "))
        assertFalse(PeakLibrary.searchQuery("a\"b").contains("a\"b"))
    }

    @Test fun parsesSearchPeaksFirst() {
        val json = """{"elements":[
            {"type":"node","lat":32.24,"lon":77.19,"tags":{"place":"town","name":"Manali"}},
            {"type":"node","lat":32.33,"lon":77.10,"tags":{"natural":"peak","name":"Hanuman Tibba","ele":"5982"}}]}"""
        val f = PeakLibrary.parseSearch(json)
        assertEquals(listOf("Hanuman Tibba", "Manali"), f.map { it.name })
        assertTrue(f[0].isPeak); assertEquals(5982, f[0].eleM)
    }

    @Test fun readsCoordinatesAndChecksRequests() {
        assertEquals(30.8612, PeakLibrary.parseCoord("30.8612")!!, 1e-9)
        assertEquals(30.86111, PeakLibrary.parseCoord("30° 51' 40\" N")!!, 1e-4)
        assertEquals(-79.5, PeakLibrary.parseCoord("79 30 W")!!, 1e-9)
        assertNotNull(PeakLibrary.problem("X", 30.0, 79.0, 5000))
        assertNotNull(PeakLibrary.problem("Ghost", 95.0, 79.0, 5000))
        assertNotNull(PeakLibrary.problem("Ghost", 30.0, 79.0, null))
        assertNull(PeakLibrary.problem("Ghost Peak", 30.0, 79.0, 5100))
        assertTrue("Latitude: 30.00000" in PeakLibrary.requestText("Ghost Peak", 30.0, 79.0, 5100, ""))
    }
}

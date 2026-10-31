package com.bluemob.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {
    @Test fun worldCentreIsTheMiddleOfTheFirstTile() {
        val (x, y) = Tiles.pixel(0.0, 0.0, 0)
        assertEquals(128.0, x, 1e-6); assertEquals(128.0, y, 1e-6)
    }

    @Test fun delhiLandsInTheRightTile() {
        // New Delhi (28.6139 N, 77.2090 E) at zoom 17 is tile x 93_646, y 54_655.
        val (x, y) = Tiles.pixel(28.6139, 77.2090, 17)
        assertEquals(93_646, (x / Tiles.SIZE).toInt())
        assertEquals(54_655, (y / Tiles.SIZE).toInt())
    }

    @Test fun groundScaleShrinksTowardsThePoles() {
        assertEquals(156_543.03, Tiles.metresPerPixel(0.0, 0), 0.01)
        assertTrue(Tiles.metresPerPixel(60.0, 10) < Tiles.metresPerPixel(0.0, 10))
    }

    @Test fun roughPositionsZoomOut() {
        assertEquals(17, Tiles.zoomFor(10.0)); assertEquals(16, Tiles.zoomFor(200.0)); assertEquals(12, Tiles.zoomFor(5_000.0))
    }

    @Test fun googleLinksUseDotDecimalsAndSatellite() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=28.613900,77.209000", Tiles.googleMaps(28.6139, 77.209))
        assertTrue(Tiles.googleSatellite(28.6139, 77.209).endsWith("basemap=satellite"))
    }
}

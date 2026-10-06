package com.bluemob.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointInfoTest {
    @Test
    fun roundTrip() {
        assertEquals("abc123" to "Asha", EndpointInfo.decode(EndpointInfo.encode("abc123", "Asha")))
    }

    @Test
    fun pipeInNameDoesNotBreakParsing() {
        assertEquals("abc123" to "A B", EndpointInfo.decode(EndpointInfo.encode("abc123", "A|B")))
    }

    @Test
    fun rejectsForeignEndpoints() {
        assertNull(EndpointInfo.decode("SomeOtherApp"))
        assertNull(EndpointInfo.decode("BM9|abc|name"))
        assertNull(EndpointInfo.decode("BM1|abc|name")) // older, unsigned protocol
        assertNull(EndpointInfo.decode("BM2||name"))
    }
}

class EndpointVersionTest {
    @Test fun recognisesEveryBlueMobVersionButOnlyLinksWithOurs() {
        assertEquals(1 to "Ravi", EndpointInfo.version("BM1|abc|Ravi"))
        assertEquals(3 to "Asha", EndpointInfo.version("BM3|abc|Asha"))
        assertEquals(2 to "Asha", EndpointInfo.version(EndpointInfo.encode("abc", "Asha")))
        assertNull(EndpointInfo.version("SomeOtherApp|x|y"))
        assertNull(EndpointInfo.version("BMX|x|y"))
    }
}

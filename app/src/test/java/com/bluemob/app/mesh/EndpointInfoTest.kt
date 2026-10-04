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
        assertNull(EndpointInfo.decode("BM2|abc|name"))
        assertNull(EndpointInfo.decode("BM1||name"))
    }
}

package com.bluemob.app.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FieldTestTest {
    private var clock = 1_000_000L
    private fun test() = FieldTest(File.createTempFile("field", ".jsonl").apply { delete() }) { clock }
    private fun minutes(n: Int) { clock += n * 60_000L }

    @Test fun measuresDeliveryPathsBatteryAndStops() {
        val t = test()
        t.start("g-trip", 10, "Motorola edge 50 · Android 14")
        t.battery(80, false)
        t.probe("m1", 3)
        t.delivered("m1", "B", hops = 1, viaInternet = false, ms = 2_000)
        t.delivered("m1", "C", hops = 3, viaInternet = false, ms = 9_000)
        t.delivered("m1", "C", hops = 3, viaInternet = false, ms = 9_000) // a second receipt counts once
        t.delivered("other", "B", hops = 1, viaInternet = false, ms = 1) // not a field-test roll call
        repeat(30) { minutes(1); t.beat(peers = 2, meshOn = true) }
        minutes(20) // the phone stopped BlueMob for 20 minutes
        t.beat(peers = 4, meshOn = true)
        minutes(10)
        t.battery(72, false)
        t.probe("m2", 3)
        t.delivered("m2", "D", hops = 0, viaInternet = true, ms = 30_000)
        t.stop()

        val r = t.report()
        assertEquals("Motorola edge 50 · Android 14", r.device)
        assertEquals(60, r.minutes)
        assertEquals(6, r.sent)
        assertEquals(3, r.delivered)
        assertEquals(50, r.deliveryPct)
        assertEquals(mapOf("Direct" to 1, "2 phones" to 1, "Internet" to 1), r.paths)
        assertEquals(30_000L, r.slowestMs)
        assertEquals(8.0, r.batteryPerHour!!, 0.01)
        assertEquals(2, r.stops) // the 20-minute gap, and the 10 minutes before stopping
        assertEquals(30, r.stoppedMinutes)
        assertEquals(4, r.maxPeers)
        assertTrue(r.summary().contains("Delivery: 50% (3 of 6)"))
    }

    @Test fun aRunningTestSurvivesBlueMobRestarting() {
        val file = File.createTempFile("field", ".jsonl").apply { delete() }
        FieldTest(file) { clock }.start(null, 5, "phone")
        val again = FieldTest(file) { clock }
        assertNotNull(again.session.value)
        again.stop()
        assertNull(FieldTest(file) { clock }.session.value)
    }

    @Test fun timeOnTheChargerIsLeftOut() {
        val t = test()
        t.start(null, 5, "phone")
        t.battery(50, true); minutes(30); t.battery(90, true); minutes(30); t.battery(88, false); minutes(60); t.battery(84, false)
        assertEquals(4.0, t.report().batteryPerHour!!, 0.01)
    }
}

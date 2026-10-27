package com.bluemob.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TripCodeTest {
    // A test key pair (not BlueMob's real one).
    private val pub = "04306aa4f1c896f2e9f8db57da07eea30efcec9f1751623a06882633e5abb195368c126eb62dcc980328776f51796b1ec49a60dfc8e21ab4e6b6cb30df4898910e"
    private val priv = "301a40bbaa59b401e663aaad34ce91bba0f280dc0f57030b9ae764532f86ef7f"
    private val invite = TripInvite("g-abc123", "0123456789abcdef", "Group 10", "3f9a1c2b7d4e8a01", 0, 1_790_000_000_000)
    private val origin = TripCode.Origin("3f9a1c2b7d4e8a01", 1_790_000_000_000, "0123456789abcdef", 1_790_000_600_000, "Motorola edge 50", "0.34.0")

    @Test fun aPhoneReadsWhatItNeedsAndOnlyTheAdminKeyOpensTheRest() {
        val code = TripCode.encode(invite, origin, pub)
        assertTrue(code.startsWith(TripCode.PREFIX))
        // Nothing readable in it: no IDs, no name.
        assertFalse(code.contains("abc123") || code.contains("3f9a") || code.contains("Group"))
        assertEquals(invite, TripInvite.parse(code))
        assertEquals(invite, TripInvite.parse("Join my BlueMob trip group: $code (open BlueMob → Nearby)"))
        val admin = TripCode.openForAdmin(code, priv)!!
        assertEquals("3f9a1c2b7d4e8a01", admin.getString("creator"))
        assertEquals(1_790_000_600_000, admin.getLong("madeAt"))
        assertEquals("Motorola edge 50", admin.getString("device"))
        // Another key can't open it, and each code is different even for the same group.
        assertNull(TripCode.openForAdmin(code, "11".repeat(32)))
        assertTrue(code != TripCode.encode(invite, origin, pub))
        File("build").mkdirs(); File("build/tripcode-from-app.txt").writeText(code)
    }

    @Test fun opensACodeMadeByTheServer() {
        // relay.js sealTripCode(pub, {...}) with the test key.
        val fromServer = "bluemob:t:AkEE5BIbSMTHI5c-psSUNt0zhOXYwzmjrptxOJJu24YKwIWVw_ZH7oNjDpfEzRFz-5_TOvDGAL7KjlpH3beq4tgQIwDpg1ukxFqTNErGyKSBI3bTdQNeecLyLO7iF6tjlEnjaSjceM6Il-NX40wP0ubUMExb7w3YbSV_fQTKLcyjdQeLTRMcjS_RQ4CzGGOjMkNNQd7vt5wVUYIunV4fHg50-ScjEDYyODSSlcJyXkrnfv6Teqp98nHdHwJZs8mcT_IU6Zrwy662npvNPwjzOfMZP9GXc0gC2ZIb4ARY9N29OIq4wUIZzBl8g3GxUXGL4v6tpTktaXPlytS1iRSBRC2W0iSxe6_8FqH9Qwo6uM2QakgvEGbkfuu0NCc8IxtqztE6u8W0MXds4TpJXsoehxmqpzdaIR01q_VLvJWpwfffn1RaPr9TZnjxx3muujlqZukaQKrfjU9M2FGR4Y0ZDs99jKwrtOYlA6IF5uchx5zNbjq78Aq9rgs_Jt1ilogM8Dz8XANlwxnUSpDqvtpowKQlBWnU04xQwMRoaaf1BjjSoogODb6LmwNFZybNobk"
        assertEquals(invite, TripInvite.parse(fromServer))
        assertEquals("Group 10", TripCode.openForAdmin(fromServer, priv)!!.getString("name"))
    }

    @Test fun oldReadableCodesStillJoin() {
        val old = TripInvite("g-abc123", "0123456789abcdef", "Trek").encode()
        assertEquals(TripInvite("g-abc123", "0123456789abcdef", "Trek"), TripInvite.parse(old))
        assertNull(TripInvite.parse("bluemob:t:AAAA" + "x".repeat(60)))
    }

    @Test fun onlyTheAdminChangesSettingsAndTheGroupClosesADayAfterTheEnd() {
        val admin = "3f9a1c2b7d4e8a01"; val other = "0123456789abcdef"
        val g = ChatGroup("g-1", "Group 1", mapOf(admin to "Arjun"), 0, trip = true, owner = admin)
        val bad = g.merge(other, "Hacked", mapOf("aaaaaaaaaaaaaaaa" to "Asha"), TripMeta(owner = other, autoMin = 5, closedAt = 9))
        assertEquals("Group 1", bad.name); assertEquals(admin, bad.owner); assertEquals(0, bad.autoMin); assertEquals(0, bad.closedAt)
        assertTrue("aaaaaaaaaaaaaaaa" in bad.members) // members still grow
        val good = g.merge(admin, "Group 1", emptyMap(), TripMeta(owner = admin, autoMin = 10, expiresAt = 1_000))
        assertEquals(10, good.autoMin)
        assertFalse(good.isClosed(1_000 + TripMeta.GRACE_MS - 1))
        assertTrue(good.isClosed(1_000 + TripMeta.GRACE_MS))
        assertTrue(good.canManage(admin)); assertFalse(good.canManage(other))
        assertTrue(good.copy(captain = other).canManage(other))
    }
}

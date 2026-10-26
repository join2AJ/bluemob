package com.bluemob.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTest {
    private val all = listOf(
        Route.Chat("sky"), Route.Group("g1"), Route.Media("p"), Route.Photo("m1"), Route.Info("m2"), Route.NewChat, Route.NewGroup, Route.SkyAi,
        Route.Article("help-sos"), Route.Quiz("q"), Route.GuidePacks, Route.Sos, Route.Signal, Route.SosContacts, Route.Rescues, Route.Rescue("r1"),
        Route.Games, Route.Match("x"), Route.Game("ttt"), Route.Trips, Route.Trip("t1"), Route.Person("abc"), Route.Verify("abc"), Route.News, Route.Account,
        Route.Connections, Route.AutoStart, Route.Audit, Route.Badges, Route.Activity, Route.Bridge, Route.Backup, Route.Storage,
        Route.Diagnostics, Route.Report, Route.Calls, Route.SosComing("s1"), Route.Call,
    )

    @Test fun everyRouteSurvivesItsKey() = all.forEach { assertEquals(it, Route.parse(it.key)) }

    @Test fun keysAreUnique() = assertEquals(all.size, all.map { it.key }.toSet().size)

    /** Keys saved by earlier versions (and used by notifications) still open the same screens. */
    @Test fun oldKeysStillWork() {
        assertEquals(Route.Chat("ab12"), Route.parse("chat:ab12"))
        assertEquals(Route.Rescue("r:9"), Route.parse("rescue:r:9"))
        assertEquals(Route.SosContacts, Route.parse("sos-contacts"))
        assertEquals(Route.SosComing("x"), Route.parse("sos-coming:x"))
    }

    @Test fun unknownOrEmptyIsNull() {
        assertNull(Route.parse("nothing"))
        assertNull(Route.parse("chat:"))
        assertNull(Route.parse(":x"))
        assertNull(Route.parse("widget:1"))
    }
}

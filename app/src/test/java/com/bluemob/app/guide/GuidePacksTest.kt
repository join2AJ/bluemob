package com.bluemob.app.guide

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The packs the relay serves (server/guides/) must read cleanly in the app. */
class GuidePacksTest {
    private val dir = listOf(File("../server/guides"), File("server/guides")).firstOrNull { it.isDirectory }

    @Test fun everyPackParsesWithUniquePrefixedIds() {
        assumeTrue(dir != null)
        val files = dir!!.listFiles { f -> f.name.endsWith(".json") }!!
        assertTrue(files.size >= 4)
        val ids = mutableSetOf<String>()
        files.forEach { f ->
            val json = JSONObject(f.readText())
            val (info, articles) = GuidePacks.parse(json)
            assertEquals(json.getJSONArray("articles").length(), articles.size)
            articles.forEach { a ->
                assertTrue(a.id.startsWith(info.id + "-"))
                assertTrue("${a.id} needs steps", a.steps.size >= 2)
                assertTrue("duplicate ${a.id}", ids.add(a.id))
                assertTrue("${a.id} clashes with a built-in guide", GuideContent.articles.none { it.id == a.id })
            }
        }
    }
}

class GuideDashboardTest {
    @Test fun countsWhatsHereAndWhatsBeenRead() {
        val all = GuideContent.articles
        val first = all[0].id
        val second = all[1].id
        val d = GuideDashboard.of(all, saved = setOf(first, "gone"), read = mapOf(first to 10L, second to 20L, "gone" to 30L), isNew = { it == first }, packs = 0)
        assertEquals(all.size, d.total)
        assertEquals(1, d.saved)
        assertEquals(2, d.readCount)
        assertEquals(1, d.newCount)
        assertEquals(second, d.lastRead)
        assertEquals(all.size, d.byCategory.values.sum())
        // The guide of the day stays put all day, and moves on the next.
        assertEquals(GuideDashboard.ofTheDay(all, 100), GuideDashboard.ofTheDay(all, 100))
        assertTrue(GuideDashboard.ofTheDay(all, 100) != GuideDashboard.ofTheDay(all, 101))
    }
}

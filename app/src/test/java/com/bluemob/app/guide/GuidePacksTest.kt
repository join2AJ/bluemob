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

package edu.taehyeon.myconcert

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run with upgradePhase=seed on 1.2.1, install -r 1.3.0, then run upgradePhase=verify. */
@RunWith(AndroidJUnit4::class)
class UpgradeStateTest {
    @Test fun savedUserDataSurvivesInstallingXmlVersion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val phase = InstrumentationRegistry.getArguments().getString("upgradePhase", "standalone")
        val prefs = context.getSharedPreferences("myconcert", 0)
        if (phase != "verify") {
            prefs.edit().clear().putBoolean("onboarded", true).putBoolean("location_prompted", true).commit()
            val catalog = ArtistCatalog(context)
            val artist = catalog.beginSearch("ILLIT")
            catalog.persist(artist.id); catalog.editAliases(artist.id, listOf("ILLIT", "아일릿"))
            prefs.edit().putStringSet("favorites", setOf("ive", artist.id)).putString("recent", artist.id)
                .putString("region_code", "26").putBoolean("dark", true).putString("upgrade_qa_artist", artist.id).commit()
        }
        val id = prefs.getString("upgrade_qa_artist", null)!!
        assertTrue(prefs.getBoolean("onboarded", false))
        assertEquals(setOf("ive", id), prefs.getStringSet("favorites", emptySet()))
        assertEquals("26", prefs.getString("region_code", "")); assertTrue(prefs.getBoolean("dark", false))
        val restored = ArtistCatalog(context)
        assertTrue(restored.isSaved(id)); assertEquals(listOf("ILLIT", "아일릿"), restored.byId(id)!!.aliases)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertEquals(if (phase == "seed") "1.2.1" else "1.3.0", version)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"), false)
                assertEquals(setOf("ive", id), model.favoriteIds)
                assertEquals("26", model.regionCode); assertTrue(model.dark)
                assertEquals(id, model.recent().single().id)
                assertNotNull(model.catalog.byId(id))
                model.selectArtist(model.catalog.byId(id)!!)
                model.navigate(Route("search"), false)
                model.route.value = Route("artist", id)
                model.state.value = LoadState(result = SearchResult(emptyList()))
            }
        }
    }
}

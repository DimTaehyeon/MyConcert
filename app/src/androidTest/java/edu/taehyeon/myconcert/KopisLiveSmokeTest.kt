package edu.taehyeon.myconcert

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.taehyeon.myconcert.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt in with -e liveKopis true; the regular regression suite remains deterministic. */
@RunWith(AndroidJUnit4::class)
class KopisLiveSmokeTest {
    @Test fun directSearchConfirmsAndPersistsActualKopisResults() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveKopis")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true)
            .putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity {
                val model=ViewModelProvider(it)[MyConcertViewModel::class.java]
                model.directSearch("터치드");model.load(true)
            }
            var state=LoadState(loading=true)
            repeat(60) {
                if(state.loading) {
                    Thread.sleep(1000)
                    scenario.onActivity { state=ViewModelProvider(it)[MyConcertViewModel::class.java].state.value!! }
                }
            }
            assertFalse("Live request must finish within 60 seconds",state.loading)
            assertNull(state.error)
            val result=checkNotNull(state.result)
            val folder=File(context.getExternalFilesDir(null),"qa3").apply { mkdirs() }
            val report=JSONObject().put("query","터치드").put("count",result.concerts.size)
                .put("warnings",result.warnings.joinToString()).put("cached",result.cached)
                .put("titles",result.concerts.joinToString("\n") { "${it.id} | ${it.title} | ${it.start}" })
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                val artist=model.catalog.byId(model.route.value!!.artistId!!)!!
                report.put("saved",model.catalog.isSaved(artist.id)).put("aliases",artist.aliases.joinToString())
                if(result.concerts.isNotEmpty()) assertTrue(ArtistCatalog(context).isSaved(artist.id))
            }
            File(folder,"live-kopis.json").writeText(report.toString(2))
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"));assertTrue(model.back())
                val reused=model.state.value!!.result!!
                assertTrue("Returning to the same results uses saved information",reused.cached)
                assertEquals(result.savedAt,reused.savedAt)
            }
            Thread.sleep(1000);instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(folder,"05-live-kopis.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
        }
    }
}

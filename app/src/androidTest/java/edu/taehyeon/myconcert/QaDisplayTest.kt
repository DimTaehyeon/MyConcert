package edu.taehyeon.myconcert

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QaDisplayTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Thread.sleep(700);instrumentation.waitForIdleSync()
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"qa3").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
    @Test fun tilesRemainReadableInDarkModePortraitAndLandscape() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true)
            .putBoolean("location_prompted",true).putBoolean("dark",true).putStringSet("favorites",setOf("ive")).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            var scale=1f
            scenario.onActivity { activity ->
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("settings"),false);scale=activity.resources.configuration.fontScale
            }
            Thread.sleep(400)
            scenario.onActivity { activity ->
                val tiles=all(activity.findViewById(android.R.id.content))
                val grid=tiles.filterIsInstance<ArtistTileGrid>().single()
                assertEquals(if(scale>=1.8f) 2 else 3,grid.columns)
                assertTrue(tiles.filterIsInstance<ArtistTile>().first().isChecked)
                tiles.filterIsInstance<ArtistTile>().flatMap(::all).filterIsInstance<TextView>().forEach { text ->
                    assertEquals(text.text.length,text.layout.getLineEnd(text.lineCount-1))
                    assertTrue("${text.text} must fit inside its tile",text.layout.height<=text.height-text.paddingTop-text.paddingBottom)
                    // getLineMax excludes trailing spaces that Android can place beyond the wrap boundary.
                    for(line in 0 until text.lineCount) assertTrue("${text.text} must fit horizontally",text.layout.getLineMax(line)<=text.width-text.paddingLeft-text.paddingRight+1)
                }
            }
            screenshot("06-dark-portrait-font-$scale.png")
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(800)
            scenario.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_LANDSCAPE,activity.resources.configuration.orientation)
                val views=all(activity.findViewById(android.R.id.content))
                assertTrue(views.filterIsInstance<ArtistTileGrid>().single().columns>=4)
                assertTrue(views.filterIsInstance<ArtistTile>().first().isChecked)
            }
            screenshot("07-dark-landscape-font-$scale.png")
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}

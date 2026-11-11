package edu.taehyeon.myconcert

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import edu.taehyeon.myconcert.data.Artist

@RunWith(AndroidJUnit4::class)
class ArtistPhotoTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun localFiles()=listOf(context.filesDir,context.cacheDir,context.noBackupFilesDir).flatMap { dir ->
        dir.walkTopDown().filter(File::isFile).map { it.absolutePath }.toList()
    }.toSet()
    private fun screenshot(name:String) {
        Thread.sleep(450)
        instrumentation.waitForIdleSync()
        val folder=File(context.getExternalFilesDir(null),"profiles").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
    private fun waitUntil(check:()->Boolean) {
        repeat(30) { if(check()) return;Thread.sleep(500) }
        assertTrue("Remote profile did not load in 15 seconds",check())
    }
    @Test fun registeredPhotosAppearInTilesAndCreateNoImageFiles() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true).putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { ViewModelProvider(it)[MyConcertViewModel::class.java].navigate(Route("settings"),false) }
            onView(withText("선호 아티스트")).check(matches(isDisplayed()))
            val before=localFiles()
            waitUntil {
                var loaded=false
                scenario.onActivity { activity ->
                    val avatars=all(activity.findViewById(android.R.id.content)).filterIsInstance<ArtistAvatarView>()
                    val expected=avatars.filter { it.artist?.id in listOf("ive","nmixx","iu") }
                    loaded=expected.size==3 && expected.all { it.hasPhoto }
                };loaded
            }
            assertEquals("Profile loading must not create local photo/cache files",before,localFiles())
            screenshot("01-preference-photos")
            scenario.onActivity { activity ->
                val tiles=all(activity.findViewById(android.R.id.content)).filterIsInstance<ArtistTile>()
                val tile=tiles.first();tile.performClick();assertTrue(tile.isChecked)
                assertTrue(all(tile).filterIsInstance<ArtistAvatarView>().single().hasPhoto)
            }
            scenario.recreate()
            screenshot("02-after-recreation")
        }
    }
    @Test fun newArtistUsesLiveProfileLookupAndMissingPhotoKeepsInitials() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true).putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            lateinit var custom:ArtistAvatarView
            lateinit var missing:ArtistAvatarView
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"),false)
                val root=activity.findViewById<ViewGroup>(android.R.id.content);root.removeAllViews()
                val row=android.widget.LinearLayout(activity)
                custom=ArtistAvatarView(activity,Artist("custom_photo_test","터치드","","터치","직접 검색",listOf("터치드"),userAdded=true),100,-1,0xff111827.toInt())
                missing=ArtistAvatarView(activity,null,100,-1,0xff111827.toInt())
                row.addView(custom,android.widget.LinearLayout.LayoutParams(160,160));row.addView(missing,android.widget.LinearLayout.LayoutParams(160,160));root.addView(row)
            }
            val before=localFiles()
            waitUntil { var loaded=false;scenario.onActivity { loaded=custom.hasPhoto };loaded }
            scenario.onActivity { assertFalse(missing.hasPhoto);assertEquals("공연",missing.contentDescription) }
            assertEquals(before,localFiles());screenshot("03-direct-search-photo")
        }
    }
}

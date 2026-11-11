package edu.taehyeon.myconcert

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.scrollTo
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
        val folder=File(context.getExternalFilesDir(null),"qa121").apply { mkdirs() }
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
                val portrait=all(tile).filterIsInstance<ArtistAvatarView>().single()
                assertTrue(portrait.hasPhoto);assertTrue(portrait.tileBackground)
                assertEquals(0,portrait.left);assertEquals(0,portrait.top)
                assertEquals(tile.width,portrait.width);assertEquals(tile.height,portrait.height)
                val capsule=all(tile).filterIsInstance<TextView>().single { it.tag=="artist-name:${tile.artist.id}" }
                val inset=(10*activity.resources.displayMetrics.density).toInt()
                assertEquals(tile.width-inset,capsule.right);assertEquals(tile.height-inset,capsule.bottom)
                assertTrue(capsule.left>=inset)
                val main=all(portrait).filterIsInstance<ImageView>().single { it.scaleType==ImageView.ScaleType.FIT_CENTER }
                val matrix=FloatArray(9);main.imageMatrix.getValues(matrix)
                assertEquals("Artist image must keep its aspect ratio",matrix[Matrix.MSCALE_X],matrix[Matrix.MSCALE_Y],0.001f)
            }
            scenario.recreate()
            screenshot("02-after-recreation")
        }
    }
    @Test fun newArtistUsesLiveProfileLookupAndMissingPhotoKeepsInitials() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true).putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            val customs=mutableListOf<ArtistAvatarView>()
            lateinit var missing:ArtistAvatarView
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"),false)
                val root=activity.findViewById<ViewGroup>(android.R.id.content);root.removeAllViews()
                val row=android.widget.LinearLayout(activity)
                listOf("터치드","ILLIT","아일릿").forEachIndexed { index,name ->
                    val custom=ArtistAvatarView(activity,Artist("custom_photo_test_$index",name,"",name.take(2),"직접 검색",listOf(name),userAdded=true),100,-1,0xff111827.toInt())
                    customs+=custom;row.addView(custom,android.widget.LinearLayout.LayoutParams(90,130))
                }
                missing=ArtistAvatarView(activity,null,100,-1,0xff111827.toInt())
                row.addView(missing,android.widget.LinearLayout.LayoutParams(90,130));root.addView(row)
            }
            val before=localFiles()
            waitUntil { var loaded=false;scenario.onActivity { loaded=customs.size==3 && customs.all { avatar -> avatar.hasPhoto } };loaded }
            scenario.onActivity { assertFalse(missing.hasPhoto);assertEquals("공연",missing.contentDescription) }
            assertEquals(before,localFiles());screenshot("03-direct-search-photo")
        }
    }
    @Test fun illitPhotoAndCompactTopRightDeleteAppearInActualScreens() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true).putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900);var id=""
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                val artist=model.catalog.beginSearch("ILLIT");id=artist.id;model.toggleFavorite(id)
                model.preferenceSearchText="ILLIT";model.navigate(Route("settings"),false)
            }
            waitUntil {
                var loaded=false;scenario.onActivity { activity ->
                    loaded=all(activity.findViewById(android.R.id.content)).filterIsInstance<ArtistAvatarView>().any { it.artist?.id==id && it.hasPhoto }
                };loaded
            }
            screenshot("04-illit-tile")
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"),false);model.route.value=Route("artist",id)
                model.state.value=LoadState(result=edu.taehyeon.myconcert.data.SearchResult(emptyList()))
            }
            onView(withContentDescription("추가한 아티스트 삭제")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val views=all(activity.findViewById(android.R.id.content))
                val delete=views.single { it.contentDescription=="추가한 아티스트 삭제" }
                val location=IntArray(2);delete.getLocationOnScreen(location)
                val density=activity.resources.displayMetrics.density
                assertTrue(location[1]<120*density);assertTrue(delete.width<=72*density)
                assertTrue(delete.height>=48*density)
                assertFalse(views.filterIsInstance<TextView>().any { it.text.toString()=="추가한 아티스트 삭제" })
            }
            screenshot("05-illit-detail-delete")
        }
    }
    @Test fun footerCreatorCreditIsOneLineAndGithubLinkIsClickable() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true).putBoolean("location_prompted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            onView(withText(context.getString(R.string.creator_credit))).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val views=all(activity.findViewById(android.R.id.content))
                val credit=views.filterIsInstance<TextView>().single { it.text.toString()==context.getString(R.string.creator_credit) }
                assertEquals(1,credit.lineCount);assertFalse(credit.text.contains('\n'))
                assertEquals(0,credit.layout.getEllipsisCount(0))
                val github=views.single { it.contentDescription=="GitHub DimTaehyeon 열기" }
                assertTrue(github.isClickable);assertTrue(github.hasOnClickListeners())
                assertEquals("https://github.com/DimTaehyeon",context.getString(R.string.creator_github_url))
            }
            screenshot("06-footer-github")
        }
    }
}

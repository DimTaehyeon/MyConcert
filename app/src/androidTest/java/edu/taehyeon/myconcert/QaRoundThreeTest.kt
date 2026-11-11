package edu.taehyeon.myconcert

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QaRoundThreeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Before fun setup() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true)
            .putBoolean("location_prompted",true).commit()
    }
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun screenshot(name:String) {
        Thread.sleep(3500) // Let checkbox transitions and transient toasts settle before visual review.
        instrumentation.waitForIdleSync()
        val folder=File(context.getExternalFilesDir(null),"qa3").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
    @Test fun tilesExposeCheckedStateAndPreserveEightSelectionsAcrossFilterAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity -> ViewModelProvider(activity)[MyConcertViewModel::class.java].navigate(Route("settings"),false) }
            scenario.onActivity { activity ->
                val views=all(activity.findViewById(android.R.id.content))
                assertEquals(3,views.filterIsInstance<ArtistTileGrid>().single().columns)
                val tiles=views.filterIsInstance<ArtistTile>()
                tiles.take(8).forEach { it.performClick() }
                assertTrue(tiles.take(8).all { it.isChecked })
                val info=tiles.first().createAccessibilityNodeInfo()
                assertTrue(info.isCheckable);assertTrue(info.isChecked)
                assertEquals("android.widget.CheckBox",info.className.toString())
            }
            screenshot("01-selected-tiles")
            scenario.onActivity { activity ->
                val tiles=all(activity.findViewById(android.R.id.content)).filterIsInstance<ArtistTile>()
                tiles[8].performClick();assertFalse(tiles[8].isChecked)
            }
            onView(withHint("한글·영문 아티스트 이름 검색")).perform(replaceText("아이브"),closeSoftKeyboard())
            onView(withContentDescription("아이브, 그룹")).check(matches(isChecked())).perform(click()).check(matches(isNotChecked()))
            onView(withHint("한글·영문 아티스트 이름 검색")).perform(replaceText(""),closeSoftKeyboard())
            onView(withContentDescription("아이브, 그룹")).check(matches(isNotChecked()))
            scenario.recreate()
            onView(withText("선택 완료 (7팀)")).check(matches(isDisplayed()))
            onView(withContentDescription("아이브, 그룹")).check(matches(isNotChecked())).perform(click())
            onView(withText("선택 완료 (8팀)")).check(matches(isDisplayed()))
        }
    }
    @Test fun customArtistCanBeDeletedAfterZeroResultsAndCancellationKeepsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            var id=""
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                val artist=model.catalog.beginSearch("QA없는아티스트");id=artist.id
                model.toggleFavorite(id);model.selectArtist(artist)
                model.navigate(Route("search"),false) // cancel real request before injecting the empty fixture
                model.route.value=Route("artist",id)
                model.state.value=LoadState(result=SearchResult(emptyList()))
            }
            screenshot("02-zero-results")
            onView(withText("추가한 아티스트 삭제")).perform(scrollTo(),click())
            onView(withText("취소")).perform(click())
            scenario.onActivity { activity -> assertNotNull(ViewModelProvider(activity)[MyConcertViewModel::class.java].catalog.byId(id)) }
            onView(withText("추가한 아티스트 삭제")).perform(click())
            screenshot("03-delete-confirmation")
            onView(withText("삭제")).perform(click())
            onView(withText("아티스트 검색")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                assertNull(model.catalog.byId(id));assertFalse(id in model.favoriteIds)
                assertFalse(model.recent().any { it.id==id })
                assertNull(ArtistCatalog(context).byId(id))
                assertFalse(model.deleteCustomArtist("ive"));assertNotNull(model.catalog.byId("ive"))
                model.back();assertNotEquals(id,model.route.value?.artistId)
            }
            scenario.recreate()
            scenario.onActivity { activity -> assertNull(ViewModelProvider(activity)[MyConcertViewModel::class.java].catalog.byId(id)) }
        }
    }
    @Test fun savedAndTemporaryArtistsHaveSeparateDeleteControlsInSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                val artist=model.catalog.beginSearch("직접등록테스트");model.catalog.persist(artist.id)
                model.catalog.beginSearch("저장전테스트")
                model.navigate(Route("search"),false)
            }
            onView(withText("내가 추가한 아티스트")).perform(scrollTo())
            onView(withContentDescription("직접등록테스트 삭제")).perform(scrollTo(),click())
            onView(withText("삭제")).perform(click())
            onView(withHint("아티스트 이름을 입력하세요")).perform(scrollTo(),replaceText("저장전테스트"),closeSoftKeyboard())
            onView(withContentDescription("저장전테스트 삭제")).perform(scrollTo(),click())
            onView(withText("삭제")).perform(click())
            scenario.onActivity { activity ->
                val catalog=ViewModelProvider(activity)[MyConcertViewModel::class.java].catalog
                assertFalse(catalog.artists.any { it.name in listOf("직접등록테스트","저장전테스트") })
            }
        }
    }
    @Test fun longNamesAndDoubleSizeFontsReflowWithoutClipping() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MyConcertViewModel::class.java].navigate(Route("settings"),false)
                val large=ContextThemeWrapper(activity,R.style.Theme_MyConcert)
                large.applyOverrideConfiguration(Configuration(activity.resources.configuration).apply { fontScale=2f })
                val grid=ArtistTileGrid(large)
                listOf("투모로우바이투게더","브로콜리너마저","아주 긴 이름의 아티스트 테스트 이름을 끝까지 확인하는 최대 길이의 이름 테스트").forEachIndexed { index,name ->
                    grid.addView(ArtistTile(large,Artist("qa$index",name,"",name.take(2),"그룹",listOf(name)),
                        ArtistTileColors(-1,0xffedf4ff.toInt(),0xfff9fafb.toInt(),0xff111827.toInt(),0xff6b7280.toInt(),0xffe5e7eb.toInt(),0xff3b82f6.toInt()),index==0) {})
                }
                val width=(372*large.resources.displayMetrics.density).toInt()
                grid.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                grid.layout(0,0,grid.measuredWidth,grid.measuredHeight)
                assertEquals(2,grid.columns)
                all(grid).filterIsInstance<TextView>().filter { it.text.length>4 }.forEach { text ->
                    assertTrue("${text.text} must fit vertically",text.layout.height<=text.height-text.paddingTop-text.paddingBottom)
                    assertEquals("${text.text} must display the entire name",text.text.length,text.layout.getLineEnd(text.lineCount-1))
                    for(line in 0 until text.lineCount) assertEquals("${text.text} must not be ellipsized",0,text.layout.getEllipsisCount(line))
                }
                val root=activity.findViewById<ViewGroup>(android.R.id.content);root.removeAllViews();root.addView(grid)
            }
            screenshot("04-large-font-tiles")
        }
    }
}

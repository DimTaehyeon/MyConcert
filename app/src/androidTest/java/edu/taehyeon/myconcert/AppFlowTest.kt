package edu.taehyeon.myconcert

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.taehyeon.myconcert.data.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun resetPreferences() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("location_prompted",true).commit()
    }
    private fun screenshot(name:String) {
        instrumentation.waitForIdleSync()
        val folder=File(context.getExternalFilesDir(null),"qa").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun artistSelectionPersistsAndKoreanSearchFindsCorrectArtist() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            onView(withHint("한글·영문 아티스트 이름 검색")).perform(replaceText("아이브"),closeSoftKeyboard())
            onView(withContentDescription("아이브, 그룹")).perform(click()).check(matches(isChecked()))
            screenshot("01-onboarding")
            onView(withText("1팀 선택 · 시작하기")).perform(click())
            onView(withContentDescription("아이브 공연 보기")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("MyConcert")).check(matches(isDisplayed()))
            onView(withContentDescription("다른 아티스트 검색")).perform(click())
            onView(withHint("아티스트 이름을 입력하세요")).perform(replaceText("IVE"),closeSoftKeyboard())
            onView(withContentDescription("아이브, 그룹, 공연 보기")).check(matches(isDisplayed()))
            onView(withText("아티스트 1팀")).check(matches(isDisplayed()))
            screenshot("02-search")
        }
    }
    @Test fun rendersActualApiFixtureHomeDetailsRegionAndDarkMode() {
        context.getSharedPreferences("myconcert",0).edit().putBoolean("onboarded",true)
            .putStringSet("favorites",setOf("nmixx")).commit()
        val xml=instrumentation.context.assets.open("kopis-samples.xml").bufferedReader().use { it.readText() }
        val fixtures=KopisXml.parse(xml,true).filter { it.title.startsWith("NMIXX") }
            .map { it.copy(artistIds=setOf("nmixx"),matchReason="공연명에 공식 아티스트명 일치") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"),false)
                model.route.value=Route("home")
                model.state.value=LoadState(result=SearchResult(fixtures))
            }
            onView(withText("MyConcert")).check(matches(isDisplayed())); screenshot("03-home")
            onView(withContentDescription("다크모드로 전환")).perform(click())
            onView(withText("MyConcert")).check(matches(isDisplayed())); screenshot("04-dark")
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.route.value=Route("artist","nmixx")
                model.state.value=LoadState(result=SearchResult(fixtures))
            }
            onView(withText("검색 표기 · NMIXX / 엔믹스")).check(matches(isDisplayed())); screenshot("05-artist")
            onView(withContentDescription("NMIXX 선호 아티스트 해제")).check(matches(isSelected())).perform(click())
            onView(withContentDescription("NMIXX 선호 아티스트 추가")).check(matches(isNotSelected()))
            screenshot("05-artist-unselected")
            onView(withContentDescription("NMIXX 선호 아티스트 추가")).perform(click())
            onView(withContentDescription("NMIXX 선호 아티스트 해제")).check(matches(isSelected()))
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.route.value=Route("detail",concert=fixtures.first())
                model.state.value=LoadState(detail=fixtures.first())
            }
            onView(withText("공연 상세")).check(matches(isDisplayed())); screenshot("06-detail")
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.route.value=Route("region")
                model.state.value=LoadState(result=SearchResult(emptyList()))
            }
            onView(withText("공연 지역 선택")).check(matches(isDisplayed())); screenshot("07-region")
            onView(withText("내 위치")).check(matches(isDisplayed()))
            onView(withText(context.getString(R.string.creator_credit))).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}

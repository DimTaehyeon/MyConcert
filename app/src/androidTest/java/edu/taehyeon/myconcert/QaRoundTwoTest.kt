package edu.taehyeon.myconcert

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QaRoundTwoTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true)
            .putBoolean("location_prompted",true).putStringSet("favorites",setOf("nmixx")).commit()
    }
    private fun show(n:Int)=Concert("QA$n","QA 공연 $n",LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),"QA 공연장",genre="대중음악")
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun paginationKeepsRefreshBelowEveryExpandedCardAndTopButtonReturnsToStart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                model.navigate(Route("search"),false)
                model.route.value=Route("region")
                model.state.value=LoadState(result=SearchResult((1..83).map(::show)))
            }
            onView(withText("공연 더 보기 (40/83)")).perform(scrollTo(),click())
            onView(withText("공연 더 보기 (80/83)")).perform(scrollTo())
            scenario.onActivity { activity ->
                val views=all(activity.findViewById(android.R.id.content))
                val refresh=views.filterIsInstance<TextView>().single { it.text.toString()=="새로고침" }
                val last=views.filterIsInstance<TextView>().single { it.text.toString()=="QA 공연 80" }
                val refreshAt=IntArray(2);val lastAt=IntArray(2)
                refresh.getLocationOnScreen(refreshAt);last.getLocationOnScreen(lastAt)
                assertTrue("Refresh must stay after the newly expanded results",refreshAt[1]>lastAt[1]+last.height)
            }
            onView(withContentDescription("맨 위로 이동")).check(matches(isDisplayed())).perform(click())
            Thread.sleep(500)
            scenario.onActivity { activity ->
                assertEquals(0,all(activity.findViewById(android.R.id.content)).filterIsInstance<ScrollView>().single().scrollY)
            }
        }
    }
    @Test fun directLookupAndUserSearchNamePersistenceDoNotNeedBuiltinCatalogEntries() {
        val catalog=ArtistCatalog(context)
        val artist=catalog.beginSearch("터치드")
        assertTrue(artist.userAdded);assertFalse(catalog.isSaved(artist.id))
        val concert=show(1).copy(title="여전히 소란스럽게 Vol.03 노브레인 with 안예은, 터치드",cast="이성우, 안예은 등")
        assertEquals(Match.CONFIRMED,ArtistMatcher.match(artist,concert).match)
        catalog.confirm(artist.id,listOf(concert))
        val reopened=ArtistCatalog(context)
        assertTrue(reopened.isSaved(artist.id));assertTrue(reopened.byId(artist.id)!!.verifiedQueries.contains("터치드"))
        reopened.editAliases(artist.id,listOf("터치드","TOUCHED"))
        assertEquals(listOf("터치드","TOUCHED"),ArtistCatalog(context).byId(artist.id)!!.aliases)
    }
    @Test fun searchTabOffersANameThatWasNotInTheRegisteredList() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { ViewModelProvider(it)[MyConcertViewModel::class.java].navigate(Route("search"),false) }
            onView(withHint("아티스트 이름을 입력하세요")).perform(replaceText("터치드"),closeSoftKeyboard())
            onView(withText("‘터치드’ 공연 직접 검색")).check(matches(isDisplayed())).perform(click())
            onView(withText("검색 표기 · 터치드")).check(matches(isDisplayed()))
        }
    }
}

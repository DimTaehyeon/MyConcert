package edu.taehyeon.myconcert

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class XmlLayoutRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun setup() {
        context.getSharedPreferences("myconcert", 0).edit().clear()
            .putBoolean("onboarded", true).putBoolean("location_prompted", true)
            .putStringSet("favorites", setOf("ive", "nmixx")).commit()
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun fixture() = Concert("XML-QA", "XML 전환 검증 공연", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15),
        "검증 공연장", area = "서울", genre = "대중음악", cast = "NMIXX", time = "토요일 18:00", runtime = "120분", age = "8세 이상", price = "99,000원",
        address = "서울특별시 검증로 1", links = listOf(TicketLink("공식 예매처", "https://example.com/tickets")), matchReason = "공연명에 공식 아티스트명 일치", detailed = true)
    private fun detail(scenario: ActivityScenario<MainActivity>, stale: Boolean = true, concert: Concert = fixture()) {
        scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[MyConcertViewModel::class.java]
            model.navigate(Route("search"), false) // Cancel any pending request before injecting a deterministic response.
            model.route.value = Route("detail", concert = concert)
            model.state.value = LoadState(detail = concert, staleDetail = stale)
        }
        instrumentation.waitForIdleSync()
    }
    private fun screenshot(name: String) {
        Thread.sleep(500)
        instrumentation.waitForIdleSync()
        val folder = File(context.getExternalFilesDir(null), "qa130").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun cachedNoticeHasTitlePeriodSpacingAndMatchReasonIsAbsent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900); detail(scenario)
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val notice = root.findViewById<View>(R.id.stale_notice)
                val genre = root.findViewById<View>(R.id.detail_genre)
                val title = root.findViewById<View>(R.id.detail_title)
                val period = root.findViewById<View>(R.id.detail_period)
                assertEquals(View.VISIBLE, notice.visibility)
                assertEquals("Notice gap should match title-to-period gap", period.top - title.bottom, genre.top - notice.bottom)
                assertEquals((12 * activity.resources.displayMetrics.density).toInt(), genre.top - notice.bottom)
                assertFalse(all(root).filterIsInstance<TextView>().any { it.text.contains("공연명에 공식 아티스트명 일치") })
                val fields = root.findViewById<View>(R.id.detail_fields)
                val calendar = root.findViewById<View>(R.id.add_calendar)
                assertEquals("Region fields should be immediately followed by calendar", (12 * activity.resources.displayMetrics.density * 2).toInt(), calendar.top - fields.bottom)
                assertEquals("서울", root.findViewById<TextView>(R.id.value_area).text.toString())
            }
            screenshot("01-cached-detail")
            detail(scenario, stale = false)
            scenario.onActivity { activity ->
                val notice = activity.findViewById<View>(R.id.stale_notice)
                assertEquals(View.GONE, notice.visibility)
                assertEquals("Hidden notice must leave no gap", 0, activity.findViewById<View>(R.id.detail_genre).top)
            }
            screenshot("02-fresh-detail")
        }
    }
    @Test fun detailButtonsPreserveShareCalendarAndBookingIntents() {
        val intents = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                intents += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900); detail(scenario)
            instrumentation.addMonitor(monitor)
            try {
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.toolbar_action).performClick()
                    activity.findViewById<View>(R.id.add_calendar).performClick()
                    all(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().single { it.text.toString() == "공식 예매처에서 예매하기  ↗" }.performClick()
                    activity.findViewById<View>(R.id.open_kopis).performClick()
                }
            } finally { instrumentation.removeMonitor(monitor) }
        }
        val share = intents.single { it.action == Intent.ACTION_CHOOSER }.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, share.action)
        assertTrue(share.getStringExtra(Intent.EXTRA_TEXT)!!.contains("XML 전환 검증 공연"))
        val calendar = intents.single { it.action == Intent.ACTION_INSERT }
        assertEquals(CalendarContract.Events.CONTENT_URI, calendar.data)
        assertEquals("XML 전환 검증 공연", calendar.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("검증 공연장", calendar.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertTrue(calendar.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        val zone = ZoneId.of("Asia/Seoul")
        assertEquals(fixture().start.atStartOfDay(zone).toInstant().toEpochMilli(), calendar.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(fixture().end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), calendar.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertEquals(setOf("https://example.com/tickets", "https://www.kopis.or.kr"), intents.filter { it.action == Intent.ACTION_VIEW }.map { it.dataString }.toSet())
    }
    @Test fun eachRouteUsesItsXmlScreenAndRetainsFavoritesAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            for ((route, id) in listOf(Route("home") to R.id.screen_home, Route("search") to R.id.screen_search,
                Route("settings") to R.id.screen_preferences, Route("artist", "ive") to R.id.screen_artist,
                Route("list") to R.id.screen_list, Route("region") to R.id.screen_region)) {
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[MyConcertViewModel::class.java]
                    model.navigate(Route("search"), false); model.route.value = route
                    model.state.value = LoadState(result = SearchResult(emptyList()))
                    assertNotNull(activity.findViewById<View>(id))
                    assertEquals(setOf("ive", "nmixx"), model.favoriteIds)
                    assertNotNull(activity.findViewById<View>(R.id.github_link))
                }
                instrumentation.waitForIdleSync(); Thread.sleep(150)
                scenario.onActivity { activity ->
                    val root = activity.findViewById<View>(R.id.app_root)
                    val bars = androidx.core.view.ViewCompat.getRootWindowInsets(root)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    assertTrue("Top system bar inset must survive navigation", root.paddingTop >= bars.top)
                    assertTrue("Bottom system bar inset must survive navigation", root.paddingBottom >= bars.bottom)
                }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.screen_region))
                assertEquals(setOf("ive", "nmixx"), ViewModelProvider(activity)[MyConcertViewModel::class.java].favoriteIds)
            }
        }
    }
    @Test fun missingDetailValuesAndEmptyBookingLinksStillShowFallbacks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            detail(scenario, false, fixture().copy(cast = "", time = "", runtime = "", age = "", price = "", area = "", address = "", links = emptyList()))
            scenario.onActivity { activity ->
                listOf(R.id.value_cast, R.id.value_time, R.id.value_runtime, R.id.value_age, R.id.value_price, R.id.value_area).forEach { id ->
                    assertEquals("KOPIS 미등록", activity.findViewById<TextView>(id).text.toString())
                }
                assertEquals(View.GONE, activity.findViewById<View>(R.id.detail_address).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.detail_poster).visibility)
                assertTrue(all(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text.contains("등록된 예매 링크가 없어요.") })
            }
        }
    }
}

package edu.taehyeon.myconcert

import android.content.pm.ActivityInfo
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import org.hamcrest.Matchers.containsString

@RunWith(AndroidJUnit4::class)
class QaAdditionalTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        context.getSharedPreferences("myconcert",0).edit().clear().putBoolean("onboarded",true)
            .putBoolean("location_prompted",true).commit()
    }
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val folder=File(context.getExternalFilesDir(null),"qa3").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
    @Test fun preferencesFilterSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(900)
            scenario.onActivity { ViewModelProvider(it)[MyConcertViewModel::class.java].navigate(Route("settings"),false) }
            onView(withHint("한글·영문 아티스트 이름 검색")).perform(replaceText("아이브"),closeSoftKeyboard())
            scenario.recreate()
            onView(withHint("한글·영문 아티스트 이름 검색")).check(matches(withText("아이브")))
            onView(withContentDescription("아이브, 그룹")).check(matches(isDisplayed()))
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(700)
            onView(withHint("한글·영문 아티스트 이름 검색")).check(matches(withText("아이브")))
            screenshot("08-filter-after-rotation")
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    @Test fun cachedSearchShowsOriginalSavedTimeAndSource() {
        // Seed one unique cache entry, so this test never needs an external API request.
        val artist=Artist("qa-cache","QA캐시진단전용","","QA","직접 검색",listOf("QA캐시진단전용"))
        val day=LocalDate.of(2026,11,11)
        val params=mapOf("stdate" to "20261111","eddate" to "20261111","shprfnm" to artist.name,"cpage" to "1","rows" to "100")
        val query=params.toSortedMap().entries.joinToString("&") { (key,value) -> "$key=${URLEncoder.encode(value,"UTF-8")}" }
        val hash=MessageDigest.getInstance("SHA-256").digest("pblprfr?$query".toByteArray()).joinToString("") { "%02x".format(it) }
        val file=File(File(context.filesDir,"kopis-cache").apply { mkdirs() },"$hash.xml")
        file.writeText("<dbs/>");assertTrue(file.setLastModified(System.currentTimeMillis()-600_000))
        val savedAt=file.lastModified() // Older Android filesystems may store whole seconds.
        val repo=KopisRepository(context)
        try {
            val result=repo.search(listOf(artist),day,day)
            val folder=File(context.getExternalFilesDir(null),"qa3").apply { mkdirs() }
            File(folder,"cache-source-diagnostic.txt").writeText("cached=${result.cached}\nactualCacheTime=$savedAt\ndisplayedTime=${result.savedAt}\n")
            assertTrue("A cached response must be identified as saved information",result.cached)
            assertEquals("A cached response must retain the original saved time",savedAt,result.savedAt)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(900)
                scenario.onActivity { activity ->
                    val model=ViewModelProvider(activity)[MyConcertViewModel::class.java]
                    model.navigate(Route("search"),false);model.route.value=Route("artist","ive")
                    model.state.value=LoadState(result=result)
                }
                val time=Instant.ofEpochMilli(savedAt).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
                onView(withText(containsString("KOPIS 제공 · 저장된 정보 $time"))).perform(scrollTo()).check(matches(isDisplayed()))
                screenshot("09-cached-source-time")
            }
        } finally { repo.close();file.delete() }
    }
    @Test fun refreshUsesNetworkAndOfflineFallbackRetainsSavedMetadata() {
        val day=LocalDate.of(2026,11,11)
        val artist=Artist("qa-refresh","QA새로고침진단","","QA","직접 검색",listOf("QA새로고침진단"))
        val params=mapOf("stdate" to "20261111","eddate" to "20261111","shprfnm" to artist.name,"cpage" to "1","rows" to "100")
        val query=params.toSortedMap().entries.joinToString("&") { (key,value) -> "$key=${URLEncoder.encode(value,"UTF-8")}" }
        val hash=MessageDigest.getInstance("SHA-256").digest("pblprfr?$query".toByteArray()).joinToString("") { "%02x".format(it) }
        val file=File(File(context.filesDir,"kopis-cache").apply { mkdirs() },"$hash.xml")
        file.writeText("<dbs/>");assertTrue(file.setLastModified(System.currentTimeMillis()-600_000))
        val oldTime=file.lastModified()
        val calls=AtomicInteger()
        val online=KopisRepository(context) { url ->
            calls.incrementAndGet()
            object:HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream()=ByteArrayInputStream("<dbs/>".toByteArray())
            }
        }
        try {
            val reused=online.search(listOf(artist),day,day)
            assertTrue(reused.cached);assertEquals(oldTime,reused.savedAt);assertEquals(0,calls.get())
            val before=System.currentTimeMillis()
            val refreshed=online.search(listOf(artist),day,day,force=true)
            assertFalse(refreshed.cached);assertTrue(refreshed.savedAt>=before);assertEquals(1,calls.get())
            val refreshedAt=file.lastModified()
            assertTrue(refreshedAt>oldTime)
            val offline=KopisRepository(context) { throw IOException("QA simulated offline") }
            try {
                val fallback=offline.search(listOf(artist),day,day,force=true)
                assertTrue(fallback.cached);assertEquals(refreshedAt,fallback.savedAt)
                assertTrue(fallback.warnings.any { "저장된 정보" in it })
            } finally { offline.close() }
        } finally { online.close();file.delete() }
    }
}

package edu.taehyeon.myconcert

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtistSurveyTest {
    @Test fun validatesActualKopisSamplesWithProductionMatcher() {
        val gson=Gson()
        val artists:List<Artist> = gson.fromJson(File("src/main/assets/artists.json").readText(),object:TypeToken<List<Artist>>(){}.type)
        val report=JsonParser.parseString(File("../docs/artist-verification.json").readText()).asJsonObject
        fun value(o:com.google.gson.JsonObject,k:String)=o.get(k)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val details=report.getAsJsonArray("details").mapNotNull { item ->
            val o=item.asJsonObject; val start=Dates.parse(value(o,"prfpdfrom")) ?: return@mapNotNull null
            Concert(value(o,"mt20id"),value(o,"prfnm"),start,Dates.parse(value(o,"prfpdto")) ?: start,
                value(o,"fcltynm"),genre=value(o,"genrenm"),cast=value(o,"prfcast"),detailed=true)
        }.associateBy { it.id }
        val summaries=artists.map { a ->
            val queries=report.getAsJsonArray("results").filter { value(it.asJsonObject,"artistId")==a.id }
            val hits=queries.filter { result -> result.asJsonObject.getAsJsonArray("samples")?.any { sample ->
                val s=sample.asJsonObject
                val c=details[value(s,"mt20id")] ?: return@any false
                ArtistMatcher.match(a,c).match==Match.CONFIRMED
            } == true }.map { value(it.asJsonObject,"query") }.distinct()
            mapOf("artistId" to a.id,"positiveQueries" to hits,"errors" to queries.count { it.asJsonObject.has("error") })
        }
        val output=File("build/reports/verified-artists.json");output.parentFile?.mkdirs();output.writeText(gson.toJson(summaries))
        val nmixx=artists.single { it.id=="nmixx" }
        val n=details.values.first { it.title.startsWith("NMIXX") && it.cast.isNotBlank() }
        assertEquals(Match.CONFIRMED,ArtistMatcher.match(nmixx,n).match)
        val ive=artists.single { it.id=="ive" }
        details.values.filter { it.title.contains("LIVE") && !ArtistMatcher.containsIdentity(it.title,"IVE") }.forEach {
            assertNotEquals(it.title,Match.CONFIRMED,ArtistMatcher.match(ive,it).match)
        }
        assertTrue("Expected many independently verified artist queries",summaries.count { (it["positiveQueries"] as List<*>).isNotEmpty() }>=15)
    }
}

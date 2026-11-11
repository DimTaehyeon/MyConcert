package edu.taehyeon.myconcert

import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.File
import com.google.gson.JsonParser

class ArtistDiscoveryTest {
    private fun show(cast:String)=Concert("PF1","실리카겔 LIVE",LocalDate.of(2026,11,11),LocalDate.of(2026,11,11),"공연장",cast=cast)
    @Test fun stableIdentityIgnoresSpacingCaseAndWidth() {
        assertEquals(ArtistDiscovery.id("Silica Gel"),ArtistDiscovery.id(" SILICA  GEL "))
        assertEquals(ArtistDiscovery.id("QWER"),ArtistDiscovery.id("ＱＷＥＲ"))
        assertNotEquals(ArtistDiscovery.id("QWER"),ArtistDiscovery.id("TOUCHED"))
    }
    @Test fun acceptsArtistNamesAndRejectsEmptyGenericSearches() {
        assertEquals("실리카겔",ArtistDiscovery.validateName("  실리카겔  "))
        assertEquals("츄",ArtistDiscovery.validateName("츄"))
        listOf(""," ","!!!","콘서트","LIVE","a".repeat(61)).forEach {
            assertTrue(it,runCatching { ArtistDiscovery.validateName(it) }.isFailure)
        }
    }
    @Test fun individualGroupMembersAreNeverLearnedAsAliases() {
        assertEquals(listOf("실리카겔"),ArtistDiscovery.observedAliases("실리카겔",listOf(show("김한주, 김춘추, 최웅희, 김건재"))))
    }
    @Test fun learnsOnlyExplicitBilingualCastIdentities() {
        assertEquals(listOf("실리카겔","Silica Gel"),ArtistDiscovery.observedAliases("실리카겔",listOf(show("실리카겔 (Silica Gel), QWER"))))
        assertEquals(listOf("Silica Gel","실리카겔"),ArtistDiscovery.observedAliases("Silica Gel",listOf(show("실리카겔(Silica Gel)"))))
    }
    @Test fun titlesVenuesAndCastRolesNeverBecomeArtistAliases() {
        val show=show("실리카겔(보컬), 실리카겔(김한주), 실리카겔(Vocal)").copy(title="실리카겔(SEOUL)",venue="Silica Hall")
        assertEquals(listOf("실리카겔"),ArtistDiscovery.observedAliases("실리카겔",listOf(show)))
    }
    @Test fun unknownArtistsMatchRealKopisSamplesWithoutBuiltinCatalogEntries() {
        val report=JsonParser.parseString(File("../docs/qa-artist-discovery.json").readText()).asJsonObject
        val details=report.getAsJsonArray("details").map { it.asJsonObject }
        listOf("터치드","브로콜리너마저").forEach { name ->
            val artist=Artist(ArtistDiscovery.id(name),name,"직접 검색",name.take(2),"직접 검색",listOf(name),userAdded=true)
            val sample=details.first { it.get("prfnm").asString.contains(name) }
            val cast=sample.get("prfcast")?.asString.orEmpty()
            val date=Dates.parse(sample.get("prfpdfrom").asString)!!
            val concert=Concert(sample.get("mt20id").asString,sample.get("prfnm").asString,date,date,sample.get("fcltynm").asString,
                cast=cast,genre=sample.get("genrenm").asString)
            assertEquals(Match.CONFIRMED,ArtistMatcher.match(artist,concert).match)
        }
    }
}

package edu.taehyeon.myconcert

import com.google.gson.JsonParser
import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtistProfileParserTest {
    private val ive=Artist("ive","아이브","IVE","IV","그룹",listOf("아이브","IVE"))
    private val photo="https://search.pstatic.net/common?type=a&amp;src=http%3A%2F%2Fsstatic.naver.net%2Fpeople%2FprofileImg%2Five.jpg"
    private fun modern(name:String="IVE",image:String=photo,details:String="")="""
        <div class="api_subject_bx"><div class="_cmTitleArea_hash"><a class="_depthTitle_hash"><strong>$name</strong></a></div>
        <div class="_cmBasicInfoArea_hash"><div class="_thumbBox_hash"><img src="$image"></div>$details</div></div>
    """
    @Test fun extractsExactGroupProfileAndDecodesHtmlEntities() {
        val result=ArtistProfileParser.parse(modern(),ive)!!
        assertEquals("IVE",result.matchedName);assertFalse("&amp;" in result.imageUrl)
        assertTrue(result.sourceUrl.startsWith("https://search.naver.com/"))
    }
    @Test fun excludesMembersOtherNamesAndBlogPictures() {
        assertNull(ArtistProfileParser.parse(modern("장원영"),ive))
        assertNull(ArtistProfileParser.parse("<div class='blog_profile'><strong>IVE</strong><img src='$photo'></div>",ive))
        assertNull(ArtistProfileParser.parse(modern(details="<dl><dt>출생</dt><dd>2004</dd></dl>"),ive))
    }
    @Test fun rejectsAmbiguousProfilesAndUntrustedImageLocations() {
        assertNull(ArtistProfileParser.parse(modern()+modern(image=photo.replace("ive.jpg","other.jpg")),ive))
        assertNull(ArtistProfileParser.parse(modern(image="https://example.com/people/ive.jpg"),ive))
        listOf("http://sstatic.naver.net/people/ive.jpg","https://search.pstatic.net.evil.test/people/ive.jpg","file:///profile.jpg","data:image/png;base64,AAAA").forEach {
            assertFalse(it,ArtistProfileParser.isPhotoUrl(it))
        }
    }
    @Test fun supportsPreviousNaverProfileLayoutAndPortraitPaths() {
        val html="""<section class="_au_people_content_wrap"><div class="cm_top_wrap"><span class="area_text_title"><strong>IVE</strong></span></div>
            <div class="cm_content_wrap"><div class="detail_info"><a class="thumb"><img src="$photo"></a></div></div></section>"""
        assertNotNull(ArtistProfileParser.parse(html,ive))
        assertTrue(ArtistProfileParser.isPhotoUrl("https://search.pstatic.net/common?src=http%3A%2F%2Fsstatic.naver.net%2Fpeople%2Fportrait%2F202109%2Fperson.jpg"))
    }
    @Test fun customNamesNeedMatchingMusicianProfile() {
        val custom=ive.copy(id="custom_qa",userAdded=true,kind="직접 검색")
        assertNull(ArtistProfileParser.parse(modern(details="<dl><dt>데뷔</dt><dd>드라마</dd></dl>"),custom))
        assertNotNull(ArtistProfileParser.parse(modern(details="<dl><dt>데뷔</dt><dd>싱글 앨범</dd></dl>"),custom))
    }
    @Test fun allRegisteredArtistsHaveVerifiedRemoteUrlsWithoutImageFiles() {
        val artists=JsonParser.parseString(File("src/main/assets/artists.json").readText()).asJsonArray.map { it.asJsonObject["id"].asString }.toSet()
        val profiles=JsonParser.parseString(File("src/main/assets/artist-profiles.json").readText()).asJsonArray
        assertEquals(artists,profiles.map { it.asJsonObject["artistId"].asString }.toSet())
        profiles.forEach { assertTrue(it.asJsonObject["artistId"].asString,ArtistProfileParser.isPhotoUrl(it.asJsonObject["imageUrl"].asString)) }
    }
}

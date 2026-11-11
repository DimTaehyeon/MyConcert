package edu.taehyeon.myconcert.data

import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class ArtistProfilePhoto(val imageUrl:String,val sourceUrl:String,val matchedName:String)

/** Extracts only an exact-name knowledge profile, never news/blog/related-person thumbnails. */
object ArtistProfileParser {
    fun displayImageUrl(value:String)=Regex("([?&])size=[^&]*").replace(value) { "${it.groupValues[1]}size=512" }
    fun searchUrl(artist:Artist,overview:Boolean=false)= "https://search.naver.com/search.naver?query="+
        URLEncoder.encode(if(overview) artist.name else "${artist.name} 프로필","UTF-8")

    fun isPhotoUrl(value:String):Boolean = runCatching {
        val uri=URI(value)
        if(uri.scheme!="https" || uri.host !in setOf("search.pstatic.net","sstatic.naver.net")) return false
        val original=uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("src=") }
            ?.substringAfter('=')?.let { URLDecoder.decode(it,"UTF-8") } ?: value
        val source=URI(original)
        source.host in setOf("sstatic.naver.net","ssl.pstatic.net") &&
            source.path.startsWith("/people/") && Regex("(?i)\\.(jpg|jpeg|png|webp)$").containsMatchIn(source.path)
    }.getOrDefault(false)

    fun parse(html:String,artist:Artist,sourceUrl:String=searchUrl(artist)):ArtistProfilePhoto? {
        val names=(artist.aliases+artist.name+artist.english).map(ArtistMatcher::normalize).filter(String::isNotBlank).toSet()
        val document=Jsoup.parse(html,sourceUrl)
        val candidates=document.select("[class*=cmBasicInfoArea], section._au_people_content_wrap .detail_info").mapNotNull { basic ->
            val card=basic.closest(".api_subject_bx, section._au_people_content_wrap") ?: return@mapNotNull null
            val name=card.selectFirst("[class*=depthTitle] strong, [class*=cmTopTitle] [class*=titleText], .cm_top_wrap .area_text_title strong")?.text() ?: return@mapNotNull null
            val subtitle=card.select("[class*=cmTopSubTitle] span, .sub_title .txt").map { it.text() }
            val aliases=basic.select("dt").filter { it.text().replace(" ","")=="다른이름" }
                .flatMap { it.nextElementSibling()?.takeIf { dd -> dd.tagName()=="dd" }?.text().orEmpty().split(Regex("[,·/()]")) }
            val identity=(listOf(name)+aliases+subtitle.filterNot { it in listOf("가수","밴드","배우","모델","방송인","프로필") })
                .map(ArtistMatcher::normalize)
            if(identity.none { it in names }) return@mapNotNull null
            if(artist.kind in listOf("그룹","밴드") && basic.select("dt").any { it.text()=="출생" }) return@mapNotNull null
            if(artist.userAdded && !Regex("앨범|가수|밴드|싱어송라이터|보컬|뮤지션|소속그룹").containsMatchIn(basic.text()+subtitle.joinToString(" "))) return@mapNotNull null
            val image=basic.selectFirst("[class*=thumbBox] img[src], .thumb img[src]")?.absUrl("src") ?: return@mapNotNull null
            if(!isPhotoUrl(image)) return@mapNotNull null
            ArtistProfilePhoto(image,sourceUrl,name)
        }.distinctBy { it.imageUrl }
        return candidates.singleOrNull()
    }
}

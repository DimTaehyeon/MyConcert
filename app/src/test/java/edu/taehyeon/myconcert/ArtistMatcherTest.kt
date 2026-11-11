package edu.taehyeon.myconcert

import edu.taehyeon.myconcert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ArtistMatcherTest {
    private val ive = Artist("ive","아이브","IVE","IV","그룹",listOf("아이브","IVE"),listOf("안유진", "장원영", "가을", "레이", "리즈", "이서"))
    private val bts = Artist("bts","BTS","방탄소년단","BTS","그룹",listOf("BTS","방탄소년단"),listOf("RM|김남준","진|김석진","슈가|민윤기"))
    private fun concert(title: String, cast: String = "", genre: String = "대중음악") = Concert("PF1",title,LocalDate.of(2026,11,11),LocalDate.of(2026,11,11),"공연장",genre=genre,cast=cast,detailed=true)
    @Test fun rejectsLiveArchiveAndDrive() {
        listOf("LIVE IN SEOUL", "DRIVE LIVE", "ARCHIVE 콘서트", "라이브 콘서트", "아카이브 공연").forEach {
            assertEquals(it,Match.REJECTED,ArtistMatcher.match(ive,concert(it)).match)
        }
    }
    @Test fun acceptsOfficialKoreanAndEnglishWithSpacing() {
        listOf("2026 IVE WORLD TOUR", "아이브 콘서트", "아이브의 팬미팅", "IVE [SHOW WHAT I HAVE]").forEach {
            assertEquals(it,Match.CONFIRMED,ArtistMatcher.match(ive,concert(it)).match)
        }
    }
    @Test fun recognizesGroupInFestivalCast() = assertEquals(Match.CONFIRMED,ArtistMatcher.match(ive,concert("뮤직 페스티벌","IVE, DAY6")).match)
    @Test fun recognizesMultipleDistinctMembers() = assertEquals(Match.CONFIRMED,ArtistMatcher.match(ive,concert("뮤직 페스티벌","안유진, 장원영, 가을, 레이, 리즈, 이서")).match)
    @Test fun rejectsSingleMemberSoloEvenWithGroupLabel() {
        listOf("장원영 단독 팬미팅","IVE 장원영 솔로 팬미팅").forEach {
            assertEquals(Match.REJECTED,ArtistMatcher.match(ive,concert(it,"장원영")).match)
        }
    }
    @Test fun oneMemberIsNeverGroupEvidence() = assertEquals(Match.REJECTED,ArtistMatcher.match(ive,concert("뮤직 페스티벌","안유진")).match)
    @Test fun membershipLabelIsNotGroupAppearance() = assertEquals(Match.REJECTED,ArtistMatcher.match(ive,concert("IVE 장원영 솔로 팬미팅","IVE 장원영")).match)
    @Test fun memberAliasesDoNotCountSamePersonTwice() = assertEquals(Match.REJECTED,ArtistMatcher.match(bts,concert("뮤직 페스티벌","진 (김석진)")).match)
    @Test fun memberRealNamesAreRecognized() = assertEquals(Match.CONFIRMED,ArtistMatcher.match(bts,concert("뮤직 페스티벌","김남준, 김석진, 민윤기")).match)
    @Test fun excludesTributesAndScreenings() {
        listOf("IVE 트리뷰트", "아이브 커버댄스", "IVE 실황상영회").forEach {
            assertEquals(Match.REJECTED,ArtistMatcher.match(ive,concert(it)).match)
        }
    }
    @Test fun tourAloneNeedsConfirmation() {
        val a=ive.copy(tours=listOf("SHOW WHAT I HAVE"))
        assertEquals(Match.UNCERTAIN,ArtistMatcher.match(a,concert("SHOW WHAT I HAVE")).match)
    }
    @Test fun musicalHomonymNeedsConfirmation() = assertEquals(Match.UNCERTAIN,ArtistMatcher.match(ive,concert("아이브",genre="뮤지컬")).match)
    @Test fun jinDoesNotMatchKwonJinAh() {
        val jin=Artist("jin","진","JIN","JN","솔로",listOf("JIN","진","김석진"))
        assertEquals(Match.UNCERTAIN,ArtistMatcher.match(jin,concert("KWON JIN AH LIVE","권진아")).match)
        assertEquals(Match.CONFIRMED,ArtistMatcher.match(jin,concert("#RUNSEOKJIN_EP.TOUR","김석진")).match)
    }
    @Test fun childrenAreNotGidle() {
        val a=Artist("gidle","i-dle","아이들","ID","그룹",listOf("아이들","(여자)아이들","i-dle"))
        assertEquals(Match.UNCERTAIN,ArtistMatcher.match(a,concert("별을 찾는 아이들","합창단")).match)
        assertEquals(Match.CONFIRMED,ArtistMatcher.match(a,concert("i-dle WORLD TOUR")).match)
    }
    @Test fun searchHandlesCaseAndPunctuation() {
        val day6=Artist("day6","DAY6","데이식스","D6","밴드",listOf("DAY6","데이식스"))
        assertEquals(listOf(day6), ArtistMatcher.findArtists(listOf(ive,day6),"  day 6  "))
        assertTrue(ArtistMatcher.findArtists(listOf(ive),"장원영").isEmpty())
    }
    @Test fun datesSplitAt31DaysWithoutGaps() {
        val from=LocalDate.of(2024,2,1);val to=LocalDate.of(2024,4,10)
        val windows=Dates.windows(from,to)
        assertEquals(from,windows.first().first);assertEquals(to,windows.last().second)
        windows.forEach { assertTrue(java.time.temporal.ChronoUnit.DAYS.between(it.first,it.second)<=30) }
        windows.zipWithNext().forEach { assertEquals(it.first.second.plusDays(1),it.second.first) }
    }
    @Test fun todayAndOngoingAreNotPast() {
        val today=LocalDate.of(2026,11,11)
        val ongoing=concert("진행 중").copy(start=today.minusDays(10),end=today.plusDays(1))
        val ended=concert("종료").copy(start=today.minusDays(2),end=today.minusDays(1))
        assertEquals(listOf(ongoing),Dates.sorted(listOf(ended,ongoing),false,today))
        assertEquals(listOf(ended),Dates.sorted(listOf(ongoing,ended),true,today))
    }
}

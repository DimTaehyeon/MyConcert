package edu.taehyeon.myconcert.data

import java.text.Normalizer
import java.util.Locale

/** Identity matching is separate from KOPIS's substring-only candidate search. */
object ArtistMatcher {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[\\s*·._’'‐‑–—-]+"), "")

    // Do not strip all punctuation: parentheses delimit cast identities and English tokens
    // must not match the tail of LIVE / DRIVE / ARCHIVE.
    fun containsIdentity(text: String, alias: String): Boolean {
        val value = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val needle = normalize(alias)
        if (needle.isEmpty()) return false
        val flexible = needle.map { Regex.escape(it.toString()) }.joinToString("[\\s*·._’'‐‑–—-]*")
        val pattern = Regex("(?<![\\p{L}\\p{N}])$flexible(?![\\p{L}\\p{N}])")
        if (pattern.containsMatchIn(value)) return true
        // Korean official names frequently attach a Korean particle or a concert suffix.
        if (needle.any { it in '가'..'힣' }) {
            val suffixes = "(?:의|와|과|콘서트|팬콘서트|팬미팅|단독|전국투어|월드투어|내한|라이브|공연)"
            return Regex("(?<![\\p{L}\\p{N}])$flexible[\\s]*$suffixes").containsMatchIn(value)
        }
        return false
    }

    fun match(artist: Artist, concert: Concert): MatchResult {
        val title = concert.title
        val cast = concert.cast
        val tribute = Regex("트리뷰트|헌정|커버|댄스커버|상영회|실황상영|tribute|cover\\s*(band|dance)", RegexOption.IGNORE_CASE)
        if (tribute.containsMatchIn(title)) return MatchResult(Match.REJECTED, "헌정·커버·상영 공연")
        val titleMatch = artist.aliases.any { containsIdentity(title, it) }
        val castMatch = artist.aliases.any { containsIdentity(cast, it) }
        val tourMatch = artist.tours.any { containsIdentity(title, it) }
        val membersInCast = artist.members.count { identity -> identity.split('|').any { containsIdentity(cast, it) } }
        val memberSolo = artist.members.any { identity -> identity.split('|').any { containsIdentity(title, it) } } &&
            Regex("solo|솔로|단독|팬미팅|fan.?meeting", RegexOption.IGNORE_CASE).containsMatchIn(title)
        if (artist.members.isNotEmpty() && memberSolo && membersInCast < 2)
            return MatchResult(Match.REJECTED, "멤버 개인 공연")
        if (castMatch) return MatchResult(Match.CONFIRMED, "출연진에 공식 아티스트명 일치")
        // Multiple member identities establish group participation; one member never does.
        if (artist.members.isNotEmpty() && membersInCast >= 2 && !memberSolo)
            return MatchResult(Match.CONFIRMED, "출연진의 그룹 멤버 2명 이상 일치")
        if (titleMatch) {
            val ambiguous = when (artist.id) {
                "jin" -> !containsIdentity(title, "김석진")
                "gidle" -> !listOf("(여자)아이들", "여자아이들", "(G)I-DLE", "G I-DLE", "i-dle").any { containsIdentity(title, it) }
                else -> false
            }
            if (ambiguous) return MatchResult(Match.UNCERTAIN, "동명이 가능한 이름 · 출연진 확인 필요")
            if (cast.isNotBlank() && artist.members.isNotEmpty() && membersInCast == 1)
                return MatchResult(Match.UNCERTAIN, "그룹명은 있으나 멤버 1명만 확인")
            // A title alone is usable only if it is not a non-music homonym.
            if (concert.genre.isNotBlank() && concert.genre !in listOf("대중음악", "복합"))
                return MatchResult(Match.UNCERTAIN, "다른 장르의 동명 공연")
            return MatchResult(Match.CONFIRMED, "공연명에 공식 아티스트명 일치")
        }
        if (tourMatch) return MatchResult(Match.UNCERTAIN, "투어명만 일치 · 출연진 확인 필요")
        return MatchResult(Match.REJECTED, "공식명·출연진 불일치")
    }

    fun findArtists(artists: List<Artist>, input: String): List<Artist> {
        val query = normalize(input.trim())
        if (query.isEmpty()) return artists
        return artists.filter { a -> (a.aliases + a.name + a.english).any { normalize(it).contains(query) } }
            .sortedBy { a -> if (a.aliases.any { normalize(it) == query }) 0 else 1 }
    }
}

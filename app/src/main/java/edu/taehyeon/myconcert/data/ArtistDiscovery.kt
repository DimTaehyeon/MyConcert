package edu.taehyeon.myconcert.data

import java.security.MessageDigest

/** A lookup confirms a query, never the identity of every person in the cast. */
object ArtistDiscovery {
    fun validateName(input: String): String {
        val name=input.trim().replace(Regex("\\s+")," ")
        require(name.length in 1..60 && name.any { it.isLetterOrDigit() }) { "아티스트 이름을 1~60자로 입력해 주세요." }
        require(ArtistMatcher.normalize(name) !in setOf("콘서트","공연","라이브","live","concert","페스티벌")) { "공연 종류 대신 아티스트 이름을 입력해 주세요." }
        return name
    }
    fun id(name: String): String = "custom_"+MessageDigest.getInstance("SHA-256")
        .digest(ArtistMatcher.normalize(name).toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }

    fun observedAliases(query: String, concerts: List<Concert>): List<String> {
        val result=linkedSetOf(validateName(query))
        concerts.forEach { concert ->
            // Only a cast identity that explicitly pairs two names can establish a bilingual alias.
            // Never learn a list of individual group members, tour names or venue names as group aliases.
            concert.cast.split(Regex("[,;\\n/]")).map(String::trim).forEach { identity ->
                if(ArtistMatcher.normalize(identity)==ArtistMatcher.normalize(query)) result+=identity
                val pair=Regex("^([^()（）]{1,60})\\s*[（(]([^()（）]{1,60})[）)]$").matchEntire(identity)
                if(pair!=null) {
                    val first=pair.groupValues[1].trim();val second=pair.groupValues[2].trim()
                    val other=when(ArtistMatcher.normalize(query)) {
                        ArtistMatcher.normalize(first) -> second
                        ArtistMatcher.normalize(second) -> first
                        else -> null
                    }
                    val bilingual=other!=null && ((query.any { it in '가'..'힣' } && other.any { it in 'A'..'Z' || it in 'a'..'z' } && other.none { it in '가'..'힣' }) ||
                        (query.any { it in 'A'..'Z' || it in 'a'..'z' } && query.none { it in '가'..'힣' } && other.any { it in '가'..'힣' }))
                    if(other!=null && bilingual && runCatching { validateName(other) }.isSuccess &&
                        !Regex("solo|솔로|멤버|보컬|기타|드럼|bass|vocal|guitar",RegexOption.IGNORE_CASE).containsMatchIn(other)) result+=other
                }
            }
        }
        return result.take(8)
    }
}

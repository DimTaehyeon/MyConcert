package edu.taehyeon.myconcert.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Artist(
    val id: String, val name: String, val english: String, val initials: String,
    val kind: String, val aliases: List<String>, val members: List<String> = emptyList(),
    val tours: List<String> = emptyList(), val source: String = "",
    val verifiedQueries: List<String> = emptyList(), val userAdded: Boolean = false
) {
    val queries: List<String> get() = (aliases + tours).distinct()
}

data class TicketLink(val name: String, val url: String)
data class Concert(
    val id: String, val title: String, val start: LocalDate, val end: LocalDate,
    val venue: String, val area: String = "", val genre: String = "", val status: String = "",
    val poster: String = "", val cast: String = "", val time: String = "",
    val price: String = "", val age: String = "", val runtime: String = "",
    val links: List<TicketLink> = emptyList(), val artistIds: Set<String> = emptySet(),
    val matchReason: String = "", val detailed: Boolean = false,
    val facilityId: String = "", val address: String = ""
)

enum class Match { CONFIRMED, UNCERTAIN, REJECTED }
data class MatchResult(val match: Match, val reason: String)
data class SearchResult(
    val concerts: List<Concert>, val uncertain: Int = 0, val warnings: List<String> = emptyList(),
    val cached: Boolean = false, val savedAt: Long = System.currentTimeMillis()
)

object Dates {
    val api: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    val display: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    fun parse(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim().replace('.', '-').take(10))
    }.getOrNull()
    fun windows(from: LocalDate, to: LocalDate): List<Pair<LocalDate, LocalDate>> {
        require(!to.isBefore(from))
        val result = mutableListOf<Pair<LocalDate, LocalDate>>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val end = minOf(cursor.plusDays(30), to)
            result += cursor to end
            cursor = end.plusDays(1)
        }
        return result
    }
    fun upcoming(c: Concert, today: LocalDate): Boolean = !c.end.isBefore(today)
    fun sorted(items: List<Concert>, past: Boolean, today: LocalDate): List<Concert> =
        items.filter { upcoming(it, today) != past }.let { list ->
            if (past) list.sortedWith(compareByDescending<Concert> { it.end }.thenBy { it.id })
            else list.sortedWith(compareBy<Concert> { maxOf(it.start, today) }.thenBy { it.id })
        }
}

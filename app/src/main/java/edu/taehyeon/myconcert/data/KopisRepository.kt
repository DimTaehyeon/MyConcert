package edu.taehyeon.myconcert.data

import android.content.Context
import edu.taehyeon.myconcert.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KopisRepository(context: Context,private val openConnection:(URL)->HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private val directory = File(context.filesDir, "kopis-cache").apply { mkdirs() }
    private val workers = Executors.newFixedThreadPool(4)
    private val memory = ConcurrentHashMap<String, Concert>()
    private val rateLock = Any()
    private var lastRequest = 0L

    private fun pace() {
        synchronized(rateLock) {
            val wait = 550 - (android.os.SystemClock.elapsedRealtime() - lastRequest)
            if (wait > 0) Thread.sleep(wait)
            lastRequest = android.os.SystemClock.elapsedRealtime()
        }
    }

    private class Session {
        val warnings = ConcurrentHashMap.newKeySet<String>()
        @Volatile var cached = false
        @Volatile var oldest = System.currentTimeMillis()
        @Synchronized fun useCache(savedAt:Long) {
            cached=true;oldest=minOf(oldest,savedAt)
        }
    }

    private fun <T> readCache(cache:File,session:Session,parse:(String)->T):T {
        val (result,savedAt)=synchronized(this) { parse(cache.readText()) to cache.lastModified() }
        session.useCache(savedAt)
        return result
    }

    private fun request(path: String, params: Map<String, String>, session: Session, force: Boolean): List<Concert> {
        return requestParsed(path,params,session,force) { KopisXml.parse(it,path!="pblprfr") }
    }
    private fun <T> requestParsed(path: String, params: Map<String,String>,session:Session,force:Boolean,parse:(String)->T): T {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        check(BuildConfig.KOPIS_API_KEY.isNotBlank()) { "KOPIS API 키가 설정되지 않았어요. local.properties를 확인해 주세요." }
        val query = params.toSortedMap().entries.joinToString("&") { (k,v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val cacheId = MessageDigest.getInstance("SHA-256").digest("$path?$query".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cache = File(directory, "$cacheId.xml")
        val detailed = path != "pblprfr"
        val ttl = if (detailed) TimeUnit.HOURS.toMillis(24) else TimeUnit.MINUTES.toMillis(15)
        if (!force && cache.exists() && System.currentTimeMillis() - cache.lastModified() < ttl) {
            return readCache(cache,session,parse)
        }
        try {
            val url = "https://www.kopis.or.kr/openApi/restful/$path?service=${URLEncoder.encode(BuildConfig.KOPIS_API_KEY, "UTF-8")}&$query"
            var response: String? = null
            for (attempt in 0..2) {
                pace()
                val connection = openConnection(URL(url))
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept", "application/xml")
                    val status = connection.responseCode
                    if (status == 200) {
                        response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        break
                    }
                    if (attempt == 2 || status !in listOf(400,429,500,502,503,504))
                        error("KOPIS 서버 연결 오류 ($status)")
                } finally { connection.disconnect() }
                Thread.sleep(1000L * (attempt + 1))
            }
            val xml = response ?: error("KOPIS 응답을 받지 못했어요.")
            val result = parse(xml)
            synchronized(this) { cache.writeText(xml) }
            return result
        } catch (e: Exception) {
            if (e is InterruptedException || Thread.currentThread().isInterrupted) throw InterruptedException()
            if (cache.exists()) {
                session.warnings += "연결이 원활하지 않아 저장된 정보를 표시해요."
                return readCache(cache,session,parse)
            }
            throw IllegalStateException(if (e is IllegalArgumentException) e.message else "공연 정보를 불러오지 못했어요. 인터넷 연결과 API 이용 상태를 확인해 주세요.")
        }
    }

    private fun lists(params: Map<String,String>, session: Session, force: Boolean): List<Concert> {
        val all = linkedMapOf<String,Concert>()
        for (page in 1..50) {
            val result = request("pblprfr", params + mapOf("cpage" to "$page", "rows" to "100"), session, force)
            result.forEach { all[it.id] = it }
            if (result.size < 100) return all.values.toList()
            if (page == 50) session.warnings += "조회량이 많아 일부 공연만 표시돼요. 기간을 줄여 다시 조회해 주세요."
        }
        return all.values.toList()
    }

    private fun <T> parallel(jobs: List<() -> T>): List<Result<T>> {
        val futures = jobs.map { job -> workers.submit(Callable { runCatching { job() } }) }
        return try { futures.map { it.get() } }
        catch (e: InterruptedException) { futures.forEach { it.cancel(true) }; throw e }
    }

    fun search(artists: List<Artist>, from: LocalDate, to: LocalDate, region: String? = null,
               expanded: Boolean = false, force: Boolean = false, progress: (String) -> Unit = {}): SearchResult {
        val session = Session()
        val candidates = ConcurrentHashMap<String,Concert>()
        val windows = Dates.windows(from, to)
        val jobs = mutableListOf<() -> Unit>()
        windows.forEach { (start,end) ->
            val base = mutableMapOf("stdate" to start.format(Dates.api), "eddate" to end.format(Dates.api))
            if (artists.isEmpty() || expanded) {
                val codes=region?.let(Regions::apiCodes) ?: listOf(null)
                codes.forEach { code ->
                    val regional=if(code==null) base else base+("signgucode" to code)
                    listOf("CCCD", "EEEA").forEach { genre -> jobs += {
                        lists(regional + ("shcate" to genre), session, force).forEach { candidates[it.id] = it }
                    } }
                }
            } else {
                region?.let { base["signgucode"] = it }
                artists.flatMap { it.queries }.distinct().forEach { query ->
                    jobs += { lists(base + ("shprfnm" to query), session, force).forEach { candidate ->
                        // Drop substring collisions before spending a detail request on LIVE / PREMIUM.
                        if (artists.any { a -> (a.aliases + a.tours).any { ArtistMatcher.containsIdentity(candidate.title,it) } })
                            candidates[candidate.id] = candidate
                    } }
                }
            }
        }
        var finished = 0
        val lock = Any()
        val outcome = parallel(jobs.map { job -> {
            try { job() } finally { synchronized(lock) { finished++; progress("공연 후보 조회 중 · $finished/${jobs.size}") } }
        } })
        outcome.filter { it.isFailure }.forEach { session.warnings += it.exceptionOrNull()?.message ?: "일부 조회 실패" }
        if (outcome.isNotEmpty() && outcome.all { it.isFailure }) throw IllegalStateException(session.warnings.first())

        val matching = mutableListOf<Concert>()
        var uncertain = 0
        if (artists.isEmpty()) {
            if(region in listOf("29","46")) {
                progress("통합 지역 공연장의 주소를 확인하고 있어요 · ${candidates.size}개 후보")
                val addresses=parallel(candidates.values.map { candidate -> {
                    val detail=if(candidate.facilityId.isBlank()) request("pblprfr/${candidate.id}",emptyMap(),session,force).firstOrNull()
                        ?: error("상세 정보 미등록") else candidate
                    check(detail.facilityId.isNotBlank()) { "공연시설 ID 미등록" }
                    val address=requestParsed("prfplc/${detail.facilityId}",emptyMap(),session,force,KopisXml::facilityAddress)
                    detail.copy(address=address)
                } })
                addresses.forEach { outcome ->
                    val concert=outcome.getOrNull()
                    val area=concert?.let { Regions.fromAddress(null,it.address) }
                    if(concert==null || area==null) session.warnings += "주소를 확인하지 못한 통합 지역 공연은 제외했어요. 다시 조회해 주세요."
                    else if(area.code==region) matching+=concert
                }
            } else matching += candidates.values
        } else {
            progress("출연진을 확인하고 있어요 · ${candidates.size}개 후보")
            val completed = java.util.concurrent.atomic.AtomicInteger()
            val details = parallel(candidates.values.map { candidate -> {
                try {
                    request("pblprfr/${candidate.id}", emptyMap(), session, force).firstOrNull()
                        ?: throw IllegalStateException("일부 공연의 상세 정보가 비어 있어요.")
                } finally {
                    val count = completed.incrementAndGet()
                    if (count % 5 == 0 || count == candidates.size) progress("출연진 확인 중 · $count/${candidates.size}")
                }
            } })
            details.forEach { result ->
                val detail = result.getOrNull()
                if (detail == null) {
                    session.warnings += "상세 조회에 실패한 공연은 출연진을 검증할 수 없어 제외했어요. 다시 시도해 주세요."
                } else {
                    memory[detail.id] = detail
                    val matches = artists.associateWith { ArtistMatcher.match(it, detail) }
                    val confirmed = matches.filterValues { it.match == Match.CONFIRMED }
                    if (confirmed.isNotEmpty()) matching += detail.copy(
                        artistIds = confirmed.keys.map { it.id }.toSet(), matchReason = confirmed.values.first().reason)
                    else if (matches.values.any { it.match == Match.UNCERTAIN }) uncertain++
                }
            }
        }
        val filtered = matching.filter { !it.end.isBefore(from) && !it.start.isAfter(to) }
        // Bound persistent raw response cache without deleting user settings.
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2500)?.forEach { it.delete() }
        return SearchResult(filtered.distinctBy { it.id }, uncertain, session.warnings.sorted(), session.cached, session.oldest)
    }

    fun detail(concert: Concert, force: Boolean = false): Pair<Concert, Boolean> {
        val session = Session()
        val result = request("pblprfr/${concert.id}", emptyMap(), session, force).firstOrNull()
            ?: throw IllegalStateException("공연 상세 정보가 아직 등록되지 않았어요.")
        memory[result.id] = result
        return result.copy(artistIds = concert.artistIds, matchReason = concert.matchReason,address=concert.address) to session.cached
    }

    fun close() { workers.shutdownNow() }
}

package edu.taehyeon.myconcert

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import edu.taehyeon.myconcert.data.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class Route(val screen: String, val artistId: String? = null, val past: Boolean = false,
                 val concert: Concert? = null)
data class LoadState(val loading: Boolean = false, val progress: String = "", val result: SearchResult? = null,
                     val error: String? = null, val detail: Concert? = null, val staleDetail: Boolean = false)

class MyConcertViewModel(application: Application) : AndroidViewModel(application) {
    val catalog = ArtistCatalog(application)
    private val prefs = application.getSharedPreferences("myconcert", 0)
    val repo = KopisRepository(application)
    val today: LocalDate get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val route = MutableLiveData(Route(if (prefs.getBoolean("onboarded", false)) "home" else "onboarding"))
    val state = MutableLiveData(LoadState())
    private val history = mutableListOf<Route>()
    val favoriteIds: Set<String> get() = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
    val favorites: List<Artist> get() = catalog.artists.filter { it.id in favoriteIds }
    val dark: Boolean get() = prefs.getBoolean("dark", false)
    var searchText = ""
    var preferenceSearchText = ""
    var regionCode = prefs.getString("region_code", "11") ?: "11"
        private set
    var regionRevision = 0
        private set
    val locationMessage = MutableLiveData<String?>(null)
    val shouldSuggestLocation: Boolean get() = !prefs.getBoolean("location_prompted",false) && !prefs.contains("region_code")
    fun markLocationPrompted() { prefs.edit().putBoolean("location_prompted",true).apply() }
    fun selectRegion(code: String, fromLocation: Boolean = false) {
        if (Regions.byCode(code) == null) return
        regionRevision++
        regionCode=code
        prefs.edit().putString("region_code",code).apply()
        locationMessage.value=if(fromLocation) "현재 위치 기준 · ${Regions.byCode(code)?.name}" else null
        if(route.value?.screen=="region") load()
    }
    var days = 30L
    var expanded = false
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var task: Future<*>? = null
    private var generation = 0
    private val results = mutableMapOf<String, SearchResult>()

    fun toggleFavorite(id: String): Boolean {
        val set = favoriteIds.toMutableSet()
        if (id in set) set.remove(id) else {
            if (set.size >= 8) return false
            set.add(id)
        }
        prefs.edit().putStringSet("favorites", set).apply()
        if(id in set) catalog.persist(id)
        results.clear()
        return true
    }
    fun finishSetup() {
        preferenceSearchText = ""
        prefs.edit().putBoolean("onboarded", true).apply()
        history.clear()
        navigate(Route("home"), false)
    }
    fun toggleDark() { prefs.edit().putBoolean("dark", !dark).apply() }
    fun cancelSearch() {
        task?.cancel(true); generation++
        state.value = LoadState(error = "조회를 중단했어요. 기간을 줄이거나 다시 시도할 수 있어요.")
    }
    fun recent(): List<Artist> = prefs.getString("recent", "").orEmpty().split(',').mapNotNull(catalog::byId)
    fun removeRecent(id: String? = null) {
        prefs.edit().putString("recent", if (id == null) "" else recent().filter { it.id != id }.joinToString(",") { it.id }).apply()
    }
    fun selectArtist(artist: Artist) {
        if(catalog.isSaved(artist.id)) rememberRecent(artist)
        expanded = false
        navigate(Route("artist", artist.id))
    }
    private fun rememberRecent(artist:Artist) {
        val recent = (listOf(artist.id) + recent().map { it.id }).distinct().take(10)
        prefs.edit().putString("recent", recent.joinToString(",")).apply()
    }
    fun directSearch(input:String) = selectArtist(catalog.beginSearch(input))
    fun editSearchAliases(id:String,values:List<String>) {
        catalog.editAliases(id,values);results.clear();load(true)
    }
    fun deleteCustomArtist(id:String):Boolean {
        if(catalog.byId(id)?.userAdded!=true) return false
        task?.cancel(true);generation++
        if(!catalog.removeCustom(id)) return false
        prefs.edit().putStringSet("favorites",favoriteIds-id)
            .putString("recent",prefs.getString("recent","").orEmpty().split(',').filter { it!=id }.joinToString(",")).apply()
        history.removeAll { it.artistId==id };results.clear()
        if(route.value?.artistId==id) navigate(Route("search"),false) else load()
        return true
    }
    fun navigate(next: Route, push: Boolean = true) {
        task?.cancel(true)
        generation++
        if (push) route.value?.let { history += it }
        route.value = next
        load()
    }
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.removeAt(history.lastIndex)
        navigate(previous, false)
        return true
    }
    fun load(force: Boolean = false) {
        val current = route.value ?: return
        task?.cancel(true)
        val token = ++generation
        if (current.screen !in listOf("home", "artist", "list", "region", "detail")) {
            state.value = LoadState()
            return
        }
        val artists = current.artistId?.let { listOfNotNull(catalog.byId(it)) } ?: favorites
        if (current.screen != "region" && current.screen != "detail" && artists.isEmpty()) {
            state.value = LoadState(result = SearchResult(emptyList()))
            return
        }
        val key = "${artists.map { it.id+":"+it.aliases.joinToString("/") }}|$regionCode|${current.screen == "region"}|$days|$expanded|$today"
        if (!force && current.screen != "detail" && results[key] != null) {
            state.value = LoadState(result = results.getValue(key).copy(cached = true)); return
        }
        state.value = LoadState(loading = true, progress = "KOPIS 공연 정보를 불러오고 있어요")
        task = executor.submit {
            try {
                if (current.screen == "detail") {
                    val (detail, stale) = repo.detail(current.concert!!, force)
                    handler.post { if (generation == token) state.value = LoadState(detail = detail, staleDetail = stale) }
                } else {
                    val from = if (current.screen == "region") today else today.minusDays(days)
                    val to = today.plusDays(days)
                    val result = repo.search(if (current.screen == "region") emptyList() else artists,
                        from, to, if (current.screen == "region") regionCode else null,
                        expanded = expanded && current.screen == "artist", force = force) { message ->
                        handler.post { if (generation == token) state.value = LoadState(loading = true, progress = message) }
                    }
                    handler.post { if (generation == token) {
                        if(current.screen=="artist" && current.artistId!=null && result.concerts.isNotEmpty()) {
                            catalog.confirm(current.artistId,result.concerts)
                            catalog.byId(current.artistId)?.let(::rememberRecent)
                        }
                        results[key] = result; state.value = LoadState(result = result)
                    } }
                }
            } catch (_: InterruptedException) {
                // A newer route owns the screen; canceled responses are never rendered.
            } catch (e: Exception) {
                handler.post { if (generation == token) state.value = LoadState(error = e.message ?: "정보를 불러오지 못했어요.") }
            }
        }
    }
    override fun onCleared() { task?.cancel(true); executor.shutdownNow(); repo.close() }
}

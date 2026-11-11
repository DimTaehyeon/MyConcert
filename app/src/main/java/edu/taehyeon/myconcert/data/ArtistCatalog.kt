package edu.taehyeon.myconcert.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array ->
    (0 until array.length()).map { array.getString(it) }
} ?: emptyList()

class ArtistCatalog(context: Context) {
    private val prefs=context.getSharedPreferences("myconcert",0)
    private val builtins: List<Artist>
    private val custom=linkedMapOf<String,Artist>()
    private val persisted=mutableSetOf<String>()
    val artists: List<Artist> get() = builtins+custom.values
    val savedArtists: List<Artist> get() = custom.values.filter { it.id in persisted }
    init {
        val verification = runCatching {
            JSONArray(context.assets.open("verification.json").bufferedReader().use { it.readText() })
        }.getOrDefault(JSONArray())
        val verified = (0 until verification.length()).associate {
            val item = verification.getJSONObject(it)
            item.getString("artistId") to item.strings("positiveQueries")
        }
        val json = JSONArray(context.assets.open("artists.json").bufferedReader().use { it.readText() })
        builtins = (0 until json.length()).map {
            val a = json.getJSONObject(it)
            Artist(a.getString("id"), a.getString("name"), a.getString("english"),
                a.getString("initials"), a.getString("kind"), a.strings("aliases"),
                a.strings("members"), a.strings("tours"), a.optString("source"),
                verified[a.getString("id")].orEmpty())
        }
        runCatching {
            val saved=JSONArray(prefs.getString("custom_artists","[]"))
            for(i in 0 until saved.length()) {
                val item=saved.getJSONObject(i)
                val name=ArtistDiscovery.validateName(item.getString("name"))
                val id=item.getString("id")
                if(!id.startsWith("custom_")) continue
                custom[id]=Artist(id,name,item.optString("english","직접 등록"),name.take(2),"직접 등록",
                    item.strings("aliases").ifEmpty { listOf(name) },verifiedQueries=item.strings("verifiedQueries"),userAdded=true)
                persisted+=id
            }
        }
    }
    fun byId(id: String): Artist? = artists.find { it.id == id }
    fun isSaved(id:String)=!id.startsWith("custom_") || id in persisted
    fun beginSearch(input:String): Artist {
        val name=ArtistDiscovery.validateName(input)
        artists.firstOrNull { a -> a.aliases.any { ArtistMatcher.normalize(it)==ArtistMatcher.normalize(name) } }?.let { return it }
        val id=ArtistDiscovery.id(name)
        return Artist(id,name,"직접 검색",name.take(2),"직접 검색",listOf(name),userAdded=true).also { custom[id]=it }
    }
    fun persist(id:String) {
        if(id !in custom) return
        persisted+=id;write()
    }
    fun confirm(id:String, concerts:List<Concert>) {
        val artist=custom[id] ?: return
        if(concerts.isEmpty()) return
        val positive=artist.aliases.filter { alias -> concerts.any { c ->
            ArtistMatcher.containsIdentity(c.title,alias) || ArtistMatcher.containsIdentity(c.cast,alias)
        } }
        if(positive.isEmpty()) return
        val observed=positive.flatMap { ArtistDiscovery.observedAliases(it,concerts) }
        val aliases=(artist.aliases+observed).distinctBy(ArtistMatcher::normalize).take(8)
        custom[id]=artist.copy(english="저장된 검색명",kind="직접 등록",aliases=aliases,
            verifiedQueries=(artist.verifiedQueries+positive).distinctBy(ArtistMatcher::normalize))
        persist(id)
    }
    fun editAliases(id:String,values:List<String>) {
        val artist=custom[id] ?: return
        val aliases=values.map(ArtistDiscovery::validateName).distinctBy(ArtistMatcher::normalize)
        require(aliases.isNotEmpty() && aliases.size<=8) { "같은 아티스트의 검색명을 1~8개 입력해 주세요." }
        require(aliases.none { alias -> artists.any { a -> a.id!=id && a.aliases.any { ArtistMatcher.normalize(it)==ArtistMatcher.normalize(alias) } } }) {
            "다른 아티스트에 등록된 이름이 포함돼 있어요. 같은 아티스트의 이름만 입력해 주세요."
        }
        custom[id]=artist.copy(aliases=aliases,verifiedQueries=artist.verifiedQueries.filter { old -> aliases.any { ArtistMatcher.normalize(it)==ArtistMatcher.normalize(old) } })
        persist(id)
    }
    fun removeCustom(id:String):Boolean {
        if(custom.remove(id)==null) return false
        persisted.remove(id);write();return true
    }
    private fun write() {
        val array=JSONArray()
        savedArtists.forEach { a -> array.put(JSONObject().put("id",a.id).put("name",a.name).put("english",a.english)
            .put("aliases",JSONArray(a.aliases)).put("verifiedQueries",JSONArray(a.verifiedQueries))) }
        prefs.edit().putString("custom_artists",array.toString()).apply()
    }
}

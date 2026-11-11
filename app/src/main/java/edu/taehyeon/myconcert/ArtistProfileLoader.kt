package edu.taehyeon.myconcert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import edu.taehyeon.myconcert.data.*
import org.json.JSONArray
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Remote photos and resolved profile metadata live in RAM only; no file or HTTP disk cache. */
object ArtistProfileLoader {
    private val executor=Executors.newFixedThreadPool(3)
    private val main=Handler(Looper.getMainLooper())
    private val images=object:LruCache<String,Bitmap>(8*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.byteCount
    }
    private data class Lookup(val photo:ArtistProfilePhoto?,val at:Long=SystemClock.elapsedRealtime())
    private val resolved=LruCache<String,Lookup>(128)
    private val failures=LruCache<String,Long>(128)
    private val pending=mutableMapOf<String,MutableList<WeakReference<ArtistAvatarView>>>()
    private var bundled:Map<String,ArtistProfilePhoto>?=null

    @Synchronized private fun catalog(context:Context):Map<String,ArtistProfilePhoto> {
        bundled?.let { return it }
        val json=JSONArray(context.assets.open("artist-profiles.json").bufferedReader().use { it.readText() })
        return (0 until json.length()).associate { index ->
            val item=json.getJSONObject(index)
            item.getString("artistId") to ArtistProfilePhoto(item.getString("imageUrl"),item.getString("sourceUrl"),item.getString("matchedName"))
        }.also { bundled=it }
    }

    fun load(artist:Artist,target:ArtistAvatarView) {
        val key=artist.id+":"+artist.name+":"+artist.aliases.joinToString("/")
        target.photoRequestKey=key
        images.get(key)?.let { target.showPhoto(it);return }
        failures.get(key)?.let { if(SystemClock.elapsedRealtime()-it<120_000) return }
        synchronized(pending) {
            pending[key]?.let { it.add(WeakReference(target));return }
            pending[key]=mutableListOf(WeakReference(target))
        }
        val known=catalog(target.context.applicationContext)[artist.id]
        executor.execute {
            val cached=resolved.get(key)?.takeIf { SystemClock.elapsedRealtime()-it.at < if(it.photo==null) 120_000 else 21_600_000 }
            val photo=cached?.photo ?: known ?: if(cached==null) find(artist).also { resolved.put(key,Lookup(it)) } else null
            var bitmap=photo?.let { decode(it.imageUrl) }
            if(bitmap==null && known!=null) {
                val refreshed=cached?.photo ?: find(artist).also { resolved.put(key,Lookup(it)) }
                bitmap=refreshed?.let { decode(it.imageUrl) }
            }
            if(bitmap!=null) images.put(key,bitmap) else failures.put(key,SystemClock.elapsedRealtime())
            val listeners=synchronized(pending) { pending.remove(key).orEmpty() }
            val loaded=bitmap
            main.post {
                listeners.forEach { reference -> reference.get()?.let { view ->
                    if(view.photoRequestKey==key && view.isAttachedToWindow && loaded!=null) view.showPhoto(loaded)
                } }
            }
        }
    }

    private fun find(artist:Artist):ArtistProfilePhoto?=runCatching {
        val source=ArtistProfileParser.searchUrl(artist)
        ArtistProfileParser.parse(fetch(source,4*1024*1024).toString(Charsets.UTF_8),artist,source)
    }.getOrNull()

    private fun decode(url:String):Bitmap?=runCatching {
        if(!ArtistProfileParser.isPhotoUrl(url)) return null
        val data=fetch(ArtistProfileParser.displayImageUrl(url),2*1024*1024)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(data,0,data.size,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
        val options=BitmapFactory.Options().apply {
            inSampleSize=1
            while(bounds.outWidth/inSampleSize>640 || bounds.outHeight/inSampleSize>640) inSampleSize*=2
        }
        BitmapFactory.decodeByteArray(data,0,data.size,options)
    }.getOrNull()

    private fun fetch(url:String,limit:Int):ByteArray {
        var current=URL(url)
        repeat(4) {
            check(current.protocol=="https" && current.host in setOf("search.naver.com","search.pstatic.net","sstatic.naver.net"))
            val connection=current.openConnection() as HttpURLConnection
            try {
                connection.useCaches=false;connection.instanceFollowRedirects=false
                connection.connectTimeout=6000;connection.readTimeout=8000
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                connection.setRequestProperty("Accept-Language","ko-KR,ko;q=0.9")
                if(connection.responseCode in listOf(301,302,303,307,308)) {
                    current=URL(current,connection.getHeaderField("Location") ?: error("Missing redirect"))
                } else {
                    check(connection.responseCode==200)
                    return connection.inputStream.use { input ->
                        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true) {
                            val count=input.read(buffer);if(count<0) break
                            check(output.size()+count<=limit);output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }
}

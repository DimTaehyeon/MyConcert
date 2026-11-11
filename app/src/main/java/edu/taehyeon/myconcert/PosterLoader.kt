package edu.taehyeon.myconcert

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object PosterLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun load(source: String, image: ImageView) {
        val url = if (source.startsWith("http://www.kopis.or.kr")) source.replaceFirst("http://", "https://") else source
        image.tag = url; cache.get(url)?.let { image.setImageBitmap(it); return }
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout=8000;connection.readTimeout=10000
                    connection.inputStream.use { input ->
                        val data=input.readBytes(); val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        BitmapFactory.decodeByteArray(data,0,data.size,bounds)
                        val options=BitmapFactory.Options().apply {
                            inSampleSize=1
                            while (bounds.outWidth/inSampleSize>1200 || bounds.outHeight/inSampleSize>1800) inSampleSize*=2
                        }; BitmapFactory.decodeByteArray(data,0,data.size,options)
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
            if(bitmap!=null) {
                cache.put(url,bitmap); image.post { if (image.tag==url) image.setImageBitmap(bitmap) }
            } else image.post { if(image.tag==url) { image.contentDescription="포스터를 불러오지 못했어요"; image.setImageResource(R.drawable.brand_logo) } }
        }
    }
}

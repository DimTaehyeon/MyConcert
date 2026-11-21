package edu.taehyeon.myconcert

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import edu.taehyeon.myconcert.data.Artist

class ArtistAvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var artist: Artist? = null
        private set
    var tileBackground = false
        private set
    private val initials: TextView
    private val photo: ImageView
    private val backdrop: ImageView
    init {
        LayoutInflater.from(context).inflate(R.layout.view_artist_avatar, this, true)
        initials = findViewById(R.id.avatar_initials)
        photo = findViewById(R.id.avatar_photo)
        backdrop = findViewById(R.id.avatar_backdrop)
        clipToOutline = true
        bind(null)
    }
    constructor(context: Context, artist: Artist?, size: Int, fill: Int, ink: Int, tileBackground: Boolean = false) : this(context) {
        bind(artist, tileBackground, size); setFallbackColors(fill, ink)
    }
    internal var photoRequestKey:String?=null
    val hasPhoto:Boolean get()=photo.drawable!=null && photo.visibility==VISIBLE
    private var requested=false
    private val visible=Rect()
    private val scrollListener=ViewTreeObserver.OnScrollChangedListener { loadIfVisible() }
    fun bind(artist: Artist?, tileBackground: Boolean = false, size: Int = 0) {
        this.artist = artist; this.tileBackground = tileBackground
        requested = false; photoRequestKey = null
        photo.setImageDrawable(null); photo.visibility = GONE
        backdrop.setImageDrawable(null); backdrop.visibility = GONE
        initials.text = artist?.initials ?: "♪"; initials.visibility = VISIBLE
        val avatarSize = if (size > 0) size else ((layoutParams?.width ?: 0) / resources.displayMetrics.density).toInt()
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(initials, 8, if (tileBackground) 36 else if (avatarSize > 40) 14 else 12, 1, TypedValue.COMPLEX_UNIT_SP)
        photo.scaleType = if (tileBackground || artist?.kind in listOf("그룹", "밴드", "직접 등록", "직접 검색")) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        contentDescription = artist?.name ?: "공연"
        setFallbackColors(ContextCompat.getColor(context, R.color.mc_surface), ContextCompat.getColor(context, R.color.mc_muted))
        post { loadIfVisible() }
    }
    fun setFallbackColors(fill: Int, ink: Int) {
        setBackgroundResource(if (tileBackground) R.drawable.bg_mc_tile else R.drawable.bg_mc_avatar)
        backgroundTintList = ColorStateList.valueOf(fill)
        initials.setTextColor(ink)
    }
    internal fun showPhoto(bitmap:Bitmap) {
        if(tileBackground) {
            // A soft cover behind the full image fills gaps without cropping group members.
            val ratio=24f/maxOf(bitmap.width,bitmap.height)
            val soft=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
            backdrop.setImageDrawable(BitmapDrawable(resources,soft).apply { isFilterBitmap=true })
            backdrop.visibility=VISIBLE
        }
        photo.setImageBitmap(bitmap);photo.visibility=VISIBLE;initials.visibility=GONE
    }
    private fun loadIfVisible() {
        if(!requested && artist!=null && isAttachedToWindow && isShown && getGlobalVisibleRect(visible)) {
            requested=true;ArtistProfileLoader.load(artist!!,this)
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow();viewTreeObserver.addOnScrollChangedListener(scrollListener)
        post { loadIfVisible() }
    }
    override fun onDetachedFromWindow() {
        if(viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        requested=false;super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);post { loadIfVisible() } }
}

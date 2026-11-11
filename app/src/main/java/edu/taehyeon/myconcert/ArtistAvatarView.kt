package edu.taehyeon.myconcert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import edu.taehyeon.myconcert.data.Artist

class ArtistAvatarView(context:Context,val artist:Artist?,size:Int,fill:Int,ink:Int,
                       val tileBackground:Boolean=false):FrameLayout(context) {
    private val initials=TextView(context).apply {
        text=artist?.initials ?: "♪";gravity=Gravity.CENTER;includeFontPadding=false
        typeface=Typeface.create("sans-serif",Typeface.BOLD);setSingleLine();setHorizontallyScrolling(false)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,8,if(tileBackground)36 else if(size>40)14 else 12,1,TypedValue.COMPLEX_UNIT_SP)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val photo=ImageView(context).apply {
        scaleType=if(tileBackground || artist?.kind in listOf("그룹","밴드","직접 등록","직접 검색")) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        visibility=GONE;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val backdrop=ImageView(context).apply {
        scaleType=ImageView.ScaleType.CENTER_CROP;alpha=0.65f
        visibility=GONE;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    internal var photoRequestKey:String?=null
    val hasPhoto:Boolean get()=photo.drawable!=null && photo.visibility==VISIBLE
    private var requested=false
    private val visible=Rect()
    private val scrollListener=ViewTreeObserver.OnScrollChangedListener { loadIfVisible() }
    init {
        contentDescription=artist?.name ?: "공연"
        clipToOutline=true
        if(tileBackground) addView(backdrop,LayoutParams(-1,-1))
        addView(initials,LayoutParams(-1,-1));addView(photo,LayoutParams(-1,-1))
        setFallbackColors(fill,ink)
    }
    fun setFallbackColors(fill:Int,ink:Int) {
        background=GradientDrawable().apply {
            shape=if(tileBackground) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            if(tileBackground) cornerRadius=16*resources.displayMetrics.density
            setColor(fill)
        }
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
            requested=true;ArtistProfileLoader.load(artist,this)
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

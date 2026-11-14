package edu.taehyeon.myconcert

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.ViewCompat
import edu.taehyeon.myconcert.data.Artist
import edu.taehyeon.myconcert.data.ArtistGridLayout

class ArtistTile(context: Context, val artist: Artist, selected: Boolean, onToggle: (ArtistTile) -> Unit) : FrameLayout(context), Checkable {
    private var checked = false
    private val check: AppCompatCheckBox
    private val portrait: ArtistAvatarView
    private val name: TextView
    private val selectedLabel: TextView
    private val body: View
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        android.view.LayoutInflater.from(context).inflate(R.layout.item_artist_tile, this, true)
        body = findViewById<View>(R.id.tile_body).apply { clipToOutline = true }
        check = findViewById(R.id.tile_check)
        portrait = findViewById<ArtistAvatarView>(R.id.tile_portrait).apply { bind(this@ArtistTile.artist, tileBackground = true) }
        name = findViewById<TextView>(R.id.tile_name).apply { text = artist.name; tag = "artist-name:${artist.id}" }
        selectedLabel = findViewById(R.id.tile_selected)
        isClickable = true; isFocusable = true; descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        contentDescription = "${artist.name}, ${artist.kind}"; tag = "artist-tile:${artist.id}"
        setOnClickListener { onToggle(this) }; setChecked(selected)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        name.measure(MeasureSpec.makeMeasureSpec((width-dp(20)).coerceAtLeast(1),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        val desired=maxOf(dp(148),(width*1.08f).toInt(),name.measuredHeight+dp(82))
        val height=resolveSize(desired,heightMeasureSpec)
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
    }
    override fun isChecked()=checked
    override fun toggle() { setChecked(!checked) }
    override fun setChecked(value:Boolean) {
        checked=value;isSelected=value;check.isChecked=value
        body.isSelected = value; name.isSelected = value
        selectedLabel.visibility = if (value) View.VISIBLE else View.GONE
        ViewCompat.setStateDescription(this,if(value) "선택됨" else "선택하지 않음")
        refreshDrawableState()
    }
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className=CheckBox::class.java.name;info.isCheckable=true;info.isChecked=checked
    }
}

/** Reflows without fixed tile heights, so large fonts and long artist names remain readable. */
class ArtistTileGrid @JvmOverloads constructor(context:Context, attrs:android.util.AttributeSet?=null) : ViewGroup(context, attrs) {
    var columns=1
        private set
    private var rowHeights=emptyList<Int>()
    private val density=resources.displayMetrics.density
    private val gap=(10*density).toInt()
    override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val available=(width-paddingLeft-paddingRight).coerceAtLeast(0)
        columns=ArtistGridLayout.columns(available/density,resources.configuration.fontScale)
        val tileWidth=((available-gap*(columns-1))/columns).coerceAtLeast(1)
        val heights=mutableListOf<Int>()
        for(i in 0 until childCount) {
            val child=getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            val row=i/columns
            if(row==heights.size) heights+=child.measuredHeight else heights[row]=maxOf(heights[row],child.measuredHeight)
        }
        for(i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(heights[i/columns],MeasureSpec.EXACTLY))
        rowHeights=heights
        setMeasuredDimension(width,resolveSize(paddingTop+paddingBottom+heights.sum()+gap*(heights.size-1).coerceAtLeast(0),heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        var top=paddingTop
        for(i in 0 until childCount) {
            if(i>0 && i%columns==0) top+=rowHeights[i/columns-1]+gap
            val child=getChildAt(i);val column=if(layoutDirection==View.LAYOUT_DIRECTION_RTL) columns-1-i%columns else i%columns
            val left=paddingLeft+column*(child.measuredWidth+gap)
            child.layout(left,top,left+child.measuredWidth,top+child.measuredHeight)
        }
    }
}


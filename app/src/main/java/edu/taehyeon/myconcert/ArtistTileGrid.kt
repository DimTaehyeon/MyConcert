package edu.taehyeon.myconcert

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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

data class ArtistTileColors(val background:Int,val selectedBackground:Int,val surface:Int,
                            val ink:Int,val muted:Int,val border:Int,val accent:Int)

class ArtistTile(context:Context,val artist:Artist,private val palette:ArtistTileColors,
                 selected:Boolean,onToggle:(ArtistTile)->Unit) : FrameLayout(context),Checkable {
    private var checked=false
    private val check=AppCompatCheckBox(context)
    private val portrait=ArtistAvatarView(context,artist,180,palette.surface,palette.muted,tileBackground=true)
    private val name=TextView(context)
    private val selectedLabel=TextView(context)
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun rounded(fill:Int,radius:Int)=GradientDrawable().apply { cornerRadius=dp(radius).toFloat();setColor(fill) }
    init {
        background=rounded(palette.surface,16);clipToOutline=true
        isClickable=true;isFocusable=true;descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        contentDescription="${artist.name}, ${artist.kind}";tag="artist-tile:${artist.id}"
        portrait.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(portrait,LayoutParams(-1,-1))
        addView(View(context).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,0x80000000.toInt()))
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LayoutParams(-1,dp(90),Gravity.BOTTOM))
        check.apply {
            isClickable=false;isFocusable=false;minWidth=0;minHeight=0;setPadding(0,0,0,0)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            buttonTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf(-android.R.attr.state_checked)),intArrayOf(palette.accent,palette.muted))
        }
        val checkPlate=FrameLayout(context).apply { background=rounded(Color.WHITE,20) }
        checkPlate.addView(check,LayoutParams(dp(32),dp(32),Gravity.CENTER))
        addView(checkPlate,LayoutParams(dp(36),dp(36),Gravity.TOP or Gravity.END).apply { topMargin=dp(10);marginEnd=dp(10) })
        selectedLabel.apply {
            text="선택됨";textSize=10f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;includeFontPadding=false
            typeface=Typeface.create("sans-serif",Typeface.BOLD);setPadding(dp(8),dp(5),dp(8),dp(5))
            background=rounded(palette.accent,12);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(selectedLabel,LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { topMargin=dp(16);marginStart=dp(10) })
        name.apply {
            text=artist.name;textSize=13f;gravity=Gravity.CENTER;includeFontPadding=false
            typeface=Typeface.create("sans-serif",Typeface.BOLD)
            setPadding(dp(12),dp(8),dp(12),dp(8));minHeight=dp(32);setTextColor(Color.WHITE)
            setLineSpacing(dp(2).toFloat(),1f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag="artist-name:${artist.id}"
        }
        addView(name,LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin=dp(10);marginStart=dp(10);marginEnd=dp(10)
        })
        setOnClickListener { onToggle(this) };setChecked(selected)
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
        foreground=GradientDrawable().apply {
            cornerRadius=dp(16).toFloat();setColor(Color.TRANSPARENT)
            setStroke(dp(if(value)3 else 1),if(value) palette.accent else palette.border)
        }
        portrait.setFallbackColors(palette.surface,palette.muted)
        name.background=rounded(if(value) palette.accent else 0xE61A2332.toInt(),20)
        selectedLabel.visibility=if(value) View.VISIBLE else View.GONE
        ViewCompat.setStateDescription(this,if(value) "선택됨" else "선택하지 않음")
        refreshDrawableState()
    }
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className=CheckBox::class.java.name;info.isCheckable=true;info.isChecked=checked
    }
}

/** Reflows without fixed tile heights, so large fonts and long artist names remain readable. */
class ArtistTileGrid(context:Context) : ViewGroup(context) {
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


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
import android.util.TypedValue
import android.widget.Checkable
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import edu.taehyeon.myconcert.data.Artist
import edu.taehyeon.myconcert.data.ArtistGridLayout

data class ArtistTileColors(val background:Int,val selectedBackground:Int,val surface:Int,
                            val ink:Int,val muted:Int,val border:Int,val accent:Int)

class ArtistTile(context:Context,val artist:Artist,private val palette:ArtistTileColors,
                 selected:Boolean,onToggle:(ArtistTile)->Unit) : LinearLayout(context),Checkable {
    private var checked=false
    private val check=AppCompatCheckBox(context)
    private val initials=TextView(context)
    private val name=TextView(context)
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    init {
        orientation=VERTICAL;gravity=Gravity.CENTER_HORIZONTAL
        setPadding(dp(12),dp(12),dp(12),dp(12));minimumHeight=dp(132)
        isClickable=true;isFocusable=true;descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        contentDescription="${artist.name}, ${artist.kind}";tag="artist-tile:${artist.id}"
        val head=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        initials.apply {
            text=artist.initials;textSize=14f;gravity=Gravity.CENTER;includeFontPadding=false
            setSingleLine()
            setHorizontallyScrolling(false)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,8,14,1,TypedValue.COMPLEX_UNIT_SP)
            typeface=Typeface.create("sans-serif",Typeface.BOLD);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        head.addView(initials,LayoutParams(dp(40),dp(40)))
        head.addView(View(context),LayoutParams(0,1,1f))
        check.apply {
            isClickable=false;isFocusable=false;minWidth=0;minHeight=0;setPadding(0,0,0,0)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            buttonTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf(-android.R.attr.state_checked)),intArrayOf(palette.accent,palette.muted))
        }
        head.addView(check,LayoutParams(dp(30),dp(40)))
        addView(head,LayoutParams(-1,-2))
        name.apply {
            text=artist.name;textSize=14f;gravity=Gravity.CENTER;includeFontPadding=false
            typeface=Typeface.create("sans-serif",Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(),1f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(name,LayoutParams(-1,-2).apply { topMargin=dp(10) })
        addView(TextView(context).apply {
            text=if(artist.userAdded) "직접 등록" else artist.kind;textSize=11f;gravity=Gravity.CENTER
            setTextColor(palette.muted);includeFontPadding=false;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LayoutParams(-1,-2).apply { topMargin=dp(6) })
        setOnClickListener { onToggle(this) };setChecked(selected)
    }
    override fun isChecked()=checked
    override fun toggle() { setChecked(!checked) }
    override fun setChecked(value:Boolean) {
        checked=value;isSelected=value;check.isChecked=value
        background=GradientDrawable().apply {
            cornerRadius=dp(16).toFloat();setColor(if(value) palette.selectedBackground else palette.background)
            setStroke(dp(if(value)2 else 1),if(value) palette.accent else palette.border)
        }
        initials.background=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(if(value) palette.accent else palette.surface) }
        initials.setTextColor(if(value) Color.WHITE else palette.muted)
        name.setTextColor(if(value) palette.accent else palette.ink)
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


package edu.taehyeon.myconcert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import edu.taehyeon.myconcert.data.*

/** The bitmap, province pins and leader lines all share the same image transform. */
class RegionMapView(context: Context, private val selected: String, private val dark: Boolean,
                    private val onSelect: (Region) -> Unit) : FrameLayout(context) {
    private val image=AppCompatResources.getDrawable(context,R.drawable.korea_map)!!
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val blue=android.graphics.Color.parseColor(if(dark) "#77A9FF" else "#3B82F6")
    private val border=android.graphics.Color.parseColor(if(dark) "#64748B" else "#B9C8DC")
    private val density=resources.displayMetrics.density
    init {
        setWillNotDraw(false)
        Regions.all.forEach { region ->
            val active=region.code==selected
            addView(AppCompatTextView(context).apply {
                text=region.name; textSize=11f; gravity=Gravity.CENTER;includeFontPadding=false
                typeface=Typeface.create("sans-serif",Typeface.BOLD)
                setTextColor(if(active) android.graphics.Color.WHITE else android.graphics.Color.parseColor(if(dark) "#F3F4F6" else "#243247"))
                background=GradientDrawable().apply {
                    shape=GradientDrawable.OVAL
                    setColor(if(active) android.graphics.Color.parseColor("#3B82F6") else android.graphics.Color.parseColor(if(dark) "#1B2432" else "#FFFFFF"))
                    setStroke((density*1.2f).toInt().coerceAtLeast(1),if(active) blue else border)
                }
                contentDescription="${region.name} 공연 조회"; isFocusable=true;isSelected=active
                setOnClickListener { onSelect(region) }
            })
        }
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val w=MeasureSpec.getSize(widthMeasureSpec)
        val h=(w*Regions.BOARD_HEIGHT/Regions.BOARD_WIDTH).toInt()
        val size=(w*.13f).coerceIn(36*density,44*density).toInt()
        for(i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY))
        setMeasuredDimension(w,h)
    }
    override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
        Regions.all.forEachIndexed { i,region ->
            val point=RegionMapGeometry.label(width.toFloat(),height.toFloat(),region)
            val child=getChildAt(i);val half=child.measuredWidth/2
            child.layout(point.x.toInt()-half,point.y.toInt()-half,point.x.toInt()-half+child.measuredWidth,point.y.toInt()-half+child.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect=RegionMapGeometry.imageBounds(width.toFloat(),height.toFloat(),image.intrinsicWidth.toFloat(),image.intrinsicHeight.toFloat())
        canvas.save();canvas.translate(rect.left,rect.top)
        canvas.scale(rect.width/image.intrinsicWidth,rect.height/image.intrinsicHeight)
        image.setBounds(0,0,image.intrinsicWidth,image.intrinsicHeight);image.draw(canvas);canvas.restore()
        Regions.all.forEach { region ->
            val pin=rect.point(region.mapX,region.mapY)
            val label=RegionMapGeometry.label(width.toFloat(),height.toFloat(),region)
            paint.color=if(region.code==selected) blue else border;paint.strokeWidth=1.1f*density
            canvas.drawLine(pin.x,pin.y,label.x,label.y,paint)
            canvas.drawCircle(pin.x,pin.y,2.6f*density,paint)
        }
    }
}

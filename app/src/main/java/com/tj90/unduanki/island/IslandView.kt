package com.tj90.unduanki.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Decorative island with touch targets mirrored by the accessible slot buttons. */
class IslandView(
    context: Context,
    private val state: IslandRepository.Snapshot,
    private val onSlot: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotCenters = listOf(65f to 165f, 135f to 145f, 215f to 145f, 285f to 165f, 105f to 225f, 255f to 225f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    init { contentDescription = "Island scene. Use the numbered slot buttons below to place decorations." }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 300f / 360f).toInt()
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 360f, height / 300f)
        canvas.save()
        canvas.translate((width - 360f * scale) / 2, (height - 300f * scale) / 2)
        canvas.scale(scale, scale)
        canvas.drawColor(Color.rgb(143, 216, 220))
        oval(canvas, 12f, 90f, 348f, 282f, 239, 203, 132)
        oval(canvas, 30f, 92f, 330f, 251f, 151, 197, 102)
        // Dock and habitat are permanent parts of the island, not purchasable slots.
        rect(canvas, 0f, 240f, 77f, 260f, 152, 101, 60)
        rect(canvas, 12f, 260f, 17f, 282f, 111, 73, 47)
        rect(canvas, 57f, 260f, 62f, 282f, 111, 73, 47)
        oval(canvas, 136f, 24f, 224f, 103f, 247, 250, 238)
        oval(canvas, 153f, 52f, 207f, 104f, 167, 216, 219)
        if (state.rescued) penguin(canvas, 180f, 82f)
        else label(canvas, "Habitat", 180f, 72f, 12f, Color.rgb(49, 102, 97))
        slotCenters.forEachIndexed { index, (x, y) ->
            val item = state.placements[index + 1]
            oval(canvas, x - 27f, y - 17f, x + 27f, y + 17f, 230, 235, 176)
            if (item != null) decoration(canvas, item, x, y - 4f)
            circle(canvas, x + 21f, y + 13f, 11f, 255, 250, 226)
            label(canvas, "${index + 1}", x + 21f, y + 17f, 12f, Color.rgb(48, 76, 55))
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop && width > 0 && height > 0) {
                    val scale = minOf(width / 360f, height / 300f)
                    val x = (event.x - (width - 360f * scale) / 2) / scale
                    val y = (event.y - (height - 300f * scale) / 2) / scale
                    val slot = slotCenters.indexOfFirst { (sx, sy) -> abs(x - sx) <= 32f && abs(y - sy) <= 28f }
                    if (slot >= 0) { performClick(); onSlot(slot + 1) }
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun penguin(canvas: Canvas, x: Float, y: Float) {
        oval(canvas, x - 15, y - 25, x + 15, y + 12, 46, 60, 69)
        oval(canvas, x - 9, y - 10, x + 9, y + 10, 255, 252, 232)
        circle(canvas, x - 5, y - 16, 2f, 255, 255, 255)
        circle(canvas, x + 5, y - 16, 2f, 255, 255, 255)
        rect(canvas, x - 3, y - 11, x + 3, y - 8, 245, 163, 48)
    }

    private fun decoration(canvas: Canvas, id: String, x: Float, y: Float) {
        when (id) {
            "flowers" -> {
                rect(canvas, x - 2, y - 7, x + 2, y + 12, 58, 133, 65)
                circle(canvas, x, y - 10, 8f, 238, 116, 144)
                circle(canvas, x, y - 10, 3f, 255, 224, 98)
            }
            "lantern" -> {
                rect(canvas, x - 9, y - 19, x + 9, y + 9, 125, 80, 49)
                rect(canvas, x - 6, y - 15, x + 6, y + 5, 255, 218, 96)
            }
            "beach_ball" -> {
                circle(canvas, x, y - 5, 15f, 245, 113, 101)
                oval(canvas, x - 6, y - 20, x + 6, y + 10, 250, 239, 188)
            }
            "palm_tree" -> {
                rect(canvas, x - 3, y - 21, x + 3, y + 11, 132, 87, 52)
                oval(canvas, x - 24, y - 30, x + 3, y - 19, 54, 139, 75)
                oval(canvas, x - 3, y - 30, x + 24, y - 19, 54, 139, 75)
            }
            "hammock" -> {
                rect(canvas, x - 20, y - 17, x - 17, y + 10, 120, 81, 54)
                rect(canvas, x + 17, y - 17, x + 20, y + 10, 120, 81, 54)
                oval(canvas, x - 18, y - 8, x + 18, y + 8, 239, 142, 101)
            }
            "treehouse" -> {
                rect(canvas, x - 15, y - 15, x + 15, y + 10, 162, 104, 65)
                val roof = Path().apply { moveTo(x - 19, y - 15); lineTo(x, y - 30); lineTo(x + 19, y - 15); close() }
                paint.color = Color.rgb(190, 83, 62); canvas.drawPath(roof, paint)
                rect(canvas, x - 4, y - 5, x + 4, y + 10, 94, 65, 52)
            }
        }
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, red: Int, green: Int, blue: Int) {
        paint.color = Color.rgb(red, green, blue); c.drawRect(l, t, r, b, paint)
    }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, red: Int, green: Int, blue: Int) {
        paint.color = Color.rgb(red, green, blue); c.drawOval(RectF(l, t, r, b), paint)
    }
    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, red: Int, green: Int, blue: Int) {
        paint.color = Color.rgb(red, green, blue); c.drawCircle(x, y, radius, paint)
    }
    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color; paint.textSize = size; paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
        c.drawText(value, x, y, paint); paint.isFakeBoldText = false
    }
}

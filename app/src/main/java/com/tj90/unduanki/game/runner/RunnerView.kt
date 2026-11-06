package com.tj90.unduanki.game.runner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Self-contained rendering/input host. The owner must pause it when its activity pauses. */
class RunnerView(context: Context, val simulation: RunnerSimulation) : View(context) {
    var onFinished: ((RunnerStatus, Int) -> Unit)? = null
    var onAction: ((RunnerAction) -> Unit)? = null
    var tutorialLocked = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastFrame = 0L
    private var notified = false
    private var startX = 0f
    private var startY = 0f
    private var gestureValid = false
    private val frame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            val now = SystemClock.uptimeMillis()
            if (lastFrame != 0L && !tutorialLocked) simulation.advance((now - lastFrame).coerceIn(0, 250).toInt())
            lastFrame = now
            if (!notified && (simulation.status == RunnerStatus.WON || simulation.status == RunnerStatus.LOST)) {
                notified = true
                onFinished?.invoke(simulation.status, simulation.stars)
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    init { contentDescription = "Jungle runner game"; isFocusable = true }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); lastFrame = 0; postOnAnimation(frame) }
    override fun onDetachedFromWindow() { pauseGame(); removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) pauseGame()
    }
    fun pauseGame() { simulation.pause(); lastFrame = 0; invalidate() }
    fun resumeGame() { simulation.resume(); lastFrame = 0; invalidate() }
    fun command(action: RunnerAction) {
        if (simulation.status != RunnerStatus.RUNNING) return
        onAction?.invoke(action)
        if (!tutorialLocked) simulation.act(action)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; gestureValid = true; return true }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> gestureValid = false
            MotionEvent.ACTION_UP -> {
                if (gestureValid) {
                    val density = resources.displayMetrics.density
                    RunnerInput.swipe((event.x - startX) / density, (event.y - startY) / density)?.let(::command)
                    performClick()
                }
                gestureValid = false
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(222, 241, 210))
        // A fixed portrait playfield is letterboxed rather than stretching game geometry.
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val scale = minOf(w / 360f, h / 600f)
        canvas.save()
        canvas.translate((w - 360f * scale) / 2, (h - 600f * scale) / 2)
        canvas.scale(scale, scale)
        fill(canvas, Color.rgb(95, 165, 99), 0f, 0f, 360f, 600f)
        fill(canvas, Color.rgb(215, 180, 119), 45f, 0f, 315f, 600f)
        for (lane in 1..2) fill(canvas, Color.rgb(242, 218, 165), 45f + lane * 90f - 2f, 0f, 45f + lane * 90f + 2f, 600f)
        for (row in simulation.rows) {
            val until = row.contactMs - simulation.elapsedMs
            if (until !in 0..simulation.difficulty.warningMs) continue
            val y = 510f - 440f * until / simulation.difficulty.warningMs
            for (lane in 0..2) {
                val obstacle = row.lanes[lane] ?: continue
                val color = when (obstacle) {
                    Obstacle.LOG -> Color.rgb(120, 72, 37)
                    Obstacle.BRANCH -> Color.rgb(61, 108, 49)
                    Obstacle.ROCK -> Color.rgb(96, 100, 102)
                }
                val x = 90f + lane * 90f
                fill(canvas, color, x - 25f, y - 18f, x + 25f, y + 18f)
            }
            row.starLane?.let { lane ->
                paint.color = Color.rgb(255, 215, 48)
                canvas.drawCircle(90f + lane * 90f, y, 12f, paint)
            }
        }
        val position = simulation.previousLane + (simulation.lane - simulation.previousLane) * simulation.laneProgress
        val playerX = 90f + position * 90f
        paint.color = Color.rgb(62, 103, 186)
        val playerY = if (simulation.pose == RunnerPose.JUMP) 465f else 510f
        canvas.drawCircle(playerX, playerY, if (simulation.pose == RunnerPose.SLIDE) 13f else 19f, paint)
        paint.color = Color.rgb(30, 55, 40); paint.textSize = 19f; paint.isFakeBoldText = true
        canvas.drawText("Time ${simulation.elapsedMs / 1000} / 60   Stars ${simulation.stars}   Hits ${simulation.collisions} / 3", 10f, 28f, paint)
        if (simulation.status != RunnerStatus.RUNNING) {
            paint.color = Color.rgb(30, 55, 40); paint.textSize = 28f
            val label = when (simulation.status) {
                RunnerStatus.PAUSED -> "Paused"
                RunnerStatus.COUNTDOWN -> "Ready ${(simulation.countdownMs + 999) / 1000}"
                RunnerStatus.WON -> "You made it!"
                RunnerStatus.LOST -> "Try again!"
                else -> ""
            }
            canvas.drawText(label, 90f, 300f, paint)
        }
        canvas.restore()
    }
    private fun fill(canvas: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = color
        canvas.drawRect(RectF(l, t, r, b), paint)
    }
}

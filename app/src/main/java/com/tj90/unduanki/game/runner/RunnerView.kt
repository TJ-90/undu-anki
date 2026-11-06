package com.tj90.unduanki.game.runner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val path = Path()
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
        fill(canvas, Color.rgb(78, 149, 91), 0f, 0f, 360f, 600f)
        fill(canvas, Color.rgb(218, 179, 119), 45f, 0f, 315f, 600f)
        // Repeating track marks move with elapsed play time, not wall-clock time.
        for (lane in 1..2) {
            val x = 45f + lane * 90f
            var y = (simulation.elapsedMs / 18f) % 80f - 80f
            while (y < 600f) {
                fill(canvas, Color.rgb(242, 218, 165), x - 2f, y, x + 2f, y + 36f)
                y += 80f
            }
        }
        for (side in listOf(20f, 340f)) {
            for (i in 0..5) {
                val y = ((i * 120f + simulation.elapsedMs / 25f) % 720f) - 60f
                circle(canvas, Color.rgb(43, 119, 75), side, y, 24f)
                circle(canvas, Color.rgb(91, 169, 85), side + if (side < 180f) 8f else -8f, y - 8f, 13f)
            }
        }
        if (simulation.elapsedMs >= 57000) {
            val y = 70f + 440f * (simulation.elapsedMs - 57000) / 3000f
            for (i in 0 until 6) {
                val color = if (i % 2 == 0) Color.rgb(255, 250, 225) else Color.rgb(55, 86, 70)
                fill(canvas, color, 45f + i * 45f, y - 9f, 45f + (i + 1) * 45f, y + 9f)
            }
            paint.color = Color.rgb(40, 74, 55); paint.textSize = 17f; paint.isFakeBoldText = true
            canvas.drawText("FINISH", 151f, y - 15f, paint)
        }
        for (row in simulation.rows) {
            val until = row.contactMs - simulation.elapsedMs
            if (until !in 0..simulation.difficulty.warningMs) continue
            val y = 510f - 440f * until / simulation.difficulty.warningMs
            for (lane in 0..2) {
                val obstacle = row.lanes[lane] ?: continue
                val x = 90f + lane * 90f
                drawObstacle(canvas, obstacle, x, y)
            }
            row.starLane?.let { lane ->
                drawStar(canvas, 90f + lane * 90f, y)
            }
        }
        val position = simulation.previousLane + (simulation.lane - simulation.previousLane) * simulation.laneProgress
        val playerX = 90f + position * 90f
        val playerY = if (simulation.pose == RunnerPose.JUMP) 465f else 510f
        drawExplorer(canvas, playerX, playerY, simulation.pose == RunnerPose.SLIDE)
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

    private fun circle(canvas: Canvas, color: Int, x: Float, y: Float, radius: Float) {
        paint.color = color
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawObstacle(canvas: Canvas, obstacle: Obstacle, x: Float, y: Float) {
        when (obstacle) {
            Obstacle.LOG -> {
                fill(canvas, Color.rgb(118, 71, 39), x - 25f, y - 13f, x + 23f, y + 13f)
                circle(canvas, Color.rgb(167, 108, 62), x + 21f, y, 13f)
                circle(canvas, Color.rgb(222, 169, 105), x + 21f, y, 8f)
                fill(canvas, Color.rgb(90, 54, 32), x - 20f, y - 5f, x + 8f, y - 2f)
                fill(canvas, Color.rgb(90, 54, 32), x - 15f, y + 6f, x + 12f, y + 9f)
            }
            Obstacle.BRANCH -> {
                fill(canvas, Color.rgb(104, 68, 38), x - 25f, y - 5f, x + 25f, y + 5f)
                circle(canvas, Color.rgb(48, 126, 66), x - 15f, y - 8f, 10f)
                circle(canvas, Color.rgb(69, 154, 72), x, y - 10f, 11f)
                circle(canvas, Color.rgb(48, 126, 66), x + 15f, y - 8f, 10f)
            }
            Obstacle.ROCK -> {
                path.reset()
                path.moveTo(x - 25f, y + 13f); path.lineTo(x - 18f, y - 11f)
                path.lineTo(x, y - 18f); path.lineTo(x + 20f, y - 9f)
                path.lineTo(x + 25f, y + 13f); path.close()
                paint.color = Color.rgb(103, 113, 113); canvas.drawPath(path, paint)
                fill(canvas, Color.rgb(151, 163, 156), x - 12f, y - 7f, x + 4f, y - 3f)
            }
        }
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float) {
        path.reset()
        for (i in 0 until 10) {
            val angle = Math.PI * i / 5.0 - Math.PI / 2
            val radius = if (i % 2 == 0) 13f else 6f
            val px = x + (kotlin.math.cos(angle) * radius).toFloat()
            val py = y + (kotlin.math.sin(angle) * radius).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        paint.color = Color.rgb(255, 216, 57); canvas.drawPath(path, paint)
        circle(canvas, Color.rgb(255, 244, 168), x - 2f, y - 3f, 3f)
    }

    private fun drawExplorer(canvas: Canvas, x: Float, y: Float, sliding: Boolean) {
        paint.color = Color.argb(65, 50, 67, 45)
        canvas.drawOval(RectF(x - 20f, 522f, x + 20f, 531f), paint)
        if (sliding) {
            fill(canvas, Color.rgb(52, 91, 150), x - 18f, y - 5f, x + 9f, y + 9f)
            circle(canvas, Color.rgb(232, 182, 125), x + 13f, y - 4f, 9f)
            fill(canvas, Color.rgb(137, 88, 50), x + 3f, y - 14f, x + 23f, y - 10f)
        } else {
            fill(canvas, Color.rgb(66, 83, 117), x - 13f, y + 7f, x - 3f, y + 19f)
            fill(canvas, Color.rgb(66, 83, 117), x + 3f, y + 7f, x + 13f, y + 19f)
            fill(canvas, Color.rgb(62, 103, 186), x - 16f, y - 7f, x + 16f, y + 11f)
            circle(canvas, Color.rgb(232, 182, 125), x, y - 14f, 12f)
            fill(canvas, Color.rgb(137, 88, 50), x - 15f, y - 24f, x + 15f, y - 19f)
            fill(canvas, Color.rgb(181, 126, 66), x - 9f, y - 29f, x + 9f, y - 23f)
            circle(canvas, Color.rgb(54, 49, 42), x - 4f, y - 14f, 1.5f)
            circle(canvas, Color.rgb(54, 49, 42), x + 4f, y - 14f, 1.5f)
        }
    }
}

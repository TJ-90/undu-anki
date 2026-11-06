package com.tj90.unduanki.game.runner

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

enum class RunnerMode { TUTORIAL, PRACTICE, MISSION }
data class RunnerResult(val mode: RunnerMode, val status: RunnerStatus, val stars: Int)

/** Embeddable screen; only the owner may decide whether a mission result earns rewards. */
class RunnerScreen(
    context: Context,
    seed: Long,
    difficulty: RunnerDifficulty,
    val mode: RunnerMode,
    showButtons: Boolean = true,
    onFinished: (RunnerResult) -> Unit,
    onExit: () -> Unit
) : LinearLayout(context) {
    val runnerView = RunnerView(context, RunnerSimulation(seed, difficulty))
    private val instructions = TextView(context)
    private val practiced = mutableSetOf<RunnerAction>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val dp = resources.displayMetrics.density
        instructions.textSize = 16f
        instructions.setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        addView(instructions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        runnerView.tutorialLocked = mode == RunnerMode.TUTORIAL
        runnerView.onAction = { action ->
            if (runnerView.tutorialLocked) {
                practiced += if (action == RunnerAction.RIGHT) RunnerAction.LEFT else action
                if (practiced.containsAll(listOf(RunnerAction.LEFT, RunnerAction.JUMP, RunnerAction.SLIDE))) {
                    runnerView.tutorialLocked = false
                }
                updateInstructions()
            }
        }
        runnerView.onFinished = { status, stars -> onFinished(RunnerResult(mode, status, stars)) }
        addView(runnerView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (showButtons) {
            val controls = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            listOf("Left" to RunnerAction.LEFT, "Right" to RunnerAction.RIGHT,
                "Jump" to RunnerAction.JUMP, "Slide" to RunnerAction.SLIDE).forEach { (label, action) ->
                controls.addView(Button(context).apply {
                    text = label
                    setOnClickListener { runnerView.command(action) }
                }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            addView(controls, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val navigation = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        navigation.addView(Button(context).apply {
            text = "Pause / Resume"
            setOnClickListener {
                if (runnerView.simulation.status == RunnerStatus.PAUSED) runnerView.resumeGame()
                else runnerView.pauseGame()
            }
        })
        navigation.addView(Button(context).apply {
            text = "Leave run"
            setOnClickListener { runnerView.pauseGame(); onExit() }
        })
        addView(navigation)
        updateInstructions()
    }

    /** Call from Activity.onPause even when the window keeps focus. */
    fun pauseGame() = runnerView.pauseGame()

    private fun updateInstructions() {
        instructions.text = when {
            runnerView.tutorialLocked -> "Tutorial: try left or right, jump, and slide. Swipe or use the buttons."
            mode == RunnerMode.TUTORIAL -> "Tutorial: great! Run to the finish. No rewards are earned."
            mode == RunnerMode.PRACTICE -> "Practice run — no rewards are earned."
            else -> "Mission run — swipe to move, jump, and slide."
        }
    }
}

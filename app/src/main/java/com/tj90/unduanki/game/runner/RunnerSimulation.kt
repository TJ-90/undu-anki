package com.tj90.unduanki.game.runner

enum class RunnerAction { LEFT, RIGHT, JUMP, SLIDE }
enum class RunnerStatus { RUNNING, PAUSED, COUNTDOWN, WON, LOST }
enum class RunnerPose { RUN, JUMP, SLIDE }

/** Deterministic fixed-step game logic. The host supplies monotonic frame deltas in milliseconds. */
class RunnerSimulation(seed: Long, val difficulty: RunnerDifficulty) {
    val rows: List<RunnerRow> = RunnerPatterns.generate(seed, difficulty)
    var elapsedMs = 0; private set
    var lane = 1; private set
    var previousLane = 1; private set
    val laneProgress: Float get() = 1f - laneRemaining / 150f
    var pose = RunnerPose.RUN; private set
    var collisions = 0; private set
    var stars = 0; private set
    var status = RunnerStatus.RUNNING; private set
    var countdownMs = 0; private set
    private var accumulator = 0
    private var laneRemaining = 0
    private var poseRemaining = 0
    private var invulnerableRemaining = 0
    private var nextRow = 0

    fun act(action: RunnerAction) {
        if (status != RunnerStatus.RUNNING) return
        when (action) {
            RunnerAction.LEFT, RunnerAction.RIGHT -> if (laneRemaining == 0) {
                val target = (lane + if (action == RunnerAction.LEFT) -1 else 1).coerceIn(0, 2)
                if (target != lane) { previousLane = lane; lane = target; laneRemaining = 150 }
            }
            RunnerAction.JUMP, RunnerAction.SLIDE -> if (poseRemaining == 0) {
                pose = if (action == RunnerAction.JUMP) RunnerPose.JUMP else RunnerPose.SLIDE
                poseRemaining = 650
            }
        }
    }

    fun pause() {
        if (status == RunnerStatus.RUNNING || status == RunnerStatus.COUNTDOWN) {
            status = RunnerStatus.PAUSED
            accumulator = 0
        }
    }

    fun resume() {
        if (status == RunnerStatus.PAUSED) { status = RunnerStatus.COUNTDOWN; countdownMs = 3000 }
    }

    /** Clamp individual frame lag; background transitions must explicitly call pause(). */
    fun advance(frameMs: Int) {
        require(frameMs >= 0)
        if (status == RunnerStatus.PAUSED || status == RunnerStatus.WON || status == RunnerStatus.LOST) return
        accumulator += frameMs.coerceAtMost(250)
        while (accumulator >= STEP_MS && (status == RunnerStatus.RUNNING || status == RunnerStatus.COUNTDOWN)) {
            accumulator -= STEP_MS
            if (status == RunnerStatus.COUNTDOWN) {
                countdownMs -= STEP_MS
                if (countdownMs == 0) status = RunnerStatus.RUNNING
            } else tick()
        }
    }

    private fun tick() {
        elapsedMs += STEP_MS
        laneRemaining = (laneRemaining - STEP_MS).coerceAtLeast(0)
        poseRemaining = (poseRemaining - STEP_MS).coerceAtLeast(0)
        if (poseRemaining == 0) pose = RunnerPose.RUN
        invulnerableRemaining = (invulnerableRemaining - STEP_MS).coerceAtLeast(0)
        // Finish takes precedence over any collision on the last simulation tick.
        if (elapsedMs >= 60000) { status = RunnerStatus.WON; return }
        while (nextRow < rows.size && rows[nextRow].contactMs <= elapsedMs) {
            val row = rows[nextRow++]
            if (row.starLane == lane) stars++
            val obstacle = row.lanes[lane]
            val avoided = obstacle == null || (obstacle == Obstacle.LOG && pose == RunnerPose.JUMP) ||
                (obstacle == Obstacle.BRANCH && pose == RunnerPose.SLIDE)
            if (!avoided && invulnerableRemaining == 0) {
                collisions++
                invulnerableRemaining = 1200
                if (collisions >= 3) { status = RunnerStatus.LOST; return }
            }
        }
    }

    companion object { const val STEP_MS = 10 }
}

package com.tj90.unduanki.game.runner

import java.util.Random

enum class Obstacle { LOG, BRANCH, ROCK }
enum class RunnerDifficulty(val warningMs: Int, val rowIntervalMs: Int) {
    EASY(1800, 1400), NORMAL(1400, 1000)
}

data class RunnerRow(val contactMs: Int, val lanes: List<Obstacle?>, val starLane: Int?)

/** Authored rows always leave a lane open; spacing permits crossing both lanes between rows. */
object RunnerPatterns {
    private val patterns = listOf(
        listOf(null, Obstacle.LOG, Obstacle.ROCK),
        listOf(Obstacle.BRANCH, null, Obstacle.LOG),
        listOf(Obstacle.ROCK, Obstacle.BRANCH, null),
        listOf(null, Obstacle.ROCK, Obstacle.BRANCH),
        listOf(Obstacle.LOG, null, Obstacle.ROCK),
        listOf(Obstacle.BRANCH, Obstacle.LOG, null),
        listOf(null, Obstacle.LOG, null),
        listOf(Obstacle.ROCK, null, null)
    )

    fun generate(seed: Long, difficulty: RunnerDifficulty): List<RunnerRow> {
        val random = Random(seed)
        val rows = mutableListOf<RunnerRow>()
        var contact = 5000 + difficulty.warningMs
        while (contact <= 57000) {
            val pattern = patterns[random.nextInt(patterns.size)]
            val open = pattern.indices.filter { pattern[it] == null }
            val star = if (rows.size < 20) open[random.nextInt(open.size)] else null
            rows += RunnerRow(contact, pattern, star)
            contact += difficulty.rowIntervalMs
        }
        return rows
    }
}

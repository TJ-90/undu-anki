package com.tj90.unduanki.game.runner

import org.junit.Assert.*
import org.junit.Test

class RunnerSimulationTest {
    private fun advance(sim: RunnerSimulation, ms: Int) { repeat(ms / 10) { sim.advance(10) } }

    @Test fun inputAndActionConstraints() {
        assertNull(RunnerInput.swipe(23f, 0f))
        assertEquals(RunnerAction.JUMP, RunnerInput.swipe(0f, -24f))
        assertEquals(RunnerAction.RIGHT, RunnerInput.swipe(25f, 2f))
        val sim = RunnerSimulation(1, RunnerDifficulty.EASY)
        sim.act(RunnerAction.LEFT); sim.act(RunnerAction.RIGHT)
        assertEquals(0, sim.lane)
        sim.act(RunnerAction.JUMP); sim.act(RunnerAction.SLIDE)
        assertEquals(RunnerPose.JUMP, sim.pose)
        advance(sim, 150); sim.act(RunnerAction.LEFT)
        assertEquals(0, sim.lane)
        advance(sim, 500); sim.act(RunnerAction.SLIDE)
        assertEquals(RunnerPose.SLIDE, sim.pose)
    }

    @Test fun pauseAndCountdownDoNotAdvanceGame() {
        val sim = RunnerSimulation(2, RunnerDifficulty.EASY)
        advance(sim, 1000); sim.pause(); advance(sim, 5000)
        assertEquals(1000, sim.elapsedMs)
        sim.resume(); advance(sim, 3000)
        assertEquals(1000, sim.elapsedMs)
        advance(sim, 10); assertEquals(1010, sim.elapsedMs)
    }

    @Test fun safePathCompletesAndIsReproducible() {
        fun run(): RunnerSimulation {
            val sim = RunnerSimulation(12, RunnerDifficulty.NORMAL)
            for (row in sim.rows) {
                while (sim.elapsedMs < row.contactMs - 400 && sim.status == RunnerStatus.RUNNING) sim.advance(10)
                val target = row.lanes.indexOf(null)
                while (sim.lane != target) {
                    sim.act(if (sim.lane < target) RunnerAction.RIGHT else RunnerAction.LEFT)
                    advance(sim, 150)
                }
                while (sim.elapsedMs < row.contactMs && sim.status == RunnerStatus.RUNNING) sim.advance(10)
            }
            while (sim.status == RunnerStatus.RUNNING) sim.advance(10)
            return sim
        }
        val first = run(); val second = run()
        assertEquals(RunnerStatus.WON, first.status)
        assertEquals(60000, first.elapsedMs)
        assertEquals(0, first.collisions)
        assertTrue(first.stars <= 20)
        assertEquals(first.stars, second.stars)
    }

    @Test fun obstacleAvoidanceDependsOnPose() {
        for (obstacle in Obstacle.values()) {
            val seed = (0L..1000L).map { it * 1000003 }.first { RunnerPatterns.generate(it, RunnerDifficulty.EASY)[0].lanes[1] == obstacle }
            fun collisions(action: RunnerAction?): Int {
                val sim = RunnerSimulation(seed, RunnerDifficulty.EASY)
                val contact = sim.rows.first().contactMs
                advance(sim, contact - 300)
                if (action != null) sim.act(action)
                advance(sim, 300)
                return sim.collisions
            }
            assertEquals(1, collisions(null))
            assertEquals(if (obstacle == Obstacle.LOG) 0 else 1, collisions(RunnerAction.JUMP))
            assertEquals(if (obstacle == Obstacle.BRANCH) 0 else 1, collisions(RunnerAction.SLIDE))
        }
    }

    @Test fun invulnerabilityAndThirdCollision() {
        val seed = (0L..10000L).first { candidate ->
            RunnerPatterns.generate(candidate, RunnerDifficulty.NORMAL).take(2).all { it.lanes[1] != null }
        }
        val sim = RunnerSimulation(seed, RunnerDifficulty.NORMAL)
        advance(sim, sim.rows[1].contactMs)
        assertEquals(1, sim.collisions) // second row is within 1.2 seconds
        while (sim.status == RunnerStatus.RUNNING) sim.advance(10)
        assertEquals(RunnerStatus.LOST, sim.status)
        assertEquals(3, sim.collisions)
    }

    @Test fun lagIsClamped() {
        val sim = RunnerSimulation(1, RunnerDifficulty.NORMAL)
        sim.advance(10000)
        assertEquals(250, sim.elapsedMs)
    }
}

package com.tj90.unduanki.game.runner

import org.junit.Assert.*
import org.junit.Test

class RunnerPatternsTest {
    @Test fun authoredRowsAreSafeAndDeterministicForManySeeds() {
        for (difficulty in RunnerDifficulty.values()) for (seed in 0L until 100L) {
            val rows = RunnerPatterns.generate(seed, difficulty)
            assertEquals(rows, RunnerPatterns.generate(seed, difficulty))
            assertTrue(rows.first().contactMs - difficulty.warningMs >= 5000)
            assertTrue(rows.last().contactMs <= 57000)
            assertTrue(rows.count { it.starLane != null } <= 20)
            rows.forEach { row ->
                assertEquals(3, row.lanes.size)
                assertTrue(row.lanes.contains(null))
                row.starLane?.let { assertNull(row.lanes[it]) }
            }
            rows.zipWithNext().forEach { (a, b) ->
                assertTrue(b.contactMs - a.contactMs >= difficulty.rowIntervalMs)
                assertTrue(b.contactMs - a.contactMs >= 300)
            }
        }
    }
}

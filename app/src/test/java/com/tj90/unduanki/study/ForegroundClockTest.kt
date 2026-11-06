package com.tj90.unduanki.study

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundClockTest {
    @Test fun backgroundTimeIsExcluded() {
        var t = 1000L
        val clock = ForegroundClock { t }
        clock.start()
        t += 4000; clock.pause()
        t += 600_000; clock.resume()
        t += 2500
        assertEquals(6500, clock.finish())
    }

    @Test fun resumeDoesNothingWhenNoCardIsPresented() {
        var t = 0L
        val clock = ForegroundClock { t }
        clock.resume(); t += 5000
        assertEquals(0, clock.elapsedMs())
        clock.start(); t += 100
        clock.finish(); clock.resume(); t += 9000
        assertEquals(0, clock.elapsedMs())
    }
}

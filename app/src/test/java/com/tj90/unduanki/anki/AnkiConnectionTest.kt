package com.tj90.unduanki.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiConnectionTest {
    @Test fun eachStateIsDistinguishedInOrder() {
        assertEquals(ConnectionState.NOT_INSTALLED, AnkiConnection.classify(null, true, true))
        assertEquals(ConnectionState.VERSION_NOT_VERIFIED, AnkiConnection.classify("2.99.0", true, true))
        assertEquals(ConnectionState.PERMISSION_NEEDED, AnkiConnection.classify("2.25.3", false, false))
        assertEquals(ConnectionState.NOT_READY, AnkiConnection.classify("2.25.3", true, false))
        assertEquals(ConnectionState.WORKING, AnkiConnection.classify("2.25.3", true, true))
    }

    @Test fun anUntestedReleaseNeverReachesWorkingEvenWhenReadable() {
        for (version in listOf("2.24.0", "2.25.4", "2.26.0", "")) {
            assertEquals(version, ConnectionState.VERSION_NOT_VERIFIED, AnkiConnection.classify(version, true, true))
        }
    }
}

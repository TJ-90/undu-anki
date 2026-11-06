package com.tj90.unduanki.anki

import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opt in on a fresh, disposable emulator; this suite does not write reviews. */
@RunWith(AndroidJUnit4::class)
class AnkiDroidProviderGateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver

    @Before fun requireExplicitGate() {
        // Normal emulator CI reports this suite as skipped. The separate gate job always passes
        // this argument and fails rather than skipping if its prerequisites are missing.
        assumeTrue("Run only in the separately provisioned AnkiDroid provider gate",
            InstrumentationRegistry.getArguments().getString("anki_provider_gate") == "true")
        val info = context.packageManager.getPackageInfo("com.ichi2.anki", 0)
        assertEquals("Unexpected AnkiDroid release", "2.25.3", info.versionName)
        assertEquals("AnkiDroid API permission is not granted", PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission("com.ichi2.anki.permission.READ_WRITE_DATABASE"))
    }

    @Test fun freshCollectionHasReadableDefaultDeckAndScheduler() {
        val deck = resolver.query(Uri.parse(AnkiContract.deckUri(1)), arrayOf("deck_id"), null, null, null)
        assertNotNull("AnkiDroid did not return a deck cursor", deck)
        deck!!.use { assertTrue("Fresh default deck was not found", it.moveToFirst()); assertEquals(1L, it.getLong(0)) }
        val gateway = AnkiDroidGateway(resolver, 1)
        val card = gateway.nextCard()
        if (card != null) assertEquals(card.state, gateway.readState(card.cardId))
    }

    @Test fun nonexistentDeckIsNotReportedAsAnEmptyQueue() {
        try {
            AnkiDroidGateway(resolver, Long.MAX_VALUE).nextCard()
            throw AssertionError("Unknown deck was treated as an empty queue")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Selected deck is unavailable"))
        }
    }
}

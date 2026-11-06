package com.tj90.unduanki.anki

import android.content.pm.PackageManager
import android.content.ContentValues
import android.net.Uri
import android.os.Build
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

    @Test fun syntheticForwardNoteIsScheduledAndCanBeReadBack() {
        // Insert only when the caller explicitly attests this is the disposable CI fixture.
        // A normal provider gate or an ordinary instrumentation run never creates a note.
        assertEquals("Fixture requires an explicitly disposable emulator", "true",
            InstrumentationRegistry.getArguments().getString("anki_fixture_ephemeral"))
        assertTrue("Fixture may run only on an Android emulator",
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))

        val root = "content://com.ichi2.anki.flashcards"
        val notes = resolver.query(Uri.parse("$root/notes_v2"), arrayOf("_id"), null, null, null)
        assertNotNull("Cannot verify that the collection is empty", notes)
        notes!!.use { assertEquals("Refusing to write to a nonempty collection", 0, it.count) }

        val models = resolver.query(Uri.parse("$root/models"),
            arrayOf("_id", "name", "field_names", "num_cards", "type", "deck_id"), null, null, null)
        assertNotNull("Cannot inspect note types", models)
        val modelId = models!!.use { cursor ->
            var found: Long? = null
            while (cursor.moveToNext()) {
                val fields = cursor.getString(2).split('\u001f')
                if (cursor.getString(1) == "Basic" && fields == listOf("Front", "Back") &&
                    cursor.getInt(3) == 1 && cursor.getInt(4) == 0 && cursor.getLong(5) == 1L) {
                    found = cursor.getLong(0)
                    break
                }
            }
            found
        }
        assertNotNull("Fresh collection has no supported Basic forward note type", modelId)
        val values = ContentValues().apply {
            put("mid", modelId!!)
            put("flds", "Synthetic gate question\u001fSynthetic gate answer")
            put("tags", "mischief_gate_fixture")
        }
        val created = resolver.insert(Uri.parse("$root/notes"), values)
        assertNotNull("Provider did not return the new synthetic note", created)
        val noteId = created!!.lastPathSegment!!.toLong()
        val gateway = AnkiDroidGateway(resolver, 1)
        val scheduled = gateway.nextCard()
        assertNotNull("Synthetic card was not supplied by the scheduler", scheduled)
        assertEquals(noteId, scheduled!!.noteId)
        assertEquals(0, scheduled.ordinal)
        assertEquals(0, scheduled.state.reps)
        assertEquals(scheduled.state, gateway.readState(scheduled.cardId))
    }

    /** Disposable-emulator write test: one fresh synthetic note per rating, answered once each. */
    @Test fun eachRatingIsRecordedWithDistinctSchedulerOutcomes() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("anki_fixture_ephemeral"))
        assertTrue(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        val root = "content://com.ichi2.anki.flashcards"
        resolver.query(Uri.parse("$root/notes_v2"), arrayOf("_id"), null, null, null)!!.use {
            assertEquals("Refusing to write to a nonempty collection", 0, it.count)
        }
        val modelId = basicModelId(root)
        val noteIds = (1..4).map { n ->
            val values = ContentValues().apply {
                put("mid", modelId)
                put("flds", "Synthetic rating $n question\u001fSynthetic rating $n answer")
                put("tags", "mischief_gate_fixture")
            }
            resolver.insert(Uri.parse("$root/notes"), values)!!.lastPathSegment!!.toLong()
        }
        val gateway = AnkiDroidGateway(resolver, 1)
        val journal = RecordingJournal()
        val coordinator = com.tj90.unduanki.study.ReviewSubmissionCoordinator(gateway, journal)
        val outcomes = mutableMapOf<Rating, AnkiDroidGateway.Snapshot>()
        val answered = mutableSetOf<Long>()
        for ((index, rating) in Rating.values().withIndex()) {
            val before = gateway.nextCard()
            assertNotNull("Scheduler supplied no card for $rating", before)
            before!!
            assertTrue("Scheduler repeated an answered card", answered.add(before.cardId))
            assertTrue("Card is not one of the synthetic notes", before.noteId in noteIds)
            assertEquals("Unsupported button count", 4, before.buttonCount)
            assertEquals(com.tj90.unduanki.study.SubmissionResult.CONFIRMED,
                coordinator.submit("gate-$index", null, before.state, rating, 1500))
            val after = gateway.inspect(before.noteId, before.ordinal)
            assertEquals(before.state.reps + 1, after.state.reps)
            assertTrue(after.state.lastReviewTimeSeconds > before.state.lastReviewTimeSeconds)
            outcomes[rating] = after
        }
        assertEquals(listOf("pending:gate-0", "confirmed:gate-0", "pending:gate-1", "confirmed:gate-1",
            "pending:gate-2", "confirmed:gate-2", "pending:gate-3", "confirmed:gate-3"), journal.events)
        // Observed mapping on a new card: Again < Hard < Good delay while still learning; Easy graduates.
        val now = System.currentTimeMillis() / 1000
        fun delay(r: Rating) = outcomes.getValue(r).due - now
        assertEquals(1, outcomes.getValue(Rating.AGAIN).queue)
        assertEquals(1, outcomes.getValue(Rating.HARD).queue)
        assertEquals(1, outcomes.getValue(Rating.GOOD).queue)
        assertTrue("Again ${delay(Rating.AGAIN)} !< Hard ${delay(Rating.HARD)}", delay(Rating.AGAIN) < delay(Rating.HARD))
        assertTrue("Hard ${delay(Rating.HARD)} !< Good ${delay(Rating.GOOD)}", delay(Rating.HARD) < delay(Rating.GOOD))
        assertEquals(2, outcomes.getValue(Rating.EASY).queue)
        assertTrue(outcomes.getValue(Rating.EASY).interval >= 1)
        android.util.Log.i("MischiefGate", "observed delays s: again=${delay(Rating.AGAIN)} hard=${delay(Rating.HARD)} good=${delay(Rating.GOOD)} easyInterval=${outcomes.getValue(Rating.EASY).interval}d")
    }

    private fun basicModelId(root: String): Long {
        val models = resolver.query(Uri.parse("$root/models"),
            arrayOf("_id", "name", "field_names", "num_cards", "type", "deck_id"), null, null, null)
        assertNotNull("Cannot inspect note types", models)
        return models!!.use { cursor ->
            var found: Long? = null
            while (cursor.moveToNext()) {
                val fields = cursor.getString(2).split('\u001f')
                if (cursor.getString(1) == "Basic" && fields == listOf("Front", "Back") &&
                    cursor.getInt(3) == 1 && cursor.getInt(4) == 0 && cursor.getLong(5) == 1L) { found = cursor.getLong(0); break }
            }
            checkNotNull(found) { "Fresh collection has no supported Basic forward note type" }
        }
    }

    private class RecordingJournal : com.tj90.unduanki.study.ReviewJournal {
        val events = mutableListOf<String>()
        override fun pending(id: String, batchId: String?, before: CardState, rating: Rating, elapsedMs: Long) { events += "pending:$id" }
        override fun uncertain(id: String) { events += "uncertain:$id" }
        override fun confirmed(id: String) { events += "confirmed:$id" }
    }
}

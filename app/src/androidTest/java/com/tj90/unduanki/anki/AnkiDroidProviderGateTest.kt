package com.tj90.unduanki.anki

import android.content.pm.PackageManager
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * The only test that writes to AnkiDroid: one fresh synthetic note per rating, answered once each.
     * Keep it the sole writer; it refuses a nonempty collection.
     */
    @Test fun eachRatingIsRecordedWithDistinctSchedulerOutcomes() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("anki_fixture_ephemeral"))
        assertTrue(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        val root = "content://com.ichi2.anki.flashcards"
        resolver.query(Uri.parse("$root/notes_v2"), arrayOf("_id"), null, null, null)!!.use {
            assertEquals("Refusing to write to a nonempty collection", 0, it.count)
        }
        val modelId = basicModelId(root)
        val noteIds = (1..6).map { n ->
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
            assertEquals(0, before.ordinal)
            assertEquals("A new synthetic card must have no prior reviews", 0, before.state.reps)
            assertEquals(before.state, gateway.readState(before.cardId))
            assertEquals(com.tj90.unduanki.study.SubmissionResult.CONFIRMED,
                coordinator.submit("gate-$index", null, CardRef(before.noteId, before.ordinal), before.state, rating, 1500))
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
        reconcileInterruptedReviews(gateway, noteIds, answered)
        deckSelectionIsolatesCards(root, modelId, noteIds)
        futureLearningCardIsSuppliedUnderLearnAhead(gateway, coordinator, answered)
        // Last: the reversed note below leaves a second new card in the default deck.
        catalogInspectsRealDecksAndNotes(root, modelId)
        android.util.Log.i("MischiefGate", "observed delays s: again=${delay(Rating.AGAIN)} hard=${delay(Rating.HARD)} good=${delay(Rating.GOOD)} easyInterval=${outcomes.getValue(Rating.EASY).interval}d")
    }

    /**
     * Real provider, real on-disk journal: an interruption after the provider write must recover
     * without a second review; one before the write must never be resubmitted automatically.
     */
    private fun reconcileInterruptedReviews(gateway: AnkiDroidGateway, noteIds: List<Long>, answered: MutableSet<Long>) {
        val name = "gate-${java.util.UUID.randomUUID()}.db"
        val db = com.tj90.unduanki.data.GameDatabase(context, databaseName = name)
        try {
            val progress = com.tj90.unduanki.data.ProgressRepository(db)
            val store = com.tj90.unduanki.study.PersistentRecoveryStore(progress)
            val recovery = com.tj90.unduanki.study.ReviewRecovery(gateway, store)

            // A: provider recorded the review, then the process died before local confirmation.
            val a = gateway.nextCard()!!
            assertTrue("Scheduler repeated an answered card", answered.add(a.cardId))
            assertTrue(a.noteId in noteIds)
            val refA = CardRef(a.noteId, a.ordinal)
            val dying = object : com.tj90.unduanki.study.ReviewJournal by com.tj90.unduanki.study.PersistentReviewJournal(progress) {
                override fun confirmed(id: String) { throw IllegalStateException("process died before local confirmation") }
            }
            assertEquals(com.tj90.unduanki.study.SubmissionResult.UNCERTAIN,
                com.tj90.unduanki.study.ReviewSubmissionCoordinator(gateway, dying).submit("lost-confirmation", null, refA, a.state, Rating.GOOD, 900))
            assertEquals("uncertain", progress.unresolvedAttempts().single().status)
            assertEquals("Provider should hold exactly the one review", a.state.reps + 1, gateway.inspect(a.noteId, a.ordinal).state.reps)
            assertEquals(com.tj90.unduanki.study.RecoveryOutcome.CONFIRMED, recovery.recover(store.unresolved().single()))
            assertTrue(progress.unresolvedAttempts().isEmpty())
            assertEquals("Recovery must not write again", a.state.reps + 1, gateway.inspect(a.noteId, a.ordinal).state.reps)

            // B: the process died before the provider write. Recovery must not guess or resubmit.
            val b = gateway.nextCard()!!
            assertTrue("Scheduler repeated an answered card", answered.add(b.cardId))
            val refB = CardRef(b.noteId, b.ordinal)
            com.tj90.unduanki.study.PersistentReviewJournal(progress).pending("never-written", null, refB, b.state, Rating.EASY, 800)
            assertEquals(com.tj90.unduanki.study.RecoveryOutcome.UNCHANGED, recovery.recover(store.unresolved().single()))
            assertEquals("uncertain", progress.unresolvedAttempts().single().status)
            assertEquals("Recovery must never replay a write", b.state, gateway.inspect(b.noteId, b.ordinal).state)
            progress.abandonUnresolved("never-written")
            answered.remove(b.cardId)
            assertEquals("After 'not recorded' the same card is scheduled fresh", b.cardId, gateway.nextCard()!!.cardId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createDeck(root: String, name: String): Long {
        val values = ContentValues().apply { put("deck_name", name) }
        val uri = resolver.insert(Uri.parse("$root/decks"), values)
        assertNotNull("Provider did not create deck $name", uri)
        return uri!!.lastPathSegment!!.toLong()
    }

    private fun addNoteToDeck(root: String, modelId: Long, text: String, deckId: Long): Long {
        val values = ContentValues().apply { put("mid", modelId); put("flds", "$text question\u001f$text answer"); put("tags", "mischief_gate_fixture") }
        val noteId = resolver.insert(Uri.parse("$root/notes"), values)!!.lastPathSegment!!.toLong()
        val move = ContentValues().apply { put("deck_id", deckId) }
        assertEquals("Provider did not move the card", 1, resolver.update(Uri.parse("$root/notes/$noteId/cards/0"), move, null, null))
        return noteId
    }

    /** An unrelated deck must never leak cards; descendant behaviour is observed and recorded, not assumed. */
    private fun deckSelectionIsolatesCards(root: String, modelId: Long, existing: List<Long>) {
        val parent = createDeck(root, "Gate::Parent")
        val child = createDeck(root, "Gate::Parent::Child")
        val other = createDeck(root, "Gate::Other")
        val inChild = addNoteToDeck(root, modelId, "Child deck", child)
        val inOther = addNoteToDeck(root, modelId, "Other deck", other)
        val onlyOther = AnkiDroidGateway(resolver, other).nextCard()
        assertNotNull("Selected deck supplied nothing", onlyOther)
        assertEquals("Selecting an unrelated deck must supply only its own card", inOther, onlyOther!!.noteId)
        val fromDefault = generateSequence { AnkiDroidGateway(resolver, 1).nextCard() }.take(1).toList()
        assertTrue("Default deck leaked a card from another deck",
            fromDefault.none { it.noteId == inChild || it.noteId == inOther })
        val viaParent = AnkiDroidGateway(resolver, parent).nextCard()
        val childReachable = viaParent?.noteId == inChild
        val parentLeaks = viaParent != null && (viaParent.noteId == inOther || viaParent.noteId in existing)
        assertTrue("Selecting a parent deck leaked an unrelated card", !parentLeaks)
        android.util.Log.i("MischiefGate", "deck isolation: other-only=true parentIncludesDescendant=$childReachable")
    }

    /** With AnkiDroid's default nonzero learn-ahead, a learning card due soon is supplied once nothing else is. */
    private fun futureLearningCardIsSuppliedUnderLearnAhead(
        gateway: AnkiDroidGateway, coordinator: com.tj90.unduanki.study.ReviewSubmissionCoordinator, answered: MutableSet<Long>
    ) {
        // Drain remaining new cards in the default deck, so only learning/review cards are left.
        while (true) {
            val card = gateway.nextCard() ?: break
            if (card.state.reps > 0) {
                val now = System.currentTimeMillis() / 1000
                val snap = gateway.inspect(card.noteId, card.ordinal)
                assertEquals("Supplied card should be a learning card", 1, snap.queue)
                assertTrue("Learning card should be in the future (learn-ahead), due ${snap.due} now $now", snap.due > now)
                assertTrue("Supplied card must be one this gate already answered", card.cardId in answered)
                android.util.Log.i("MischiefGate", "learn-ahead supplied a future learning card ${snap.due - now}s ahead")
                return
            }
            assertTrue("Unexpected new card", answered.add(card.cardId))
            assertEquals(com.tj90.unduanki.study.SubmissionResult.CONFIRMED,
                coordinator.submit("drain-${card.cardId}", null, CardRef(card.noteId, card.ordinal), card.state, Rating.AGAIN, 500))
        }
        throw AssertionError("Scheduler supplied no learning card under the default learn-ahead")
    }

    /** Setup inspection against the real provider: supported, reversed and media notes are told apart. */
    private fun catalogInspectsRealDecksAndNotes(root: String, basicId: Long) {
        val catalog = AnkiDeckCatalog(resolver)
        assertTrue("Catalog must report a working collection", catalog.isReadable())
        val decks = catalog.decks()
        assertTrue("Created deck not listed: $decks", decks.any { it.name == "Gate::Other" })
        val models = catalog.models()
        val basic = models.getValue(basicId)
        assertEquals(FieldMapping("Front", "Back") to null, DeckCompatibilityChecker.deriveMapping(basic))

        val good = DeckCompatibilityChecker.evaluate(models, catalog.notesInDeck("Gate::Other"))
        assertTrue(good.summary(), good.usable)
        assertEquals(1, good.noteCount)

        val reversed = models.values.first { it.name == "Basic (and reversed card)" }
        val reversedDeck = createDeck(root, "Gate::Reversed")
        val reversedValues = ContentValues().apply { put("mid", reversed.id); put("flds", "Rev q\u001fRev a"); put("tags", "mischief_gate_fixture") }
        val reversedNote = resolver.insert(Uri.parse("$root/notes"), reversedValues)!!.lastPathSegment!!.toLong()
        resolver.update(Uri.parse("$root/notes/$reversedNote/cards/0"), ContentValues().apply { put("deck_id", reversedDeck) }, null, null)
        val badReversed = DeckCompatibilityChecker.evaluate(models, catalog.notesInDeck("Gate::Reversed"))
        assertFalse("Reversed notes must block the deck", badReversed.usable)
        assertTrue(badReversed.summary(), badReversed.summary().contains("card templates"))

        val mediaDeck = createDeck(root, "Gate::Media")
        val mediaNote = addNoteToDeck(root, basicId, "Media", mediaDeck)
        resolver.update(Uri.parse("$root/notes/$mediaNote"), ContentValues().apply { put("flds", "Look <img src=x>\u001fAnswer") }, null, null)
        val media = DeckCompatibilityChecker.evaluate(models, catalog.notesInDeck("Gate::Media"))
        assertFalse("A note with an image must block the deck", media.usable)
        assertEquals(1, media.verdicts.single().unsupportedNotes)

        val fresh = catalog.note(noteIdOf(catalog, "Gate::Other"))
        assertNotNull(fresh)
        assertEquals(2, fresh!!.fields.size)
        android.util.Log.i("MischiefGate", "catalog: decks=${decks.size} basic ok, reversed blocked, media blocked")
    }

    private fun noteIdOf(catalog: AnkiDeckCatalog, deck: String): Long = catalog.notesInDeck(deck).first().id

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
        override fun pending(id: String, batchId: String?, ref: CardRef, before: CardState, rating: Rating, elapsedMs: Long) { events += "pending:$id" }
        override fun uncertain(id: String) { events += "uncertain:$id" }
        override fun confirmed(id: String) { events += "confirmed:$id" }
    }
}

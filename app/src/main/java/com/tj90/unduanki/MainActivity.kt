package com.tj90.unduanki

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tj90.unduanki.anki.AnkiConnection
import com.tj90.unduanki.anki.AnkiDeckCatalog
import com.tj90.unduanki.anki.AnkiDroidGateway
import com.tj90.unduanki.anki.CardContentMapper
import com.tj90.unduanki.anki.CardRef
import com.tj90.unduanki.anki.ConnectionState
import com.tj90.unduanki.anki.DeckCompatibility
import com.tj90.unduanki.anki.DeckCompatibilityChecker
import com.tj90.unduanki.anki.FieldMapping
import com.tj90.unduanki.anki.MappedCard
import com.tj90.unduanki.anki.Rating
import com.tj90.unduanki.data.GameDatabase
import com.tj90.unduanki.data.ProgressRepository
import com.tj90.unduanki.game.runner.RunnerDifficulty
import com.tj90.unduanki.game.runner.RunnerMode
import com.tj90.unduanki.game.runner.RunnerScreen
import com.tj90.unduanki.game.runner.RunnerStatus
import com.tj90.unduanki.island.IslandRepository
import com.tj90.unduanki.island.IslandScreen
import com.tj90.unduanki.study.ForegroundClock
import com.tj90.unduanki.study.LiveSettings
import com.tj90.unduanki.study.PersistentRecoveryStore
import com.tj90.unduanki.study.PersistentReviewJournal
import com.tj90.unduanki.study.RecoveryOutcome
import com.tj90.unduanki.study.ReviewRecovery
import com.tj90.unduanki.study.ReviewSubmissionCoordinator
import com.tj90.unduanki.study.SubmissionResult
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Two modes with separate databases. Demo mode is synthetic and writes nothing to AnkiDroid. Live mode
 * studies the parent-chosen AnkiDroid deck and is offered only for AnkiDroid releases with proven behavior.
 */
class MainActivity : Activity() {
    private var live = false
    private lateinit var database: GameDatabase
    private lateinit var progress: ProgressRepository
    private lateinit var island: IslandRepository
    private var runner: RunnerScreen? = null
    private val modePrefs by lazy { getSharedPreferences("app-mode", MODE_PRIVATE) }
    private val prefs by lazy { getSharedPreferences(if (live) "live-settings" else "demo-settings", MODE_PRIVATE) }
    private val settings by lazy { LiveSettings(getSharedPreferences("live-connection", MODE_PRIVATE)) }
    private val io = Executors.newSingleThreadExecutor()
    private val clock = ForegroundClock()
    private val questions = listOf(
        "Which planet do we live on?" to "Earth",
        "How many legs does a spider have?" to "Eight",
        "What do bees make?" to "Honey",
        "Which ocean animal has eight arms?" to "An octopus",
        "What is the largest land animal?" to "The African elephant"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = modePrefs.getBoolean("live", false)
        database = GameDatabase(this, demo = !live)
        progress = ProgressRepository(database)
        island = IslandRepository(database)
        // Synthetic records have no external side effect and can safely be abandoned after interruption.
        // Live records may describe a real AnkiDroid write and are only ever resolved by recovery or a parent.
        if (!live) progress.unresolvedAttempts().forEach { progress.abandonUnresolved(it.id) }
        home()
        if (live) recoverInterruptedReviews { if (runner == null) home() }
    }

    override fun onResume() { super.onResume(); clock.resume() }
    override fun onPause() { clock.pause(); runner?.pauseGame(); super.onPause() }
    override fun onDestroy() { io.shutdown(); database.close(); super.onDestroy() }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { runner?.pauseGame(); home() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (live) parentSettings()
    }

    // ---- shared UI helpers -------------------------------------------------------------------

    private fun layout(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        addView(TextView(context).apply { text = title; textSize = 24f })
        addView(TextView(context).apply {
            text = if (live) "Your AnkiDroid deck · answers you give here are saved in AnkiDroid."
            else "DEMO · Sample questions and a separate island save. Real AnkiDroid study is off in demo mode; a parent can turn it on in settings."
            textSize = 16f
        })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }.also { parent.addView(it) }

    private fun body(parent: LinearLayout, text: String, size: Float = 18f) {
        parent.addView(TextView(this).apply { this.text = text; textSize = size; setPadding(0, 12, 0, 12) })
    }

    private fun show(content: View) { runner = null; setContentView(content) }
    private fun scroll(content: View) = ScrollView(this).apply { addView(content) }

    private fun info(title: String, message: String) {
        val root = layout(title)
        body(root, message)
        button(root, "Home") { home() }
        show(scroll(root))
    }

    private fun <T> background(work: () -> T, done: (Result<T>) -> Unit) {
        io.execute {
            val result = runCatching(work)
            runOnUiThread { if (!isDestroyed && !isFinishing) done(result) }
        }
    }

    private val demoTag get() = if (live) "" else "demo "

    // ---- home --------------------------------------------------------------------------------

    private fun tutorialDone() = prefs.getBoolean("tutorial-completed", false) ||
        (live && getSharedPreferences("demo-settings", MODE_PRIVATE).getBoolean("tutorial-completed", false))

    private fun home() {
        val root = layout("Mischief Island")
        if (!tutorialDone()) {
            button(root, "Play the tutorial") { run(RunnerMode.TUTORIAL) }
        } else {
            val ticket = ticket()
            if (ticket != null) button(root, "Continue ${demoTag}mission") { run(RunnerMode.MISSION, ticket) }
            else if (!live) button(root, "Study sample questions") { study() }
            else liveStudyEntry(root)
            button(root, "Practice runner") { run(RunnerMode.PRACTICE) }
            button(root, "Visit ${demoTag}island") { showIsland() }
            button(root, "Play tutorial again") { run(RunnerMode.TUTORIAL) }
        }
        val parent = TextView(this).apply {
            text = "Parent settings · press and hold for 3 seconds"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        val handler = Handler(Looper.getMainLooper())
        val openSettings = Runnable { parentSettings() }
        parent.setOnClickListener { }
        parent.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { handler.postDelayed(openSettings, 3000); true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.x < 0 || event.y < 0 || event.x > parent.width || event.y > parent.height)
                        handler.removeCallbacks(openSettings)
                    true
                }
                MotionEvent.ACTION_UP -> { handler.removeCallbacks(openSettings); parent.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(openSettings); true }
                else -> true
            }
        }
        root.addView(parent)
        show(scroll(root))
    }

    private fun liveStudyEntry(root: LinearLayout) {
        when {
            progress.unresolvedAttempts().isNotEmpty() ->
                body(root, "Study is paused for now. Please ask a parent to check AnkiDroid.")
            !settings.configured -> body(root, "A parent needs to connect an AnkiDroid deck first.")
            else -> button(root, "Study a few questions") { liveStudy() }
        }
    }

    // ---- parent settings ---------------------------------------------------------------------

    private fun parentSettings() { if (live) liveParentSettings() else demoParentSettings() }

    private fun commonSettings(root: LinearLayout) {
        val target = prefs.getInt("batch-size", 5)
        body(root, "Questions per mission: $target")
        button(root, "Fewer questions") {
            prefs.edit().putInt("batch-size", (target - 1).coerceAtLeast(3)).apply(); parentSettings()
        }
        button(root, "More questions") {
            prefs.edit().putInt("batch-size", (target + 1).coerceAtMost(10)).apply(); parentSettings()
        }
        val normal = prefs.getBoolean("normal-difficulty", false)
        button(root, "Runner difficulty: ${if (normal) "Normal" else "Easy"}") {
            prefs.edit().putBoolean("normal-difficulty", !normal).apply(); parentSettings()
        }
        val buttons = prefs.getBoolean("runner-buttons", true)
        button(root, "Runner buttons: ${if (buttons) "On" else "Off"}") {
            prefs.edit().putBoolean("runner-buttons", !buttons).apply(); parentSettings()
        }
    }

    private fun demoParentSettings() {
        val root = layout("Demo parent settings")
        commonSettings(root)
        body(root, "Settings apply to new batches; an existing mission keeps its difficulty.", 16f)
        body(root, "To study your own AnkiDroid deck, switch to live study. Demo progress is kept separately and is not carried over.", 16f)
        button(root, "Use my AnkiDroid deck (live study)") {
            modePrefs.edit().putBoolean("live", true).apply(); recreate()
        }
        button(root, "Home") { home() }
        show(scroll(root))
    }

    private fun installedAnkiVersion(): String? = try {
        packageManager.getPackageInfo(ANKI_PACKAGE, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) { null }

    private fun checkConnection(done: (ConnectionState, String?) -> Unit) {
        val version = installedAnkiVersion()
        val granted = checkSelfPermission(ANKI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (version == null || version !in AnkiConnection.VERIFIED_VERSIONS || !granted) {
            done(AnkiConnection.classify(version, granted, false), version)
            return
        }
        background({ AnkiDeckCatalog(contentResolver).isReadable() }) {
            done(AnkiConnection.classify(version, true, it.getOrDefault(false)), version)
        }
    }

    private fun liveParentSettings() {
        val root = layout("Parent settings")
        commonSettings(root)
        val status = TextView(this).apply { text = "Checking AnkiDroid…"; textSize = 16f; setPadding(0, 16, 0, 16) }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(actions)
        reviewResolution(root)
        button(root, "Use demo mode instead") { modePrefs.edit().putBoolean("live", false).apply(); recreate() }
        button(root, "Home") { home() }
        show(scroll(root))
        checkConnection { state, version ->
            val deck = if (settings.configured) "\nDeck: ${settings.deckName}" else "\nNo deck chosen yet."
            status.text = AnkiConnection.explain(state, version) + deck
            when (state) {
                ConnectionState.PERMISSION_NEEDED ->
                    button(actions, "Allow access to AnkiDroid") { requestPermissions(arrayOf(ANKI_PERMISSION), 7) }
                ConnectionState.WORKING ->
                    button(actions, if (settings.configured) "Change deck" else "Choose a deck") { chooseDeck() }
                else -> button(actions, "Check again") { parentSettings() }
            }
        }
    }

    /** Uncertain reviews pause study until a parent checks AnkiDroid; nothing is ever resubmitted. */
    private fun reviewResolution(root: LinearLayout) {
        val unresolved = progress.unresolvedAttempts()
        if (unresolved.isEmpty()) return
        body(root, "A review could not be confirmed", 20f)
        body(root, "The last answer may or may not have reached AnkiDroid. Open AnkiDroid and check whether that card was just reviewed " +
            "(its review count or the Card Info history). Study stays paused until you choose. Nothing will be sent again automatically.", 16f)
        button(root, "Check AnkiDroid again") {
            recoverInterruptedReviews { parentSettings() }
        }
        for (attempt in unresolved) {
            button(root, "AnkiDroid shows it was recorded") {
                progress.resolveAsRecorded(attempt.id); parentSettings()
            }
            button(root, "It was not recorded: use a fresh card") {
                progress.abandonUnresolved(attempt.id); parentSettings()
            }
        }
    }

    private fun chooseDeck() {
        val root = layout("Choose a deck")
        body(root, "Loading decks…")
        button(root, "Back") { parentSettings() }
        show(scroll(root))
        background({ AnkiDeckCatalog(contentResolver).decks() }) { result ->
            val list = layout("Choose a deck")
            result.fold(
                onSuccess = { decks ->
                    if (decks.isEmpty()) body(list, "AnkiDroid has no decks yet.")
                    decks.forEach { deck -> button(list, deck.name) { inspectDeck(deck) } }
                },
                onFailure = { body(list, "AnkiDroid could not be reached. Open AnkiDroid and try again.") }
            )
            button(list, "Back") { parentSettings() }
            show(scroll(list))
        }
    }

    private fun inspectDeck(deck: AnkiDeckCatalog.DeckInfo) {
        val checking = layout("Checking ${deck.name}")
        body(checking, "Looking at every note in this deck…")
        show(scroll(checking))
        background({
            val catalog = AnkiDeckCatalog(contentResolver)
            DeckCompatibilityChecker.evaluate(catalog.models(), catalog.notesInDeck(deck.name))
        }) { result ->
            val root = layout(deck.name)
            result.fold(
                onSuccess = { compat ->
                    body(root, compat.summary(), 16f)
                    if (compat.usable) button(root, "Use this deck") { confirmDeck(deck, compat) }
                },
                onFailure = { body(root, "AnkiDroid could not be read. Open AnkiDroid and try again.") }
            )
            button(root, "Choose a different deck") { chooseDeck() }
            button(root, "Back") { parentSettings() }
            show(scroll(root))
        }
    }

    private fun confirmDeck(deck: AnkiDeckCatalog.DeckInfo, compat: DeckCompatibility) {
        val active = activeBatch()
        val switching = settings.configured && settings.deckId != deck.id
        fun commit() {
            try {
                if (active != null && switching) progress.abandonBatch(active)
                settings.save(deck.id, deck.name, installedAnkiVersion() ?: "",
                    compat.verdicts.associate { it.model.id to checkNotNull(it.mapping) })
                parentSettings()
            } catch (_: IllegalStateException) {
                Toast.makeText(this, "Resolve the unconfirmed review first.", Toast.LENGTH_LONG).show()
            }
        }
        if (active != null && switching) {
            AlertDialog.Builder(this)
                .setTitle("Switch deck?")
                .setMessage("The study batch in progress will be dropped. Missions already earned and your island stay.")
                .setPositiveButton("Switch") { _, _ -> commit() }
                .setNegativeButton("Cancel", null)
                .show()
        } else commit()
    }

    // ---- recovery ----------------------------------------------------------------------------

    private fun recoverInterruptedReviews(done: () -> Unit) {
        if (!settings.configured || progress.unresolvedAttempts().isEmpty()) { done(); return }
        background({
            val recovery = ReviewRecovery(AnkiDroidGateway(contentResolver, settings.deckId), PersistentRecoveryStore(progress))
            PersistentRecoveryStore(progress).unresolved().map { recovery.recover(it) }.any { it == RecoveryOutcome.CONFIRMED }
        }) { done() }
    }

    // ---- live study --------------------------------------------------------------------------

    private sealed class Fetched {
        object Empty : Fetched()
        data class Unsupported(val why: String) : Fetched()
        data class Ready(val card: AnkiDroidGateway.ScheduledCard, val mapped: MappedCard) : Fetched()
    }

    private fun batchProgress(batch: String): Pair<Int, Int> {
        val target = database.readableDatabase.rawQuery("SELECT target FROM batches WHERE id=?", arrayOf(batch))
            .use { if (it.moveToFirst()) it.getInt(0) else 5 }
        val done = database.readableDatabase.rawQuery("SELECT COUNT(*) FROM credits WHERE batch_id=?", arrayOf(batch))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return target to done
    }

    private fun difficultyName() = if (prefs.getBoolean("normal-difficulty", false)) "normal" else "easy"

    private fun liveStudy() {
        if (progress.unresolvedAttempts().isNotEmpty() || !settings.configured) { home(); return }
        val batch = try {
            activeBatch() ?: progress.startBatch(settings.connectionId, prefs.getInt("batch-size", 5),
                System.currentTimeMillis(), difficultyName())
        } catch (_: IllegalStateException) { home(); return }
        val gateway = AnkiDroidGateway(contentResolver, settings.deckId)
        val coordinator = ReviewSubmissionCoordinator(gateway, PersistentReviewJournal(progress))
        val mappings = settings.mappings()
        info("Finding your next question…", "One moment.")
        background({ fetchNext(gateway, mappings) }) { result ->
            result.fold(
                onSuccess = { fetched ->
                    when (fetched) {
                        Fetched.Empty -> finishLiveBatch(batch)
                        is Fetched.Unsupported -> info("This question can't be shown", fetched.why)
                        is Fetched.Ready -> presentLive(batch, coordinator, fetched)
                    }
                },
                onFailure = { info("AnkiDroid isn't answering", "Please ask a parent to open AnkiDroid and check Parent settings.") }
            )
        }
    }

    /** Reads fresh note content and re-verifies the layout; anything doubtful is refused, never guessed. */
    private fun fetchNext(gateway: AnkiDroidGateway, mappings: Map<Long, FieldMapping>): Fetched {
        val card = gateway.nextCard() ?: return Fetched.Empty
        val catalog = AnkiDeckCatalog(contentResolver)
        val changed = "This deck changed since it was connected. Please ask a parent to check Parent settings."
        val note = catalog.note(card.noteId) ?: return Fetched.Unsupported(changed)
        val saved = mappings[note.modelId] ?: return Fetched.Unsupported(changed)
        val model = catalog.models()[note.modelId] ?: return Fetched.Unsupported(changed)
        if (card.ordinal != 0 || note.fields.size != model.fieldNames.size) return Fetched.Unsupported(changed)
        if (DeckCompatibilityChecker.deriveMapping(model).first != saved) return Fetched.Unsupported(changed)
        val mapped = CardContentMapper.map(model.templates, saved, model.fieldNames.zip(note.fields).toMap())
            ?: return Fetched.Unsupported("This card has pictures, sounds, formulas or formatting that can't be shown safely. Please ask a parent.")
        return Fetched.Ready(card, mapped)
    }

    private fun finishLiveBatch(batch: String) {
        val done = batchProgress(batch).second
        if (done == 0) {
            info("No questions are due", "You're all caught up for now. Try practice, or visit your island!")
            return
        }
        try { progress.finishExhausted(batch) } catch (_: IllegalStateException) { }
        if (ticket() != null) Toast.makeText(this, "Mission earned!", Toast.LENGTH_LONG).show()
        home()
    }

    private fun html(value: String) = Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT)

    private fun presentLive(batch: String, coordinator: ReviewSubmissionCoordinator, ready: Fetched.Ready) {
        val (target, done) = batchProgress(batch)
        val root = layout("Question ${done + 1} of $target")
        root.addView(TextView(this).apply { text = html(ready.mapped.questionHtml); textSize = 22f; setPadding(0, 16, 0, 16) })
        clock.start()
        val reveal = button(root, "Reveal answer") { }
        reveal.setOnClickListener {
            root.removeView(reveal)
            root.addView(TextView(this).apply { text = html(ready.mapped.answerHtml); textSize = 20f; setPadding(0, 16, 0, 16) })
            val ratingButtons = mutableListOf<Button>()
            listOf(
                "Again · forgot" to Rating.AGAIN, "Hard · remembered with difficulty" to Rating.HARD,
                "Good · remembered" to Rating.GOOD, "Easy · remembered very easily" to Rating.EASY
            ).forEach { (label, rating) ->
                ratingButtons += button(root, label) {
                    ratingButtons.forEach { it.isEnabled = false }
                    val elapsed = clock.finish()
                    val attempt = UUID.randomUUID().toString()
                    val ref = CardRef(ready.card.noteId, ready.card.ordinal)
                    background({ coordinator.submit(attempt, batch, ref, ready.card.state, rating, elapsed) }) { afterSubmit(it) }
                }
            }
        }
        button(root, "Home") { clock.finish(); home() }
        show(scroll(root))
    }

    private fun afterSubmit(result: Result<SubmissionResult>) {
        result.fold(
            onSuccess = {
                when (it) {
                    SubmissionResult.CONFIRMED -> if (ticket() != null) home() else liveStudy()
                    SubmissionResult.STALE -> liveStudy()
                    SubmissionResult.UNCERTAIN -> info("Study is paused",
                        "We couldn't be sure that answer reached AnkiDroid. Please ask a parent to check, so nothing gets counted twice.")
                }
            },
            onFailure = { info("Something went wrong", "That answer was not sent. Please try again, or ask a parent.") }
        )
    }

    // ---- demo study, game, island ------------------------------------------------------------

    private data class Ticket(val id: String, val seed: Long, val difficulty: RunnerDifficulty)
    private fun ticket(): Ticket? = database.readableDatabase.rawQuery(
        "SELECT id, seed, difficulty FROM tickets WHERE status='earned'", null
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else Ticket(cursor.getString(0), cursor.getLong(1),
            if (cursor.getString(2) == "normal") RunnerDifficulty.NORMAL else RunnerDifficulty.EASY)
    }

    private fun activeBatch(): String? = database.readableDatabase.rawQuery(
        "SELECT id FROM batches WHERE state='active'", null
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun study() {
        val batch = activeBatch() ?: progress.startBatch("synthetic-demo", prefs.getInt("batch-size", 5),
            System.currentTimeMillis(), difficultyName())
        val answered = mutableSetOf<Long>()
        database.readableDatabase.rawQuery("SELECT card_id FROM credits WHERE batch_id=?", arrayOf(batch)).use {
            while (it.moveToNext()) answered.add(it.getLong(0))
        }
        val index = questions.indices.firstOrNull { (it + 1).toLong() !in answered }
        if (index == null) { progress.finishExhausted(batch); home(); return }
        val root = layout("Sample question ${answered.size + 1} of ${questions.size}")
        root.addView(TextView(this).apply { text = questions[index].first; textSize = 22f })
        button(root, "Reveal answer") {
            root.addView(TextView(this).apply { text = questions[index].second; textSize = 20f })
            // Remove reveal so one question can only be answered once per presentation.
            root.removeViewAt(3)
            listOf("Again · forgot" to "again", "Hard · remembered with difficulty" to "hard",
                "Good · remembered" to "good", "Easy · remembered very easily" to "easy").forEach { (label, rating) ->
                button(root, label) {
                    val id = UUID.randomUUID().toString()
                    progress.recordPending(id, batch, (index + 1).toLong(), 0, (index + 1).toLong(), 0, 0, rating, 0)
                    if (progress.confirm(id)) home() else study()
                }
            }
        }
        button(root, "Home") { home() }
        show(scroll(root))
    }

    private fun run(mode: RunnerMode, ticket: Ticket? = null) {
        val difficulty = ticket?.difficulty ?: if (prefs.getBoolean("normal-difficulty", false)) RunnerDifficulty.NORMAL else RunnerDifficulty.EASY
        val screen = RunnerScreen(this, ticket?.seed ?: System.currentTimeMillis(), difficulty,
            mode, showButtons = prefs.getBoolean("runner-buttons", true), onFinished = { result ->
                if (mode == RunnerMode.TUTORIAL && result.status == RunnerStatus.WON)
                    prefs.edit().putBoolean("tutorial-completed", true).apply()
                val settled = if (mode == RunnerMode.MISSION && ticket != null)
                    island.settle(ticket.id, result.stars, result.status == RunnerStatus.WON, false) else false
                val root = layout(if (result.status == RunnerStatus.WON) "You made it!" else "Good try!")
                root.addView(TextView(this).apply {
                    text = if (settled) "${if (live) "Mission" else "Demo mission"} complete! Your island earned ${10 + result.stars.coerceAtMost(20)} stars."
                    else "No island stars were awarded for this run."
                })
                if (result.status != RunnerStatus.WON) button(root, "Try again") { run(mode, ticket) }
                button(root, "Home") { home() }
                show(scroll(root))
            }, onExit = { home() })
        runner = screen
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = if (live) "Mission run" else "DEMO · Sample gameplay only" })
            addView(screen, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    private fun showIsland() {
        val root = layout(if (live) "My island" else "Demo island")
        root.addView(IslandScreen(this, island), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        button(root, "Home") { home() }
        show(scroll(root))
    }

    private companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        const val ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}

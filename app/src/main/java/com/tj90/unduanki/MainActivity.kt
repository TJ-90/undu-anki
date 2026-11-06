package com.tj90.unduanki

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tj90.unduanki.data.GameDatabase
import com.tj90.unduanki.data.ProgressRepository
import com.tj90.unduanki.game.runner.RunnerDifficulty
import com.tj90.unduanki.game.runner.RunnerMode
import com.tj90.unduanki.game.runner.RunnerScreen
import com.tj90.unduanki.game.runner.RunnerStatus
import com.tj90.unduanki.island.IslandRepository
import com.tj90.unduanki.island.IslandScreen
import java.util.UUID

/** Synthetic exploration only; no AnkiDroid provider access is enabled here. */
class MainActivity : Activity() {
    private lateinit var database: GameDatabase
    private lateinit var progress: ProgressRepository
    private lateinit var island: IslandRepository
    private var runner: RunnerScreen? = null
    private val prefs by lazy { getSharedPreferences("demo-settings", MODE_PRIVATE) }
    private val questions = listOf(
        "Which planet do we live on?" to "Earth",
        "How many legs does a spider have?" to "Eight",
        "What do bees make?" to "Honey",
        "Which ocean animal has eight arms?" to "An octopus",
        "What is the largest land animal?" to "The African elephant"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = GameDatabase(this, demo = true)
        progress = ProgressRepository(database)
        island = IslandRepository(database)
        // Synthetic records have no external side effect and can safely be abandoned after interruption.
        progress.unresolvedAttempts().forEach { progress.abandonUnresolved(it.id) }
        home()
    }

    override fun onPause() { runner?.pauseGame(); super.onPause() }
    override fun onDestroy() { database.close(); super.onDestroy() }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { runner?.pauseGame(); home() }

    private fun layout(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        addView(TextView(context).apply { text = title; textSize = 24f })
        addView(TextView(context).apply {
            text = "DEMO · Sample questions and a separate island save. Real AnkiDroid study is not available yet."
            textSize = 16f
        })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }

    private fun show(content: View) { runner = null; setContentView(content) }
    private fun scroll(content: View) = ScrollView(this).apply { addView(content) }

    private fun home() {
        val root = layout("Mischief Island")
        if (!prefs.getBoolean("tutorial-completed", false)) {
            button(root, "Play the tutorial") { run(RunnerMode.TUTORIAL) }
        } else {
            val ticket = ticket()
            if (ticket != null) button(root, "Continue demo mission") { run(RunnerMode.MISSION, ticket) }
            else button(root, "Study sample questions") { study() }
            button(root, "Practice runner") { run(RunnerMode.PRACTICE) }
            button(root, "Visit demo island") { showIsland() }
            button(root, "Play tutorial again") { run(RunnerMode.TUTORIAL) }
        }
        show(scroll(root))
    }

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
        val batch = activeBatch() ?: progress.startBatch("synthetic-demo", questions.size, System.currentTimeMillis(), "easy")
        val answered = mutableSetOf<Long>()
        database.readableDatabase.rawQuery("SELECT card_id FROM credits WHERE batch_id=?", arrayOf(batch)).use {
            while (it.moveToNext()) answered.add(it.getLong(0))
        }
        val index = questions.indices.firstOrNull { (it + 1).toLong() !in answered }
        if (index == null) { home(); return }
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
                    progress.recordPending(id, batch, (index + 1).toLong(), 0, 0, rating, 0)
                    if (progress.confirm(id)) home() else study()
                }
            }
        }
        button(root, "Home") { home() }
        show(scroll(root))
    }

    private fun run(mode: RunnerMode, ticket: Ticket? = null) {
        val screen = RunnerScreen(this, ticket?.seed ?: System.currentTimeMillis(), ticket?.difficulty ?: RunnerDifficulty.EASY,
            mode, onFinished = { result ->
                if (mode == RunnerMode.TUTORIAL && result.status == RunnerStatus.WON)
                    prefs.edit().putBoolean("tutorial-completed", true).apply()
                val settled = if (mode == RunnerMode.MISSION && ticket != null)
                    island.settle(ticket.id, result.stars, result.status == RunnerStatus.WON, false) else false
                val root = layout(if (result.status == RunnerStatus.WON) "You made it!" else "Good try!")
                root.addView(TextView(this).apply { text = if (settled) "Demo mission complete! Your island earned ${10 + result.stars.coerceAtMost(20)} stars." else "No island stars were awarded for this run." })
                if (result.status != RunnerStatus.WON) button(root, "Try again") { run(mode, ticket) }
                button(root, "Home") { home() }
                show(scroll(root))
            }, onExit = { home() })
        runner = screen
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = "DEMO · Sample gameplay only" })
            addView(screen, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    private fun showIsland() {
        val root = layout("Demo island")
        root.addView(IslandScreen(this, island), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        button(root, "Home") { home() }
        show(scroll(root))
    }
}

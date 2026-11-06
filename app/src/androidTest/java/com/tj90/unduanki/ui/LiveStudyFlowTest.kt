package com.tj90.unduanki.ui

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tj90.unduanki.MainActivity
import com.tj90.unduanki.anki.AnkiDroidGateway
import com.tj90.unduanki.data.GameDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole live path through the real screens against a real AnkiDroid: connect, choose and inspect a
 * deck, study, rate, earn a mission. Runs only in the dedicated provider job on a disposable emulator with
 * a fresh, empty collection, and only with the explicit attestations below.
 */
@RunWith(AndroidJUnit4::class)
class LiveStudyFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val root = "content://com.ichi2.anki.flashcards"
    private var activity: MainActivity? = null
    private var owns = false

    @Before fun requireDedicatedDisposableRun() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run only in the dedicated live-flow job", args.getString("anki_live_flow") == "true")
        assertEquals("true", args.getString("anki_fixture_ephemeral"))
        assertTrue("Emulator only", Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        // Gradle installs a fresh copy of the app for every connected run, so grant AnkiDroid's API
        // permission here with the instrumentation's shell identity (what `pm grant` does).
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "com.ichi2.anki.permission.READ_WRITE_DATABASE")
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission("com.ichi2.anki.permission.READ_WRITE_DATABASE"))
        assertTrue("Fresh app state required", context.getSharedPreferences("app-mode", 0).all.isEmpty() &&
            !context.getDatabasePath("game-progress.db").exists())
        resolver.query(Uri.parse("$root/notes_v2"), arrayOf("_id"), null, null, null)!!.use {
            assertEquals("Refusing to write to a nonempty collection", 0, it.count)
        }
        owns = true
    }

    @After fun cleanup() {
        if (!owns) return
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        instrumentation.waitForIdleSync()
        context.deleteDatabase("game-progress.db")
        for (name in listOf("app-mode", "live-settings", "live-connection")) context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    @Test fun parentConnectsADeckAndTheChildStudiesAndEarnsAMission() {
        val deckId = createDeck("Gate Live")
        val noteIds = (1..3).map { addBasicNote("Live $it", deckId) }
        context.getSharedPreferences("app-mode", 0).edit().putBoolean("live", true).commit()
        context.getSharedPreferences("live-settings", 0).edit().putBoolean("tutorial-completed", true).putInt("batch-size", 3).commit()

        launch()
        assertTrue(hasText("A parent needs to connect an AnkiDroid deck first."))
        openParentSettings()
        waitUntil("connected status") { hasText("Connected to AnkiDroid 2.25.3") }
        click("Choose a deck")
        click("Gate Live")
        waitUntil("compatibility summary") { hasText("All 3 notes can be shown") }
        click("Use this deck")
        waitUntil("saved deck") { hasText("Deck: Gate Live") }
        click("Home")

        click("Study a few questions")
        repeat(3) {
            click("Reveal answer")
            click("Good · remembered")
        }
        waitUntil("mission ticket") { hasButton("Continue mission") }

        val gateway = AnkiDroidGateway(resolver, deckId)
        for (note in noteIds) assertEquals("Each card reviewed exactly once", 1, gateway.inspect(note, 0).state.reps)

        val database = GameDatabase(context)
        try {
            database.readableDatabase.rawQuery("SELECT status, COUNT(*) FROM attempts GROUP BY status", null).use {
                assertTrue(it.moveToFirst()); assertEquals("confirmed", it.getString(0)); assertEquals(3, it.getInt(1)); assertTrue(!it.moveToNext())
            }
            database.readableDatabase.rawQuery("SELECT COUNT(*) FROM tickets WHERE status='earned'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        } finally {
            database.close()
        }
        android.util.Log.i("MischiefGate", "live flow: setup, study of 3 cards and mission earned through the real UI")
    }

    private fun createDeck(name: String): Long {
        val uri = resolver.insert(Uri.parse("$root/decks"), ContentValues().apply { put("deck_name", name) })
        assertNotNull(uri)
        return uri!!.lastPathSegment!!.toLong()
    }

    private fun addBasicNote(text: String, deckId: Long): Long {
        val model = resolver.query(Uri.parse("$root/models"), arrayOf("_id", "name", "field_names", "num_cards", "type"), null, null, null)!!.use {
            var id: Long? = null
            while (it.moveToNext()) if (it.getString(1) == "Basic" && it.getString(2).split('\u001f') == listOf("Front", "Back") && it.getInt(3) == 1) { id = it.getLong(0); break }
            checkNotNull(id)
        }
        val values = ContentValues().apply { put("mid", model); put("flds", "$text question\u001f$text answer"); put("tags", "mischief_gate_fixture") }
        val noteId = resolver.insert(Uri.parse("$root/notes"), values)!!.lastPathSegment!!.toLong()
        assertEquals(1, resolver.update(Uri.parse("$root/notes/$noteId/cards/0"), ContentValues().apply { put("deck_id", deckId) }, null, null))
        return noteId
    }

    private fun launch() {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent) as MainActivity
        instrumentation.waitForIdleSync()
    }

    private fun openParentSettings() {
        instrumentation.runOnMainSync {
            val parent = views(activity!!.window.decorView).filterIsInstance<TextView>()
                .first { it.text.toString().startsWith("Parent settings ·") }
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
            parent.dispatchTouchEvent(down)
            down.recycle()
        }
        SystemClock.sleep(3300)
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            var ok = false
            instrumentation.runOnMainSync { ok = condition() }
            if (ok) return
            SystemClock.sleep(200)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun click(label: String) {
        waitUntil("button '$label'") { hasButton(label) }
        instrumentation.runOnMainSync {
            val target = views(activity!!.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }
            assertTrue("Button did not accept click: $label", target.performClick())
        }
        instrumentation.waitForIdleSync()
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }

    private fun hasButton(label: String) = views(activity!!.window.decorView).filterIsInstance<Button>().any { it.text.toString() == label }
    private fun hasText(text: String) = views(activity!!.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(text) }
}

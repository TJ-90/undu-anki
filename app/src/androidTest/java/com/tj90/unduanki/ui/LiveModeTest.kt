package com.tj90.unduanki.ui

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tj90.unduanki.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live mode on a device without AnkiDroid: it must refuse to study and explain why. Runs only on a fresh
 * install and removes only what it created; the AnkiDroid-provider suite is separate.
 */
@RunWith(AndroidJUnit4::class)
class LiveModeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val modePrefs by lazy { context.getSharedPreferences("app-mode", 0) }
    private val livePrefs by lazy { context.getSharedPreferences("live-settings", 0) }
    private var owns = false
    private var activity: MainActivity? = null

    @Before fun freshInstallOnly() {
        assumeTrue("Requires a fresh installation", modePrefs.all.isEmpty() && livePrefs.all.isEmpty() &&
            !context.getDatabasePath("game-progress.db").exists())
        val anki = try { context.packageManager.getPackageInfo("com.ichi2.anki", 0); true } catch (_: Exception) { false }
        assumeTrue("Expects a device without AnkiDroid", !anki)
        owns = true
    }

    @After fun cleanup() {
        if (!owns) return
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        instrumentation.waitForIdleSync()
        context.deleteDatabase("game-progress.db")
        modePrefs.edit().clear().commit()
        livePrefs.edit().clear().commit()
        context.getSharedPreferences("live-connection", 0).edit().clear().commit()
    }

    @Test fun liveModeWithoutAnkiDroidOffersNoStudyAndExplainsTheParentStep() {
        modePrefs.edit().putBoolean("live", true).commit()
        livePrefs.edit().putBoolean("tutorial-completed", true).commit()
        launch()
        assertTrue(hasText("A parent needs to connect an AnkiDroid deck first."))
        assertFalse(hasButton("Study a few questions"))
        assertFalse(hasButton("Study sample questions"))

        openParentSettings()
        assertTrue(hasText("AnkiDroid is not installed"))
        assertFalse(hasButton("Choose a deck"))
        assertTrue(hasButton("Use demo mode instead"))
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
        SystemClock.sleep(3200)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }

    private fun hasButton(label: String) = views(activity!!.window.decorView).filterIsInstance<Button>().any { it.text.toString() == label }
    private fun hasText(text: String) = views(activity!!.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(text) }
}

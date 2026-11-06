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

/** UI smoke tests use only a fresh demo installation and never erase an existing save. */
@RunWith(AndroidJUnit4::class)
class DemoFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs by lazy { context.getSharedPreferences("demo-settings", 0) }
    private var ownsDemoState = false
    private var activity: MainActivity? = null

    @Before fun freshDemoOnly() {
        assumeTrue("Requires a fresh demo installation", !context.getDatabasePath("demo-progress.db").exists() && prefs.all.isEmpty())
        ownsDemoState = true
    }

    @After fun cleanup() {
        if (!ownsDemoState) return
        closeActivity()
        context.deleteDatabase("demo-progress.db")
        prefs.edit().clear().commit()
    }

    @Test fun firstLaunchExplainsDemoAndOffersTutorial() {
        launch()
        assertTrue(hasText("Real AnkiDroid study is not available yet."))
        assertNotNull(button("Play the tutorial"))
        assertFalse(hasButton("Study sample questions"))
    }

    @Test fun sampleRatingsAreHiddenUntilRevealAndEarnPersistentDemoMission() {
        prefs.edit().putBoolean("tutorial-completed", true).commit()
        launch()
        click("Study sample questions")
        assertTrue(hasText("Which planet do we live on?"))
        assertFalse(hasButton("Again · forgot"))

        val ratings = listOf("Again · forgot", "Hard · remembered with difficulty", "Good · remembered", "Again · forgot", "Easy · remembered very easily")
        ratings.forEach { rating ->
            click("Reveal answer")
            assertTrue(hasButton(rating))
            click(rating)
        }
        assertTrue(hasButton("Continue demo mission"))
        closeActivity()
        launch()
        assertTrue(hasButton("Continue demo mission"))
    }

    @Test fun parentCanSetLargerBatchAndExhaustTheSampleQueue() {
        prefs.edit().putBoolean("tutorial-completed", true).commit()
        launch()
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
        assertTrue(hasText("Demo parent settings"))
        click("More questions")
        assertTrue(hasText("Questions per mission: 6"))
        click("Home")
        click("Study sample questions")
        repeat(5) { click("Reveal answer"); click("Good · remembered") }
        assertTrue(hasButton("Continue demo mission"))
    }

    private fun launch() {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent) as MainActivity
        instrumentation.waitForIdleSync()
    }

    private fun closeActivity() {
        activity?.let { current -> instrumentation.runOnMainSync { current.finish() } }
        instrumentation.waitForIdleSync()
        activity = null
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }

    private fun button(label: String): Button? = views(activity!!.window.decorView)
        .filterIsInstance<Button>().firstOrNull { it.text.toString() == label }

    private fun hasButton(label: String) = button(label) != null
    private fun hasText(text: String) = views(activity!!.window.decorView).filterIsInstance<TextView>()
        .any { it.text.toString().contains(text) }

    private fun click(label: String) {
        instrumentation.runOnMainSync {
            val target = button(label)
            assertNotNull("Missing button: $label", target)
            assertTrue("Button did not accept click: $label", target!!.performClick())
        }
        instrumentation.waitForIdleSync()
    }
}

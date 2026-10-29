package app.duenorth.budget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** A crash's trace is kept and offered once on the next launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashLogTest {
    @Test
    fun theNextLaunchSharesTheLastCrashOnce() {
        val context = RuntimeEnvironment.getApplication()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            CrashLog.install(context)
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
        assertTrue(CrashLog.offer(context))
        val chooser = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("IllegalStateException: boom"))
        assertFalse(CrashLog.offer(context))
    }
}

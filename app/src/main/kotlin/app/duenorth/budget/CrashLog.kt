package app.duenorth.budget

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * The app keeps the last crash's stack trace, and the next launch offers to share it, since a phone has no other
 * way to show one without a computer and adb. Release builds too: they are what goes on the phone that crashes.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText("Due North crash on ${thread.name}\n\n${error.stackTraceToString()}") }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Opens the share sheet with the last crash's trace, once; false when there was none. */
    fun offer(context: Context): Boolean {
        val file = File(context.filesDir, FILE)
        val text = runCatching { file.readText() }.getOrNull() ?: return false
        file.delete()
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Due North crash report")
                putExtra(Intent.EXTRA_TEXT, text)
            }
        val chooser = Intent.createChooser(send, "Share the crash report")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return true
    }
}

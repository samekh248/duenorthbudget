package app.duenorth.budget

import android.os.Looper
import org.robolectric.Shadows

/** Runs everything queued on the main looper: the view model's coroutines and, made with `io = Dispatchers.Main`, its file work too. */
internal fun idleMain() {
    Shadows.shadowOf(Looper.getMainLooper()).idle()
}

/**
 * Idles the main looper until [predicate] holds. A view model made with `io = Dispatchers.Main` needs no clock: its
 * work all runs on that looper, so the first idle usually settles it. The deadline and the short sleeps are for work
 * on other threads only, such as an activity's own view model or a local server.
 */
internal fun awaitMain(
    timeoutMs: Long = 10_000,
    predicate: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    idleMain()
    while (!predicate()) {
        check(System.nanoTime() < deadline) { "timed out" }
        Thread.sleep(5)
        idleMain()
    }
}

/** Gives any write a chance to land, so a test that expects none would see it. */
internal fun settleMain(rounds: Int = 20) {
    repeat(rounds) {
        idleMain()
        Thread.sleep(10)
    }
}

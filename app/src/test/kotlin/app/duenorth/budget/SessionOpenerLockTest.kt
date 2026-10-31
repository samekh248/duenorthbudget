package app.duenorth.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionOpenerLockTest {
    /**
     * A sync applying a long reply holds the file in a transaction; a screen reload on another thread
     * opening the same file must wait its turn rather than fail with "database is locked".
     */
    @Test
    fun aSecondOpenWaitsForALongTransactionInsteadOfFailing() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "opener-lock.sqlite").apply { delete() }
        val opener = AndroidSessionOpener()
        opener.use(file) { it.exec("CREATE TABLE t (v INTEGER)") }

        val inside = CountDownLatch(1)
        val writer =
            thread {
                opener.use(file) { session ->
                    session.transaction {
                        session.exec("INSERT INTO t (v) VALUES (?)", listOf(1))
                        inside.countDown()
                        // Longer than Android's 2.5 s busy timeout.
                        Thread.sleep(3_500)
                    }
                }
            }
        assert(inside.await(5, TimeUnit.SECONDS))

        var failure: Throwable? = null
        val reader =
            thread {
                try {
                    opener.use(file) { session ->
                        session.query("SELECT COUNT(*) AS n FROM t", emptyList())
                        session.transaction { session.exec("INSERT INTO t (v) VALUES (?)", listOf(2)) }
                    }
                } catch (error: Throwable) {
                    failure = error
                }
            }
        writer.join()
        reader.join()

        assertNull(failure)
        val count = opener.use(file) { it.query("SELECT COUNT(*) AS n FROM t", emptyList()).single().values["n"] }
        assertEquals(2L, count)
    }
}

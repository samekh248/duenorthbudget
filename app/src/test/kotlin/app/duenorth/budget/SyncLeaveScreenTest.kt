package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.ActualTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.KeyMaterial
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncLeaveScreenTest {
    @Test
    fun leavingTheScreenKeepsTheDownloadProgress() {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "leave-screen").apply {
                deleteRecursively()
                mkdirs()
            }
        val seed = File(context.cacheDir, "seed.sqlite")
        seed.delete()
        AndroidSessionOpener().use(seed) { session ->
            app.duenorth.budget.core.ActualSchema
                .create(session)
        }
        val bytes = seed.readBytes()
        val transport = HoldingDownload(bytes)
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val sync =
            SyncCoordinator(
                library,
                transport,
                MemorySecretStore(),
                BudgetClock { LocalDate.of(2026, 10, 7) },
            )
        val model = ShellViewModel(library, sync)
        await { !model.state.value.loading }
        model.showServer()
        await { model.state.value.route == ShellRoute.Server }
        model.connect("http://127.0.0.1:9", "server-secret")
        await { model.state.value.signedIn && !model.state.value.syncing }
        model.requestRemote(RemoteFile("file-home", "group-1", "Home", null))
        drain()
        assertTrue(transport.started.await(5, TimeUnit.SECONDS))
        val progress = model.state.value.syncProgress
        assertNotNull(progress)
        assertTrue(progress!! > 0f)
        assertTrue(model.state.value.syncing)
        assertTrue(model.back())
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(progress, model.state.value.syncProgress)
        assertTrue(model.state.value.syncing)
        transport.release.countDown()
        await { !model.state.value.syncing }
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    private fun drain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() > deadline) error("timed out")
            drain()
            Thread.sleep(15)
        }
    }

    private class HoldingDownload(
        private val bytes: ByteArray,
    ) : ActualTransport {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun login(
            address: String,
            password: String,
        ): String = "token"

        override fun listFiles(
            address: String,
            token: String,
        ): List<RemoteFile> = listOf(RemoteFile("file-home", "group-1", "Home", null))

        override fun download(
            address: String,
            token: String,
            fileId: String,
            onProgress: (received: Long, total: Long) -> Unit,
        ): ByteArray {
            onProgress(100, 1_000)
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            onProgress(1_000, 1_000)
            return bytes
        }

        override fun upload(
            address: String,
            token: String,
            fileId: String,
            groupId: String?,
            name: String,
            bytes: ByteArray,
        ): String = "group-1"

        override fun sync(
            address: String,
            token: String,
            body: ByteArray,
        ): ByteArray = SyncProto.encodeResponse("{}", emptyList())

        override fun encryptionKey(
            address: String,
            token: String,
            fileId: String,
        ): KeyMaterial? = null
    }
}

package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.ActualTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.KeyMaterial
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncNetworkException
import app.duenorth.budget.core.SyncProto
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncAfterWriteTest {
    @Test
    fun aChangeIsSentRightAwayAndAnOfflineOneWhenTheAppCloses() {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "sync-after-write").apply {
                deleteRecursively()
                mkdirs()
            }
        val seed = File(context.cacheDir, "seed-after-write.sqlite")
        seed.delete()
        AndroidSessionOpener().use(seed) { session ->
            app.duenorth.budget.core.ActualSchema
                .create(session)
        }
        val transport = CountingServer(seed.readBytes())
        val clock = BudgetClock { LocalDate.of(2026, 10, 7) }
        val library = BudgetLibrary(root, AndroidSessionOpener(), clock)
        val sync = SyncCoordinator(library, transport, MemorySecretStore(), clock, retryDelays = emptyList())
        val model = ShellViewModel(library, sync, io = Dispatchers.Main)
        awaitMain { !model.state.value.loading }
        model.servers.show()
        awaitMain { model.state.value.route == ShellRoute.Server }
        model.servers.connect("http://127.0.0.1:9", "server-secret")
        awaitMain { model.state.value.signedIn && !model.state.value.syncing }
        model.budgetFiles.requestRemote(RemoteFile("file-home", "group-1", "Home", null))
        awaitMain { model.state.value.settings.openBudgetId == "file-home" && !model.state.value.syncing }
        settleMain()
        val opened = transport.syncs.get()

        library.saveCategoryNote("file-home", "c-food", "#template 50")
        awaitMain { transport.syncs.get() > opened }
        settleMain()
        val sent = transport.syncs.get()

        transport.online = false
        library.saveCategoryNote("file-home", "c-food", "#template 60")
        awaitMain { transport.syncs.get() > sent }
        settleMain()
        transport.online = true
        val failed = transport.syncs.get()
        model.onScreenLocked()
        awaitMain { transport.syncs.get() > failed }
        settleMain()
        val closed = transport.syncs.get()
        model.onScreenLocked()
        settleMain()
        assertEquals(closed, transport.syncs.get())
    }

    /** Lets sends already started finish, so counts read afterwards are final. */
    private class CountingServer(
        private val bytes: ByteArray,
    ) : ActualTransport {
        val syncs = AtomicInteger()

        @Volatile
        var online = true

        override fun login(
            address: String,
            password: String,
        ): String = "token"

        override fun loginMethods(address: String): List<String> = listOf("password")

        override fun beginOpenId(
            address: String,
            returnUrl: String,
        ): String = "$address/openid?returnUrl=$returnUrl"

        override fun listFiles(
            address: String,
            token: String,
        ): List<RemoteFile> = listOf(RemoteFile("file-home", "group-1", "Home", null))

        override fun download(
            address: String,
            token: String,
            fileId: String,
            onProgress: (received: Long, total: Long) -> Unit,
        ): ByteArray = bytes

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
        ): ByteArray {
            syncs.incrementAndGet()
            if (!online) throw SyncNetworkException(java.io.IOException("offline"))
            return SyncProto.encodeResponse("{}", emptyList())
        }

        override fun encryptionKey(
            address: String,
            token: String,
            fileId: String,
        ): KeyMaterial? = null
    }
}

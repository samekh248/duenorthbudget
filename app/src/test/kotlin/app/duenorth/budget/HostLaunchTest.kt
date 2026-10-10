package app.duenorth.budget

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HostLaunchTest {
    @Test
    fun composeHostLaunchesWithoutALauncherFilter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, ComponentActivity::class.java)
        ActivityScenario.launch<ComponentActivity>(intent).use { scenario ->
            scenario.onActivity { assertNotNull(it) }
        }
    }
}

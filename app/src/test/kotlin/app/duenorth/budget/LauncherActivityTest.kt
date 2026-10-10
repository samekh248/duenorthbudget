package app.duenorth.budget

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LauncherActivityTest {
    @Test
    fun installedAppOpensTheBudgetShellNamedDueNorthBudget() {
        val manifest = packagedDebugManifest()
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0)
        val label = application.attributes.getNamedItem("android:label").nodeValue
        assertEquals("@string/app_name", label)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Due North Budget", context.getString(R.string.app_name))

        val activities = document.getElementsByTagName("activity")
        val launchers = mutableListOf<String>()
        for (index in 0 until activities.length) {
            val activity = activities.item(index)
            val name = activity.attributes.getNamedItem("android:name").nodeValue
            val filters = activity.childNodes
            var launcher = false
            for (child in 0 until filters.length) {
                val node = filters.item(child)
                if (node.nodeName != "intent-filter") continue
                val categories = node.childNodes
                for (category in 0 until categories.length) {
                    val item = categories.item(category)
                    if (item.nodeName == "category" &&
                        item.attributes.getNamedItem("android:name")?.nodeValue == "android.intent.category.LAUNCHER"
                    ) {
                        launcher = true
                    }
                }
            }
            if (launcher) launchers.add(name)
        }
        assertEquals(listOf("app.duenorth.budget.MainActivity"), launchers)
    }

    private fun packagedDebugManifest(): File {
        val candidates =
            listOf(
                File("build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"),
                File("app/build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"),
            )
        return candidates.firstOrNull { it.isFile }
            ?: error("packaged debug manifest not found in ${candidates.map { it.path }}")
    }
}

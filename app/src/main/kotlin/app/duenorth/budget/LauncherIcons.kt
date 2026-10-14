package app.duenorth.budget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The home-screen icon styles; each is an activity-alias in the manifest. */
enum class LauncherIcon(
    val alias: String,
    val label: String,
) {
    /** The white mark on the magenta tile. */
    TILE("app.duenorth.budget.LauncherTile", "magenta tile"),

    /** The magenta mark with nothing behind it. */
    CLEAR("app.duenorth.budget.LauncherClear", "transparent"),
}

/**
 * Switches the launcher icon by enabling one alias and disabling the rest. The aliases' enabled
 * state is the stored setting, so nothing else needs saving. Some launchers drop the home-screen
 * shortcut on a switch; the app drawer always shows the new icon.
 */
object LauncherIcons {
    fun current(context: Context): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.TILE
                else -> false
            }
        } ?: LauncherIcon.TILE
    }

    fun set(
        context: Context,
        choice: LauncherIcon,
    ) {
        if (current(context) == choice) return
        val pm = context.packageManager
        // Enable the new alias first so there is never a moment with no launcher entry.
        val ordered = listOf(choice) + LauncherIcon.entries.filter { it != choice }
        ordered.forEach { icon ->
            val state =
                if (icon == choice) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            pm.setComponentEnabledSetting(component(context, icon), state, PackageManager.DONT_KILL_APP)
        }
    }

    private fun component(
        context: Context,
        icon: LauncherIcon,
    ) = ComponentName(context.packageName, icon.alias)
}

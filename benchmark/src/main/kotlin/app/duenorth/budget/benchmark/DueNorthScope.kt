package app.duenorth.budget.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test. Its benchmark build types share the release application id. */
const val PACKAGE = "app.duenorth.budget"

/** How long a screen may take to show before the run fails, far past any budget so only a hang trips it. */
private const val WAIT_MS = 10_000L

/** The pivot items of home, in the order of HomeSections.headers. */
private const val BUDGET_ITEM = 1

private val INBOX_CONTENT = Pattern.compile("inbox-(empty|featured|group-.*)")

/**
 * Writes the "wide month" sample budget (many groups, a long register) and opens it, once per install.
 * The notification ask is granted first, so its system dialog never covers the screen being measured.
 */
fun MacrobenchmarkScope.seedBudget() {
    device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
    device.executeShellCommand("am start -W -n $PACKAGE/.BenchmarkSeedActivity")
}

/** Waits until the open budget is on screen: the pivot is up and home has read the month. */
fun MacrobenchmarkScope.waitForBudget() {
    check(device.wait(Until.hasObject(By.res("pivot")), WAIT_MS)) { "home never appeared" }
    // Home's first item is the inbox, which shows its rows or its empty state only once the month is read.
    check(device.wait(Until.hasObject(By.res(INBOX_CONTENT)), WAIT_MS)) { "the open budget never loaded" }
}

/** Taps the budget header, then returns its category list once it shows. */
fun MacrobenchmarkScope.openBudgetList(): UiObject2 {
    device.findObject(By.res("pivot-header-$BUDGET_ITEM")).click()
    return checkNotNull(device.wait(Until.findObject(By.res("budget-list")), WAIT_MS)) { "the budget list never appeared" }
}

/** Flings [list] to its end and back, keeping clear of the system's edge gestures. */
fun MacrobenchmarkScope.flingThrough(list: UiObject2) {
    list.setGestureMargin(device.displayWidth / 5)
    repeat(FLINGS) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(FLINGS) {
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** Swipes the home pivot through every item, the way a person pans across it. */
fun MacrobenchmarkScope.swipeThroughPivot() {
    val pivot = device.findObject(By.res("pivot"))
    pivot.setGestureMargin(device.displayWidth / 5)
    repeat(PIVOT_ITEMS) {
        pivot.swipe(Direction.LEFT, 0.8f)
        device.waitForIdle()
    }
}

private const val FLINGS = 3
private const val PIVOT_ITEMS = 5

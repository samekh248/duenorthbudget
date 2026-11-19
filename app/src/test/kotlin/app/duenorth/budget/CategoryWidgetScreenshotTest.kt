package app.duenorth.budget

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The category widget at the launcher sizes it is most often given, as the widget's own views draw it: one cell
 * tall (narrow and wide), two by two, four by two and four by four, on a light and a dark home screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h1500dp-xxhdpi")
class CategoryWidgetScreenshotTest {
    @Test
    fun sizesLight() = snap("category_widget_sizes_light", dark = false)

    @Test
    fun sizesDark() = snap("category_widget_sizes_dark", dark = true)

    @Test
    fun emptyLight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val home = home(activity, dark = false)
        home.addView(widget(activity, 336, 192, null, "4×2, with no budget on the phone yet"))
        home.captureRoboImage("src/test/snapshots/category_widget_empty_light.png")
    }

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val home = home(activity, dark)
        listOf(
            Triple(336, 92, "4×1"),
            Triple(164, 92, "2×1"),
            Triple(164, 192, "2×2"),
            Triple(336, 192, "4×2"),
            Triple(336, 392, "4×4"),
        ).forEach { (w, h, label) -> home.addView(widget(activity, w, h, content, label)) }
        home.captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun home(
        activity: Activity,
        dark: Boolean,
    ): LinearLayout {
        val home =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        if (dark) {
                            intArrayOf(
                                0xFF0D1B2A.toInt(),
                                0xFF241018.toInt(),
                            )
                        } else {
                            intArrayOf(0xFFCFE3F2.toInt(), 0xFFE8D3E8.toInt())
                        },
                    )
                setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 8))
            }
        activity.setContentView(home, ViewGroup.LayoutParams(dp(activity, 368), ViewGroup.LayoutParams.WRAP_CONTENT))
        home.tag = dark
        return home
    }

    private fun widget(
        activity: Activity,
        widthDp: Int,
        heightDp: Int,
        content: CategoryWidget.Content?,
        label: String,
    ): LinearLayout {
        val frame = FrameLayout(activity)
        val views = CategoryWidget.views(activity, 7, CategoryWidgetPlan.of(widthDp, heightDp), content)
        frame.addView(views.apply(activity, frame), FrameLayout.LayoutParams(dp(activity, widthDp), dp(activity, heightDp)))
        val caption =
            TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(if ((activity.resources.configuration.uiMode and 0x30) == 0x20) Color.LTGRAY else Color.DKGRAY)
                setPadding(0, dp(activity, 3), 0, dp(activity, 10))
            }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(frame, LinearLayout.LayoutParams(dp(activity, widthDp), dp(activity, heightDp)))
            addView(caption)
        }
    }

    private fun dp(
        activity: Activity,
        value: Int,
    ) = (value * activity.resources.displayMetrics.density).toInt()

    private val content =
        CategoryWidget.Content(
            budgetId = "b",
            today = LocalDate.of(2026, 11, 19),
            currency = Currencies.byCode("USD")!!,
            options = FormatOptions(),
            rows =
                listOf(
                    row("Groceries", 60_000, -38_760),
                    row("Dining Out", 15_000, -16_825),
                    row("Gas", 20_000, -10_400),
                    row("Household", 25_000, -11_990),
                    row("Kids", 12_000, -4_500),
                    row("Fun Money", 10_000, -5_500),
                    row("Pets", 9_000, -6_230),
                    row("Coffee", 4_000, -3_150),
                    row("Gifts", 8_000, 0),
                ),
            chosen = true,
        )

    private fun row(
        name: String,
        budgeted: Long,
        spent: Long,
    ) = CategoryRow(name.lowercase(), name, budgeted, spent, budgeted + spent, carryover = false)
}

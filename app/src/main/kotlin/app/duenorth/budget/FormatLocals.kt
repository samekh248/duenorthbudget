package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.RegisterEntry

/** The budget's Settings > format choices, provided once by the app so every screen shows and reads them. */
val LocalFormatPrefs = compositionLocalOf { FormatPrefs() }

val FormatPrefs.options: FormatOptions get() = FormatOptions(hideFraction, numberFormat)

/** How amounts are written on this screen. */
@Composable
@ReadOnlyComposable
fun formatOptions(): FormatOptions = LocalFormatPrefs.current.options

/** How dates are written on this screen. */
@Composable
@ReadOnlyComposable
fun dateFormat(): String = LocalFormatPrefs.current.dateFormat

/** An amount as a box starts out showing it, in the person's number format. */
fun FormatPrefs.editAmount(
    minor: Long,
    decimals: Int,
): String = MoneyParse.editText(minor, decimals, options)

/** A typed amount rewritten as "1234.56" for code that reads amounts that way. */
fun FormatPrefs.readAmount(
    text: String,
    decimals: Int = 2,
): String = MoneyParse.canonical(text, numberFormat, decimals)

/** A typed date rewritten as `yyyy-MM-dd` for code that reads dates that way. */
fun FormatPrefs.readDate(text: String): String = DateFormats.toIso(text, dateFormat)

/** A signed amount as a box starts out showing it: "-1.234,56" in the dot-comma format. */
fun FormatPrefs.editSignedAmount(
    minor: Long,
    decimals: Int,
): String = (if (minor < 0) "-" else "") + editAmount(minor, decimals)

/** A `yyyy-MM-dd` date as a box starts out showing it, in the person's date format; anything else unchanged. */
fun FormatPrefs.showDate(iso: String): String = RegisterEntry.parseDate(iso)?.let { DateFormats.format(it, dateFormat) } ?: iso

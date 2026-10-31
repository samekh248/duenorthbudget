package app.duenorth.budget.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Actual 26.8.1's note template grammar (`goal-template.pegjs`), ported rule for rule. Like the PEG it tries each
 * form in order, commits to the first that matches, and fails the line unless that form reads it to the end.
 */
object TemplateParser {
    private const val TEMPLATE = "#template"
    private const val GOAL = "#goal"

    /** True for a line Actual reads as a template or goal: from its first `#`, it starts `#template` or `#goal`. */
    fun isTemplateLine(line: String): Boolean {
        val directive = directive(line)
        return directive.startsWith(TEMPLATE) || directive.startsWith(GOAL)
    }

    /** Every template line in [note]; a line that does not parse comes back as its text. */
    fun parse(note: String): List<Result<Template>> =
        note
            .lines()
            .map { it.trim() }
            .filter { isTemplateLine(it) }
            .map { line -> parseLine(line)?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException(line)) }

    fun parseLine(line: String): Template? {
        val text = directive(line)
        if (!text.startsWith(TEMPLATE) && !text.startsWith(GOAL)) return null
        val template = runCatching { Peg(text).expr() }.getOrNull() ?: return null
        // template-notes.ts refuses a percentage adjustment outside -100% to 1000%.
        val adjustment =
            when (template) {
                is Template.Average -> template.adjustment
                is Template.Schedule -> template.adjustment
                else -> null
            }
        if (adjustment != null && adjustment.percent) {
            if (adjustment.amount <= BigDecimal(-100) || adjustment.amount > BigDecimal(1000)) return null
        }
        return template
    }

    private fun directive(line: String): String = line.substring(line.indexOf('#').coerceAtLeast(0)).trim()

    private class Peg(
        val s: String,
    ) {
        var i = 0

        fun expr(): Template? {
            val goal = attempt { if (lit(GOAL, ignoreCase = true)) amount()?.let { Template.Goal(it) } else null }
            if (goal != null) return goal.takeIf { i == s.length }
            if (!lit(TEMPLATE)) return null
            val priority = attempt { if (lit("-")) digits()?.toInt() else null } ?: 0
            val start = i
            val forms: List<() -> Template?> =
                listOf(
                    { percentage(priority) },
                    { periodic(priority) },
                    { by(priority) },
                    { simple(priority) },
                    { limitOnly(priority) },
                    { schedule(priority) },
                    { remainder() },
                    { average(priority) },
                    { copy(priority) },
                )
            for (form in forms) {
                i = start
                val template = form() ?: continue
                return template.takeIf { i == s.length }
            }
            return null
        }

        private fun percentage(priority: Int): Template? {
            ws()
            val (percent, previous) =
                attempt {
                    val p = percent() ?: return@attempt null
                    ws()
                    if (!lit("of", true)) return@attempt null
                    ws()
                    if (!lit("previous", true)) return@attempt null
                    ws()
                    p to true
                } ?: attempt {
                    val p = percent() ?: return@attempt null
                    ws()
                    if (!lit("of", true)) return@attempt null
                    ws()
                    p to false
                } ?: return null
            val name = name() ?: return null
            return Template.Percentage(percent, previous, name, priority)
        }

        private fun periodic(priority: Int): Template? {
            ws()
            val amount = amount() ?: return null
            ws()
            if (!repeatEvery()) return null
            ws()
            val (unit, every) = periodCount() ?: return null
            ws()
            if (!lit("starting", true)) return null
            ws()
            val starting = date() ?: return null
            val limit = attempt { limit() }
            return Template.Periodic(amount, every, unit, starting, limit, priority)
        }

        private fun by(priority: Int): Template? {
            ws()
            val amount = amount() ?: return null
            ws()
            if (!lit("by", true)) return null
            ws()
            val month = month() ?: return null
            val from =
                attempt {
                    ws()
                    if (!lit("spend", true)) return@attempt null
                    ws()
                    if (!lit("from", true)) return@attempt null
                    ws()
                    month()
                }
            val repeat =
                attempt {
                    ws()
                    if (!repeatEvery()) return@attempt null
                    ws()
                    repeat()
                }
            val annual = repeat?.first
            val count = repeat?.second
            return if (from != null) {
                Template.Spend(amount, month, from, annual, count, priority)
            } else {
                Template.By(amount, month, annual, count, priority)
            }
        }

        private fun simple(priority: Int): Template? {
            ws()
            val monthly = amount() ?: return null
            return Template.Simple(monthly, attempt { limit() }, priority)
        }

        private fun limitOnly(priority: Int): Template? {
            ws()
            return Template.Simple(null, limit() ?: return null, priority)
        }

        private fun schedule(priority: Int): Template? {
            ws()
            if (!lit("schedule", true)) return null
            ws()
            val full = attempt { if (lit("full", true)) true.also { ws() } else null } ?: false
            val name = scheduleName() ?: return null
            val adjustment = attempt { modifiers() }
            return Template.Schedule(name, full, priority, adjustment = adjustment)
        }

        private fun remainder(): Template? {
            ws()
            if (!lit("remainder", true)) return null
            ws()
            val weight = attempt { positive() }?.let { BigDecimal(it) } ?: BigDecimal.ONE
            return Template.Remainder(weight, attempt { limit() })
        }

        private fun average(priority: Int): Template? {
            ws()
            if (!lit("average", true)) return null
            ws()
            val months = positive()?.toIntOrNull() ?: return null
            ws()
            lit("months", true)
            return Template.Average(months, priority, attempt { modifiers() })
        }

        private fun copy(priority: Int): Template? {
            ws()
            if (!lit("copy from", true)) return null
            ws()
            val lookBack = positive()?.toIntOrNull() ?: return null
            ws()
            if (!lit("months ago", true)) return null
            return Template.Copy(lookBack, priority, attempt { limit() })
        }

        /** `up to N per week starting DAY [hold]`, `up to N per day [hold]` or `up to N [hold]`. */
        private fun limit(): TemplateLimit? =
            attempt {
                val amount = upTo() ?: return@attempt null
                ws()
                if (!lit("per week starting", true)) return@attempt null
                ws()
                val start = date() ?: return@attempt null
                ws()
                TemplateLimit(amount, hold(), LimitPeriod.WEEKLY, start)
            } ?: attempt {
                val amount = upTo() ?: return@attempt null
                ws()
                if (!lit("per day", true)) return@attempt null
                ws()
                TemplateLimit(amount, hold(), LimitPeriod.DAILY)
            } ?: attempt {
                val amount = upTo() ?: return@attempt null
                ws()
                TemplateLimit(amount, hold(), LimitPeriod.MONTHLY)
            }

        private fun upTo(): BigDecimal? {
            ws()
            if (!lit("up", true)) return null
            ws()
            if (!lit("to", true)) return null
            ws()
            return amount()
        }

        private fun hold(): Boolean = lit("hold", true)

        private fun repeatEvery(): Boolean =
            attempt {
                if (!lit("repeat", true)) return@attempt null
                ws()
                if (lit("every", true)) true else null
            } ?: false

        /** The `repeat every` of a by template: (annual, how many), or null when it does not read. */
        private fun repeat(): Pair<Boolean, Int?>? =
            attempt { if (lit("month", true)) false to null else null }
                ?: attempt {
                    val n = positive() ?: return@attempt null
                    ws()
                    if (lit("months", true)) false to n.toInt() else null
                } ?: attempt { if (lit("year", true)) true to null else null }
                ?: attempt {
                    val n = positive() ?: return@attempt null
                    ws()
                    if (lit("years", true)) true to n.toInt() else null
                }

        /** 26.8.1's `periodCount`, which still allows a count of 0. */
        private fun periodCount(): Pair<RepeatUnit, Int>? =
            attempt { if (lit("day", true)) RepeatUnit.DAY to 1 else null }
                ?: counted("days", RepeatUnit.DAY, trailing = true)
                ?: attempt { if (lit("week", true)) (RepeatUnit.WEEK to 1).also { ws() } else null }
                ?: counted("weeks", RepeatUnit.WEEK, trailing = false)
                ?: counted("months", RepeatUnit.MONTH, trailing = true)
                ?: attempt { if (lit("year", true)) (RepeatUnit.YEAR to 1).also { ws() } else null }
                ?: counted("years", RepeatUnit.YEAR, trailing = true)

        private fun counted(
            word: String,
            unit: RepeatUnit,
            trailing: Boolean,
        ): Pair<RepeatUnit, Int>? =
            attempt {
                val n = digits()?.toIntOrNull() ?: return@attempt null
                ws()
                if (!lit(word, true)) return@attempt null
                if (trailing) ws()
                unit to n
            }

        private fun modifiers(): TemplateAdjustment? {
            ws()
            if (!lit("[")) return null
            val sign =
                when {
                    lit("increase", true) -> BigDecimal.ONE
                    lit("decrease", true) -> BigDecimal.ONE.negate()
                    else -> return null
                }
            ws()
            val value = decimal() ?: return null
            val percent = attempt { ws().let { if (lit("%")) true else null } } ?: false
            if (!lit("]")) return null
            return TemplateAdjustment(sign * value, percent)
        }

        /** The schedule's name: anything up to a `[increase` or `[decrease` modifier, trimmed. */
        private fun scheduleName(): String? {
            if (i >= s.length || s[i] == ' ' || s[i] == '\t' || modifierAt(i)) return null
            val begin = i
            i++
            while (i < s.length && !modifierAfterSpace(i)) i++
            return s.substring(begin, i).trim()
        }

        private fun modifierAfterSpace(at: Int): Boolean {
            var j = at
            while (j < s.length && (s[j] == ' ' || s[j] == '\t')) j++
            return modifierAt(j)
        }

        private fun modifierAt(at: Int): Boolean =
            s.startsWith("[", at) && (s.regionMatches(at + 1, "increase", 0, 8, true) || s.regionMatches(at + 1, "decrease", 0, 8, true))

        private fun name(): String? {
            val begin = i
            while (i < s.length && s[i] != '\t' && s[i] != '\n' && s[i] != '\r') i++
            return if (i > begin) s.substring(begin, i) else null
        }

        /** `amount`: an optional currency symbol, then `-12`, `12.`, `12.3` or `12.34`. */
        private fun amount(): BigDecimal? =
            attempt {
                if (i < s.length && Character.getType(s[i]) == Character.CURRENCY_SYMBOL.toInt()) i++
                ws()
                val begin = i
                lit("-")
                if (digits() == null) return@attempt null
                if (lit(".")) {
                    if (i < s.length && s[i] in '0'..'9') {
                        i++
                        if (i < s.length && s[i] in '0'..'9') i++
                    }
                }
                s.substring(begin, i).removeSuffix(".").toBigDecimalOrNull()
            }

        private fun percent(): BigDecimal? =
            attempt {
                val value = decimal() ?: return@attempt null
                ws()
                if (lit("%")) value else null
            }

        private fun decimal(): BigDecimal? =
            attempt {
                val begin = i
                if (digits() == null) return@attempt null
                if (lit(".")) digits()
                s.substring(begin, i).removeSuffix(".").toBigDecimalOrNull()
            }

        private fun month(): YearMonth? =
            attempt {
                if (i + 7 > s.length) return@attempt null
                val text = s.substring(i, i + 7)
                if (!Regex("""\d{4}-\d{2}""").matches(text)) return@attempt null
                i += 7
                runCatching { YearMonth.parse(text) }.getOrNull()
            }

        private fun date(): LocalDate? =
            attempt {
                if (i + 10 > s.length) return@attempt null
                val text = s.substring(i, i + 10)
                if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(text)) return@attempt null
                i += 10
                runCatching { LocalDate.parse(text) }.getOrNull()
            }

        private fun positive(): String? {
            if (i >= s.length || s[i] !in '1'..'9') return null
            return digits()
        }

        private fun digits(): String? {
            val begin = i
            while (i < s.length && s[i] in '0'..'9') i++
            return if (i > begin) s.substring(begin, i) else null
        }

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        }

        fun lit(
            text: String,
            ignoreCase: Boolean = false,
        ): Boolean {
            if (!s.regionMatches(i, text, 0, text.length, ignoreCase)) return false
            i += text.length
            return true
        }

        fun <T> attempt(rule: () -> T?): T? {
            val save = i
            return rule() ?: run {
                i = save
                null
            }
        }
    }
}

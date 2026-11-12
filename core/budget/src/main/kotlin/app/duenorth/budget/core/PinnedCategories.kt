package app.duenorth.budget.core

/**
 * The category balances pinned across the top of the assistant: up to [MAX] for each budget, kept on this phone in
 * [PhoneSettings.pinnedCategories] in the order they were pinned.
 */
object PinnedCategories {
    const val MAX = 6

    const val PIN = "pin to assistant"
    const val UNPIN = "unpin from assistant"

    /** The ids pinned in [budgetId], oldest first. */
    fun ids(
        settings: PhoneSettings,
        budgetId: String?,
    ): List<String> = budgetId?.let { settings.pinnedCategories[it] }.orEmpty()

    /** True when [budgetId] has room for another pin. */
    fun hasRoom(
        settings: PhoneSettings,
        budgetId: String?,
    ): Boolean = ids(settings, budgetId).size < MAX

    /**
     * [settings] with [categoryId] pinned in [budgetId] if it was not, or unpinned if it was. A pin past [MAX] is
     * ignored, so the row never grows past what the assistant lays out.
     */
    fun toggle(
        settings: PhoneSettings,
        budgetId: String,
        categoryId: String,
    ): PhoneSettings {
        val now = ids(settings, budgetId)
        val next =
            when {
                categoryId in now -> now - categoryId
                now.size >= MAX -> return settings
                else -> now + categoryId
            }
        val all = if (next.isEmpty()) settings.pinnedCategories - budgetId else settings.pinnedCategories + (budgetId to next)
        return settings.copy(pinnedCategories = all)
    }

    /** The pinned categories found in this month's [groups], in pin order; a deleted category simply drops out. */
    fun resolve(
        ids: List<String>,
        groups: List<GroupRow>,
    ): List<CategoryRow> {
        if (ids.isEmpty()) return emptyList()
        val byId = groups.flatMap { it.categories }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.take(MAX)
    }
}

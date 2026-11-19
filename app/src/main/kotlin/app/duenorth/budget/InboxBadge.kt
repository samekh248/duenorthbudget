package app.duenorth.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.Finding
import app.duenorth.budget.core.MonthShell

/**
 * The assistant's count on the app icon: everything the assistant holds, so the inbox's rows and
 * matches plus every other finding. Android has no call that sets an icon's number: launchers
 * badge an app from its notifications, and the ones that show a number read it from the
 * notification's own count. So while the inbox holds anything, one quiet notification says how
 * many, and it goes away when the inbox is empty. Launchers that only draw a dot (Pixel) show the
 * dot; the number shows on those that count (Samsung, Nova, and others that read the count).
 */
object InboxBadge {
    internal const val CHANNEL = "inbox-count"
    private const val ID = 1
    private const val ASKED = "inbox-badge-asked"

    /** What the inbox card counts: rows to categorize and possible matches. Pending rows are never in the inbox. */
    fun inboxCount(shell: MonthShell): Int = inboxQueue(shell, null).size + shell.matches.size

    /** What the icon counts: the inbox card's count plus the findings it shows. */
    fun count(
        shell: MonthShell,
        findings: List<Finding>,
    ): Int = inboxCount(shell) + findings.size

    /** The icon's count for what the assistant shows; null until its checks have been read, so the icon never shows the inbox alone. */
    fun count(
        shell: MonthShell,
        assistant: AssistantUi,
    ): Int? = assistant.report?.let { count(shell, assistant.shown) }

    /** The open budget's count, read from disk, for the background sync; null when no budget can be read. */
    fun count(library: BudgetLibrary): Int? {
        val id = library.settings().openBudgetId ?: return 0
        val shell = library.readShell(id) ?: return null
        val report = runCatching { library.readAssistant(id) }.getOrNull() ?: AssistantReport.EMPTY
        return count(shell, report.findings)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether to ask for the notification permission: Android 13 and up, not granted, and not asked before. */
    fun shouldAsk(context: Context): Boolean =
        !canPost(context) && !context.getSharedPreferences(ASKED, Context.MODE_PRIVATE).getBoolean(ASKED, false)

    fun markAsked(context: Context) {
        context
            .getSharedPreferences(ASKED, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ASKED, true)
            .apply()
    }

    /** Shows [count] on the icon, or clears it at zero. Quiet: no sound, no vibration, no heads-up. */
    fun show(
        context: Context,
        count: Int,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (count <= 0) {
            manager.cancel(ID)
            return
        }
        if (!canPost(context)) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Assistant count", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The number of things the assistant has for you to look at, shown on the app icon."
                setShowBadge(true)
            },
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Assistant")
                .setContentText("$count to look at")
                .setNumber(count)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .build()
        runCatching { manager.notify(ID, notification) }
    }
}

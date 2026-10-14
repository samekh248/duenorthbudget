package app.duenorth.budget.core

import java.net.URI

/**
 * What a person typed as their Actual server, and the URLs to try for it.
 * A bare host is tried as `https://` first, then `http://` (spec 011 R4).
 */
data class ServerAddress(
    val raw: String,
    val candidates: List<String>,
    val host: String,
) {
    /** The instance name the sign-in page offers before the person edits it. */
    val defaultLabel: String get() = host.removePrefix("www.")

    companion object {
        fun parse(raw: String): ServerAddress? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val lower = trimmed.lowercase()
            val candidates =
                if (lower.startsWith("https://") || lower.startsWith("http://")) {
                    listOf(trimmed)
                } else if ("://" in trimmed) {
                    return null
                } else {
                    listOf("https://$trimmed", "http://$trimmed")
                }
            val host =
                runCatching { URI(candidates.first()).host }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
            return ServerAddress(raw, candidates, host)
        }

        fun isSecure(address: String): Boolean = address.lowercase().startsWith("https://")
    }
}

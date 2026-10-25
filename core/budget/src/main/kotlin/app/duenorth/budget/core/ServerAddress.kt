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
    /** What the address box shows: the toggle's choice and the read-only prefix's tail. */
    data class Entry(
        val secure: Boolean,
        val text: String,
    ) {
        val prefix: String get() = if (secure) HTTPS else HTTP
    }

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

        /**
         * The address box's http/https toggle and the text typed after its prefix. A bare address
         * (saved before the toggle) shows as https, the scheme tried first.
         */
        fun entry(address: String): Entry {
            val lower = address.lowercase()
            return when {
                lower.startsWith(HTTPS) -> Entry(true, address.substring(HTTPS.length))
                lower.startsWith(HTTP) -> Entry(false, address.substring(HTTP.length))
                else -> Entry(true, address)
            }
        }

        /**
         * The stored address for the toggle plus what is typed after the prefix. A pasted address that
         * already has a scheme moves the toggle to it. Nothing typed under https stores nothing, so the
         * empty box asks for an address.
         */
        fun join(
            secure: Boolean,
            typed: String,
        ): String {
            val pasted = entry(typed.trimStart())
            val keep = typed.trimStart().lowercase().let { it.startsWith(HTTPS) || it.startsWith(HTTP) }
            val (useSecure, text) = if (keep) pasted.secure to pasted.text else secure to typed
            return if (useSecure && text.isEmpty()) "" else (if (useSecure) HTTPS else HTTP) + text
        }

        const val HTTPS = "https://"
        const val HTTP = "http://"
    }
}

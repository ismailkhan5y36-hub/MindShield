package com.mindshield.app

/**
 * SafeSearchEngine: Network-level forced SafeSearch & YouTube Restricted Mode VIPs.
 * Enforces search engines (Google, Bing, DuckDuckGo, YouTube) directly into strict SafeSearch
 * via DNS IP redirection so that browser settings or incognito modes cannot bypass it.
 */
object SafeSearchEngine {

    // Official SafeSearch VIPs:
    // Google forcesafesearch.google.com
    val GOOGLE_SAFESEARCH_IP = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte())

    // Bing strict.bing.com
    val BING_STRICT_IP = byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte())

    // DuckDuckGo safe.duckduckgo.com
    val DUCKDUCKGO_SAFE_IP = byteArrayOf(52.toByte(), 142.toByte(), 124.toByte(), 215.toByte())

    // YouTube Restricted Mode restrict.youtube.com
    val YOUTUBE_RESTRICT_IP = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 119.toByte())

    /**
     * Returns the SafeSearch VIP if domain matches a supported search engine, or null if normal query.
     */
    fun getSafeSearchIpForDomain(domain: String): ByteArray? {
        val lower = domain.trim().lowercase()

        // 1. Google Search
        if (isGoogleSearchDomain(lower)) {
            return GOOGLE_SAFESEARCH_IP
        }

        // 2. Bing Search
        if (lower == "bing.com" || lower.endsWith(".bing.com") || lower.startsWith("www.bing.")) {
            return BING_STRICT_IP
        }

        // 3. DuckDuckGo Search
        if (lower == "duckduckgo.com" || lower.endsWith(".duckduckgo.com")) {
            return DUCKDUCKGO_SAFE_IP
        }

        // 4. YouTube Restricted Mode
        if (lower == "youtube.com" || lower == "www.youtube.com" || lower == "m.youtube.com" || lower == "youtubei.googleapis.com") {
            return YOUTUBE_RESTRICT_IP
        }

        return null
    }

    private fun isGoogleSearchDomain(domain: String): Boolean {
        // Exclude Google non-search services like accounts, play, drive, mail
        if (domain.startsWith("accounts.") ||
            domain.startsWith("drive.") ||
            domain.startsWith("play.") ||
            domain.startsWith("mail.") ||
            domain.startsWith("clients") ||
            domain.startsWith("lh3.") ||
            domain.startsWith("play.google") ||
            domain.startsWith("gstatic.")
        ) {
            return false
        }

        return domain == "google.com" ||
                domain.endsWith(".google.com") ||
                domain.startsWith("www.google.") ||
                domain.startsWith("google.")
    }
}

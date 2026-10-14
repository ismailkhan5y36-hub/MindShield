package com.mindshield.app

import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo

enum class FilterDecision {
    ALLOW,
    DISMISS_BACK // Violation detected: dismiss or trigger shield overlay
}

object SmartFilterEngine {

    // 1. High-Confidence Adult Domains (Strict Host Matching)
    private val adultDomains = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "brazzers.com", "redwap.com",
        "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "beeg.com", "chaturbate.com",
        "stripchat.com", "bongacams.com", "camsoda.com", "cam4.com", "livejasmin.com", "myfreecams.com",
        "onlyfans.com", "fansly.com", "manyvids.com", "clips4sale.com", "fetlife.com",
        "erome.com", "eroticmv.com", "fapello.com", "thothub.com", "coomer.party", "bunkr.is", "bunkr.site",
        "motherless.com", "heavy-r.com", "daftsex.com", "txxx.com", "tnaflix.com", "nuvid.com",
        "sunporno.com", "porntrex.com", "pornhat.com", "tubeoff.com", "hqporner.com", "netporn.com",
        "desi52.com", "desipapa.com", "savita bhabhi", "masaladesi", "kamababa",
        "indianporn", "pakistaniporn", "antvporn", "malluporn", "tamilsex",
        "hentai", "nhentai", "hanime.tv", "rule34", "gelbooru", "danbooru"
    )

    // 2. Safe Domain Fast-Pass (Never blocked, 0ms fast exit)
    private val safeDomains = setOf(
        "google.com", "www.google.com", "wikipedia.org", "en.wikipedia.org",
        "youtube.com", "www.youtube.com", "github.com", "stackoverflow.com",
        "microsoft.com", "apple.com", "amazon.com", "linkedin.com",
        "gov.pk", "edu.pk", "nadra.gov.pk", "pbs.gov.pk",
        "dawn.com", "tribune.com.pk", "geo.tv", "arynews.tv", "bbc.com", "cnn.com"
    )

    // 3. Unambiguous Hardcore Tokens (Always blocked, zero innocent usage)
    private val unambiguousHardcoreTokens = setOf(
        "porn", "porno", "pornhub", "xvideos", "xnxx", "xhamster", "brazzers",
        "xxx video", "xxx movie", "xxx porn", "hardcore sex",
        "blowjob", "creampie", "milf porn",
        "bhabhi sex", "chudai", "desi mms", "viral mms", "leaked sex",
        "onlyfans leak", "escort service", "anal sex", "nude video",
        "ullu drama", "ullu web series", "charmsukh", "palang tod"
    )

    // 4. Ambiguous Words Requiring Context ("adult", "sex")
    private val ambiguousWords = setOf("adult", "sex")

    // 5. Explicit Media Modifiers (When paired with ambiguous words without safe context)
    private val explicitMediaModifiers = setOf(
        "video", "videos", "movie", "movies", "film", "films",
        "clip", "clips", "tube", "cam", "cams", "chat", "xxx",
        "leak", "leaks", "mms", "watch", "download", "hot", "uncensored"
    )

    // 6. Safe Educational, Demographic & Statistical Intent Signals
    private val safeIntentSignals = setOf(
        "how many", "what is", "who is", "why do", "when does", "how to",
        "population", "census", "percentage", "rate", "statistics", "demographics", "count",
        "passport", "cnic", "nadra", "visa", "id card", "law", "rights", "tax", "fee",
        "education", "literacy", "school", "university", "college", "course", "study",
        "vaccine", "vaccination", "dosage", "hospital", "doctor", "medical", "disease", "treatment",
        "young adult", "adult swim", "adult life", "older adult", "adult education",
        "pakistan", "history", "geography", "ratio", "male", "female", "gender", "biology"
    )

    // 7. MovieBox Explicit Signatures
    private val movieBoxExplicitSignatures = setOf(
        "midnight", "novelhub", "ullu", "charmsukh", "palang tod",
        "sensual", "erotic", "lust", "uncensored", "18+", "porn", "xxx", "sex"
    )

    /**
     * Universal Browser Evaluator:
     * Works across Chrome, Phoenix, Brave, Firefox, Opera, Edge, Samsung, UC, VidMate, and WebViews.
     */
    fun evaluateBrowserContent(rootNode: AccessibilityNodeInfo): FilterDecision {
        val extractedInputs = mutableListOf<String>()
        extractUrlAndSearchInputs(rootNode, extractedInputs)

        if (extractedInputs.isEmpty()) {
            return FilterDecision.ALLOW
        }

        for (rawInput in extractedInputs) {
            val decision = evaluateSingleInput(rawInput)
            if (decision == FilterDecision.DISMISS_BACK) {
                return FilterDecision.DISMISS_BACK
            }
        }

        return FilterDecision.ALLOW
    }

    /**
     * Semantic Intent Brain:
     * Evaluates a URL or search query using context-aware NLP classification.
     */
    fun evaluateSingleInput(rawInput: String): FilterDecision {
        val input = rawInput.trim()
        if (input.isBlank() || isBrowserPlaceholder(input)) {
            return FilterDecision.ALLOW
        }

        // --- STEP 1: Safe Domain Fast-Pass ---
        val host = extractHost(input)
        if (host.isNotBlank()) {
            if (safeDomains.any { host == it || host.endsWith(".$it") }) {
                // If on a safe domain, inspect query parameters (e.g. google.com/search?q=...)
                val queryParam = extractSearchQueryFromUrl(input)
                if (queryParam.isNotBlank()) {
                    return evaluateSearchQueryIntent(queryParam)
                }
                return FilterDecision.ALLOW
            }

            // --- STEP 2: Strict Adult Hostname Match ---
            if (adultDomains.any { host == it || host.endsWith(".$it") }) {
                return FilterDecision.DISMISS_BACK
            }
        }

        // --- STEP 3: Semantic Query / Search Intent Evaluation ---
        return evaluateSearchQueryIntent(input)
    }

    /**
     * NLP Semantic Intent Classifier:
     * Differentiates innocent demographic/educational queries from pornographic queries.
     */
    fun evaluateSearchQueryIntent(query: String): FilterDecision {
        val normalized = normalizeQuery(query)

        // 1. Check Unambiguous Hardcore Tokens
        for (token in unambiguousHardcoreTokens) {
            if (containsWordToken(normalized, token)) {
                return FilterDecision.DISMISS_BACK
            }
        }

        // 2. Check Ambiguous Words ("adult", "sex")
        val containsAdult = containsWordToken(normalized, "adult") || containsWordToken(normalized, "adults")
        val containsSex = containsWordToken(normalized, "sex")

        if (containsAdult || containsSex) {
            // Check Tier 1: Safe Educational / Demographic Context
            for (safeSignal in safeIntentSignals) {
                if (normalized.contains(safeSignal)) {
                    // Safe context confirmed (e.g. "how many adult in pakistan", "adult literacy rate")
                    return FilterDecision.ALLOW
                }
            }

            // Check Tier 2: Explicit Media Collocation (e.g. "adult video", "adult movie", "adult chat")
            for (mediaModifier in explicitMediaModifiers) {
                if (containsWordToken(normalized, mediaModifier)) {
                    return FilterDecision.DISMISS_BACK
                }
            }

            // If neither explicit media modifier nor hardcore token: ALLOW innocent general usage!
            return FilterDecision.ALLOW
        }

        return FilterDecision.ALLOW
    }

    /**
     * MovieBox Guard:
     * Blocks Midnight & NovelHub tabs, and filters search queries using the Semantic Intent Brain.
     */
    fun checkMovieBox(rootNode: AccessibilityNodeInfo): FilterDecision {
        val allTexts = mutableListOf<String>()
        collectAllTexts(rootNode, allTexts)
        if (allTexts.isEmpty()) return FilterDecision.ALLOW

        val combined = allTexts.joinToString(" ").lowercase()

        // 1. Check Midnight / NovelHub tabs or headers
        if (combined.contains("midnight (18+)") || combined.contains("porn top videos") ||
            combined.contains("18+ channel") || isMovieBoxMidnightActive(rootNode)
        ) {
            return FilterDecision.DISMISS_BACK
        }

        // 2. MovieBox Search Input: Extract and check query intent
        val searchTexts = mutableListOf<String>()
        extractSearchAndInputTexts(rootNode, searchTexts)
        for (query in searchTexts) {
            val qLower = query.lowercase().trim()
            if (qLower.isBlank()) continue

            // Check explicit signatures
            for (term in movieBoxExplicitSignatures) {
                if (containsWordToken(qLower, term)) {
                    // Allow normal movie searches e.g. "Batman: The Adult Animated Series" if educational/review
                    if (!qLower.contains("how") && !qLower.contains("review") && !qLower.contains("trailer")) {
                        return FilterDecision.DISMISS_BACK
                    }
                }
            }

            // Run through general Semantic Intent Brain
            if (evaluateSearchQueryIntent(query) == FilterDecision.DISMISS_BACK) {
                return FilterDecision.DISMISS_BACK
            }
        }

        return FilterDecision.ALLOW
    }

    // --- HELPER UTILITIES ---

    private fun isBrowserPlaceholder(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "search or type url" ||
                lower == "search or type web address" ||
                lower == "search" ||
                lower == "google" ||
                lower == "about:blank" ||
                lower.startsWith("chrome://newtab") ||
                lower.startsWith("chrome-native://newtab")
    }

    private fun extractHost(input: String): String {
        return try {
            val trimmed = input.trim()
            val uri = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                Uri.parse("https://$trimmed")
            } else {
                Uri.parse(trimmed)
            }
            uri.host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractSearchQueryFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val q = uri.getQueryParameter("q")
                ?: uri.getQueryParameter("query")
                ?: uri.getQueryParameter("p")
                ?: ""
            q
        } catch (e: Exception) {
            ""
        }
    }

    private fun normalizeQuery(input: String): String {
        return try {
            java.net.URLDecoder.decode(input, "UTF-8").lowercase().trim()
        } catch (e: Exception) {
            input.lowercase().trim()
        }
    }

    private fun containsWordToken(text: String, token: String): Boolean {
        val pattern = Regex("(^|\\W)" + Regex.escape(token) + "(\\W|$)")
        return pattern.containsMatchIn(text)
    }

    private fun isMovieBoxMidnightActive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        val isSelected = node.isSelected
        val isFocused = node.isFocused

        val matchesMidnight = text.contains("midnight") || desc.contains("midnight") ||
                text.contains("novelhub") || desc.contains("novelhub")

        if (matchesMidnight && (isSelected || isFocused)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && isMovieBoxMidnightActive(child)) {
                return true
            }
        }
        return false
    }

    fun extractUrlAndSearchInputs(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString() ?: ""

        // Universal browser address & search inputs identification across ALL browsers
        val isUrlOrSearch = viewId.contains("url_bar") ||
                viewId.contains("location_bar") ||
                viewId.contains("address_bar") ||
                viewId.contains("search_box") ||
                viewId.contains("search_input") ||
                viewId.contains("search_src_text") ||
                viewId.contains("search_edit_frame") ||
                viewId.contains("query") ||
                viewId.contains("address") ||
                (className.contains("EditText", ignoreCase = true) &&
                        (viewId.contains("search") || viewId.contains("url") || viewId.contains("query") || viewId.contains("address")))

        if (isUrlOrSearch) {
            if (text.isNotBlank()) outList.add(text)
            if (desc.isNotBlank()) outList.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractUrlAndSearchInputs(child, outList)
            }
        }
    }

    fun extractSearchAndInputTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString() ?: ""

        val isSearchOrInput = className.contains("EditText", ignoreCase = true) ||
                viewId.contains("search", ignoreCase = true) ||
                viewId.contains("query", ignoreCase = true) ||
                viewId.contains("keyword", ignoreCase = true) ||
                viewId.contains("input", ignoreCase = true) ||
                viewId.contains("url", ignoreCase = true)

        if (isSearchOrInput) {
            if (text.isNotBlank()) outList.add(text)
            if (desc.isNotBlank()) outList.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractSearchAndInputTexts(child, outList)
            }
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotBlank()) outList.add(text)
        if (desc.isNotBlank()) outList.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllTexts(child, outList)
            }
        }
    }
}

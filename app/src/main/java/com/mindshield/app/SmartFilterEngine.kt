package com.mindshield.app

import android.view.accessibility.AccessibilityNodeInfo

enum class FilterDecision {
    ALLOW,
    DISMISS_BACK // Violation detected: dismiss or trigger shield overlay
}

object SmartFilterEngine {

    // 1. High-Confidence Adult Domains (Full domain signatures)
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

    // 2. Explicit Search Queries (Checked ONLY in address/search inputs)
    private val explicitSearchTerms = setOf(
        "porn", "porno", "xvideo", "xvideos", "xnxx", "xhamster",
        "xxx video", "xxx movie", "xxx porn",
        "sex video", "nude video", "hardcore sex", "blowjob", "creampie", "milf",
        "bhabhi sex", "chudai", "desi mms", "viral mms", "leaked sex",
        "onlyfans leak", "escort service", "adult video", "adult movie",
        "midnight 18", "novelhub", "ullu drama", "charmsukh", "erotic video"
    )

    // 3. MovieBox Specific Search & Section Filter
    private val movieBoxExplicitTerms = setOf(
        "midnight", "novelhub", "ullu", "hot web series", "hot movie", "hot video",
        "charmsukh", "palang tod", "sensual", "erotic", "lust", "uncensored", "18+",
        "porn", "porno", "xxx", "sex", "nude", "hardcore", "blowjob", "creampie", "milf",
        "bhabhi", "chudai", "desi mms", "viral mms", "leaked", "adult video", "adult movie"
    )

    // 4. Blatant Explicit UI Signatures
    private val explicitUiSignatures = listOf(
        "porn top videos",
        "join 18+ channel",
        "anal sex",
        "midnight 18",
        "midnight (18+)",
        "novelhub [hot]",
        "novelhub",
        "ullu drama list",
        "hardcore sex",
        "bhabhi sex",
        "chudai video"
    )

    /**
     * MovieBox Guard:
     * Intercepts MovieBox tabs (Midnight / NovelHub) and strictly guards the MovieBox Search bar!
     */
    fun checkMovieBox(rootNode: AccessibilityNodeInfo): FilterDecision {
        val allTexts = mutableListOf<String>()
        collectAllTexts(rootNode, allTexts)
        if (allTexts.isEmpty()) return FilterDecision.ALLOW

        val combined = allTexts.joinToString(" ").lowercase()

        // 1. Check for blatant explicit UI headers or MovieBox tabs (Midnight, NovelHub, etc.)
        for (sig in explicitUiSignatures) {
            if (combined.contains(sig)) {
                return FilterDecision.DISMISS_BACK
            }
        }

        // 2. Active Midnight / NovelHub tab selection
        if (isMovieBoxMidnightActive(rootNode)) {
            return FilterDecision.DISMISS_BACK
        }

        // 3. MovieBox Search Guard: Scan all Search inputs and queries
        val searchTexts = mutableListOf<String>()
        extractSearchAndInputTexts(rootNode, searchTexts)
        for (query in searchTexts) {
            val qLower = query.lowercase().trim()
            for (term in movieBoxExplicitTerms) {
                if (containsSearchKeyword(qLower, term)) {
                    return FilterDecision.DISMISS_BACK
                }
            }
        }

        return FilterDecision.ALLOW
    }

    /**
     * Checks browsers (Chrome, Phoenix, Firefox, Opera, Brave, VidMate, etc.)
     * Scans ONLY URL bar and search query inputs (NOT Discover news feed).
     */
    fun evaluateBrowserContent(rootNode: AccessibilityNodeInfo): FilterDecision {
        val urlAndSearchTexts = mutableListOf<String>()
        extractUrlAndSearchInputs(rootNode, urlAndSearchTexts)

        if (urlAndSearchTexts.isEmpty()) {
            return FilterDecision.ALLOW
        }

        val combinedInput = urlAndSearchTexts.joinToString(" ").lowercase()

        // Check A: Adult Domains
        for (domain in adultDomains) {
            if (combinedInput.contains(domain)) {
                return FilterDecision.DISMISS_BACK
            }
        }

        // Check B: Strict Search Queries (with word boundaries)
        for (term in explicitSearchTerms) {
            if (containsSearchKeyword(combinedInput, term)) {
                return FilterDecision.DISMISS_BACK
            }
        }

        return FilterDecision.ALLOW
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

    private fun extractUrlAndSearchInputs(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString() ?: ""

        // Only extract actual URL bars or explicit search boxes, NOT generic page inputs!
        val isUrlBar = viewId.contains("url_bar") ||
                viewId.contains("search_box") ||
                viewId.contains("location_bar") ||
                viewId.contains("address_bar") ||
                viewId.contains("search_src_text") ||
                viewId.contains("search_input") ||
                viewId.contains("search_edit_frame") ||
                (className.contains("EditText", ignoreCase = true) && (viewId.contains("search") || viewId.contains("query") || viewId.contains("url")))

        if (isUrlBar) {
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

    private fun containsSearchKeyword(input: String, keyword: String): Boolean {
        val pattern = Regex("(^|\\W)" + Regex.escape(keyword) + "(\\W|$)")
        return pattern.containsMatchIn(input)
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

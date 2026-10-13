package com.mindshield.app

import android.view.accessibility.AccessibilityNodeInfo

enum class FilterDecision {
    ALLOW,
    DISMISS_TAB_BACK,    // Press GLOBAL_ACTION_BACK to dismiss bad tab/page (Chrome stays open)
    DISMISS_SECTION_BACK // Press GLOBAL_ACTION_BACK to exit Midnight tab (MovieBox stays open)
}

object SmartFilterEngine {

    // 1. High-Confidence Adult Domains (Full domain signatures)
    private val adultDomains = setOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "brazzers", "redwap",
        "youporn", "tube8", "spankbang", "eporner", "beeg", "chaturbate",
        "stripchat", "bongacams", "camsoda", "cam4", "livejasmin", "myfreecams",
        "onlyfans", "fansly", "manyvids", "clips4sale", "fetlife",
        "erome", "eroticmv", "fapello", "thothub", "coomer.party", "bunkr",
        "motherless", "heavy-r", "daftsex", "txxx", "tnaflix", "nuvid",
        "sunporno", "porntrex", "pornhat", "tubeoff", "hqporner", "netporn",
        "desi52", "desipapa", "savita bhabhi", "masaladesi", "kamababa",
        "indianporn", "pakistaniporn", "antvporn", "malluporn", "tamilsex",
        "hentai", "nhentai", "hanime", "rule34", "gelbooru", "danbooru"
    )

    // 2. Explicit Search Queries (Checked ONLY in address/search inputs)
    private val explicitSearchTerms = setOf(
        "porn", "xxx", "xnxx", "xvideo", "xhamster", "porno",
        "sex video", "nude video", "hardcore sex", "blowjob", "creampie", "milf",
        "bhabhi sex", "chudai", "desi mms", "viral mms", "leaked sex",
        "onlyfans leak", "escort service", "adult video", "adult movie"
    )

    // 3. MovieBox / MangoTV Explicit Sections
    private val midnightSectionNames = setOf(
        "midnight (18+)", "midnight 18+", "novelhub [hot]", "novelhub",
        "midnight movie", "midnight movies", "midnight cinema", "adult 18+", "18+ movies"
    )

    /**
     * Checks browsers (Chrome, Phoenix, Firefox, Opera, Brave, VidMate, etc.)
     * Scans ONLY URL bar, address input, and search queries.
     * NEVER scans Google Discover news feed or normal page content to avoid false positives.
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
                return FilterDecision.DISMISS_TAB_BACK
            }
        }

        // Check B: Strict Search Queries (with word boundaries)
        for (term in explicitSearchTerms) {
            if (containsSearchKeyword(combinedInput, term)) {
                return FilterDecision.DISMISS_TAB_BACK
            }
        }

        return FilterDecision.ALLOW
    }

    /**
     * Checks MovieBox / MangoTV.
     * Returns DISMISS_SECTION_BACK if Midnight (18+) or NovelHub [HOT] tab is active/selected.
     */
    fun evaluateMovieBoxContent(rootNode: AccessibilityNodeInfo): FilterDecision {
        if (isMidnightTabActive(rootNode)) {
            return FilterDecision.DISMISS_SECTION_BACK
        }
        return FilterDecision.ALLOW
    }

    fun isMovieBoxOrMangoTV(packageName: String, rootNode: AccessibilityNodeInfo?): Boolean {
        val pkg = packageName.lowercase()
        if (pkg.contains("moviebox") ||
            pkg.contains("showbox") ||
            pkg.contains("mangotv") ||
            pkg.contains("mango") ||
            pkg.contains("funtv") ||
            pkg == "com.mb.moviebox" ||
            pkg == "com.community.moviebox"
        ) {
            return true
        }

        if (rootNode != null) {
            val texts = mutableListOf<String>()
            extractTopLevelTexts(rootNode, texts, maxDepth = 4)
            val combined = texts.joinToString(" ").lowercase()
            if (combined.contains("mangotv") ||
                combined.contains("movieboxhd") ||
                (combined.contains("cricket") && combined.contains("midnight"))
            ) {
                return true
            }
        }
        return false
    }

    private fun isMidnightTabActive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        val isSelected = node.isSelected
        val isFocused = node.isFocused

        for (section in midnightSectionNames) {
            if (text == section || desc == section || text.contains(section) || desc.contains(section)) {
                // If the section tab is selected or focused, or text indicates header of midnight screen
                if (isSelected || isFocused || text.equals("midnight (18+)", ignoreCase = true) ||
                    text.equals("novelhub [hot]", ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && isMidnightTabActive(child)) {
                return true
            }
        }
        return false
    }

    private fun extractUrlAndSearchInputs(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val isEditable = node.isEditable
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        // Chrome: com.android.chrome:id/url_bar, search_box_text, location_bar
        val isUrlOrSearch = isEditable ||
                viewId.contains("url_bar") ||
                viewId.contains("search_box") ||
                viewId.contains("location_bar") ||
                viewId.contains("address") ||
                text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ||
                text.startsWith("www.", ignoreCase = true)

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

    private fun containsSearchKeyword(input: String, keyword: String): Boolean {
        val pattern = Regex("(^|\\W)" + Regex.escape(keyword) + "(\\W|$)")
        return pattern.containsMatchIn(input)
    }

    private fun extractTopLevelTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>, maxDepth: Int, currentDepth: Int = 0) {
        if (node == null || currentDepth > maxDepth) return

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotBlank()) outList.add(text)
        if (desc.isNotBlank()) outList.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTopLevelTexts(child, outList, maxDepth, currentDepth + 1)
            }
        }
    }
}

package com.mindshield.app

import android.view.accessibility.AccessibilityNodeInfo

object SmartFilterEngine {

    // 1. High-Confidence Adult Domains & URL signatures
    private val adultDomainPatterns = setOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "brazzers", "redwap",
        "youporn", "tube8", "spankbang", "eporner", "beeg", "chaturbate",
        "stripchat", "bongacams", "camsoda", "cam4", "livejasmin", "myfreecams",
        "onlyfans", "fansly", "manyvids", "clips4sale", "fetlife",
        "erome", "eroticmv", "fapello", "thothub", "coomer.party", "bunkr",
        "motherless", "heavy-r", "daftsex", "txxx", "tnaflix", "nuvid",
        "sunporno", "porntrex", "pornhat", "tubeoff", "hqporner", "netporn",
        "desi52", "desipapa", "savita bhabhi", "masaladesi", "kamababa",
        "indianporn", "pakistaniporn", "antvporn", "malluporn", "tamilsex",
        "hentai", "nhentai", "hanime", "rule34", "gelbooru", "danbooru",
        ".xxx", ".adult", ".porn", ".sexy"
    )

    // 2. High-Risk Adult & Explicit Keywords (English + Roman Urdu/Hindi)
    private val strictKeywords = setOf(
        "porn", "xxx", "xnxx", "xvideo", "xhamster", "porno",
        "sex video", "nude", "nudes", "nudity", "erotic", "erotica",
        "nsfw", "hardcore sex", "blowjob", "creampie", "milf", "incest",
        "bhabhi sex", "chudai", "desi mms", "viral mms", "leaked sex",
        "onlyfans leaks", "stripper", "strip club", "escort service",
        "midnight 18+", "18+ video", "adult video", "adult movie"
    )

    // 3. MovieBox & Streaming Specific Explicit Sections
    private val movieBoxSections = setOf(
        "midnight", "midnight 18+", "midnight movie", "midnight movies",
        "midnight cinema", "novelhub [hot]", "adult 18+", "18+ movies", "erotic movies"
    )

    // 4. Moderate Suspicious Keywords for Contextual Scoring
    private val moderateKeywords = setOf(
        "sexy", "hot video", "hot movie", "kissing scene", "bed scene",
        "uncensored", "bold scene", "romance 18+", "hot web series",
        "ullu", "kooku", "primeplay", "rabbit movies", "bigshots"
    )

    /**
     * Smart Heuristic Classifier:
     * Calculates risk score. If score >= 50, triggers instant block.
     */
    fun shouldBlockContent(packageName: String, rootNode: AccessibilityNodeInfo): Boolean {
        val extractedTexts = mutableListOf<String>()
        extractAllTexts(rootNode, extractedTexts)

        if (extractedTexts.isEmpty()) return false

        val fullContent = extractedTexts.joinToString(" ").lowercase()

        // Check A: MovieBox Specific Midnight / 18+ Section
        if (packageName.contains("moviebox", ignoreCase = true) ||
            packageName.contains("showbox", ignoreCase = true) ||
            packageName == "com.mb.moviebox" ||
            packageName == "com.community.moviebox"
        ) {
            for (sec in movieBoxSections) {
                if (fullContent.contains(sec)) {
                    return true
                }
            }
        }

        // Check B: Domain / URL Signature Match (Instant 100% Block)
        for (domain in adultDomainPatterns) {
            if (fullContent.contains(domain)) {
                return true
            }
        }

        // Check C: Strict Explicit Keyword Match (Instant Block)
        for (kw in strictKeywords) {
            if (fullContent.contains(kw)) {
                return true
            }
        }

        // Check D: Heuristic Contextual Scoring (AI Logic for Smart Bypasses)
        // If 2 or more moderate keywords appear in URL/title simultaneously, it's flagged.
        var moderateCount = 0
        for (mod in moderateKeywords) {
            if (fullContent.contains(mod)) {
                moderateCount++
                if (moderateCount >= 2) {
                    return true
                }
            }
        }

        return false
    }

    private fun extractAllTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            outList.add(text)
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            outList.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllTexts(child, outList)
            }
        }
    }
}

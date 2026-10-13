package com.mindshield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShieldAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: DailyQuotaManager
    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyInReels = false

    // Target packages for short videos
    private val tiktokPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    private val browsers = setOf(
        "com.android.chrome",
        "com.transsion.phoenix", // Tecno & Infinix default browser
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.google.android.googlequicksearchbox"
    )

    // High-risk keywords for instant blocking
    private val adultKeywords = listOf(
        "porn", "xxx", "xnxx", "xvideos", "pornhub", "xhamster",
        "redwap", "brazzers", "sex video", "nude", "adult video",
        "erotic", "nsfw", "onlyfans leaks"
    )

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (isCurrentlyInReels) {
                quotaManager.addUsedSeconds(1)

                if (quotaManager.isQuotaExhausted()) {
                    triggerBlock(BlockActivity.REASON_QUOTA)
                    isCurrentlyInReels = false
                    return
                }

                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        quotaManager = DailyQuotaManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // 1. Check for Galat / Adult Content in Browsers (Rule 1: Instant Kickout)
        if (browsers.contains(packageName)) {
            val rootNode = rootInActiveWindow
            if (rootNode != null && containsAdultKeyword(rootNode)) {
                quotaManager.recordBadContentBlock()
                triggerBlock(BlockActivity.REASON_ADULT)
                return
            }
        }

        // 2. Check for Reels / Shorts (Rule 2: 60 mins Daily Quota)
        val inReels = checkIfInReelsOrShorts(packageName, event)

        if (inReels) {
            if (quotaManager.isQuotaExhausted()) {
                // Quota over -> Immediate auto close
                triggerBlock(BlockActivity.REASON_QUOTA)
                stopTicker()
            } else {
                // Quota still remaining -> start ticking active seconds
                startTicker()
            }
        } else {
            stopTicker()
        }
    }

    private fun checkIfInReelsOrShorts(packageName: String, event: AccessibilityEvent): Boolean {
        // TikTok is 100% short video
        if (tiktokPackages.contains(packageName)) {
            return true
        }

        val className = event.className?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return false

        when (packageName) {
            // Instagram Reels
            "com.instagram.android" -> {
                if (className.contains("ClipsViewerActivity", ignoreCase = true)) {
                    return true
                }
                if (hasNodeWithContentDescription(rootNode, "Reels") ||
                    hasNodeWithContentDescription(rootNode, "Reel")
                ) {
                    return true
                }
            }

            // YouTube Shorts
            "com.google.android.youtube" -> {
                if (className.contains("Shorts", ignoreCase = true) ||
                    className.contains("ReelWatchFragment", ignoreCase = true)
                ) {
                    return true
                }
                if (hasNodeWithContentDescription(rootNode, "Shorts") &&
                    hasNodeWithContentDescription(rootNode, "Comments")
                ) {
                    return true
                }
            }

            // Facebook Reels
            "com.facebook.katana" -> {
                if (className.contains("Reels", ignoreCase = true) ||
                    hasNodeWithText(rootNode, "Reels")
                ) {
                    return true
                }
            }

            // Snapchat Spotlight
            "com.snapchat.android" -> {
                if (hasNodeWithText(rootNode, "Spotlight") ||
                    hasNodeWithContentDescription(rootNode, "Spotlight")
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun startTicker() {
        if (!isCurrentlyInReels) {
            isCurrentlyInReels = true
            handler.removeCallbacks(tickerRunnable)
            handler.post(tickerRunnable)
        }
    }

    private fun stopTicker() {
        if (isCurrentlyInReels) {
            isCurrentlyInReels = false
            handler.removeCallbacks(tickerRunnable)
        }
    }

    private fun triggerBlock(reason: String) {
        // Step 1: Force kickout to Android Home Screen immediately
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Step 2: Launch Fullscreen Shield Warning Screen
        val blockIntent = Intent(this, BlockActivity::class.java).apply {
            putExtra(BlockActivity.EXTRA_REASON, reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(blockIntent)
    }

    private fun containsAdultKeyword(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in adultKeywords) {
            if (text.contains(keyword) || desc.contains(keyword)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsAdultKeyword(child)) {
                return true
            }
        }
        return false
    }

    private fun hasNodeWithContentDescription(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(target, ignoreCase = true)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && hasNodeWithContentDescription(child, target)) {
                return true
            }
        }
        return false
    }

    private fun hasNodeWithText(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        if (text.contains(target, ignoreCase = true)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && hasNodeWithText(child, target)) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        stopTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTicker()
    }
}

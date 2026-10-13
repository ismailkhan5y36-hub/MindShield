package com.mindshield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShieldAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: DailyQuotaManager
    private lateinit var antiCheatManager: AntiCheatManager
    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyInReels = false

    // Target packages for short videos
    private val shortVideoPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    private val browsers = setOf(
        "com.android.chrome",
        "com.transsion.phoenix", // Tecno & Infinix browser
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.google.android.googlequicksearchbox"
    )

    // Anti-Cheat packages: Package Installer and Tecno/HiOS Freezer
    private val antiCheatTargetPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.transsion.freezer",
        "com.transsion.appmanager"
    )

    // Harmful search keywords (monitored silently for auto-kickout)
    private val restrictedKeywords = listOf(
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
        antiCheatManager = AntiCheatManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // --- RULE 1: ANTI-CHEAT & ANTI-FREEZER (2-HOUR DELAY LOCK) ---
        if (antiCheatManager.isAntiCheatEnabled() && !antiCheatManager.isMaintenanceWindowOpen()) {
            if (isAntiCheatViolation(packageName, event)) {
                antiCheatManager.triggerCoolingOffIfIdle()
                triggerAntiCheatDelay()
                return
            }
        }

        // --- RULE 2: HARMFUL SEARCHES IN BROWSERS (INSTANT HOME KICKOUT) ---
        if (browsers.contains(packageName)) {
            val rootNode = rootInActiveWindow
            if (rootNode != null && containsRestrictedKeyword(rootNode)) {
                quotaManager.recordDistractionBlock()
                triggerBlock(BlockActivity.REASON_DISTRACTION)
                return
            }
        }

        // --- RULE 3: REELS / SHORTS (60 MINS DAILY QUOTA) ---
        val inReels = checkIfInReelsOrShorts(packageName, event)

        if (inReels) {
            if (quotaManager.isQuotaExhausted()) {
                triggerBlock(BlockActivity.REASON_QUOTA)
                stopTicker()
            } else {
                startTicker()
            }
        } else {
            stopTicker()
        }
    }

    private fun isAntiCheatViolation(packageName: String, event: AccessibilityEvent): Boolean {
        // Direct attempt via Package Installer or Tecno Freezer
        if (antiCheatTargetPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return true
            if (hasNodeWithText(rootNode, "MindShield") || packageName.contains("freezer")) {
                return true
            }
        }

        // Attempting to clear data, force-stop, or uninstall from Android Settings
        if (packageName == "com.android.settings") {
            val rootNode = rootInActiveWindow ?: return false
            if (hasNodeWithText(rootNode, "MindShield")) {
                // If on MindShield App Info screen in Settings
                if (hasNodeWithText(rootNode, "Uninstall") ||
                    hasNodeWithText(rootNode, "Force stop") ||
                    hasNodeWithText(rootNode, "Disable")
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun triggerAntiCheatDelay() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val coolingIntent = Intent(this, CoolingOffActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(coolingIntent)
    }

    private fun checkIfInReelsOrShorts(packageName: String, event: AccessibilityEvent): Boolean {
        if (shortVideoPackages.contains(packageName)) {
            return true
        }

        val className = event.className?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return false

        when (packageName) {
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

            "com.facebook.katana" -> {
                if (className.contains("Reels", ignoreCase = true) ||
                    hasNodeWithText(rootNode, "Reels")
                ) {
                    return true
                }
            }

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
        performGlobalAction(GLOBAL_ACTION_HOME)
        val blockIntent = Intent(this, BlockActivity::class.java).apply {
            putExtra(BlockActivity.EXTRA_REASON, reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(blockIntent)
    }

    private fun containsRestrictedKeyword(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in restrictedKeywords) {
            if (text.contains(keyword) || desc.contains(keyword)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsRestrictedKeyword(child)) {
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

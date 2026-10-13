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

    // Mindful Pause cooldown tracker (package -> last trigger timestamp)
    private val pauseCooldownMap = mutableMapOf<String, Long>()
    private val PAUSE_COOLDOWN_MS = 15 * 60 * 1000L // 15 mins cooldown

    private val triggerAppsForPause = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    // Short video packages
    private val shortVideoPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    // Standard browsers and in-app webviews (including VidMate & Phoenix)
    private val webBrowsersAndDownloaders = setOf(
        "com.android.chrome",
        "com.transsion.phoenix", // Tecno & Infinix browser
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.UCMobile.intl",
        "com.google.android.googlequicksearchbox",
        // VidMate & Downloaders (frequent bypass vectors)
        "com.nemo.vidmate",
        "com.vidmate.android",
        "com.video.downloader",
        "com.allvideodownloader",
        "com.snaptube.premium"
    )

    // Anti-Cheat packages: Package Installer and Tecno/HiOS Freezer
    private val antiCheatTargetPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.transsion.freezer",
        "com.transsion.appmanager"
    )

    // Google Play Store bypassing tools searches
    private val restrictedPlayStoreSearches = listOf(
        "vpn", "proxy", "turbo vpn", "nord vpn", "super vpn",
        "snack video", "tiktok lite", "instagram lite"
    )

    // Incognito tab indicator keywords
    private val incognitoKeywords = listOf(
        "incognito", "inprivate", "private tab", "close incognito tabs"
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

        // --- RULE 0: EMERGENCY WHITELIST (CRITICAL APPS NEVER BLOCKED) ---
        if (EmergencyWhitelist.isWhitelisted(packageName)) {
            return
        }

        // --- RULE 1: ANTI-CHEAT & ANTI-FREEZER (2-HOUR DELAY LOCK) ---
        if (antiCheatManager.isAntiCheatEnabled() && !antiCheatManager.isMaintenanceWindowOpen()) {
            if (isAntiCheatViolation(packageName, event)) {
                antiCheatManager.triggerCoolingOffIfIdle()
                triggerAntiCheatDelay()
                return
            }
        }

        val rootNode = rootInActiveWindow

        // --- RULE 2: MOVIEBOX "MIDNIGHT 18+" SECTION BLOCKER ---
        if (isMovieBoxApp(packageName)) {
            if (rootNode != null && SmartFilterEngine.shouldBlockContent(packageName, rootNode)) {
                quotaManager.recordDistractionBlock()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // --- RULE 3: INCOGNITO / PRIVATE TAB BLOCKER (CHROME, PHOENIX, ETC.) ---
        if (webBrowsersAndDownloaders.contains(packageName)) {
            if (rootNode != null && isIncognitoModeActive(rootNode)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // --- RULE 4: SMART ON-DEVICE FILTER (BROWSERS, VIDMATE, WEBVIEWS) ---
        if (webBrowsersAndDownloaders.contains(packageName) || packageName.contains("vidmate", ignoreCase = true)) {
            if (rootNode != null && SmartFilterEngine.shouldBlockContent(packageName, rootNode)) {
                quotaManager.recordDistractionBlock()
                // Silent Home Kickout (Clean, discreet, zero embarrassment)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // --- RULE 5: VPN & CLONE APP GUARD ON GOOGLE PLAY STORE ---
        if (packageName == "com.android.vending") {
            if (rootNode != null && isPlayStoreRestrictedSearch(rootNode)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // --- RULE 6: 5-SECOND MINDFUL PAUSE (DOPAMINE FRICTION) ---
        if (triggerAppsForPause.contains(packageName) && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkAndTriggerMindfulPause(packageName)
        }

        // --- RULE 7: REELS / SHORTS (60 MINS DAILY QUOTA) ---
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

    private fun isMovieBoxApp(packageName: String): Boolean {
        return packageName.contains("moviebox", ignoreCase = true) ||
                packageName.contains("showbox", ignoreCase = true) ||
                packageName == "com.mb.moviebox" ||
                packageName == "com.community.moviebox"
    }

    private fun isIncognitoModeActive(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (kw in incognitoKeywords) {
            if (text.contains(kw) || desc.contains(kw)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && isIncognitoModeActive(child)) {
                return true
            }
        }
        return false
    }

    private fun isPlayStoreRestrictedSearch(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (kw in restrictedPlayStoreSearches) {
            if (text.contains(kw) || desc.contains(kw)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && isPlayStoreRestrictedSearch(child)) {
                return true
            }
        }
        return false
    }

    private fun checkAndTriggerMindfulPause(packageName: String) {
        val now = System.currentTimeMillis()
        val lastTrigger = pauseCooldownMap[packageName] ?: 0L
        if (now - lastTrigger > PAUSE_COOLDOWN_MS) {
            pauseCooldownMap[packageName] = now
            val pauseIntent = Intent(this, MindfulPauseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(pauseIntent)
        }
    }

    private fun isAntiCheatViolation(packageName: String, event: AccessibilityEvent): Boolean {
        if (antiCheatTargetPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return true
            if (hasNodeWithText(rootNode, "MindShield") || packageName.contains("freezer")) {
                return true
            }
        }

        if (packageName == "com.android.settings") {
            val rootNode = rootInActiveWindow ?: return false
            if (hasNodeWithText(rootNode, "MindShield")) {
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

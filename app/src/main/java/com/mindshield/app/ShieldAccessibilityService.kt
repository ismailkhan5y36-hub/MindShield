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

    private var lastDismissActionTime = 0L
    private val DISMISS_COOLDOWN_MS = 2000L // 2.0s debounce cooldown to prevent rapid-fire BACK presses

    private fun performDebouncedBackAction() {
        val now = System.currentTimeMillis()
        if (now - lastDismissActionTime > DISMISS_COOLDOWN_MS) {
            lastDismissActionTime = now
            quotaManager.recordDistractionBlock()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
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

        // --- RULE 2: UNIVERSAL EXPLICIT CONTENT & MOVIEBOX MIDNIGHT BLOCKER ---
        if (rootNode != null) {
            // Instant tab click detection
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val evText = event.text?.joinToString(" ")?.lowercase() ?: ""
                val evDesc = event.contentDescription?.toString()?.lowercase() ?: ""
                if (evText.contains("midnight") || evDesc.contains("midnight") ||
                    evText.contains("novelhub") || evDesc.contains("novelhub") ||
                    evText.contains("18+ channel") || evDesc.contains("18+ channel")
                ) {
                    performDebouncedBackAction()
                    return
                }
            }

            if (SmartFilterEngine.checkUniversalExplicitUi(rootNode) == FilterDecision.DISMISS_BACK) {
                performDebouncedBackAction()
                return
            }
        }

        // --- RULE 3: INCOGNITO / PRIVATE TAB BLOCKER (CHROME, PHOENIX, ETC.) ---
        if (webBrowsersAndDownloaders.contains(packageName)) {
            if (rootNode != null && isIncognitoModeActive(rootNode)) {
                performDebouncedBackAction()
                return
            }
        }

        // --- RULE 4: SMART SURGICAL FILTER (BROWSERS, VIDMATE, WEBVIEWS) ---
        if (webBrowsersAndDownloaders.contains(packageName) || packageName.contains("vidmate", ignoreCase = true)) {
            if (rootNode != null) {
                val decision = SmartFilterEngine.evaluateBrowserContent(rootNode)
                if (decision == FilterDecision.DISMISS_BACK) {
                    performDebouncedBackAction()
                    return
                }
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
        val rootNode = rootInActiveWindow ?: return false
        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val content = allTexts.joinToString(" ").lowercase()

        // 1. TECNO HIOS FREEZER & PHONE MASTER (BLOCK APP FREEZING!)
        val isFreezerApp = packageName.contains("freezer", ignoreCase = true) ||
                packageName.contains("phonemanager", ignoreCase = true) ||
                packageName.contains("hilauncher", ignoreCase = true) ||
                packageName == "com.transsion.freezer" ||
                packageName == "com.transsion.phonemanager"

        if (isFreezerApp && (content.contains("freezer") || content.contains("freeze") || content.contains("frozen"))) {
            return true
        }

        if (content.contains("freezer") && (content.contains("freeze") || content.contains("add"))) {
            return true
        }

        // 2. CHECK IF USER IS TRYING TO STOP / TURN OFF MINDSHIELD IN ACCESSIBILITY SETTINGS
        val isTryingToTurnOff = content.contains("stop mindshield") ||
                content.contains("turn off mindshield") ||
                content.contains("disable mindshield") ||
                (content.contains("stop") && content.contains("turning off")) ||
                (content.contains("stop service") && content.contains("mindshield"))

        if (isTryingToTurnOff) {
            return true // Instantly lock with 2-hour delay! Urge loophole closed!
        }

        // 3. Package Installer: Allow Update, Block Uninstall
        if (antiCheatTargetPackages.contains(packageName)) {
            if (content.contains("update") || content.contains("installing")) {
                return false // Always allow updates!
            }
            if (content.contains("mindshield") && (content.contains("uninstall") || content.contains("delete"))) {
                return true
            }
        }

        // 4. Force Stop or Uninstall prompt in Android Settings
        if (packageName == "com.android.settings") {
            if (content.contains("mindshield")) {
                if (content.contains("do you want to uninstall") ||
                    content.contains("uninstall this app") ||
                    (content.contains("force stop") && content.contains("if you force stop"))
                ) {
                    return true
                }
            }
        }

        // 5. LEGITIMATE PERMISSION & SETUP SCREENS (Display Over Apps, Usage, Battery)
        if (content.contains("display over other apps") ||
            content.contains("appear on top") ||
            content.contains("draw over other apps") ||
            content.contains("allow display") ||
            content.contains("usage access") ||
            content.contains("usage stats") ||
            content.contains("battery") ||
            content.contains("unrestricted") ||
            content.contains("restricted settings") ||
            content.contains("accessibility") ||
            content.contains("special app access")
        ) {
            return false // Always allow initial setup permissions!
        }

        return false
    }

    private fun collectAllNodeTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) outList.add(text)
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) outList.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) collectAllNodeTexts(child, outList)
        }
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

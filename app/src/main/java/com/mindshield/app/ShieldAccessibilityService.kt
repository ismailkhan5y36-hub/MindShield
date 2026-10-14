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
    private lateinit var customRulesManager: CustomRulesManager
    private lateinit var nightShieldManager: NightShieldManager
    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyInReels = false

    // Dedicated short-video apps (100% hard block)
    private val dedicatedShortVideoPackages = setOf(
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok
        "com.zhiliaoapp.musically.go",   // TikTok Lite
        "com.kwai.video",                // SnackVideo
        "com.kuaishou.nebula",           // SnackVideo
        "com.kwai.bulldog",              // Kwai
        "video.like",                    // Likee
        "video.like.lite",               // Likee Lite
        "in.moj.app",                    // Moj
        "in.moj.lite",                   // Moj Lite
        "com.eterno.shortvideos",        // Josh
        "com.funnypick.zili",            // Zili
        "com.next.innovation.takatak",   // MX TakaTak
        "io.chingari.app",               // Chingari
        "com.roposo.android"             // Roposo
    )

    // Universal Multi-Browser Coverage (All major Android browsers & downloaders)
    // NOTE: Incognito mode is fully supported for private browsing — only explicit content is filtered!
    private val webBrowsersAndDownloaders = setOf(
        "com.android.chrome",
        "com.transsion.phoenix",         // Tecno & Infinix default browser
        "com.brave.browser",             // Brave
        "org.mozilla.firefox",           // Firefox
        "com.opera.browser",             // Opera
        "com.opera.mini.native",         // Opera Mini
        "com.sec.android.app.sbrowser",  // Samsung Internet
        "com.microsoft.emmx",            // Microsoft Edge
        "com.UCMobile.intl",             // UC Browser
        "com.duckduckgo.mobile.android", // DuckDuckGo
        // VidMate & Downloaders (frequent bypass vectors)
        "com.nemo.vidmate",
        "com.vidmate.android",
        "com.video.downloader",
        "com.allvideodownloader",
        "com.snaptube.premium"
    )

    // Anti-Cheat packages: Package Installer, Tecno/HiOS Freezer & PhoneMaster
    private val antiCheatTargetPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.transsion.freezer",
        "com.transsion.phonemaster",
        "com.transsion.phonemanager",
        "com.transsion.appmanager",
        "com.cyin.himgr"
    )

    private val freezerPackages = setOf(
        "com.transsion.phonemaster",
        "com.transsion.phonemanager",
        "com.transsion.freezer",
        "com.cyin.himgr"
    )

    // Google Play Store bypassing tools searches
    private val restrictedPlayStoreSearches = listOf(
        "vpn", "proxy", "turbo vpn", "nord vpn", "super vpn",
        "snack video", "tiktok lite", "instagram lite"
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
        customRulesManager = CustomRulesManager.getInstance(this)
        nightShieldManager = NightShieldManager.getInstance(this)
    }

    private var lastBlockActionTime = 0L
    private val BLOCK_COOLDOWN_MS = 1500L

    private fun triggerSafeViolationAction() {
        val now = System.currentTimeMillis()
        if (now - lastBlockActionTime < BLOCK_COOLDOWN_MS) return
        lastBlockActionTime = now

        quotaManager.recordDistractionBlock()

        // High-Risk Windows (2-5 PM, 9 PM-6 AM, Sunday): trigger 3-Minute Cold Friction Lock
        if (RiskTimeEngine.isHighRiskWindow(now)) {
            triggerColdFrictionLock()
        } else {
            triggerBlock(BlockActivity.REASON_DISTRACTION)
        }
    }

    private fun triggerColdFrictionLock() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val frictionIntent = Intent(this, ColdFrictionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(frictionIntent)
    }

    private fun triggerNightRestLock() {
        val now = System.currentTimeMillis()
        if (now - lastBlockActionTime < BLOCK_COOLDOWN_MS) return
        lastBlockActionTime = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        val nightIntent = Intent(this, NightRestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(nightIntent)
    }

    private fun triggerBlock(reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val blockIntent = Intent(this, BlockActivity::class.java).apply {
            putExtra(BlockActivity.EXTRA_REASON, reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(blockIntent)
    }

    private fun triggerAntiCheatDelay() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val coolingIntent = Intent(this, CoolingOffActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(coolingIntent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // --- RULE 0: EXCLUSION WHITELIST (CRITICAL SYSTEM COMPONENTS NEVER TOUCHED) ---
        // Never interfere with MindShield itself, System UI, MovieBox, or Emergency Whitelist!
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            isMovieBoxPackage(packageName) ||
            EmergencyWhitelist.isWhitelisted(packageName)) {
            return
        }

        // Launcher Check: Launcher/Home screen is whitelisted for normal navigation,
        // UNLESS the user is inside Tecno HiOS Freezer attempting to freeze apps while Anti-Cheat is armed!
        val isLauncher = packageName == "com.transsion.hilauncher" ||
                packageName.contains("launcher", ignoreCase = true)

        if (isLauncher) {
            val rootNode = rootInActiveWindow

            // Universal Self-Defense: MindShield or Notes can NEVER be added to Freezer or disabled
            if (isFreezerTargetingMindShield(rootNode, event)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }

            // If anti-cheat is NOT active or maintenance window is open: launcher is safe to pass immediately
            if (!antiCheatManager.isAntiCheatEnabled() || antiCheatManager.isMaintenanceWindowOpen()) {
                return
            }

            // Anti-cheat IS active: Check if user is clicking or interacting with Freezer inside launcher
            if (isFreezerActive(rootNode, event)) {
                antiCheatManager.triggerCoolingOffIfIdle()
                triggerAntiCheatDelay()
                return
            }
            // Normal Home screen navigation: allow freely!
            return
        }

        // --- RULE 1: ANTI-CHEAT HARDWARE & SETTINGS LOCK (2-HOUR DELAY) ---
        // Only active IF user has armed it!
        if (antiCheatManager.isAntiCheatEnabled() && !antiCheatManager.isMaintenanceWindowOpen()) {
            if (isAntiCheatViolation(packageName, event)) {
                antiCheatManager.triggerCoolingOffIfIdle()
                triggerAntiCheatDelay()
                return
            }
        }

        // --- RULE 1.5: CUSTOM BLOCKED APPS ---
        if (customRulesManager.isPackageBlocked(packageName)) {
            triggerBlock(BlockActivity.REASON_DISTRACTION)
            return
        }

        // --- RULE 1.6: BEDTIME / NIGHT SHIELD LOCKDOWN ---
        if (nightShieldManager.isBedtimeActive() && isBrowserOrDownloader(packageName)) {
            triggerNightRestLock()
            return
        }

        // --- RULE 2: DEDICATED SHORT VIDEO APPS (100% HARD BLOCK) ---
        // TikTok, TikTok Lite, SnackVideo, Likee, Moj, Josh, Zili, etc.
        if (dedicatedShortVideoPackages.contains(packageName)) {
            triggerBlock(BlockActivity.REASON_DISTRACTION)
            return
        }

        // --- RULE 3: YOUTUBE SHORTS-TO-HOME SURGICAL REDIRECT ---
        // Long-form educational YouTube videos continue smoothly!
        if (packageName == "com.google.android.youtube") {
            handleYouTubeShortsRedirect(event)
            return
        }

        // --- RULE 4: INSTAGRAM & FACEBOOK REELS SURGICAL REDIRECT ---
        if (packageName == "com.instagram.android") {
            handleInstagramReelsRedirect(event)
            return
        }
        if (packageName == "com.facebook.katana") {
            handleFacebookReelsRedirect(event)
            return
        }

        // --- RULE 5: UNIVERSAL AI CONTENT BRAIN (BROWSERS, MEDIA, THIRD-PARTY APPS) ---
        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: packageName

        // Fast-pass: Never interfere with MovieBox or Whitelisted apps
        if (isMovieBoxPackage(activePkg) || isMovieBoxPackage(packageName) || EmergencyWhitelist.isWhitelisted(activePkg)) {
            return
        }

        if (rootNode != null) {
            val decision = SmartFilterEngine.evaluateUniversalScreenContent(rootNode, packageName)
            if (decision == FilterDecision.DISMISS_BACK) {
                triggerSafeViolationAction()
                return
            }
        }

        // --- RULE 7: VPN & CLONE APP GUARD ON GOOGLE PLAY STORE ---
        if (packageName == "com.android.vending") {
            val rootNode = rootInActiveWindow
            if (rootNode != null && isPlayStoreRestrictedSearch(rootNode)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }
    }

    private fun isBrowserOrDownloader(packageName: String): Boolean {
        if (webBrowsersAndDownloaders.contains(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("browser") || lower.contains("vidmate") || lower.contains("downloader")
    }

    // --- YOUTUBE SHORTS REDIRECTOR ---
    private var lastYouTubeRedirectTime = 0L
    private val YT_REDIRECT_COOLDOWN_MS = 800L

    private fun handleYouTubeShortsRedirect(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        // Detect Shorts tab click
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val evText = event.text?.joinToString(" ")?.lowercase() ?: ""
            val evDesc = event.contentDescription?.toString()?.lowercase() ?: ""
            if (evText.contains("shorts") || evDesc.contains("shorts")) {
                if (now - lastYouTubeRedirectTime > YT_REDIRECT_COOLDOWN_MS) {
                    lastYouTubeRedirectTime = now
                    redirectYouTubeToHome(rootNode)
                }
                return
            }
        }

        // Detect if active in Shorts player
        if (isYouTubeInShorts(rootNode, event)) {
            if (now - lastYouTubeRedirectTime > YT_REDIRECT_COOLDOWN_MS) {
                lastYouTubeRedirectTime = now
                redirectYouTubeToHome(rootNode)
            }
        }
    }

    private fun isYouTubeInShorts(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("ReelWatchFragment", ignoreCase = true) ||
            className.contains("ShortsPlayer", ignoreCase = true)) {
            return true
        }

        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val combined = allTexts.joinToString(" ").lowercase()

        // Check if Shorts tab is selected
        val shortsNodes = rootNode.findAccessibilityNodeInfosByText("Shorts")
        for (node in shortsNodes) {
            if (node.isSelected || node.parent?.isSelected == true) {
                return true
            }
        }

        // Check full-screen Shorts UI elements
        val hasShortsIndicator = combined.contains("shorts")
        val hasShortsControls = (combined.contains("remix") || combined.contains("sound") || combined.contains("dislike"))
                && (combined.contains("comments") || combined.contains("share"))

        return hasShortsIndicator && hasShortsControls
    }

    private fun redirectYouTubeToHome(rootNode: AccessibilityNodeInfo) {
        // Attempt 1: Find Home tab in bottom navigation and click it
        if (findAndClickHomeTab(rootNode)) {
            return
        }

        // Attempt 2: If in full-screen player, find Navigate Up / Back button and click it
        if (findAndClickBackButton(rootNode)) {
            return
        }

        // Attempt 3: Single clean global action back to exit Shorts player
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun findAndClickHomeTab(rootNode: AccessibilityNodeInfo): Boolean {
        val homeNodes = rootNode.findAccessibilityNodeInfosByText("Home")
        for (node in homeNodes) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc == "home" || desc.startsWith("home, tab") || desc.startsWith("home tab") || desc == "ہوم") {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                val parent = node.parent
                if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }
        return false
    }

    private fun findAndClickBackButton(rootNode: AccessibilityNodeInfo): Boolean {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("navigate up") || desc.contains("go back") || desc == "back") {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                val parent = node.parent
                if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }
        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, outList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        outList.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) collectAllNodes(child, outList)
        }
    }

    // --- INSTAGRAM & FACEBOOK REELS REDIRECTOR ---
    private var lastInstaRedirectTime = 0L

    private fun handleInstagramReelsRedirect(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        val isInClips = className.contains("ClipsViewerActivity", ignoreCase = true) ||
                (hasNodeWithContentDescription(rootNode, "Reels") && hasNodeWithContentDescription(rootNode, "Audio"))

        if (isInClips || (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.contentDescription?.toString()?.contains("Reels", ignoreCase = true) == true)) {
            if (now - lastInstaRedirectTime > 800L) {
                lastInstaRedirectTime = now
                if (!findAndClickHomeTab(rootNode)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private var lastFbRedirectTime = 0L

    private fun handleFacebookReelsRedirect(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        val isInReels = className.contains("Reel", ignoreCase = true) ||
                hasNodeWithText(rootNode, "Reels and short videos") ||
                hasNodeWithContentDescription(rootNode, "Reels")

        if (isInReels) {
            if (now - lastFbRedirectTime > 800L) {
                lastFbRedirectTime = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun isMovieBoxPackage(packageName: String): Boolean {
        return packageName.contains("moviebox", ignoreCase = true) ||
                packageName == "com.transsion.moviebox" ||
                packageName == "com.community.oneroom" ||
                packageName == "com.transsion.magellan" ||
                packageName == "com.playbox.movie" ||
                packageName == "com.mb.android" ||
                packageName == "com.universal.free.movie"
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

    private fun isAntiCheatViolation(packageName: String, event: AccessibilityEvent): Boolean {
        val lowerPkg = packageName.lowercase()

        // 1. TECNO HIOS FREEZER & PHONE MASTER (100% UNBYPASSABLE HARD LOCK)
        val isFreezerApp = lowerPkg.contains("freezer") ||
                lowerPkg.contains("phonemaster") ||
                lowerPkg.contains("phonemanager") ||
                lowerPkg.contains("cyin.himgr") ||
                freezerPackages.contains(lowerPkg)

        if (isFreezerApp) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val allTexts = mutableListOf<String>()
                collectAllNodeTexts(rootNode, allTexts)
                val content = allTexts.joinToString(" ").lowercase()

                if (content.contains("freezer") || content.contains("freeze") ||
                    content.contains("frozen") || content.contains("disable") ||
                    content.contains("uninstall") || content.contains("mindshield") ||
                    content.contains("notes")) {
                    return true
                }
            }
            return true
        }

        val rootNode = rootInActiveWindow ?: return false
        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val content = allTexts.joinToString(" ").lowercase()

        // 2. CHECK IF USER IS TRYING TO STOP / TURN OFF MINDSHIELD IN ACCESSIBILITY SETTINGS
        val isTryingToTurnOff = content.contains("stop mindshield") ||
                content.contains("turn off mindshield") ||
                content.contains("disable mindshield") ||
                (content.contains("stop") && content.contains("turning off")) ||
                (content.contains("stop service") && content.contains("mindshield"))

        if (isTryingToTurnOff) {
            return true
        }

        // 3. CHECK IF USER IS TRYING TO DEACTIVATE DEVICE ADMIN
        if (lowerPkg == "com.android.settings") {
            if (content.contains("mindshield") && (
                content.contains("deactivate") ||
                content.contains("remove device admin") ||
                content.contains("disable device admin")
            )) {
                return true
            }
        }

        // 4. Package Installer: Allow Update, Block Uninstall
        if (antiCheatTargetPackages.contains(lowerPkg) || lowerPkg.contains("packageinstaller")) {
            if (content.contains("update") || content.contains("installing")) {
                return false
            }
            if (content.contains("mindshield") && (content.contains("uninstall") || content.contains("delete"))) {
                return true
            }
        }

        // 5. Force Stop or Uninstall prompt in Android Settings
        if (lowerPkg == "com.android.settings") {
            if (content.contains("mindshield") || content.contains("notes")) {
                if (content.contains("do you want to uninstall") ||
                    content.contains("uninstall this app") ||
                    content.contains("clear data") ||
                    content.contains("clear storage") ||
                    (content.contains("force stop") && content.contains("if you force stop"))
                ) {
                    return true
                }
            }
        }

        // 6. LEGITIMATE PERMISSION & SETUP SCREENS (Always allow initial setup)
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
            return false
        }

        return false
    }

    private fun isFreezerTargetingMindShield(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent): Boolean {
        val evText = event.text?.joinToString(" ")?.lowercase() ?: ""
        val evDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        val isTargetingApp = evText.contains("mindshield") || evText.contains("notes") ||
                evDesc.contains("mindshield") || evDesc.contains("notes")

        if (rootNode == null) {
            return isTargetingApp && (evText.contains("freeze") || evDesc.contains("freeze"))
        }

        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val content = allTexts.joinToString(" ").lowercase()

        val hasTargetApp = content.contains("mindshield") || content.contains("notes")
        val hasFreezeOrDisable = content.contains("freeze") || content.contains("freezer") ||
                content.contains("frozen") || content.contains("add to freezer") ||
                content.contains("auto freeze") || content.contains("freeze app") ||
                content.contains("unfreeze")

        return hasTargetApp && hasFreezeOrDisable
    }

    private fun isFreezerActive(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent): Boolean {
        // 1. Check Event metadata
        val evClass = event.className?.toString()?.lowercase() ?: ""
        if (evClass.contains("freeze") || evClass.contains("freezer") || evClass.contains("himgr")) {
            return true
        }

        val evText = event.text?.joinToString(" ")?.lowercase() ?: ""
        val evDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        if (evText.contains("freezer") || evText.contains("freeze") ||
            evDesc.contains("freezer") || evDesc.contains("freeze")) {
            return true
        }

        // 2. Check window content
        if (rootNode == null) return false
        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val combined = allTexts.joinToString(" ").lowercase()

        if (combined.contains("freezer") || combined.contains("freeze apps") ||
            combined.contains("frozen apps") || combined.contains("add to freezer") ||
            combined.contains("freeze app") || combined.contains("frozen") ||
            combined.contains("auto freeze") || combined.contains("freezer settings")) {
            return true
        }

        // 3. Check View IDs
        return hasNodeWithFreezerId(rootNode)
    }

    private fun hasNodeWithFreezerId(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("freeze") || viewId.contains("freezer") || viewId.contains("himgr")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && hasNodeWithFreezerId(child)) {
                return true
            }
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
        if (isCurrentlyInReels) {
            isCurrentlyInReels = false
            handler.removeCallbacks(tickerRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isCurrentlyInReels) {
            isCurrentlyInReels = false
            handler.removeCallbacks(tickerRunnable)
        }
    }
}

package com.mindshield.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShieldAccessibilityService : AccessibilityService() {

    private lateinit var quotaManager: DailyQuotaManager
    private lateinit var antiCheatManager: AntiCheatManager
    private lateinit var customRulesManager: CustomRulesManager
    private lateinit var nightShieldManager: NightShieldManager
    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyInReels = false
    private var currentReelsPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                stopTicker()
            }
        }
    }

    // Dedicated short-video apps (Under 1-Hour Daily Quota)
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
                    stopTicker()
                    return
                }

                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun startTicker(pkg: String) {
        currentReelsPackage = pkg
        if (!isCurrentlyInReels) {
            isCurrentlyInReels = true
            handler.removeCallbacks(tickerRunnable)
            handler.post(tickerRunnable)
        }
    }

    private fun stopTicker() {
        if (isCurrentlyInReels) {
            isCurrentlyInReels = false
            currentReelsPackage = null
            handler.removeCallbacks(tickerRunnable)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        quotaManager = DailyQuotaManager(this)
        antiCheatManager = AntiCheatManager(this)
        customRulesManager = CustomRulesManager.getInstance(this)
        nightShieldManager = NightShieldManager.getInstance(this)

        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.e("ShieldService", "Failed to register screenReceiver", e)
        }
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

    private var lastDirectBlockTime = 0L
    private val DIRECT_BLOCK_COOLDOWN_MS = 2000L

    private fun triggerBlock(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastDirectBlockTime < DIRECT_BLOCK_COOLDOWN_MS) return
        lastDirectBlockTime = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        val blockIntent = Intent(this, BlockActivity::class.java).apply {
            putExtra(BlockActivity.EXTRA_REASON, reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(blockIntent)
    }

    private var lastAntiCheatTriggerTime = 0L
    private val ANTICHEAT_COOLDOWN_MS = 2500L

    private fun triggerAntiCheatDelay() {
        val now = System.currentTimeMillis()
        if (now - lastAntiCheatTriggerTime < ANTICHEAT_COOLDOWN_MS) return
        lastAntiCheatTriggerTime = now

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
            if (packageName != currentReelsPackage) {
                stopTicker()
            }
            return
        }

        // Launcher Check: Launcher/Home screen is whitelisted for normal navigation & Freezer
        val isLauncher = packageName == "com.transsion.hilauncher" ||
                packageName.contains("launcher", ignoreCase = true)

        if (isLauncher) {
            stopTicker()
            val rootNode = rootInActiveWindow
            // Only intercept if an action is specifically attempting to freeze MindShield
            if (isFreezerInteraction(rootNode, event)) {
                if (antiCheatManager.isAntiCheatEnabled() && !antiCheatManager.isMaintenanceWindowOpen()) {
                    antiCheatManager.triggerCoolingOffIfIdle()
                    triggerAntiCheatDelay()
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                return
            }
            // Normal Home screen navigation & opening Freezer: ALLOW FREELY!
            return
        }

        // --- RULE 1: ANTI-CHEAT HARDWARE & SETTINGS LOCK (2-HOUR DELAY) ---
        // Only active IF user has armed it!
        if (antiCheatManager.isAntiCheatEnabled() && !antiCheatManager.isMaintenanceWindowOpen()) {
            if (isAntiCheatViolation(packageName, event)) {
                stopTicker()
                antiCheatManager.triggerCoolingOffIfIdle()
                triggerAntiCheatDelay()
                return
            }
        }

        // --- RULE 1.5: CUSTOM BLOCKED APPS ---
        if (customRulesManager.isPackageBlocked(packageName)) {
            stopTicker()
            triggerBlock(BlockActivity.REASON_DISTRACTION)
            return
        }

        // --- RULE 1.6: BEDTIME / NIGHT SHIELD LOCKDOWN ---
        if (nightShieldManager.isBedtimeActive() && isBrowserOrDownloader(packageName)) {
            stopTicker()
            triggerNightRestLock()
            return
        }

        // --- RULE 2, 3 & 4: SHORT VIDEOS & REELS 1-HOUR (60 MINS) DAILY QUOTA SYSTEM ---
        val rootNode = rootInActiveWindow
        val isShortsOrReels = when {
            dedicatedShortVideoPackages.contains(packageName) -> true
            packageName == "com.google.android.youtube" -> rootNode != null && isYouTubeInShorts(rootNode, event)
            packageName == "com.instagram.android" -> rootNode != null && isInstagramInReels(rootNode, event)
            packageName == "com.facebook.katana" || packageName == "com.facebook.lite" -> rootNode != null && isFacebookInReels(rootNode, event)
            else -> false
        }

        if (isShortsOrReels) {
            if (quotaManager.isQuotaExhausted()) {
                stopTicker()
                when (packageName) {
                    "com.google.android.youtube" -> if (rootNode != null) redirectYouTubeToHome(rootNode) else performGlobalAction(GLOBAL_ACTION_BACK)
                    "com.instagram.android" -> if (rootNode != null) redirectInstagramToHome(rootNode) else performGlobalAction(GLOBAL_ACTION_BACK)
                    "com.facebook.katana", "com.facebook.lite" -> if (rootNode != null) redirectFacebookToHome(rootNode) else performGlobalAction(GLOBAL_ACTION_BACK)
                    else -> triggerBlock(BlockActivity.REASON_QUOTA)
                }
                return
            } else {
                // Quota still remaining: User is watching within their 1-hour quota!
                // Start ticker to count down the 60 minutes. NO BACK PRESS!
                startTicker(packageName)
                return
            }
        } else {
            // Not in Reels/Shorts: stop the ticker if active
            if (currentReelsPackage == packageName ||
                packageName == "com.google.android.youtube" ||
                packageName == "com.instagram.android" ||
                packageName == "com.facebook.katana" ||
                packageName == "com.facebook.lite" ||
                dedicatedShortVideoPackages.contains(packageName)) {
                stopTicker()
            }
        }

        // --- RULE 5: UNIVERSAL AI CONTENT BRAIN (BROWSERS, MEDIA, THIRD-PARTY APPS) ---
        val activePkg = rootNode?.packageName?.toString() ?: packageName

        // Fast-pass: Never interfere with MovieBox or Whitelisted apps
        if (isMovieBoxPackage(activePkg) || isMovieBoxPackage(packageName) || EmergencyWhitelist.isWhitelisted(activePkg)) {
            return
        }

        // Only evaluate browsers and downloaders via targeted URL & search bar inspection!
        if (isBrowserOrDownloader(packageName)) {
            if (rootNode != null) {
                val decision = SmartFilterEngine.evaluateBrowserContent(rootNode)
                if (decision == FilterDecision.DISMISS_BACK) {
                    triggerSafeViolationAction()
                    return
                }
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

        // Detect Shorts tab click (only the tab itself, not video titles that mention 'shorts')
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val evText = event.text?.joinToString(" ")?.lowercase()?.trim() ?: ""
            val evDesc = event.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            if (evText.contains("shorts, tab") || evDesc.contains("shorts, tab") ||
                evText == "shorts" || evDesc == "shorts" || evDesc.startsWith("shorts tab")) {
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

        // Check if Shorts tab is selected by text or content description
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if ((desc.contains("shorts") || text.contains("shorts")) &&
                (node.isSelected || node.parent?.isSelected == true)) {
                return true
            }
            if (viewId.contains("reel_recycler") || viewId.contains("shorts_player") || viewId.contains("reel_watch_fragment")) {
                return true
            }
        }

        return false
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
            if (desc == "home" || desc.startsWith("home, tab") || desc.startsWith("home tab") ||
                desc == "ہوم" || desc.contains("news feed")) {
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
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (desc.contains("navigate up") || desc.contains("go back") || desc == "back" ||
                desc.contains("close") || viewId.contains("action_bar_back") || viewId.contains("btn_back")) {
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
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        if (isInstagramInReels(rootNode, event)) {
            if (now - lastInstaRedirectTime > 800L) {
                lastInstaRedirectTime = now
                redirectInstagramToHome(rootNode)
            }
        }
    }

    private fun isInstagramInReels(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("ClipsViewerActivity", ignoreCase = true) ||
            className.contains("ClipsViewer", ignoreCase = true) ||
            className.contains("ReelsViewerActivity", ignoreCase = true)) {
            return true
        }

        // Scan all nodes in active window
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        var isReelsTabSelected = false
        var isHomeTabSelected = false
        var hasClipsViewerId = false
        var hasReelAudioOrRemix = false
        var hasActionButtons = false

        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Check if Reels tab is selected in bottom bar
            if ((desc.contains("reels") || text.contains("reels") || desc.contains("clips")) &&
                (node.isSelected || node.parent?.isSelected == true)) {
                isReelsTabSelected = true
            }

            // Check if Home feed tab is selected in bottom bar
            if ((desc.contains("home") || desc.contains("feed") || desc.contains("ہوم")) &&
                (node.isSelected || node.parent?.isSelected == true)) {
                isHomeTabSelected = true
            }

            // Check for dedicated Reels/Clips viewer resource IDs
            if (viewId.contains("clips_viewer") || viewId.contains("clips_video_container") ||
                viewId.contains("reel_viewer") || viewId.contains("clips_action_button") ||
                viewId.contains("clips_swipe_refresh")) {
                hasClipsViewerId = true
            }

            // Check for audio / remix indicator
            if (desc.contains("original audio") || desc.contains("audio page") ||
                text.contains("original audio") || desc.contains("use audio") ||
                desc.contains("remix") || text.contains("remix")) {
                hasReelAudioOrRemix = true
            }

            // Check for reel action buttons (comment/share)
            if (desc.contains("comment") || desc.contains("share") || desc.contains("send post")) {
                hasActionButtons = true
            }
        }

        // If user is explicitly on the Reels tab
        if (isReelsTabSelected) {
            return true
        }

        // If dedicated clips viewer resource ID is found on screen
        if (hasClipsViewerId) {
            return true
        }

        // If watching full-screen reel (has audio/remix + comments/share) AND Home feed is not active
        if (hasReelAudioOrRemix && hasActionButtons && !isHomeTabSelected) {
            return true
        }

        return false
    }

    private fun redirectInstagramToHome(rootNode: AccessibilityNodeInfo) {
        if (findAndClickHomeTab(rootNode)) {
            return
        }
        if (findAndClickBackButton(rootNode)) {
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private var lastFbRedirectTime = 0L

    private fun handleFacebookReelsRedirect(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        if (isFacebookInReels(rootNode, event)) {
            if (now - lastFbRedirectTime > 800L) {
                lastFbRedirectTime = now
                redirectFacebookToHome(rootNode)
            }
        }
    }

    private fun isFacebookInReels(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("ReelsViewerActivity", ignoreCase = true) ||
            className.contains("FbShortsViewerActivity", ignoreCase = true) ||
            className.contains("FbShortsPlayerActivity", ignoreCase = true)) {
            return true
        }

        // Scan all nodes in active window
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        var isReelsTabSelected = false
        var isHomeFeedSelected = false
        var hasFbShortsId = false
        var hasReelAudioOrRemix = false

        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Check if Reels tab is selected in Facebook bar
            if ((desc.contains("reels") || text.contains("reels")) &&
                (node.isSelected || node.parent?.isSelected == true)) {
                isReelsTabSelected = true
            }

            // Check if standard News Feed / Home tab is selected
            if ((desc.contains("news feed") || desc.contains("home, tab") || desc == "home") &&
                (node.isSelected || node.parent?.isSelected == true)) {
                isHomeFeedSelected = true
            }

            // Dedicated FB shorts viewer resource ID
            if (viewId.contains("fb_shorts") || viewId.contains("reels_viewer") ||
                viewId.contains("unified_reels") || viewId.contains("reel_video")) {
                hasFbShortsId = true
            }

            // Dedicated Reels interaction markers
            if (desc.contains("remix this reel") || text.contains("remix this reel") ||
                desc.contains("reels audio") || text.contains("reels audio") ||
                desc.contains("original audio") || text.contains("original audio")) {
                hasReelAudioOrRemix = true
            }
        }

        if (isReelsTabSelected) {
            return true
        }

        if (hasFbShortsId) {
            return true
        }

        if (hasReelAudioOrRemix && !isHomeFeedSelected) {
            return true
        }

        return false
    }

    private fun redirectFacebookToHome(rootNode: AccessibilityNodeInfo) {
        if (findAndClickHomeTab(rootNode)) {
            return
        }
        if (findAndClickBackButton(rootNode)) {
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
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



    private fun isPlayStoreRestrictedSearch(rootNode: AccessibilityNodeInfo): Boolean {
        val searchBoxes = mutableListOf<AccessibilityNodeInfo>()
        findSearchInputNodes(rootNode, searchBoxes)
        for (box in searchBoxes) {
            val text = box.text?.toString()?.lowercase()?.trim() ?: ""
            if (text.isBlank()) continue
            for (kw in restrictedPlayStoreSearches) {
                if (text == kw || text.startsWith("$kw ") || text.contains(" $kw ") || text.endsWith(" $kw")) {
                    return true
                }
            }
        }
        return false
    }

    private fun findSearchInputNodes(node: AccessibilityNodeInfo?, outList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (className.contains("EditText", ignoreCase = true) || viewId.contains("search") || viewId.contains("query")) {
            outList.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) findSearchInputNodes(child, outList)
        }
    }

    private fun isAntiCheatViolation(packageName: String, event: AccessibilityEvent): Boolean {
        val lowerPkg = packageName.lowercase()

        val isTargetPkg = lowerPkg == "com.android.settings" ||
                lowerPkg.contains("packageinstaller") ||
                lowerPkg.contains("freezer") ||
                lowerPkg.contains("phonemaster") ||
                lowerPkg.contains("phonemanager") ||
                lowerPkg.contains("cyin.himgr") ||
                antiCheatTargetPackages.contains(lowerPkg) ||
                freezerPackages.contains(lowerPkg)

        // Fast-pass: If not a settings, installer, or freezer package, NEVER flag as anti-cheat!
        if (!isTargetPkg) {
            return false
        }

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

                val targetsMindShield = content.contains("mindshield") || content.contains("notes")
                val isFreezeOrDisable = content.contains("freeze") || content.contains("disable") ||
                        content.contains("uninstall") || content.contains("clear data") || content.contains("force stop")

                if (targetsMindShield && isFreezeOrDisable) {
                    return true
                }
            }
            return false
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

    private fun isFreezerInteraction(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent): Boolean {
        if (rootNode == null) return false

        val evText = event.text?.joinToString(" ")?.trim()?.lowercase() ?: ""
        val evDesc = event.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        // 1. Direct click on MindShield/Notes row or checkbox in Freezer's Add-to-Freezer screen
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            var clickedMindShield = evText.contains("mindshield") || evDesc.contains("mindshield") ||
                    evText.contains("notes") || evDesc.contains("notes")

            if (!clickedMindShield) {
                val parentNode = event.source?.parent
                if (parentNode != null) {
                    val parentTexts = mutableListOf<String>()
                    collectAllNodeTexts(parentNode, parentTexts)
                    val parentCombined = parentTexts.joinToString(" ").lowercase()
                    if (parentCombined.contains("mindshield") || parentCombined.contains("notes")) {
                        clickedMindShield = true
                    }
                }
            }

            if (clickedMindShield) {
                val allTexts = mutableListOf<String>()
                collectAllNodeTexts(rootNode, allTexts)
                val combined = allTexts.joinToString(" ").lowercase()
                if (combined.contains("freeze") || combined.contains("freezer") ||
                    combined.contains("frozen") || combined.contains("add")) {
                    return true
                }
            }
        }

        // 2. Active confirmation dialog attempting to freeze MindShield specifically
        val allTexts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, allTexts)
        val combined = allTexts.joinToString(" ").lowercase()

        val targetsMindShield = combined.contains("mindshield") || combined.contains("notes")
        val isFreezeDialog = (combined.contains("freeze") || combined.contains("freezer") || combined.contains("frozen")) &&
                (combined.contains("are you sure") || combined.contains("freeze app") || combined.contains("add to freezer") ||
                 (combined.contains("ok") && combined.contains("cancel")))

        if (targetsMindShield && isFreezeDialog) {
            return true
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

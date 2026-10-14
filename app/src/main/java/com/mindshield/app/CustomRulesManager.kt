package com.mindshield.app

import android.content.Context
import android.content.SharedPreferences

/**
 * CustomRulesManager: Manages user-customized app blacklists, domain blacklists, and whitelists.
 * Uses persistent SharedPreferences with concurrent in-memory caching for zero-latency lookups.
 */
class CustomRulesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mindshield_custom_rules_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BLOCKED_PACKAGES = "custom_blocked_packages"
        private const val KEY_BLOCKED_DOMAINS = "custom_blocked_domains"
        private const val KEY_WHITELISTED_DOMAINS = "custom_whitelisted_domains"

        @Volatile
        private var instance: CustomRulesManager? = null

        fun getInstance(context: Context): CustomRulesManager {
            return instance ?: synchronized(this) {
                instance ?: CustomRulesManager(context).also { instance = it }
            }
        }
    }

    private val blockedPackages = HashSet<String>()
    private val blockedDomains = HashSet<String>()
    private val whitelistedDomains = HashSet<String>()

    init {
        loadRules()
    }

    @Synchronized
    private fun loadRules() {
        blockedPackages.clear()
        blockedPackages.addAll(prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet())

        blockedDomains.clear()
        val domains = prefs.getStringSet(KEY_BLOCKED_DOMAINS, emptySet()) ?: emptySet()
        blockedDomains.addAll(domains.map { cleanDomain(it) })

        whitelistedDomains.clear()
        val white = prefs.getStringSet(KEY_WHITELISTED_DOMAINS, emptySet()) ?: emptySet()
        whitelistedDomains.addAll(white.map { cleanDomain(it) })
    }

    private fun cleanDomain(raw: String): String {
        return raw.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/")[0]
            .trim()
    }

    // --- Package Blacklist ---
    @Synchronized
    fun isPackageBlocked(pkg: String): Boolean {
        return blockedPackages.contains(pkg.trim())
    }

    @Synchronized
    fun getBlockedPackages(): Set<String> {
        return HashSet(blockedPackages)
    }

    @Synchronized
    fun addBlockedPackage(pkg: String) {
        val clean = pkg.trim()
        if (clean.isNotEmpty() && blockedPackages.add(clean)) {
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, blockedPackages).apply()
        }
    }

    @Synchronized
    fun removeBlockedPackage(pkg: String) {
        val clean = pkg.trim()
        if (blockedPackages.remove(clean)) {
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, blockedPackages).apply()
        }
    }

    // --- Domain Blacklist ---
    @Synchronized
    fun isDomainBlocked(domain: String): Boolean {
        val clean = cleanDomain(domain)
        if (clean.isEmpty()) return false
        if (blockedDomains.contains(clean)) return true

        // Check subdomain matching (e.g. sub.distraction.com matches distraction.com)
        for (b in blockedDomains) {
            if (clean.endsWith(".$b")) return true
        }
        return false
    }

    @Synchronized
    fun getBlockedDomains(): Set<String> {
        return HashSet(blockedDomains)
    }

    @Synchronized
    fun addBlockedDomain(domain: String) {
        val clean = cleanDomain(domain)
        if (clean.isNotEmpty() && blockedDomains.add(clean)) {
            prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, blockedDomains).apply()
        }
    }

    @Synchronized
    fun removeBlockedDomain(domain: String) {
        val clean = cleanDomain(domain)
        if (blockedDomains.remove(clean)) {
            prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, blockedDomains).apply()
        }
    }

    // --- Whitelist ---
    @Synchronized
    fun isDomainWhitelisted(domain: String): Boolean {
        val clean = cleanDomain(domain)
        if (clean.isEmpty()) return false
        if (whitelistedDomains.contains(clean)) return true
        for (w in whitelistedDomains) {
            if (clean.endsWith(".$w")) return true
        }
        return false
    }

    @Synchronized
    fun getWhitelistedDomains(): Set<String> {
        return HashSet(whitelistedDomains)
    }

    @Synchronized
    fun addWhitelistedDomain(domain: String) {
        val clean = cleanDomain(domain)
        if (clean.isNotEmpty() && whitelistedDomains.add(clean)) {
            prefs.edit().putStringSet(KEY_WHITELISTED_DOMAINS, whitelistedDomains).apply()
        }
    }

    @Synchronized
    fun removeWhitelistedDomain(domain: String) {
        val clean = cleanDomain(domain)
        if (whitelistedDomains.remove(clean)) {
            prefs.edit().putStringSet(KEY_WHITELISTED_DOMAINS, whitelistedDomains).apply()
        }
    }
}

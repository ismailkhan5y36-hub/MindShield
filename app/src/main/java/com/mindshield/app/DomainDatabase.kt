package com.mindshield.app

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object DomainDatabase {

    private const val ASSET_NAME = "adult_domains_sorted.txt"
    private const val DB_FILE_NAME = "adult_domains_sorted.txt"
    private var randomAccessFile: RandomAccessFile? = null
    private var fileSize: Long = 0L
    @Volatile
    private var isInitialized = false
    private val executor = Executors.newSingleThreadExecutor()

    // Top High-Frequency Adult Domains (Instant O(1) in-memory cache)
    private val topTierAdultCache = hashSetOf(
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

    fun init(context: Context) {
        if (isInitialized) return
        executor.execute {
            synchronized(this) {
                if (isInitialized) return@execute
                try {
                    val targetFile = File(context.filesDir, DB_FILE_NAME)
                    if (!targetFile.exists() || targetFile.length() < 10_000_000L) {
                        context.assets.open(ASSET_NAME).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    if (targetFile.exists()) {
                        randomAccessFile = RandomAccessFile(targetFile, "r")
                        fileSize = targetFile.length()
                        isInitialized = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Dual-Speed Domain Lookup:
     * 1. Instant O(1) in-memory top-tier cache
     * 2. O(log N) disk binary search across 950,000+ domains in 19MB file (~0.05ms)
     */
    fun isDomainBlocked(rawDomain: String): Boolean {
        var domain = rawDomain.lowercase().trim()
        if (domain.endsWith(".")) domain = domain.dropLast(1)
        if (domain.isBlank()) return false

        // 1. Check top-tier in-memory cache
        var current: String? = domain
        while (!current.isNullOrBlank()) {
            if (topTierAdultCache.contains(current)) {
                return true
            }
            val dot = current.indexOf('.')
            current = if (dot != -1 && dot < current.length - 1) {
                current.substring(dot + 1)
            } else {
                null
            }
        }

        // 2. Binary search across the 950,000 domains database
        if (!isInitialized || randomAccessFile == null || fileSize == 0L) {
            return false
        }

        current = domain
        while (!current.isNullOrBlank()) {
            if (binarySearchDomain(current)) {
                return true
            }
            val dot = current.indexOf('.')
            current = if (dot != -1 && dot < current.length - 1) {
                current.substring(dot + 1)
            } else {
                null
            }
        }

        return false
    }

    private fun binarySearchDomain(target: String): Boolean {
        val raf = randomAccessFile ?: return false
        var low = 0L
        var high = fileSize - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            raf.seek(mid)

            // Seek to start of next line if not at beginning
            if (mid > 0) {
                readLine(raf)
            }

            val line = readLine(raf)?.trim()?.lowercase()
            if (line == null) {
                high = mid - 1
                continue
            }

            val cmp = target.compareTo(line)
            if (cmp == 0) {
                return true
            } else if (cmp < 0) {
                high = mid - 1
            } else {
                low = raf.filePointer
            }
        }
        return false
    }

    private fun readLine(raf: RandomAccessFile): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val b = raf.read()
            if (b == -1) break
            if (b == '\n'.code) break
            if (b != '\r'.code) bytes.add(b.toByte())
            if (bytes.size > 255) break
        }
        if (bytes.isEmpty() && raf.filePointer >= fileSize) return null
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }
}

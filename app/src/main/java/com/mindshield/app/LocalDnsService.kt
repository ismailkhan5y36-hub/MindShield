package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class LocalDnsService : VpnService() {

    companion object {
        const val ACTION_START = "com.mindshield.app.START_DNS"
        const val ACTION_STOP = "com.mindshield.app.STOP_DNS"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "mindshield_dns_guard"
        private const val TAG = "LocalDnsService"

        // Upstream Fast Anycast DNS with Safe filtering & high availability failover
        private val UPSTREAM_DNS_SERVERS = listOf(
            "1.1.1.3",      // Cloudflare Family Safe DNS (filters adult/malware, lowest ping in Asia)
            "94.140.14.14", // AdGuard Family Safe DNS
            "1.1.1.1",      // Cloudflare Standard Anycast fallback
            "8.8.8.8"       // Google Standard Anycast fallback
        )
        private const val UPSTREAM_DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 1200
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var isRunning = false

    // High throughput dynamic cached thread pool - no queue bottleneck for parallel queries
    private val workerExecutor = Executors.newCachedThreadPool()

    // 0ms DNS In-Memory Cache (Domain -> Resolved DNS Payload)
    private val dnsCache = LruCache<String, ByteArray>(1024)

    // 15-second Watchdog thread for zero-touch Accessibility survival
    private var watchdogThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        DomainDatabase.init(this)
        // Check & heal accessibility on service start
        AccessibilityAutoHealer.healAccessibility(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopDnsService()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startDnsService()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "MindShield 24/7 Network Guard",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Runs local DNS firewall protecting all browsers 24/7"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("🛡️ MindShield 24/7 Network Guard Active")
                .setContentText("Local DNS Firewall blocking 950,000+ adult domains across all apps.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
                } catch (e: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startDnsService() {
        if (isRunning) return
        isRunning = true

        startWatchdog()

        workerExecutor.execute {
            try {
                // Route ONLY DNS traffic through local interface (Zero data/speed drop on normal internet)
                val builder = Builder()
                    .setSession("MindShield Local DNS Guard")
                    .addAddress("10.0.0.2", 32)
                    .addDnsServer("10.0.0.2")
                    .addRoute("10.0.0.2", 32)
                    .setBlocking(true)

                vpnInterface = builder.establish() ?: return@execute

                val fd = vpnInterface!!.fileDescriptor
                val inStream = FileInputStream(fd)
                val outStream = FileOutputStream(fd)
                val packetBuffer = ByteArray(4096)

                while (isRunning) {
                    val length = inStream.read(packetBuffer)
                    if (length > 0) {
                        handlePacket(packetBuffer, length, outStream)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS loop encountered error: ${e.message}")
            } finally {
                stopDnsService()
            }
        }
    }

    private fun startWatchdog() {
        watchdogThread?.interrupt()
        watchdogThread = Thread({
            Log.i(TAG, "Accessibility watchdog thread started")
            while (isRunning) {
                try {
                    Thread.sleep(15000)
                    if (!isRunning) break

                    if (!AccessibilityAutoHealer.isAccessibilityEnabled(this)) {
                        Log.w(TAG, "Watchdog detected Accessibility was inactive! Auto-healing via WRITE_SECURE_SETTINGS...")
                        AccessibilityAutoHealer.healAccessibility(this)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog loop error: ${e.message}")
                }
            }
            Log.i(TAG, "Accessibility watchdog thread stopped")
        }, "MindShield-Watchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, outStream: FileOutputStream) {
        val query = DnsPacketParser.parseDnsQuery(packet, length) ?: return
        val domain = query.domain
        val customRules = CustomRulesManager.getInstance(this)

        // 0. Whitelist Check: If domain is explicitly whitelisted by user, forward directly!
        if (customRules.isDomainWhitelisted(domain)) {
            val cachedPayload = dnsCache.get(domain)
            if (cachedPayload != null) {
                workerExecutor.execute {
                    sendCachedDnsResponse(query, cachedPayload, outStream)
                }
                return
            }
            workerExecutor.execute {
                forwardCleanQuery(query, packet, outStream)
            }
            return
        }

        // 1. Custom Blacklist Check: If user manually blocked this domain, block instantly (0.0.0.0)
        if (customRules.isDomainBlocked(domain)) {
            sendSynthesizedDnsResponse(DnsPacketParser.buildBlockedResponse(query, packet), outStream)
            return
        }

        // 2. 950,000+ Adult Domain Database Check: Block instantly (0.0.0.0)
        if (DomainDatabase.isDomainBlocked(domain)) {
            sendSynthesizedDnsResponse(DnsPacketParser.buildBlockedResponse(query, packet), outStream)
            return
        }

        // 3. Forced SafeSearch Check: Google, Bing, DuckDuckGo, YouTube Restricted Mode VIPs
        val safeSearchIp = SafeSearchEngine.getSafeSearchIpForDomain(domain)
        if (safeSearchIp != null) {
            val safeSearchPacket = DnsPacketParser.buildResolvedResponse(query, packet, safeSearchIp)
            sendSynthesizedDnsResponse(safeSearchPacket, outStream)
            return
        }

        // 4. Check 0ms In-Memory DNS Cache for clean resolved response
        val cachedPayload = dnsCache.get(domain)
        if (cachedPayload != null) {
            workerExecutor.execute {
                sendCachedDnsResponse(query, cachedPayload, outStream)
            }
            return
        }

        // 5. Clean Query -> Forward to High-Speed Upstream Anycast DNS with multi-server failover
        workerExecutor.execute {
            forwardCleanQuery(query, packet, outStream)
        }
    }

    private fun sendSynthesizedDnsResponse(responsePacket: ByteArray, outStream: FileOutputStream) {
        synchronized(outStream) {
            try {
                outStream.write(responsePacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendCachedDnsResponse(query: DnsQuery, cachedPayload: ByteArray, outStream: FileOutputStream) {
        try {
            val respLength = cachedPayload.size
            val udpLength = 8 + respLength
            val totalLength = 20 + udpLength
            val clientPacket = ByteArray(totalLength)

            // IPv4 Header
            clientPacket[0] = 0x45.toByte()
            clientPacket[2] = ((totalLength ushr 8) and 0xFF).toByte()
            clientPacket[3] = (totalLength and 0xFF).toByte()
            clientPacket[8] = 64.toByte() // TTL
            clientPacket[9] = 17.toByte() // Protocol UDP
            System.arraycopy(query.dstIp, 0, clientPacket, 12, 4) // Source IP
            System.arraycopy(query.srcIp, 0, clientPacket, 16, 4) // Dest IP

            // IP Checksum
            val checksum = computeChecksum(clientPacket, 0, 20)
            clientPacket[10] = ((checksum ushr 8) and 0xFF).toByte()
            clientPacket[11] = (checksum and 0xFF).toByte()

            // UDP Header
            clientPacket[20] = ((query.dstPort ushr 8) and 0xFF).toByte()
            clientPacket[21] = (query.dstPort and 0xFF).toByte()
            clientPacket[22] = ((query.srcPort ushr 8) and 0xFF).toByte()
            clientPacket[23] = (query.srcPort and 0xFF).toByte()
            clientPacket[24] = ((udpLength ushr 8) and 0xFF).toByte()
            clientPacket[25] = (udpLength and 0xFF).toByte()

            // DNS Payload
            System.arraycopy(cachedPayload, 0, clientPacket, 28, respLength)

            // Overwrite Transaction ID with current query's transaction ID
            clientPacket[28] = ((query.transactionId.toInt() ushr 8) and 0xFF).toByte()
            clientPacket[29] = (query.transactionId.toInt() and 0xFF).toByte()

            synchronized(outStream) {
                outStream.write(clientPacket)
            }
        } catch (ignored: Exception) {}
    }

    private fun forwardCleanQuery(query: DnsQuery, originalPacket: ByteArray, outStream: FileOutputStream) {
        val dnsPayload = ByteArray(query.dnsPayloadLength)
        System.arraycopy(originalPacket, query.dnsPayloadOffset, dnsPayload, 0, query.dnsPayloadLength)

        var resolvedPacket: ByteArray? = null
        var resolvedPayload: ByteArray? = null

        // Try primary safe anycast servers first, fall back seamlessly
        for (upstreamIp in UPSTREAM_DNS_SERVERS) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket) // Protect socket from looping into our own VPN!
                socket.soTimeout = DNS_TIMEOUT_MS

                val upstreamAddr = InetAddress.getByName(upstreamIp)
                val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddr, UPSTREAM_DNS_PORT)
                socket.send(sendPacket)

                val respBuffer = ByteArray(2048)
                val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
                socket.receive(recvPacket)

                val respLength = recvPacket.length
                if (respLength >= 12) {
                    val udpLength = 8 + respLength
                    val totalLength = 20 + udpLength
                    val clientPacket = ByteArray(totalLength)

                    // IPv4 Header
                    clientPacket[0] = 0x45.toByte()
                    clientPacket[2] = ((totalLength ushr 8) and 0xFF).toByte()
                    clientPacket[3] = (totalLength and 0xFF).toByte()
                    clientPacket[8] = 64.toByte() // TTL
                    clientPacket[9] = 17.toByte() // Protocol UDP
                    System.arraycopy(query.dstIp, 0, clientPacket, 12, 4) // Source IP
                    System.arraycopy(query.srcIp, 0, clientPacket, 16, 4) // Dest IP

                    // IP Checksum
                    val checksum = computeChecksum(clientPacket, 0, 20)
                    clientPacket[10] = ((checksum ushr 8) and 0xFF).toByte()
                    clientPacket[11] = (checksum and 0xFF).toByte()

                    // UDP Header
                    clientPacket[20] = ((query.dstPort ushr 8) and 0xFF).toByte()
                    clientPacket[21] = (query.dstPort and 0xFF).toByte()
                    clientPacket[22] = ((query.srcPort ushr 8) and 0xFF).toByte()
                    clientPacket[23] = (query.srcPort and 0xFF).toByte()
                    clientPacket[24] = ((udpLength ushr 8) and 0xFF).toByte()
                    clientPacket[25] = (udpLength and 0xFF).toByte()

                    // DNS Payload
                    System.arraycopy(respBuffer, 0, clientPacket, 28, respLength)

                    resolvedPacket = clientPacket
                    resolvedPayload = respBuffer.copyOf(respLength)
                    break // Successfully resolved!
                }
            } catch (e: Exception) {
                // Timeout or network drop on this upstream -> try next server
                continue
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }

        if (resolvedPacket != null && resolvedPayload != null) {
            // Cache resolved DNS response for instantaneous 0ms future lookups
            dnsCache.put(query.domain, resolvedPayload)
            synchronized(outStream) {
                try {
                    outStream.write(resolvedPacket)
                } catch (ignored: Exception) {}
            }
        } else {
            // IMPORTANT: NEVER synthesize 0.0.0.0 on network timeout!
            // Dropping the packet allows the OS and apps to retransmit cleanly without poisoning DNS cache
            Log.w(TAG, "DNS lookup for ${query.domain} timed out across all upstreams; dropped.")
        }
    }

    private fun computeChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = buf[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) buf[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }
        while (sum ushr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun stopDnsService() {
        isRunning = false
        watchdogThread?.interrupt()
        watchdogThread = null
        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDnsService()
    }
}

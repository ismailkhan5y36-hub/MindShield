package com.mindshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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

        // CleanBrowsing Family Filter Safe DNS (Filters adult domains & malicious sites)
        private const val UPSTREAM_SAFE_DNS = "185.228.168.168"
        private const val UPSTREAM_DNS_PORT = 53
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var isRunning = false
    private val workerExecutor = Executors.newFixedThreadPool(2)

    override fun onCreate() {
        super.onCreate()
        DomainDatabase.init(this)
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

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startDnsService() {
        if (isRunning) return
        isRunning = true

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
                e.printStackTrace()
            } finally {
                stopDnsService()
            }
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, outStream: FileOutputStream) {
        val query = DnsPacketParser.parseDnsQuery(packet, length) ?: return
        val domain = query.domain
        val customRules = CustomRulesManager.getInstance(this)

        // 0. Whitelist Check: If domain is explicitly whitelisted by user, forward directly!
        if (customRules.isDomainWhitelisted(domain)) {
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

        // 4. Clean Query -> Forward to Upstream CleanBrowsing Family Safe DNS (185.228.168.168)
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


    private fun forwardCleanQuery(query: DnsQuery, originalPacket: ByteArray, outStream: FileOutputStream) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket) // Protect socket from looping into our own VPN!
            socket.soTimeout = 2500

            val dnsPayload = ByteArray(query.dnsPayloadLength)
            System.arraycopy(originalPacket, query.dnsPayloadOffset, dnsPayload, 0, query.dnsPayloadLength)

            val upstreamAddr = InetAddress.getByName(UPSTREAM_SAFE_DNS)
            val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddr, UPSTREAM_DNS_PORT)
            socket.send(sendPacket)

            val respBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
            socket.receive(recvPacket)

            val respLength = recvPacket.length
            if (respLength > 0) {
                // Synthesize complete IP+UDP packet wrapping the upstream DNS response
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

                synchronized(outStream) {
                    outStream.write(clientPacket)
                }
            }
        } catch (e: Exception) {
            // Fallback: If upstream times out or fails, synthesize 0.0.0.0
            try {
                val fallback = DnsPacketParser.buildBlockedResponse(query, originalPacket)
                synchronized(outStream) {
                    outStream.write(fallback)
                }
            } catch (ignored: Exception) {}
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
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

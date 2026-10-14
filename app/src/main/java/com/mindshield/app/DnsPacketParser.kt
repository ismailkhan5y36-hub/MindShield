package com.mindshield.app

import java.nio.ByteBuffer

data class DnsQuery(
    val domain: String,
    val transactionId: Short,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayloadOffset: Int,
    val dnsPayloadLength: Int
)

object DnsPacketParser {

    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // 20 byte IPv4 + 8 byte UDP

        val buffer = ByteBuffer.wrap(packet, 0, length)

        // 1. IP Header
        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val version = versionAndIhl ushr 4
        if (version != 4) return null // Only IPv4 for local loopback DNS

        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl + 8) return null

        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return null // 17 = UDP

        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        System.arraycopy(packet, 12, srcIp, 0, 4)
        System.arraycopy(packet, 16, dstIp, 0, 4)

        // 2. UDP Header
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != 53) return null // DNS query port

        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        val dnsOffset = ihl + 8
        val dnsLength = udpLength - 8
        if (dnsOffset + dnsLength > length || dnsLength < 12) return null

        // 3. DNS Header
        val txId = buffer.getShort(dnsOffset)
        val flags = buffer.getShort(dnsOffset + 2).toInt() and 0xFFFF
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val qdCount = buffer.getShort(dnsOffset + 4).toInt() and 0xFFFF
        if (qdCount < 1) return null

        // 4. Parse Question QNAME
        var pos = dnsOffset + 12
        val domainParts = mutableListOf<String>()
        val maxPos = dnsOffset + dnsLength

        while (pos < maxPos) {
            val labelLen = packet[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if (pos + labelLen > maxPos) return null

            val part = String(packet, pos, labelLen, Charsets.US_ASCII)
            domainParts.add(part)
            pos += labelLen
        }

        if (domainParts.isEmpty()) return null
        val fullDomain = domainParts.joinToString(".").lowercase()

        return DnsQuery(
            domain = fullDomain,
            transactionId = txId,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLength = dnsLength
        )
    }

    /**
     * Builds a synthesized DNS response returning 0.0.0.0 (Blocked)
     */
    fun buildBlockedResponse(query: DnsQuery, originalPacket: ByteArray): ByteArray {
        val qOffset = query.dnsPayloadOffset
        val qLength = query.dnsPayloadLength

        // Find end of question section in original packet
        var qEnd = qOffset + 12
        while (qEnd < qOffset + qLength) {
            val len = originalPacket[qEnd].toInt() and 0xFF
            qEnd++
            if (len == 0) break
            qEnd += len
        }
        qEnd += 4 // QTYPE (2) + QCLASS (2)

        val questionSectionLength = qEnd - (qOffset + 12)

        // DNS Answer for 0.0.0.0:
        // Name pointer (2) + Type A (2) + Class IN (2) + TTL (4) + RDLENGTH (2) + RDATA (4) = 16 bytes
        val dnsResponseLength = 12 + questionSectionLength + 16
        val udpLength = 8 + dnsResponseLength
        val totalLength = 20 + udpLength

        val resp = ByteArray(totalLength)
        val buf = ByteBuffer.wrap(resp)

        // 1. IPv4 Header (20 bytes)
        buf.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        buf.put(0x00.toByte()) // DSCP / ECN
        buf.putShort(totalLength.toShort()) // Total length
        buf.putShort(0.toShort()) // Identification
        buf.putShort(0x4000.toShort()) // Flags: Don't Fragment
        buf.put(64.toByte()) // TTL
        buf.put(17.toByte()) // Protocol: UDP
        buf.putShort(0.toShort()) // Checksum (computed later)
        buf.put(query.dstIp) // Source IP = original Dest IP
        buf.put(query.srcIp) // Dest IP = original Source IP

        // Compute IP checksum
        val ipChecksum = computeIpChecksum(resp, 0, 20)
        resp[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        resp[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header (8 bytes)
        buf.position(20)
        buf.putShort(query.dstPort.toShort()) // Source Port (53)
        buf.putShort(query.srcPort.toShort()) // Dest Port
        buf.putShort(udpLength.toShort()) // UDP Length
        buf.putShort(0.toShort()) // UDP Checksum (optional 0 in IPv4)

        // 3. DNS Header (12 bytes)
        buf.putShort(query.transactionId)
        buf.putShort(0x8180.toShort()) // Response, No Error, Recursion Available
        buf.putShort(1.toShort()) // Questions: 1
        buf.putShort(1.toShort()) // Answer RRs: 1
        buf.putShort(0.toShort()) // Authority RRs: 0
        buf.putShort(0.toShort()) // Additional RRs: 0

        // 4. Copy Question Section
        System.arraycopy(originalPacket, qOffset + 12, resp, 20 + 8 + 12, questionSectionLength)
        buf.position(20 + 8 + 12 + questionSectionLength)

        // 5. Answer Section (Type A, 0.0.0.0)
        buf.putShort(0xC00C.toShort()) // Pointer to Question QNAME (offset 12 in DNS header)
        buf.putShort(1.toShort()) // Type: A
        buf.putShort(1.toShort()) // Class: IN
        buf.putInt(300) // TTL: 300 seconds
        buf.putShort(4.toShort()) // Data length: 4 bytes
        buf.put(byteArrayOf(0, 0, 0, 0)) // IP: 0.0.0.0

        return resp
    }

    private fun computeIpChecksum(buf: ByteArray, offset: Int, length: Int): Int {
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
}

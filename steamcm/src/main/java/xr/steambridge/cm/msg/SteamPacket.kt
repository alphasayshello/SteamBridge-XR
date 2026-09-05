package xr.steambridge.cm.msg

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MsgHdrProtoBuf framing — the inner packet shared by both transports.
 *
 * Layout (little-endian throughout), matching SteamKit2 MsgHdrProtoBuf:
 *   [ u32 rawEMsg        ]   EMsg with PROTO_MASK set
 *   [ u32 headerLength   ]   byte length of the CMsgProtoBufHeader that follows
 *   [ CMsgProtoBufHeader ]   protobuf: job ids, target_job_name, steamid, session_id, eresult...
 *   [ body               ]   the message protobuf
 *
 * Transport wrapping is added elsewhere: raw TCP prefixes [u32 len]["VT01"]; WebSocket sends one
 * binary frame per packet with no prefix (TLS handles framing). Header/body move as raw bytes.
 */
data class SteamPacket(
    val eMsg: Int,          // logical EMsg (PROTO_MASK already stripped)
    val headerBytes: ByteArray,
    val bodyBytes: ByteArray,
) {
    /** Serialize to the inner packet bytes (no transport wrapper). */
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream(8 + headerBytes.size + bodyBytes.size)
        val dwords = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        dwords.putInt(EMsg.withProto(eMsg))
        dwords.putInt(headerBytes.size)
        out.write(dwords.array())
        out.write(headerBytes)
        out.write(bodyBytes)
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SteamPacket) return false
        return eMsg == other.eMsg &&
            headerBytes.contentEquals(other.headerBytes) &&
            bodyBytes.contentEquals(other.bodyBytes)
    }

    override fun hashCode(): Int {
        var result = eMsg
        result = 31 * result + headerBytes.contentHashCode()
        result = 31 * result + bodyBytes.contentHashCode()
        return result
    }

    companion object {
        const val VT01_MAGIC: Int = 0x31305456 // "VT01" little-endian

        /**
         * Parse an inner packet (no transport wrapper).
         *
         * @throws IllegalArgumentException on a non-protobuf message or a truncated buffer — every
         *         message SteamBridge-XR handles is protobuf-framed.
         */
        fun decode(data: ByteArray): SteamPacket {
            require(data.size >= 8) { "packet too short: ${data.size}B" }
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val rawEMsg = buf.int
            require(EMsg.isProto(rawEMsg)) {
                "non-protobuf EMsg ${EMsg.strip(rawEMsg)} unsupported"
            }
            val headerLen = buf.int
            require(headerLen in 0..(data.size - 8)) { "bad header length $headerLen" }
            val header = ByteArray(headerLen)
            buf.get(header)
            val body = ByteArray(buf.remaining())
            buf.get(body)
            return SteamPacket(EMsg.strip(rawEMsg), header, body)
        }

        /** Wrap an inner packet in the raw-TCP frame: [len][VT01][packet]. WebSocket does not use this. */
        fun wrapTcp(packet: ByteArray): ByteArray {
            val out = ByteBuffer.allocate(8 + packet.size).order(ByteOrder.LITTLE_ENDIAN)
            out.putInt(packet.size)
            out.putInt(VT01_MAGIC)
            out.put(packet)
            return out.array()
        }
    }
}

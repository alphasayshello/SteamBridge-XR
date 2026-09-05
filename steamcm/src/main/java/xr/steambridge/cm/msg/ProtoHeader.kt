package xr.steambridge.cm.msg

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The subset of CMsgProtoBufHeader (steammessages_base.proto) the router needs, field numbers from Valve:
 *   1  steamid          fixed64
 *   2  client_sessionid int32  (varint)
 *   3  routing_appid    uint32 (varint)
 *   10 jobid_source     fixed64 (default 0xFFFFFFFFFFFFFFFF)
 *   11 jobid_target     fixed64 (default 0xFFFFFFFFFFFFFFFF)
 *   12 target_job_name  string  (unified-RPC method, e.g. "Authentication.PollAuthSessionStatus#1")
 *   13 eresult          int32  (varint, default 2 = Fail)
 *   14 error_message    string
 *   32 realm            uint32 (varint; must be 1 on pre-logon NonAuthed service calls)
 */
data class ProtoHeader(
    var steamId: Long = 0L,
    var clientSessionId: Int = 0,
    var routingAppId: Int = 0,
    var jobIdSource: Long = NO_JOB,
    var jobIdTarget: Long = NO_JOB,
    var targetJobName: String? = null,
    var eResult: Int = 2, // k_EResultFail default; always check for OK(1) explicitly
    var errorMessage: String? = null,
    var realm: Int = 0, // field 32; the CM requires realm=1 on pre-logon NonAuthed service calls
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream(64)
        if (steamId != 0L) writeFixed64(out, 1, steamId)
        if (clientSessionId != 0) writeVarintField(out, 2, clientSessionId.toLong())
        if (routingAppId != 0) writeVarintField(out, 3, routingAppId.toLong() and 0xFFFFFFFFL)
        if (jobIdSource != NO_JOB) writeFixed64(out, 10, jobIdSource)
        if (jobIdTarget != NO_JOB) writeFixed64(out, 11, jobIdTarget)
        targetJobName?.let { writeString(out, 12, it) }
        if (realm != 0) writeVarintField(out, 32, realm.toLong())
        return out.toByteArray()
    }

    companion object {
        const val NO_JOB: Long = -1L // 0xFFFFFFFFFFFFFFFF

        fun decode(bytes: ByteArray): ProtoHeader {
            val h = ProtoHeader()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            while (buf.hasRemaining()) {
                val tag = readVarint(buf)
                val field = (tag ushr 3).toInt()
                when ((tag and 0x7L).toInt()) {
                    0 -> { // varint
                        val v = readVarint(buf)
                        when (field) {
                            2 -> h.clientSessionId = v.toInt()
                            3 -> h.routingAppId = v.toInt()
                            13 -> h.eResult = v.toInt()
                            32 -> h.realm = v.toInt()
                        }
                    }
                    1 -> { // fixed64
                        val v = buf.long
                        when (field) {
                            1 -> h.steamId = v
                            10 -> h.jobIdSource = v
                            11 -> h.jobIdTarget = v
                        }
                    }
                    2 -> { // length-delimited
                        val len = readVarint(buf).toInt()
                        val slice = ByteArray(len)
                        buf.get(slice)
                        when (field) {
                            12 -> h.targetJobName = String(slice, Charsets.UTF_8)
                            14 -> h.errorMessage = String(slice, Charsets.UTF_8)
                        }
                    }
                    5 -> buf.int // fixed32, skip
                    else -> throw IllegalArgumentException("unknown wire type in header field $field")
                }
            }
            return h
        }

        private fun writeFixed64(out: ByteArrayOutputStream, field: Int, value: Long) {
            writeTag(out, field, 1)
            val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
            out.write(b)
        }

        private fun writeVarintField(out: ByteArrayOutputStream, field: Int, value: Long) {
            writeTag(out, field, 0)
            writeVarint(out, value)
        }

        private fun writeString(out: ByteArrayOutputStream, field: Int, value: String) {
            writeTag(out, field, 2)
            val b = value.toByteArray(Charsets.UTF_8)
            writeVarint(out, b.size.toLong())
            out.write(b)
        }

        private fun writeTag(out: ByteArrayOutputStream, field: Int, wireType: Int) {
            writeVarint(out, ((field.toLong()) shl 3) or wireType.toLong())
        }

        private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
            var v = value
            while (true) {
                val b = (v and 0x7F).toInt()
                v = v ushr 7
                if (v != 0L) {
                    out.write(b or 0x80)
                } else {
                    out.write(b)
                    break
                }
            }
        }

        private fun readVarint(buf: ByteBuffer): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf.get().toInt() and 0xFF
                result = result or ((b.toLong() and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }
    }
}

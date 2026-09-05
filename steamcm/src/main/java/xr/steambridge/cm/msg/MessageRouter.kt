package xr.steambridge.cm.msg

import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

/**
 * Routes decoded CM packets: correlates request/response by job id, unpacks Multi envelopes, and
 * exposes a coroutine-friendly request/response call over the transport.
 *
 * Job-id correlation: outbound messages get a monotonically increasing jobid_source in the header;
 * the CM echoes it back as jobid_target on the reply. Unified RPCs (ServiceMethodResponse) and classic
 * job replies both use this. Non-job messages (logon response, heartbeats, encrypted-app-ticket
 * response) are delivered to registered [onMessage] handlers keyed by EMsg.
 */
class MessageRouter(
    private val send: (ByteArray) -> Boolean,
    private val onLog: (String) -> Unit = {},
) {
    private val jobCounter = AtomicLong(1L)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<SteamPacket>>()
    private val handlers = ConcurrentHashMap<Int, (SteamPacket) -> Unit>()

    @Volatile var steamId: Long = 0L
    @Volatile var sessionId: Int = 0

    /** Register a handler for an unsolicited/broadcast EMsg (e.g. ClientLogOnResponse). */
    fun on(eMsg: Int, handler: (SteamPacket) -> Unit) {
        handlers[eMsg] = handler
    }

    /**
     * Send a unified-service RPC and await its ServiceMethodResponse.
     *
     * All of SteamBridge-XR's RPCs are IAuthenticationService calls made before logon, so they go out
     * as ServiceMethodCallFromClientNonAuthed (9804); the CM replies as ServiceMethodResponse (147),
     * correlated back by job id.
     *
     * @param methodName e.g. "Authentication.GetPasswordRSAPublicKey#1"
     * @param body serialized request protobuf bytes
     */
    suspend fun serviceCall(methodName: String, body: ByteArray): SteamPacket {
        val jobId = jobCounter.getAndIncrement()
        val header = ProtoHeader(
            steamId = steamId,
            clientSessionId = sessionId,
            jobIdSource = jobId,
            targetJobName = methodName,
            realm = 1, // the CM requires realm=1 to route a pre-logon NonAuthed service call
        )
        val eMsg = EMsg.ServiceMethodCallFromClientNonAuthed
        val deferred = CompletableDeferred<SteamPacket>()
        pending[jobId] = deferred
        val packet = SteamPacket(eMsg, header.encode(), body).encode()
        val sent = send(packet)
        onLog("-> $methodName job=$jobId emsg=$eMsg bytes=${packet.size} sent=$sent")
        if (!sent) {
            pending.remove(jobId)
            throw IllegalStateException("send failed for $methodName")
        }
        return deferred.await()
    }

    /** Send a classic protobuf message (logon, heartbeat, ticket request) — fire, replies via [on]. */
    fun sendMessage(eMsg: Int, body: ByteArray, withJob: Boolean = false): Long {
        val jobId = if (withJob) jobCounter.getAndIncrement() else ProtoHeader.NO_JOB
        val header = ProtoHeader(
            steamId = steamId,
            clientSessionId = sessionId,
            jobIdSource = jobId,
        )
        send(SteamPacket(eMsg, header.encode(), body).encode())
        return jobId
    }

    /** Feed a raw inbound frame from the transport. Handles Multi expansion + dispatch. */
    fun onFrame(frame: ByteArray) {
        onLog("<- frame ${frame.size}B")
        val packet = try {
            SteamPacket.decode(frame)
        } catch (e: Exception) {
            onLog("drop malformed frame: ${e.message}")
            return
        }
        onLog("<- EMsg=${packet.eMsg} header=${packet.headerBytes.size}B body=${packet.bodyBytes.size}B")
        dispatch(packet)
    }

    private fun dispatch(packet: SteamPacket) {
        if (packet.eMsg == EMsg.Multi) {
            expandMulti(packet.bodyBytes)
            return
        }
        val header = try {
            ProtoHeader.decode(packet.headerBytes)
        } catch (e: Exception) {
            onLog("drop packet EMsg=${packet.eMsg}: bad header ${e.message}")
            return
        }
        // Adopt session/steamid from the first message that carries them (logon response).
        if (header.clientSessionId != 0) sessionId = header.clientSessionId
        if (header.steamId != 0L) steamId = header.steamId

        val target = header.jobIdTarget
        if (target != ProtoHeader.NO_JOB && pending.containsKey(target)) {
            pending.remove(target)?.complete(packet)
            return
        }
        handlers[packet.eMsg]?.invoke(packet)
            ?: onLog("no handler for EMsg=${packet.eMsg} (jobTarget=$target)")
    }

    /**
     * CMsgMulti body: field 1 size_unzipped (varint), field 2 message_body (bytes).
     * If size_unzipped > 0 the body is gzip'd; otherwise it is a raw run of [u32 len][packet].
     */
    private fun expandMulti(multiBody: ByteArray) {
        var sizeUnzipped = 0
        var payload = ByteArray(0)
        val buf = ByteBuffer.wrap(multiBody).order(ByteOrder.LITTLE_ENDIAN)
        while (buf.hasRemaining()) {
            val tag = readVarint(buf)
            val field = (tag ushr 3).toInt()
            when ((tag and 0x7L).toInt()) {
                0 -> {
                    val v = readVarint(buf).toInt()
                    if (field == 1) sizeUnzipped = v
                }
                2 -> {
                    val len = readVarint(buf).toInt()
                    val slice = ByteArray(len)
                    buf.get(slice)
                    if (field == 2) payload = slice
                }
                else -> return
            }
        }
        val data = if (sizeUnzipped > 0) gunzip(payload) else payload
        val inner = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (inner.remaining() >= 4) {
            val len = inner.int
            if (len < 0 || len > inner.remaining()) break
            val sub = ByteArray(len)
            inner.get(sub)
            onFrame(sub)
        }
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

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

    fun failAllPending(reason: String) {
        pending.values.forEach { it.completeExceptionally(IllegalStateException(reason)) }
        pending.clear()
    }
}

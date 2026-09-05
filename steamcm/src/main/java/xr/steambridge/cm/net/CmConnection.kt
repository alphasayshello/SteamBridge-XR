package xr.steambridge.cm.net

import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket transport to a Steam CM.
 *
 * WebSocket-first (Report 2 vs 4 resolution): wss rides TLS, so the ChannelEncrypt RSA+AES handshake
 * that raw TCP requires is skipped entirely — strictly less crypto work. Each binary frame is exactly
 * one inner SteamPacket ([SteamPacket.encode] bytes); no length prefix, no VT01 magic (that wrapper is
 * raw-TCP only).
 *
 * This class is transport only: it moves opaque packet bytes. Framing/routing is [MessageRouter]'s job.
 */
class CmConnection(
    private val onPacket: (ByteArray) -> Unit,
    private val onClosed: (reason: String) -> Unit = {},
    private val onLog: (String) -> Unit = {},
) {
    private val client = OkHttpClient.Builder()
        // No OkHttp WS ping: Steam's CM does not reliably pong client ping frames, so OkHttp's
        // pingInterval tears the socket down mid-session ("sent ping but didn't receive pong"). Steam
        // keeps the connection alive itself, and our own poll/heartbeat traffic holds NAT open.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
        .build()

    @Volatile private var ws: WebSocket? = null

    /** Opens the socket. Completes when the WebSocket handshake succeeds, or fails on connect error. */
    suspend fun connect(wsUrl: String) {
        val opened = CompletableDeferred<Unit>()
        val req = Request.Builder().url(wsUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onLog("CM connected: $wsUrl (ext=${response.header("Sec-WebSocket-Extensions") ?: "none"})")
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onPacket(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "unknown"
                onLog("CM failure: $msg")
                if (!opened.isCompleted) opened.completeExceptionally(t)
                onClosed(msg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onLog("CM closed: $code $reason")
                onClosed(reason)
            }
        })
        opened.await()
    }

    /** Send one inner packet (already [SteamPacket.encode]'d). */
    fun send(packet: ByteArray): Boolean {
        val w = ws ?: return false
        return w.send(packet.toByteString())
    }

    fun close() {
        ws?.close(1000, "bye")
        ws = null
    }
}

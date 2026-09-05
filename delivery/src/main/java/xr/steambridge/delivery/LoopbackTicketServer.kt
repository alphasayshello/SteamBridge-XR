package xr.steambridge.delivery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP server bound to 127.0.0.1:[port] that speaks the steamshim wire contract.
 *
 * Chosen over an AIDL bound service because the ticket consumer is native C++ inside
 * com.vankrupt.pavlov: steamshim.cpp `relay_fetch()` already does inet_addr()+connect() with a 5s
 * timeout (steamshim.cpp:186-189). Pointing files/relay.txt at "127.0.0.1:<port>" means the game side
 * needs zero change — it connects here instead of to a PC on the LAN. Both apps need only INTERNET
 * (loopback counts).
 *
 * On each connect we resolve a fresh-or-cached ticket, format it via [WireFormat], write the whole
 * block, then close — exactly the shim's expectation (read until EOF into an 8192 buffer,
 * steamshim.cpp:191-193).
 */
class LoopbackTicketServer(
    private val cache: TicketCache,
    private val port: Int = 48010,
    private val onLog: (String) -> Unit = {},
    /** Perform the CM round-trip and return a fresh entry, or null on failure. */
    private val mint: suspend () -> TicketCache.Entry?,
) {
    @Volatile private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        val loopback = InetAddress.getByName("127.0.0.1")
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(loopback, port), 8)
        serverSocket = ss
        onLog("loopback listening on 127.0.0.1:$port")
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                val client = try {
                    ss.accept()
                } catch (e: Exception) {
                    if (isRunning) onLog("accept error: ${e.message}")
                    break
                }
                // Serve inline: connections are rare (one per join attempt) and the cache collapses
                // bursts, so a per-connection coroutine buys nothing and a serial loop keeps mint
                // ordering trivial.
                launch(Dispatchers.IO) { serveOne(client) }
            }
        }
    }

    private suspend fun serveOne(client: Socket) {
        client.use { sock ->
            val ep = sock.remoteSocketAddress
            try {
                val entry = cache.get(mint)
                if (entry == null) {
                    sock.getOutputStream().apply {
                        write("ERROR: mint failed\n".toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                    onLog("served $ep -> ERROR (no ticket)")
                    return
                }
                val block = WireFormat.build(entry.steamId64, entry.personaName, entry.ticket)
                withContext(Dispatchers.IO) {
                    sock.getOutputStream().apply {
                        write(block)
                        flush()
                    }
                }
                onLog("served $ep -> ${block.size}B (ticket ${entry.ticket.size}B, id ${entry.steamId64})")
            } catch (e: Exception) {
                onLog("serve error $ep: ${e.message}")
            }
        }
    }

    fun stop() {
        val ss = serverSocket
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        try {
            ss?.close()
        } catch (_: Exception) {
        }
        onLog("loopback stopped")
    }
}

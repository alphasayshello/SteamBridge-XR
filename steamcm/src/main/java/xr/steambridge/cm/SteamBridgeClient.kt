package xr.steambridge.cm

import kotlinx.coroutines.CoroutineScope
import xr.steambridge.cm.auth.AuthSession
import xr.steambridge.cm.logon.CmLogon
import xr.steambridge.cm.msg.ClientHello
import xr.steambridge.cm.msg.DeviceDetails
import xr.steambridge.cm.msg.EMsg
import xr.steambridge.cm.msg.MachineId
import xr.steambridge.cm.msg.MessageRouter
import xr.steambridge.cm.net.CmConnection
import xr.steambridge.cm.net.CmServerList
import xr.steambridge.cm.ticket.AppTicketService

/**
 * Top-level driver: connect to a CM, authenticate (or reuse a refresh_token), log on, and mint the
 * encrypted app ticket. This is the whole Phase 0→4 chain in one object.
 *
 * Two entry points:
 *   - [authenticate] runs the interactive auth lane (credentials or QR); its [AuthSession.state] flow
 *     drives the UI, and it returns the [GuardState.Done] carrying the refresh_token + guard_data to
 *     persist.
 *   - [mintWithToken] is the silent path: given a stored refresh_token, connect→logon→ticket with no
 *     user interaction. This is what the loopback server calls on each cold cache.
 */
class SteamBridgeClient(
    private val scope: CoroutineScope,
    private val machineSeed: String,
    private val deviceName: String = "SteamBridge-XR (Quest)",
    private val onLog: (String) -> Unit = {},
) {
    private val serverList = CmServerList(onLog = onLog)
    private val machineId: ByteArray = MachineId.build(machineSeed)
    private val device = DeviceDetails(friendlyName = deviceName, machineId = machineId)

    private var connection: CmConnection? = null
    private var router: MessageRouter? = null

    data class MintResult(val steamId64: ULong, val personaName: String, val ticket: ByteArray)

    /** Open a CM connection and wire the router. Tries endpoints until one accepts. */
    private suspend fun connect(): MessageRouter {
        val endpoints = serverList.fetch()
        var lastErr: Exception? = null
        for (ep in endpoints) {
            // router and connection are mutually referential: the router sends through the connection,
            // the connection feeds inbound frames to the router. A captured var closes the loop —
            // the send lambda reads `conn` at call time, after it is assigned below.
            var conn: CmConnection? = null
            val bound = MessageRouter(send = { conn?.send(it) ?: false }, onLog = onLog)
            conn = CmConnection(
                onPacket = { bound.onFrame(it) },
                onClosed = { reason -> bound.failAllPending("CM closed: $reason") },
                onLog = onLog,
            )
            try {
                conn.connect(serverList.toWsUrl(ep))
                // The CM will not route ANY client message until it receives a ClientHello first.
                // Fire-and-forget (no reply); WebSocket preserves order so this precedes every later send.
                bound.sendMessage(EMsg.ClientHello, ClientHello.request())
                onLog("sent ClientHello")
                this.connection = conn
                this.router = bound
                onLog("connected to CM $ep")
                return bound
            } catch (e: Exception) {
                lastErr = e
                conn.close()
                onLog("CM $ep failed: ${e.message}")
            }
        }
        throw IllegalStateException("no CM reachable", lastErr)
    }

    /**
     * Open an interactive auth session over a live CM connection, WITHOUT starting a login yet.
     *
     * The caller observes [AuthSession.state] for guard prompts / QR, launches
     * [AuthSession.loginWithCredentials] or [AuthSession.loginWithQr] in its own coroutine, and answers
     * prompts via [AuthSession.submitGuardCode] mid-flight. Returning the session before the login runs
     * is what lets the UI catch the guard state while the poll loop is still suspended.
     */
    suspend fun openAuthSession(): AuthSession {
        val r = router ?: connect()
        return AuthSession(r, device, onLog)
    }

    /**
     * Silent mint: connect (if needed), log on with the stored refresh_token, request the ticket.
     * @throws IllegalStateException on logon failure (token expired -> re-auth) or ticket failure.
     */
    suspend fun mintWithToken(accountName: String, refreshToken: String): MintResult {
        val r = router ?: connect()
        val logon = CmLogon(r, onLog)
        val res = logon.logon(scope, accountName, refreshToken, machineId)
        if (!res.ok) {
            throw IllegalStateException("logon failed eresult=${res.eResult} (token may be expired)")
        }
        val ticket = AppTicketService(r, onLog).request()
        // Persona-name fetch over the CM is a later nicety; account name is a valid, non-empty NAME
        // that satisfies the shim's null-persona guard.
        return MintResult(
            steamId64 = res.steamId.toULong(),
            personaName = accountName,
            ticket = ticket.bytes,
        )
    }

    fun close() {
        connection?.close()
        connection = null
        router = null
    }
}

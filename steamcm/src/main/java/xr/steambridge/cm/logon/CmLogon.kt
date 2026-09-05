package xr.steambridge.cm.logon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xr.steambridge.cm.EResult
import xr.steambridge.cm.msg.ClientHeartBeat
import xr.steambridge.cm.msg.ClientLogon
import xr.steambridge.cm.msg.EMsg
import xr.steambridge.cm.msg.MessageRouter

/**
 * Phase 3: log onto the CM with the refresh_token, then keep the session alive with heartbeats.
 * After a successful logon the same connection can request the encrypted app ticket.
 */
class CmLogon(
    private val router: MessageRouter,
    private val onLog: (String) -> Unit = {},
) {
    private var heartbeatJob: Job? = null

    data class Result(val eResult: Int, val steamId: Long) {
        val ok: Boolean get() = eResult == EResult.OK
    }

    /**
     * @param machineId stable machine_id blob.
     * @return the logon result; on OK the router's steamId/sessionId are populated and heartbeats run.
     */
    suspend fun logon(
        scope: CoroutineScope,
        accountName: String,
        refreshToken: String,
        machineId: ByteArray,
        cellId: Int = 0,
    ): Result {
        val response = CompletableDeferred<ClientLogon.Response>()
        router.on(EMsg.ClientLogOnResponse) { packet ->
            if (!response.isCompleted) response.complete(ClientLogon.parse(packet.bodyBytes))
        }
        // The header steamid must be a blank individual id for a token logon.
        router.steamId = ClientLogon.BLANK_INDIVIDUAL_STEAMID
        router.sessionId = 0
        router.sendMessage(
            EMsg.ClientLogon,
            ClientLogon.request(accountName, refreshToken, machineId, cellId),
        )
        val resp = response.await()
        if (resp.eResult == EResult.OK) {
            onLog("logon OK, steamId=${resp.steamId}, heartbeat=${resp.heartbeatSeconds}s")
            startHeartbeat(scope, resp.heartbeatSeconds)
        } else {
            onLog("logon FAILED eresult=${resp.eResult}")
        }
        return Result(resp.eResult, resp.steamId)
    }

    private fun startHeartbeat(scope: CoroutineScope, seconds: Int) {
        heartbeatJob?.cancel()
        val periodMs = (if (seconds > 0) seconds else 9) * 1000L
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(periodMs)
                router.sendMessage(EMsg.ClientHeartBeat, ClientHeartBeat.request())
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}

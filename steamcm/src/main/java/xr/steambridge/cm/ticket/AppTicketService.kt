package xr.steambridge.cm.ticket

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import xr.steambridge.cm.EResult
import xr.steambridge.cm.msg.EMsg
import xr.steambridge.cm.msg.MessageRouter
import xr.steambridge.cm.msg.RequestEncryptedAppTicket

/**
 * Phase 4: request the encrypted app ticket for [PAVLOV_SHACK_APPID] over a logged-on connection.
 *
 * The response's encrypted_app_ticket (field 3) is the serialized EncryptedAppTicket message, byte-
 * identical to ISteamUser::GetEncryptedAppTicket — exactly what the PC minter served and what eosshim
 * forwards as EOS STEAM_APP_TICKET(1).
 */
class AppTicketService(
    private val router: MessageRouter,
    private val onLog: (String) -> Unit = {},
) {
    data class Ticket(val appId: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ticket) return false
            return appId == other.appId && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = 31 * appId + bytes.contentHashCode()
    }

    /**
     * @return the serialized EncryptedAppTicket bytes.
     * @throws IllegalStateException if Steam returns a non-OK eresult (e.g. LimitExceeded 25) or times out.
     */
    suspend fun request(appId: Int = PAVLOV_SHACK_APPID, timeoutMs: Long = 20_000L): Ticket {
        val response = CompletableDeferred<RequestEncryptedAppTicket.Response>()
        router.on(EMsg.ClientRequestEncryptedAppTicketResponse) { packet ->
            if (!response.isCompleted) response.complete(RequestEncryptedAppTicket.parse(packet.bodyBytes))
        }
        router.sendMessage(
            EMsg.ClientRequestEncryptedAppTicket,
            RequestEncryptedAppTicket.request(appId),
        )
        val resp = withTimeout(timeoutMs) { response.await() }
        if (resp.eResult != EResult.OK) {
            val note = if (resp.eResult == EResult.LimitExceeded) " (rate-limited ~1/min)" else ""
            throw IllegalStateException("RequestEncryptedAppTicket eresult=${resp.eResult}$note")
        }
        if (resp.encryptedAppTicket.isEmpty()) {
            throw IllegalStateException("RequestEncryptedAppTicket returned empty ticket")
        }
        onLog("ticket minted: ${resp.encryptedAppTicket.size} bytes for app $appId")
        return Ticket(resp.appId, resp.encryptedAppTicket)
    }

    companion object {
        const val PAVLOV_SHACK_APPID = 3504270
    }
}

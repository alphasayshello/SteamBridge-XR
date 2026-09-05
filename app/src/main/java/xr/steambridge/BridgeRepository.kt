package xr.steambridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import xr.steambridge.cm.SteamBridgeClient
import xr.steambridge.cm.msg.OwnedGame
import xr.steambridge.secure.TokenStore
import xr.steambridge.delivery.TicketCache

/**
 * Minting is stateless: each cold-cache mint spins up a fresh [SteamBridgeClient], connects, logs on
 * with the stored refresh_token, pulls one ticket, and closes. The 90s [TicketCache] absorbs the
 * connect cost across a join burst and keeps within Steam's ~1/min ticket rate limit.
 */
class BridgeRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    val tokens: TokenStore = TokenStore.open(appContext)
    val cache = TicketCache()

    suspend fun mintTicket(): TicketCache.Entry? {
        val account = tokens.accountName
        val refresh = tokens.refreshToken
        if (account.isNullOrEmpty() || refresh.isNullOrEmpty()) {
            LogBus.log("No saved session — sign in first")
            return null
        }
        val appId = tokens.activeAppId
        RelayStatus.minting(appId)
        val client = SteamBridgeClient(scope = scope, machineSeed = tokens.machineSeed, onLog = LogBus::log)
        return try {
            val result = client.mintWithToken(account, refresh, appId)
            tokens.steamId64 = result.steamId64.toString()
            RelayStatus.ready(appId, result.personaName)
            TicketCache.Entry(
                steamId64 = result.steamId64,
                personaName = result.personaName,
                ticket = result.ticket,
                mintedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            val msg = e.message ?: "unknown error"
            LogBus.log("Mint failed: $msg")
            // eresult 15 = AccessDenied → the signed-in account doesn't own this app.
            RelayStatus.failed(appId, msg, notOwned = msg.contains("eresult=15"))
            null
        } finally {
            client.close()
        }
    }

    /** Pull the signed-in account's owned games. Null on failure. */
    suspend fun fetchLibrary(): List<OwnedGame>? {
        val account = tokens.accountName
        val refresh = tokens.refreshToken
        if (account.isNullOrEmpty() || refresh.isNullOrEmpty()) return null
        val client = SteamBridgeClient(scope = scope, machineSeed = tokens.machineSeed, onLog = LogBus::log)
        return try {
            client.fetchLibrary(account, refresh)
        } catch (e: Exception) {
            LogBus.log("Library fetch failed: ${e.message}")
            null
        } finally {
            client.close()
        }
    }
}

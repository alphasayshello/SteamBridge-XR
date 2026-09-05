package xr.steambridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import xr.steambridge.cm.SteamBridgeClient
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
        val client = SteamBridgeClient(scope = scope, machineSeed = tokens.machineSeed, onLog = LogBus::log)
        return try {
            val result = client.mintWithToken(account, refresh)
            tokens.steamId64 = result.steamId64.toString()
            TicketCache.Entry(
                steamId64 = result.steamId64,
                personaName = result.personaName,
                ticket = result.ticket,
                mintedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            LogBus.log("Mint failed: ${e.message}")
            null
        } finally {
            client.close()
        }
    }
}

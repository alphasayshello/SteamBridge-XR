package xr.steambridge.delivery

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the last minted EncryptedAppTicket and gates re-minting.
 *
 * Mirrors the PC relay's cache discipline (minter.cpp:33 CACHE_MS=90_000, relay.ps1:66 90s):
 * Steam rate-limits ISteamUser::RequestEncryptedAppTicket to ~1/min and returns
 * k_EResultLimitExceeded(25) if pushed harder. So:
 *   - Serve a cached ticket for up to [ttlMs].
 *   - Never trigger a re-mint more often than [minRemintMs], even if the cache looks stale, so a burst
 *     of concurrent loopback connects collapses to a single upstream request.
 *
 * The cache stores raw ticket bytes plus the identity that came with them; the loopback server turns
 * an [Entry] into the wire block via [WireFormat].
 */
class TicketCache(
    private val ttlMs: Long = 90_000L,
    private val minRemintMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry(
        val steamId64: ULong,
        val personaName: String,
        val ticket: ByteArray,
        val mintedAt: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return steamId64 == other.steamId64 &&
                personaName == other.personaName &&
                ticket.contentEquals(other.ticket) &&
                mintedAt == other.mintedAt
        }

        override fun hashCode(): Int {
            var result = steamId64.hashCode()
            result = 31 * result + personaName.hashCode()
            result = 31 * result + ticket.contentHashCode()
            result = 31 * result + mintedAt.hashCode()
            return result
        }
    }

    private val mutex = Mutex()
    private var entry: Entry? = null
    private var lastMintAttemptAt = Long.MIN_VALUE

    /** Fresh = within TTL. */
    fun isFresh(): Boolean {
        val e = entry ?: return false
        return clock() - e.mintedAt < ttlMs
    }

    /** Current cached entry regardless of freshness, or null if nothing minted yet. */
    fun peek(): Entry? = entry

    /**
     * Return a fresh entry, minting on demand via [mint] only when needed and permitted.
     *
     * Contract for [mint]: perform the CM round-trip and return a new [Entry], or null on failure.
     * It is invoked at most once per [get] call, under the cache lock, and never more often than
     * [minRemintMs] apart. A failed mint leaves any existing (possibly stale) entry in place so the
     * caller can decide whether serving stale bytes is acceptable.
     *
     * @return the entry to serve (fresh if minting succeeded), or the last known entry, or null if
     *         nothing has ever been minted and this attempt failed.
     */
    suspend fun get(mint: suspend () -> Entry?): Entry? = mutex.withLock {
        val current = entry
        val now = clock()
        if (current != null && now - current.mintedAt < ttlMs) return current

        if (now - lastMintAttemptAt < minRemintMs && current != null) {
            // Too soon to re-mint and we still hold something — serve the stale entry rather than
            // risk k_EResultLimitExceeded upstream.
            return current
        }

        lastMintAttemptAt = now
        val minted = mint()
        if (minted != null) {
            entry = minted
            return minted
        }
        return current
    }

    fun clear() {
        entry = null
        lastMintAttemptAt = Long.MIN_VALUE
    }
}

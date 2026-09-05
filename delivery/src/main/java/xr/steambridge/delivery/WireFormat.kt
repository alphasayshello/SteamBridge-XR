package xr.steambridge.delivery

/**
 * The exact wire contract steamshim.cpp `relay_fetch()` parses.
 *
 * Consumer (steamshim.cpp:195-207) is `strstr`-based and order-tolerant:
 *   - STEAMID: read with strtoull base-10 up to newline.
 *   - NAME:    copied byte-for-byte to the first '\n'/'\r' into a 64-byte buffer.
 *   - TICKET:  hex-decoded 2 nibbles/byte until the first non-hex char, cap 2048 bytes.
 *
 * Hard constraints enforced here so the parser never chokes:
 *   - NAME must contain no '\n'/'\r' (would truncate) and fits the shim's 63-usable-byte buffer.
 *   - TICKET hex is lowercase [0-9a-f] with NO interior delimiter (decode stops at first non-hex).
 *   - The full message is one contiguous block; the caller writes it all before closing the socket.
 */
object WireFormat {

    /** steamshim g_persona buffer is char[64]; 63 usable bytes + NUL. Keep NAME within that. */
    const val MAX_NAME_BYTES = 63

    /** steamshim g_ticket buffer is uint8_t[2048]. Bytes beyond that are dropped by the parser. */
    const val MAX_TICKET_BYTES = 2048

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Build the reply block for one loopback connect.
     *
     * @param steamId64  the account SteamID64 as an unsigned 64-bit value.
     * @param personaName real Steam persona; sanitized to survive the shim's line-copy.
     * @param ticket     serialized EncryptedAppTicket bytes (raw, not hex).
     * @return ASCII bytes ready to write to the socket.
     * @throws IllegalArgumentException if the ticket is empty or exceeds the shim buffer.
     */
    fun build(steamId64: ULong, personaName: String, ticket: ByteArray): ByteArray {
        require(ticket.isNotEmpty()) { "empty ticket" }
        require(ticket.size <= MAX_TICKET_BYTES) {
            "ticket ${ticket.size}B exceeds shim buffer $MAX_TICKET_BYTES"
        }
        val name = sanitizeName(personaName)
        val hex = toHex(ticket)
        // Order matches the PC relay (relay.ps1:68); shim parses by strstr so order is not load-bearing,
        // but NAME before TICKET keeps the human-readable fields adjacent.
        val sb = StringBuilder(24 + name.length + hex.length)
        sb.append("STEAMID:").append(steamId64.toString()).append('\n')
        sb.append("NAME:").append(name).append('\n')
        sb.append("TICKET:").append(hex).append('\n')
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /**
     * Strip anything that would break the shim's line copy: newlines/carriage returns terminate the
     * NAME field early, so they are removed. Non-ASCII is transliterated to '?' because the shim copies
     * raw bytes into a fixed buffer the game later treats as a C string. Result is byte-capped to fit.
     */
    fun sanitizeName(raw: String): String {
        if (raw.isEmpty()) return "player" // shim guards against a null persona (steamshim.cpp:198-201)
        val cleaned = buildString(raw.length) {
            for (ch in raw) {
                when {
                    ch == '\n' || ch == '\r' -> {} // drop: would truncate the field
                    ch.code in 0x20..0x7E -> append(ch)
                    else -> append('?')
                }
            }
        }
        val trimmed = cleaned.trim()
        val safe = trimmed.ifEmpty { "player" }
        return capBytes(safe, MAX_NAME_BYTES)
    }

    private fun capBytes(s: String, maxBytes: Int): String {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        if (bytes.size <= maxBytes) return s
        return String(bytes, 0, maxBytes, Charsets.US_ASCII)
    }

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX[v ushr 4]
            out[i++] = HEX[v and 0x0F]
        }
        return String(out)
    }
}

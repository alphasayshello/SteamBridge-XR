package xr.steambridge.cm.msg

/**
 * The EMsg subset SteamBridge-XR needs. Values match Valve's SteamLanguage / SteamKit2 EMsg.cs.
 *
 * Protobuf-backed messages carry the high bit [PROTO_MASK] set in the raw 32-bit EMsg on the wire.
 * Always strip it before comparing (`raw and PROTO_MASK.inv()`), and set it when sending a protobuf
 * message.
 */
object EMsg {
    /** High bit of the raw wire EMsg dword: message body is a protobuf, header is CMsgProtoBufHeader. */
    const val PROTO_MASK: Int = 0x7FFFFFFF.inv() // 0x80000000

    // --- channel encryption (raw-TCP transport only; WebSocket rides TLS and skips these) ---
    const val ChannelEncryptRequest = 1303
    const val ChannelEncryptResponse = 1304
    const val ChannelEncryptResult = 1305

    /**
     * First frame the client MUST send on every fresh CM connection, before any other message.
     * The CM does not route client messages until it receives this protocol-version handshake — omit it
     * and the CM silently drops everything (verified against SteamKit2 + node-steam-session).
     */
    const val ClientHello = 9805

    // --- unified-messages (IAuthenticationService, IPlayerService et al.) ---
    /** Client -> CM service call before logon (auth lives here). Reply arrives as [ServiceMethodResponse]. */
    const val ServiceMethodCallFromClientNonAuthed = 9804
    /** Client -> CM service call AFTER logon (e.g. Player.GetOwnedGames). Same reply EMsg. */
    const val ServiceMethodCallFromClient = 151
    const val ServiceMethodResponse = 147

    // --- logon ---
    const val ClientLogon = 5514
    const val ClientLogOnResponse = 751
    const val ClientHeartBeat = 703
    const val ClientLoggedOff = 757

    // --- encrypted app ticket ---
    const val ClientRequestEncryptedAppTicket = 5526
    const val ClientRequestEncryptedAppTicketResponse = 5527

    // --- multiplexing ---
    const val Multi = 1

    /** Extract the logical EMsg from a raw wire dword (clears the protobuf flag). */
    fun strip(raw: Int): Int = raw and PROTO_MASK.inv()

    /** Whether a raw wire dword has the protobuf flag set. */
    fun isProto(raw: Int): Boolean = (raw and PROTO_MASK) != 0

    /** Set the protobuf flag for sending. */
    fun withProto(msg: Int): Int = msg or PROTO_MASK
}

package xr.steambridge.cm.msg

/**
 * Classic client protobuf messages: logon and the encrypted-app-ticket request/response.
 * Field numbers pinned from SteamDatabase/Protobufs steammessages_clientserver_login.proto and
 * steammessages_clientserver_2.proto.
 */

object ClientLogon {
    /** SteamKit2 CMsgClientLogon.CurrentProtocol — bump only if the CM starts rejecting logons. */
    const val PROTOCOL_VERSION = 65580

    /** EOSType marker for Android; not load-bearing for auth but sent like a real client. */
    const val OS_ANDROID = -500

    /** Blank individual SteamID (universe=Public, type=Individual, instance=Desktop, accountid=0). */
    const val BLANK_INDIVIDUAL_STEAMID: Long = 0x0110000100000000L

    /**
     * @param accountName the Steam account name.
     * @param refreshToken the refresh_token from PollAuthSessionStatus (goes in access_token, field 108).
     * @param machineId stable machine_id blob (see [MachineId]).
     */
    fun request(
        accountName: String,
        refreshToken: String,
        machineId: ByteArray,
        cellId: Int = 0,
    ): ByteArray = ProtoWriter().apply {
        varint(1, PROTOCOL_VERSION)               // protocol_version
        varint(3, cellId)                         // cell_id
        varint(5, 1771)                           // client_package_version (plausible client build)
        string(6, "english")                      // client_language
        varint(7, OS_ANDROID)                     // client_os_type
        bool(8, true)                             // should_remember_password
        bytes(30, machineId)                      // machine_id
        string(50, accountName)                   // account_name
        bool(102, true)                           // supports_rate_limit_response
        string(108, refreshToken)                 // access_token = refresh token
    }.toByteArray()

    data class Response(
        val eResult: Int,
        val heartbeatSeconds: Int,
        val steamId: Long,
    )

    fun parse(body: ByteArray): Response {
        var eresult = 2; var hb = 9; var steamId = 0L
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> eresult = r.readVarintValue().toInt()          // eresult
                3 -> hb = r.readVarintValue().toInt()               // heartbeat_seconds
                5 -> r.readFixed32()                                // rtime32_server_time (skip value)
                20 -> steamId = r.readFixed64()                     // client_supplied_steamid
                else -> r.skip(f.wireType)
            }
        }
        return Response(eresult, hb, steamId)
    }
}

object ClientHeartBeat {
    fun request(): ByteArray = ByteArray(0) // CMsgClientHeartBeat has no required fields
}

object ClientHello {
    /**
     * CMsgClientHello: field 1 protocol_version (varint) = MsgClientLogon.CurrentProtocol.
     * Sent as the first frame on a fresh CM connection; the CM does not reply to it.
     */
    fun request(): ByteArray = ProtoWriter().varint(1, ClientLogon.PROTOCOL_VERSION).toByteArray()
}

object RequestEncryptedAppTicket {
    fun request(appId: Int, userData: ByteArray? = null): ByteArray = ProtoWriter().apply {
        varint(1, appId)                          // app_id
        userData?.let { bytes(2, it) }            // userdata
    }.toByteArray()

    data class Response(
        val appId: Int,
        val eResult: Int,
        /** Raw serialized EncryptedAppTicket bytes (response field 3) — the deliverable, byte-identical
         *  to ISteamUser::GetEncryptedAppTicket output. Empty if eresult != OK. */
        val encryptedAppTicket: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Response) return false
            return appId == other.appId && eResult == other.eResult &&
                encryptedAppTicket.contentEquals(other.encryptedAppTicket)
        }

        override fun hashCode(): Int {
            var r = appId
            r = 31 * r + eResult
            r = 31 * r + encryptedAppTicket.contentHashCode()
            return r
        }
    }

    fun parse(body: ByteArray): Response {
        var appId = 0; var eresult = 2; var ticket = ByteArray(0)
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> appId = r.readVarintValue().toInt()
                2 -> eresult = r.readVarintValue().toInt()
                3 -> ticket = r.readBytes()   // the serialized EncryptedAppTicket message, verbatim
                else -> r.skip(f.wireType)
            }
        }
        return Response(appId, eresult, ticket)
    }
}

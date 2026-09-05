package xr.steambridge.cm.msg

/**
 * IAuthenticationService message codecs, hand-rolled against the pinned field numbers from
 * SteamDatabase/Protobufs `steammessages_auth.steamclient.proto`. Only the fields SteamBridge-XR sends
 * or reads are implemented; everything else on the wire is skipped on read.
 */

/** EAuthTokenPlatformType. SteamClient(1) makes machine-auth / guard_data behave like the desktop client. */
object EAuthTokenPlatformType {
    const val Unknown = 0
    const val SteamClient = 1
    const val WebBrowser = 2
    const val MobileApp = 3
}

/** EAuthSessionGuardType — how a session must be confirmed. */
object EAuthSessionGuardType {
    const val Unknown = 0
    const val None = 1
    const val EmailCode = 2
    const val DeviceCode = 3
    const val DeviceConfirmation = 4
    const val EmailConfirmation = 5
    const val MachineToken = 6
    const val LegacyMachineAuth = 7
}

/** ESessionPersistence (enums.proto). Persistent(1) yields a long-lived refresh token. */
object ESessionPersistence {
    const val Invalid = -1
    const val Ephemeral = 0
    const val Persistent = 1
}

/** EOSType values we send. The os_type MUST be consistent with platform_type: a SteamClient (desktop)
 *  session must report a desktop OS, or the Steam Mobile app rejects the QR session with
 *  "Failed to load QR code info". node-steam-session/SteamKit pair SteamClient with a real desktop OS. */
object EOSType {
    const val Win11 = 20
    const val AndroidUnknown = -500 // only valid paired with platform_type = MobileApp
}

/** CAuthentication_DeviceDetails — describes this client to the auth service. */
data class DeviceDetails(
    val friendlyName: String,
    val platformType: Int = EAuthTokenPlatformType.SteamClient,
    // Desktop OS to match platform_type=SteamClient. A negative Android value here contradicts the
    // platform and makes the mobile approver fail to load the session.
    val osType: Int = EOSType.Win11,
    val gamingDeviceType: Int = 1, // EGamingDeviceType.StandardPC — SteamClient always sends this
    val machineId: ByteArray? = null,
) {
    fun encode(): ByteArray = ProtoWriter().apply {
        string(1, friendlyName)
        varint(2, platformType)
        varint(3, osType)
        varint(4, gamingDeviceType)
        machineId?.let { bytes(6, it) }
    }.toByteArray()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceDetails) return false
        return friendlyName == other.friendlyName && platformType == other.platformType &&
            osType == other.osType && gamingDeviceType == other.gamingDeviceType &&
            (machineId?.contentEquals(other.machineId) ?: (other.machineId == null))
    }

    override fun hashCode(): Int {
        var r = friendlyName.hashCode()
        r = 31 * r + platformType
        r = 31 * r + osType
        r = 31 * r + gamingDeviceType
        r = 31 * r + (machineId?.contentHashCode() ?: 0)
        return r
    }
}

object GetPasswordRSAPublicKey {
    fun request(accountName: String): ByteArray =
        ProtoWriter().string(1, accountName).toByteArray()

    data class Response(val modulusHex: String, val exponentHex: String, val timestamp: Long)

    fun parse(body: ByteArray): Response {
        var mod = ""; var exp = ""; var ts = 0L
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> mod = r.readString()
                2 -> exp = r.readString()
                3 -> ts = r.readVarintValue()
                else -> r.skip(f.wireType)
            }
        }
        return Response(mod, exp, ts)
    }
}

object BeginAuthSessionViaCredentials {
    fun request(
        accountName: String,
        encryptedPasswordB64: String,
        encryptionTimestamp: Long,
        device: DeviceDetails,
        guardData: String? = null,
        persistence: Int = ESessionPersistence.Persistent,
    ): ByteArray = ProtoWriter().apply {
        string(1, device.friendlyName)
        string(2, accountName)
        string(3, encryptedPasswordB64)
        varint(4, encryptionTimestamp)
        bool(5, true) // remember_login
        varint(6, EAuthTokenPlatformType.SteamClient)
        varint(7, persistence)
        bytes(9, device.encode())
        guardData?.let { string(10, it) }
    }.toByteArray()

    data class AllowedConfirmation(val type: Int, val message: String)

    data class Response(
        val clientId: Long,
        val requestId: ByteArray,
        val intervalSec: Float,
        val allowed: List<AllowedConfirmation>,
        val steamId: Long,
        val extendedError: String?,
    )

    fun parse(body: ByteArray): Response {
        var clientId = 0L; var requestId = ByteArray(0); var interval = 5f
        val allowed = ArrayList<AllowedConfirmation>(); var steamId = 0L; var err: String? = null
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> clientId = r.readFixed64OrVarint(f.wireType)
                2 -> requestId = r.readBytes()
                3 -> interval = Float.fromBits(r.readFixed32())
                4 -> allowed.add(parseAllowed(r.readBytes()))
                5 -> steamId = r.readFixed64OrVarint(f.wireType)
                8 -> err = r.readString()
                else -> r.skip(f.wireType)
            }
        }
        return Response(clientId, requestId, interval, allowed, steamId, err)
    }

    private fun parseAllowed(bytes: ByteArray): AllowedConfirmation {
        var type = 0; var msg = ""
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> type = r.readVarintValue().toInt()
                2 -> msg = r.readString()
                else -> r.skip(f.wireType)
            }
        }
        return AllowedConfirmation(type, msg)
    }
}

object BeginAuthSessionViaQR {
    // Matches node-steam-session/SteamKit: send ONLY device_details (field 3), and NO machine_id inside
    // it on the QR path (machine_id is a credentials-flow field). Top-level friendly_name/platform_type
    // are omitted — they are redundant with device_details.
    fun request(device: DeviceDetails): ByteArray = ProtoWriter().apply {
        bytes(3, device.copy(machineId = null).encode())
    }.toByteArray()

    data class Response(
        val clientId: Long,
        val challengeUrl: String,
        val requestId: ByteArray,
        val intervalSec: Float,
    )

    fun parse(body: ByteArray): Response {
        var clientId = 0L; var url = ""; var requestId = ByteArray(0); var interval = 5f
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> clientId = r.readFixed64OrVarint(f.wireType)
                2 -> url = r.readString()
                3 -> requestId = r.readBytes()
                4 -> interval = Float.fromBits(r.readFixed32())
                else -> r.skip(f.wireType)
            }
        }
        return Response(clientId, url, requestId, interval)
    }
}

object PollAuthSessionStatus {
    // client_id is uint64 (field 1) -> VARINT wire type, NOT fixed64. Sending fixed64 makes Steam read
    // client_id as 0, match no session, and return an empty response forever.
    fun request(clientId: Long, requestId: ByteArray): ByteArray = ProtoWriter().apply {
        varint(1, clientId)
        bytes(2, requestId)
    }.toByteArray()

    data class Response(
        val newClientId: Long,
        val newChallengeUrl: String?,
        val refreshToken: String?,
        val accessToken: String?,
        val accountName: String?,
        val newGuardData: String?,
    )

    fun parse(body: ByteArray): Response {
        var newClientId = 0L; var url: String? = null; var refresh: String? = null
        var access: String? = null; var name: String? = null; var guard: String? = null
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> newClientId = r.readFixed64OrVarint(f.wireType)
                2 -> url = r.readString()
                3 -> refresh = r.readString()
                4 -> access = r.readString()
                6 -> name = r.readString()
                7 -> guard = r.readString()
                else -> r.skip(f.wireType)
            }
        }
        return Response(newClientId, url, refresh, access, name, guard)
    }
}

object UpdateAuthSessionWithSteamGuardCode {
    fun request(clientId: Long, steamId: Long, code: String, codeType: Int): ByteArray =
        ProtoWriter().apply {
            varint(1, clientId)   // client_id: uint64 -> varint (NOT fixed64)
            fixed64(2, steamId)   // steamid: fixed64 (field 2 is genuinely fixed64 here)
            string(3, code)
            varint(4, codeType)
        }.toByteArray()
}

/** Read a field that Steam declares uint64 (varint) but some builds emit as fixed64; tolerate both. */
private fun ProtoReader.readFixed64OrVarint(wireType: Int): Long =
    if (wireType == 1) readFixed64() else readVarintValue()

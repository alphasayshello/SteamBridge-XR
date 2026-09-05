package xr.steambridge.cm.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xr.steambridge.cm.crypto.PasswordCipher
import xr.steambridge.cm.msg.BeginAuthSessionViaCredentials
import xr.steambridge.cm.msg.BeginAuthSessionViaQR
import xr.steambridge.cm.msg.DeviceDetails
import xr.steambridge.cm.msg.EAuthSessionGuardType
import xr.steambridge.cm.msg.GetPasswordRSAPublicKey
import xr.steambridge.cm.msg.MessageRouter
import xr.steambridge.cm.msg.PollAuthSessionStatus
import xr.steambridge.cm.msg.ProtoHeader
import xr.steambridge.cm.msg.UpdateAuthSessionWithSteamGuardCode

/**
 * Phase 1 of on-device Steam auth: IAuthenticationService RPCs over an un-logged-on CM connection,
 * yielding a refresh_token. Supports both the credentials lane and the QR lane; Steam Guard prompts
 * are surfaced through [state] and answered via [submitGuardCode].
 *
 * The refresh_token this produces is the input to Phase 2 (CmLogon), which then requests the ticket.
 */
class AuthSession(
    private val router: MessageRouter,
    private val device: DeviceDetails,
    private val onLog: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<GuardState>(GuardState.None)
    val state: StateFlow<GuardState> = _state

    private var clientId = 0L
    private var requestId = ByteArray(0)
    private var steamId = 0L
    private var accountName = ""
    private var intervalMs = 5_000L

    /** Begin credentials login. Suspends through polling until [GuardState.Done] or [GuardState.Failed]. */
    suspend fun loginWithCredentials(account: String, password: String, guardData: String? = null): GuardState {
        accountName = account
        val rsaResp = router.serviceCall(
            "Authentication.GetPasswordRSAPublicKey#1",
            GetPasswordRSAPublicKey.request(account),
        )
        val rsa = GetPasswordRSAPublicKey.parse(rsaResp.bodyBytes)
        val encPw = PasswordCipher.encrypt(password, rsa.modulusHex, rsa.exponentHex)

        val beginResp = router.serviceCall(
            "Authentication.BeginAuthSessionViaCredentials#1",
            BeginAuthSessionViaCredentials.request(
                accountName = account,
                encryptedPasswordB64 = encPw,
                encryptionTimestamp = rsa.timestamp,
                device = device,
                guardData = guardData,
            ),
        )
        val beginHeader = ProtoHeader.decode(beginResp.headerBytes)
        val begin = BeginAuthSessionViaCredentials.parse(beginResp.bodyBytes)
        if (begin.clientId == 0L) {
            val reason = begin.extendedError?.takeIf { it.isNotBlank() }
                ?: beginHeader.errorMessage?.takeIf { it.isNotBlank() }
                ?: "eresult=${beginHeader.eResult}"
            return fail("credentials rejected: $reason")
        }
        clientId = begin.clientId
        requestId = begin.requestId
        steamId = begin.steamId
        intervalMs = (begin.intervalSec.coerceAtLeast(1f) * 1000).toLong()
        applyGuard(begin.allowed.map { it.type }, begin.allowed.firstOrNull()?.message ?: "")
        return pollUntilToken()
    }

    /** Begin QR login. [GuardState.QrChallenge] carries the URL to render; resolves on phone approval. */
    suspend fun loginWithQr(): GuardState {
        val resp = router.serviceCall(
            "Authentication.BeginAuthSessionViaQR#1",
            BeginAuthSessionViaQR.request(device),
        )
        val qr = BeginAuthSessionViaQR.parse(resp.bodyBytes)
        onLog("QR resp: bodyBytes=${resp.bodyBytes.size} clientId=${qr.clientId} url='${qr.challengeUrl}' urlLen=${qr.challengeUrl.length} reqIdLen=${qr.requestId.size}")
        if (qr.clientId == 0L) return fail("BeginAuthSessionViaQR failed")
        clientId = qr.clientId
        requestId = qr.requestId
        intervalMs = (qr.intervalSec.coerceAtLeast(1f) * 1000).toLong()
        _state.value = GuardState.QrChallenge(qr.challengeUrl)
        return pollUntilToken()
    }

    /** Answer an email/device code prompt (EmailCode or DeviceCode). */
    suspend fun submitGuardCode(code: String, isDeviceCode: Boolean) {
        val type = if (isDeviceCode) EAuthSessionGuardType.DeviceCode else EAuthSessionGuardType.EmailCode
        router.serviceCall(
            "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
            UpdateAuthSessionWithSteamGuardCode.request(clientId, steamId, code, type),
        )
        onLog("submitted guard code (deviceCode=$isDeviceCode)")
        // Next poll will return the token.
    }

    private suspend fun pollUntilToken(): GuardState {
        while (true) {
            delay(intervalMs)
            val resp = router.serviceCall(
                "Authentication.PollAuthSessionStatus#1",
                PollAuthSessionStatus.request(clientId, requestId),
            )
            val poll = PollAuthSessionStatus.parse(resp.bodyBytes)
            if (poll.newClientId != 0L) clientId = poll.newClientId
            if (!poll.newChallengeUrl.isNullOrEmpty()) {
                _state.value = GuardState.QrChallenge(poll.newChallengeUrl) // QR rotated
            }
            val refresh = poll.refreshToken
            if (!refresh.isNullOrEmpty()) {
                val done = GuardState.Done(
                    refreshToken = refresh,
                    steamId = steamId,
                    accountName = poll.accountName ?: accountName,
                    guardData = poll.newGuardData,
                )
                _state.value = done
                onLog("auth complete: refresh token acquired for ${done.accountName}")
                return done
            }
        }
    }

    private fun applyGuard(types: List<Int>, message: String) {
        _state.value = when {
            types.contains(EAuthSessionGuardType.DeviceConfirmation) -> GuardState.DeviceConfirmation
            types.contains(EAuthSessionGuardType.DeviceCode) -> GuardState.DeviceCode
            types.contains(EAuthSessionGuardType.EmailCode) -> GuardState.EmailCode(message)
            types.all { it == EAuthSessionGuardType.None } -> GuardState.None
            else -> GuardState.None
        }
    }

    private fun fail(reason: String): GuardState {
        val f = GuardState.Failed(reason)
        _state.value = f
        onLog("auth failed: $reason")
        return f
    }
}

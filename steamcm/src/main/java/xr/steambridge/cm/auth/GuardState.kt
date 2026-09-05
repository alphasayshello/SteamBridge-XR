package xr.steambridge.cm.auth

/**
 * What the auth session is waiting on, surfaced to the UI.
 *
 * Derived from BeginAuthSessionVia*.allowed_confirmations[].confirmation_type (EAuthSessionGuardType).
 */
sealed class GuardState {
    /** No guard — polling will return a refresh token as soon as the CM approves. */
    data object None : GuardState()

    /** A code was emailed; the user types it. */
    data class EmailCode(val hint: String) : GuardState()

    /** A TOTP device code from the Steam Mobile authenticator; the user types it. */
    data object DeviceCode : GuardState()

    /** The user must approve the login in the Steam Mobile app; just keep polling. */
    data object DeviceConfirmation : GuardState()

    /** A QR challenge is live; render [url] and wait for the phone to scan+approve. */
    data class QrChallenge(val url: String) : GuardState()

    /** Terminal failure. */
    data class Failed(val reason: String) : GuardState()

    /** Auth complete; token acquired. */
    data class Done(val refreshToken: String, val steamId: Long, val accountName: String, val guardData: String?) : GuardState()
}

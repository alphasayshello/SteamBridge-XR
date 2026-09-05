package xr.steambridge.cm

/** The EResult values SteamBridge-XR checks. Full enum lives in Valve's steammessages_base; these are
 *  the ones the auth/ticket paths actually branch on. Default on the wire is Fail(2). */
object EResult {
    const val OK = 1
    const val Fail = 2
    const val NoConnection = 3
    const val InvalidPassword = 5
    const val LoggedInElsewhere = 6
    const val InvalidProtocolVer = 7
    const val AccessDenied = 15                // ticket path: the account doesn't own the app
    const val Timeout = 16
    const val LimitExceeded = 25
    const val AccountLogonDenied = 63          // Steam Guard email code required
    const val AccountLoginDeniedNeedTwoFactor = 85
    const val RateLimitExceeded = 84
    const val Expired = 27
    const val TryAnotherCM = 42
    const val Pending = 22
}

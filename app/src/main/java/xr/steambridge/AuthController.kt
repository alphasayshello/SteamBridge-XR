package xr.steambridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xr.steambridge.cm.SteamBridgeClient
import xr.steambridge.cm.auth.AuthSession
import xr.steambridge.cm.auth.GuardState
import xr.steambridge.secure.TokenStore
import xr.steambridge.service.LoopbackServerService

/**
 * On Quest the panel Activity is destroyed the moment the panel loses focus, which would cancel any
 * login running in a ViewModel scope. So the whole auth flow lives here on an application-lifetime
 * scope: a login survives the panel closing, and a reopened UI re-attaches to [ui] and [LogBus] and
 * sees the live state.
 */
object AuthController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var tokens: TokenStore

    private val _ui = MutableStateFlow<UiState>(UiState.LoggedOut())
    val ui: StateFlow<UiState> = _ui

    private var client: SteamBridgeClient? = null
    private var session: AuthSession? = null
    private var loginJob: Job? = null
    private var observerJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        tokens = TokenStore.open(appContext)
        if (tokens.hasToken) {
            _ui.value = UiState.LoggedIn(tokens.accountName ?: "?", tokens.steamId64 ?: "?", relayRunning = false)
            startRelay() // already signed in on launch -> bring the relay up and flip the pill
        } else {
            _ui.value = UiState.LoggedOut()
        }
        initialized = true
    }

    fun loginWithQr() {
        loginJob?.cancel()
        pinProcess()
        LogBus.log("Starting QR sign-in…")
        _ui.value = UiState.Working("Reaching Steam…")
        loginJob = scope.launch {
            try {
                val s = freshClient().openAuthSession()
                session = s
                observe(s)
                finishAuth(s.loginWithQr())
            } catch (e: Exception) {
                LogBus.log("Sign-in failed: ${e.message}")
                _ui.value = UiState.LoggedOut(e.message ?: "Sign-in failed")
            }
        }
    }

    fun logout() {
        loginJob?.cancel()
        observerJob?.cancel()
        client?.close()
        client = null
        session = null
        tokens.clearSession()
        LogBus.log("Signed out")
        _ui.value = UiState.LoggedOut()
    }

    fun startRelay() {
        LoopbackServerService.start(appContext)
        (_ui.value as? UiState.LoggedIn)?.let { _ui.value = it.copy(relayRunning = true) }
    }

    private fun freshClient(): SteamBridgeClient {
        client?.close()
        return SteamBridgeClient(scope = scope, machineSeed = tokens.machineSeed, onLog = LogBus::log)
            .also { client = it }
    }

    // Bring the foreground relay up now so the process survives a panel close mid-login.
    private fun pinProcess() = runCatching { LoopbackServerService.start(appContext) }

    private fun observe(s: AuthSession) {
        observerJob?.cancel()
        observerJob = scope.launch {
            s.state.collect { g ->
                when (g) {
                    is GuardState.QrChallenge -> _ui.value = UiState.ShowQr(g.url)
                    is GuardState.Failed -> _ui.value = UiState.LoggedOut(g.reason)
                    else -> {}
                }
            }
        }
    }

    private fun finishAuth(result: GuardState) {
        if (result !is GuardState.Done) return
        tokens.saveSession(result.accountName, result.refreshToken, result.guardData, result.steamId.toULong())
        observerJob?.cancel()
        client?.close()
        client = null
        session = null
        LogBus.log("Signed in as ${result.accountName} (${result.steamId})")
        _ui.value = UiState.LoggedIn(result.accountName, result.steamId.toString(), relayRunning = false)
        startRelay()
    }
}

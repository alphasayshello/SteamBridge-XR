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
import xr.steambridge.cm.msg.OwnedGame
import xr.steambridge.secure.TokenStore
import xr.steambridge.service.LoopbackServerService

/**
 * On Quest the panel Activity is destroyed the moment the panel loses focus, which would cancel any
 * login running in a ViewModel scope. So the whole auth flow lives here on an application-lifetime
 * scope: a login survives the panel closing, and a reopened UI re-attaches to the flows below.
 */
object AuthController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var tokens: TokenStore

    private val _ui = MutableStateFlow<UiState>(UiState.LoggedOut())
    val ui: StateFlow<UiState> = _ui

    private val _library = MutableStateFlow<List<OwnedGame>>(emptyList())
    val library: StateFlow<List<OwnedGame>> = _library

    private val _libraryLoading = MutableStateFlow(false)
    val libraryLoading: StateFlow<Boolean> = _libraryLoading

    private val _activeAppId = MutableStateFlow(3504270)
    val activeAppId: StateFlow<Int> = _activeAppId

    /** Live mint status (minting / ready / failed), surfaced from the loopback service to the UI. */
    val relayStatus: StateFlow<RelayStatus.State> = RelayStatus.state

    private var client: SteamBridgeClient? = null
    private var session: AuthSession? = null
    private var loginJob: Job? = null
    private var observerJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        tokens = TokenStore.open(appContext)
        _activeAppId.value = tokens.activeAppId
        if (tokens.hasToken) {
            _ui.value = UiState.LoggedIn(tokens.accountName ?: "?", tokens.steamId64 ?: "?", relayRunning = false)
            startRelay()
            loadLibrary()
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
        _library.value = emptyList()
        RelayStatus.idle()
        LogBus.log("Signed out")
        _ui.value = UiState.LoggedOut()
    }

    /** Make [appId] the active app: persist it, point the relay at it, and mint its ticket now. */
    fun startApp(appId: Int) {
        tokens.activeAppId = appId
        _activeAppId.value = appId
        val name = _library.value.firstOrNull { it.appId == appId }?.name ?: appId.toString()
        LogBus.log("Starting relay for $name ($appId)")
        RelayStatus.minting(appId)   // instant feedback; the service confirms ready/failed
        LoopbackServerService.switchApp(appContext, appId)
    }

    fun startRelay() {
        LoopbackServerService.start(appContext)
        (_ui.value as? UiState.LoggedIn)?.let { _ui.value = it.copy(relayRunning = true) }
    }

    /** Stop serving tickets — shuts the loopback relay down until the user starts an app again. */
    fun stopRelay() {
        LoopbackServerService.stop(appContext)
        RelayStatus.idle()
        LogBus.log("Relay stopped")
        (_ui.value as? UiState.LoggedIn)?.let { _ui.value = it.copy(relayRunning = false) }
    }

    fun refreshLibrary() = loadLibrary()

    private fun loadLibrary() {
        scope.launch {
            _libraryLoading.value = true
            val acc = tokens.accountName
            val rt = tokens.refreshToken
            if (!acc.isNullOrEmpty() && !rt.isNullOrEmpty()) {
                val c = SteamBridgeClient(scope, tokens.machineSeed, onLog = LogBus::log)
                try {
                    val games = c.fetchLibrary(acc, rt)
                    if (games.isNotEmpty()) _library.value = games.sortedByDescending { it.lastPlayedUnix }
                } catch (e: Exception) {
                    LogBus.log("Library fetch failed: ${e.message}")
                } finally {
                    c.close()
                }
            }
            _libraryLoading.value = false
        }
    }

    private fun freshClient(): SteamBridgeClient {
        client?.close()
        return SteamBridgeClient(scope = scope, machineSeed = tokens.machineSeed, onLog = LogBus::log)
            .also { client = it }
    }

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
        loadLibrary()
    }
}

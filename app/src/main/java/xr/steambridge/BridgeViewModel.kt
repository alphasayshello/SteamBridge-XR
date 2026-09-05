package xr.steambridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xr.steambridge.cm.msg.OwnedGame

sealed interface UiState {
    data class LoggedOut(val error: String? = null) : UiState
    data class Working(val message: String) : UiState
    data class ShowQr(val challengeUrl: String) : UiState
    data class LoggedIn(val account: String, val steamId: String, val relayRunning: Boolean) : UiState
}

class BridgeViewModel(app: Application) : AndroidViewModel(app) {
    init {
        AuthController.init(app)
    }

    val ui: StateFlow<UiState> = AuthController.ui
    val logs: StateFlow<List<String>> = LogBus.lines
    val library: StateFlow<List<OwnedGame>> = AuthController.library
    val libraryLoading: StateFlow<Boolean> = AuthController.libraryLoading
    val activeAppId: StateFlow<Int> = AuthController.activeAppId

    fun loginWithQr() = AuthController.loginWithQr()
    fun logout() = AuthController.logout()
    fun startApp(appId: Int) = AuthController.startApp(appId)
    fun refreshLibrary() = AuthController.refreshLibrary()
}

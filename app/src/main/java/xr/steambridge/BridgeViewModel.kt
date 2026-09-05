package xr.steambridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xr.steambridge.cm.msg.OwnedGame

sealed interface UiState {
    data class LoggedOut(val error: String? = null) : UiState
    data class Working(val message: String) : UiState
    data class ShowQr(val challengeUrl: String) : UiState
    /** Steam Guard is asking for a code — from email ([isDeviceCode]=false) or the mobile authenticator. */
    data class GuardPrompt(val isDeviceCode: Boolean, val hint: String) : UiState
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
    val relayStatus: StateFlow<RelayStatus.State> = AuthController.relayStatus

    fun loginWithQr() = AuthController.loginWithQr()
    fun loginWithCredentials(account: String, password: String) = AuthController.loginWithCredentials(account, password)
    fun submitGuardCode(code: String) = AuthController.submitGuardCode(code)
    fun logout() = AuthController.logout()
    fun startApp(appId: Int) = AuthController.startApp(appId)
    fun stopRelay() = AuthController.stopRelay()
    fun refreshLibrary() = AuthController.refreshLibrary()
}

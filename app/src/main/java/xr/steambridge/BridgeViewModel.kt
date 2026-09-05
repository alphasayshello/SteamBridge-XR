package xr.steambridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

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

    fun loginWithQr() = AuthController.loginWithQr()
    fun logout() = AuthController.logout()
    fun startRelay() = AuthController.startRelay()
}

package xr.steambridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide mint/relay status. The minting layer ([BridgeRepository.mintTicket], driven by the
 * loopback service + warmer) pushes here; the UI observes it. This is what surfaces a failed mint —
 * e.g. switching to an app the account doesn't own — instead of it dying silently in the log.
 */
object RelayStatus {
    sealed interface State {
        /** No relay / no mint attempted yet (also the state after Stop). */
        data object Idle : State
        data class Minting(val appId: Int) : State
        data class Ready(val appId: Int, val persona: String) : State
        data class Failed(val appId: Int, val reason: String, val notOwned: Boolean) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun minting(appId: Int) { _state.value = State.Minting(appId) }
    fun ready(appId: Int, persona: String) { _state.value = State.Ready(appId, persona) }
    fun failed(appId: Int, reason: String, notOwned: Boolean) {
        _state.value = State.Failed(appId, reason, notOwned)
    }
    fun idle() { _state.value = State.Idle }
}

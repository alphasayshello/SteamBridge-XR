package xr.steambridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import xr.steambridge.ui.BridgeScreen
import xr.steambridge.ui.SteamBridgeTheme

class AppEntryActivity : ComponentActivity() {
    private val vm: BridgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SteamBridgeTheme {
                Surface {
                    val state by vm.ui.collectAsState()
                    val logs by vm.logs.collectAsState()
                    val library by vm.library.collectAsState()
                    val libraryLoading by vm.libraryLoading.collectAsState()
                    val activeAppId by vm.activeAppId.collectAsState()
                    val relayStatus by vm.relayStatus.collectAsState()
                    BridgeScreen(
                        state = state,
                        logs = logs,
                        library = library,
                        libraryLoading = libraryLoading,
                        activeAppId = activeAppId,
                        relayStatus = relayStatus,
                        onLoginQr = vm::loginWithQr,
                        onLogout = vm::logout,
                        onStartApp = vm::startApp,
                        onStopRelay = vm::stopRelay,
                        onRefresh = vm::refreshLibrary,
                    )
                }
            }
        }
    }
}

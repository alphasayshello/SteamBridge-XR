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
                    BridgeScreen(
                        state = state,
                        logs = logs,
                        onLoginQr = vm::loginWithQr,
                        onLogout = vm::logout,
                    )
                }
            }
        }
    }
}

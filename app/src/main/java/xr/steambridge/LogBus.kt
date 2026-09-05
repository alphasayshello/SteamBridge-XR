package xr.steambridge

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A shared, in-memory ring of recent log lines the UI can render, so the login chain is visible on the
 * headset without a cable. Everything that logs (auth, CM client, relay) funnels through [log]; lines
 * also go to logcat under "SteamBridge".
 */
object LogBus {
    private const val TAG = "SteamBridge"
    private const val CAP = 200

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun log(message: String) {
        Log.i(TAG, message)
        val stamped = "${clock.format(Date())}  $message"
        _lines.value = (_lines.value + stamped).takeLast(CAP)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

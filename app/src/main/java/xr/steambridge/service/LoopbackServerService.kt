package xr.steambridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xr.steambridge.BridgeRepository
import xr.steambridge.LogBus
import xr.steambridge.R
import xr.steambridge.delivery.LoopbackTicketServer

/**
 * Foreground service hosting the loopback ticket server on 127.0.0.1:48010.
 *
 * Runs foreground so Horizon OS doesn't reap it while Pavlov is in the foreground: the game connects
 * here on each join attempt (relay.txt = "127.0.0.1:48010"), so this must outlive the app's UI.
 */
class LoopbackServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: BridgeRepository
    private lateinit var server: LoopbackTicketServer
    private var running = false

    override fun onCreate() {
        super.onCreate()
        repo = BridgeRepository(this, scope)
        server = LoopbackTicketServer(
            cache = repo.cache,
            port = PORT,
            onLog = LogBus::log,
            mint = { repo.mintTicket() },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (!running) {
            server.start(scope)
            startWarmer()
            running = true
        }
        if (intent?.action == ACTION_SWITCH_APP) {
            // Active app changed: drop the old app's cached ticket and mint the new one right away.
            scope.launch {
                repo.cache.clear()
                repo.cache.get { repo.mintTicket() }
            }
        }
        return START_STICKY
    }

    /**
     * Keep the cache warm so a shim connect always hits cached bytes and returns well inside its 5s
     * recv timeout. A cold mint (WS connect + logon + ticket) can take several seconds — too long to do
     * synchronously on the game's request — so we pre-mint on start and refresh ahead of the 90s TTL.
     */
    private fun startWarmer() {
        scope.launch {
            while (isActive) {
                repo.cache.get { repo.mintTicket() }
                delay(WARM_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        running = false
        server.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "SteamBridge relay", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("SteamBridge-XR")
            .setContentText("Ticket relay on 127.0.0.1:$PORT")
            .setSmallIcon(R.drawable.ic_bridge)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "relay"
        private const val NOTIF_ID = 1
        private const val WARM_INTERVAL_MS = 75_000L // refresh ahead of the 90s cache TTL
        const val PORT = 48010

        private const val ACTION_SWITCH_APP = "xr.steambridge.SWITCH_APP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LoopbackServerService::class.java))
        }

        /** Tell the running relay the active app changed — clears the cache and mints the new ticket. */
        fun switchApp(context: Context, appId: Int) {
            val intent = Intent(context, LoopbackServerService::class.java).apply {
                action = ACTION_SWITCH_APP
                putExtra("appId", appId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LoopbackServerService::class.java))
        }
    }
}

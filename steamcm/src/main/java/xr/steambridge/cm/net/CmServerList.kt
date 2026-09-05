package xr.steambridge.cm.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Resolves CM WebSocket endpoints via ISteamDirectory, with a hardcoded bootstrap fallback for when
 * the directory is unreachable (captive portals, DNS holes).
 *
 * GET ISteamDirectory/GetCMListForConnect/v1?cellid=0&cmtype=websockets returns a JSON serverlist of
 * "host:port" WebSocket endpoints; the wss URL is https over that host at /cmsocket/.
 */
class CmServerList(
    private val http: OkHttpClient = OkHttpClient(),
    private val onLog: (String) -> Unit = {},
) {
    /** Bootstrap endpoints (host:port). Used only if the directory call fails. */
    private val bootstrap = listOf(
        "ext1-iad1.steamserver.net:27021",
        "ext2-iad1.steamserver.net:27021",
        "ext1-sea1.steamserver.net:27021",
    )

    /** @return ordered list of "host:port" WebSocket CM endpoints. */
    suspend fun fetch(cellId: Int = 0): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.steampowered.com/ISteamDirectory/GetCMListForConnect/v1/" +
                "?cellid=$cellId&cmtype=websockets"
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    onLog("CM directory HTTP ${resp.code}; using bootstrap")
                    return@withContext bootstrap
                }
                val body = resp.body?.string() ?: return@withContext bootstrap
                val list = parse(body).ifEmpty { bootstrap }
                onLog("CM directory returned ${list.size} endpoints")
                list
            }
        } catch (e: Exception) {
            onLog("CM directory fetch failed: ${e.message}; using bootstrap")
            bootstrap
        }
    }

    /** Turn a "host:port" endpoint into the wss URL the CM WebSocket lives at. */
    fun toWsUrl(endpoint: String): String = "wss://$endpoint/cmsocket/"

    private fun parse(json: String): List<String> {
        val root = JSONObject(json)
        val response = root.optJSONObject("response") ?: return emptyList()
        val list = response.optJSONArray("serverlist") ?: return emptyList()
        val out = ArrayList<String>(list.length())
        for (i in 0 until list.length()) {
            val item = list.opt(i)
            when (item) {
                is String -> out.add(item)
                is JSONObject -> item.optString("endpoint").takeIf { it.isNotEmpty() }?.let(out::add)
            }
        }
        return out
    }
}

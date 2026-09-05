package xr.steambridge.cm.msg

/** One owned Steam game, from IPlayerService.GetOwnedGames. [isVr] is filled in later by VR detection. */
data class OwnedGame(
    val appId: Int,
    val name: String,
    val iconHash: String,
    val capsuleFilename: String?,
    val playtimeForeverMin: Int,
    val lastPlayedUnix: Int,
    val isVr: Boolean = false,
)

/**
 * Player.GetOwnedGames#1 — sent as an authed ServiceMethod after logon; returns the full library of the
 * signed-in owner. Field numbers pinned from SteamDatabase/Protobufs steammessages_player.steamclient.proto.
 */
object GetOwnedGames {
    const val METHOD = "Player.GetOwnedGames#1"

    fun request(steamId64: Long, language: String = "english"): ByteArray = ProtoWriter().apply {
        varint(1, steamId64)   // steamid (uint64) — MUST be the caller's own id
        bool(2, true)          // include_appinfo — required for name/icon/capsule
        bool(3, true)          // include_played_free_games
        string(7, language)
    }.toByteArray()

    /** Parse CPlayer_GetOwnedGames_Response.games (field 2, repeated Game). */
    fun parse(body: ByteArray): List<OwnedGame> {
        val games = ArrayList<OwnedGame>()
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                2 -> games.add(parseGame(r.readBytes()))
                else -> r.skip(f.wireType)
            }
        }
        return games
    }

    private fun parseGame(bytes: ByteArray): OwnedGame {
        var appId = 0; var name = ""; var icon = ""; var capsule: String? = null
        var playtime = 0; var lastPlayed = 0
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> appId = r.readVarintValue().toInt()      // appid
                2 -> name = r.readString()                    // name
                4 -> playtime = r.readVarintValue().toInt()   // playtime_forever
                5 -> icon = r.readString()                    // img_icon_url (hash)
                11 -> lastPlayed = r.readVarintValue().toInt() // rtime_last_played
                12 -> capsule = r.readString()                // capsule_filename
                else -> r.skip(f.wireType)
            }
        }
        return OwnedGame(appId, name, icon, capsule, playtime, lastPlayed)
    }
}

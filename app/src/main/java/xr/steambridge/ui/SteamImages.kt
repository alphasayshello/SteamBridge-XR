package xr.steambridge.ui

/** Public Steam CDN art URLs by appid. No auth; loaded at runtime by Coil. */
object SteamImages {
    private const val CDN = "https://cdn.cloudflare.steamstatic.com/steam/apps"
    private const val MEDIA = "https://media.steampowered.com/steamcommunity/public/images/apps"

    /** Portrait library capsule (2:3) — the grid tile. Missing on some newer titles → fall back. */
    fun capsule(appId: Int) = "$CDN/$appId/library_600x900.jpg"

    /** Landscape header (460x215) — present whenever the legacy path exists; capsule fallback. */
    fun header(appId: Int) = "$CDN/$appId/header.jpg"

    /** Tiny square icon from GetOwnedGames' img_icon_url hash — last-resort fallback. */
    fun icon(appId: Int, hash: String) = "$MEDIA/$appId/$hash.jpg"
}

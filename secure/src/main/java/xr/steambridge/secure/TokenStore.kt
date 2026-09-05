package xr.steambridge.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Encrypted persistence for the credentials we keep between launches.
 *
 * We store ONLY the refresh_token + guard_data (a machine-token JWT) + account_name + steamid — never
 * the password. Everything sits in EncryptedSharedPreferences under an Android Keystore AES-256-GCM
 * master key (StrongBox-backed where the device offers it, TEE otherwise). If the key is ever
 * invalidated (device credential removed, key store reset), the store throws on open; the caller wipes
 * and forces a fresh login.
 *
 * A stable per-install [machineSeed] also lives here so MachineId produces the same blob every launch
 * (machine-auth trust depends on that).
 */
class TokenStore private constructor(private val prefs: SharedPreferences) {

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) = prefs.edit().putStringOrRemove(KEY_REFRESH, v).apply()

    var guardData: String?
        get() = prefs.getString(KEY_GUARD, null)
        set(v) = prefs.edit().putStringOrRemove(KEY_GUARD, v).apply()

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(v) = prefs.edit().putStringOrRemove(KEY_ACCOUNT, v).apply()

    var steamId64: String?
        get() = prefs.getString(KEY_STEAMID, null)
        set(v) = prefs.edit().putStringOrRemove(KEY_STEAMID, v).apply()

    /** Stable per-install seed for MachineId. Generated once, then constant. */
    val machineSeed: String
        get() {
            prefs.getString(KEY_SEED, null)?.let { return it }
            val seed = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_SEED, seed).apply()
            return seed
        }

    val hasToken: Boolean get() = !refreshToken.isNullOrEmpty()

    fun saveSession(account: String, refresh: String, guard: String?, steamId: ULong) {
        prefs.edit()
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_REFRESH, refresh)
            .putStringOrRemove(KEY_GUARD, guard)
            .putString(KEY_STEAMID, steamId.toString())
            .apply()
    }

    /** Wipe credentials (keep the machine seed so machine trust survives a re-login). */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_REFRESH)
            .remove(KEY_GUARD)
            .remove(KEY_ACCOUNT)
            .remove(KEY_STEAMID)
            .apply()
    }

    companion object {
        private const val FILE = "steambridge_secure"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_GUARD = "guard_data"
        private const val KEY_ACCOUNT = "account_name"
        private const val KEY_STEAMID = "steamid"
        private const val KEY_SEED = "machine_seed"

        /**
         * Open the store, recovering from a corrupt/invalidated keystore by wiping and retrying once.
         */
        fun open(context: Context): TokenStore {
            return try {
                TokenStore(build(context))
            } catch (e: Exception) {
                context.deleteSharedPreferences(FILE)
                TokenStore(build(context))
            }
        }

        private fun build(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true) // silently falls back to TEE if StrongBox is absent
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private fun SharedPreferences.Editor.putStringOrRemove(key: String, value: String?): SharedPreferences.Editor =
            if (value == null) remove(key) else putString(key, value)
    }
}

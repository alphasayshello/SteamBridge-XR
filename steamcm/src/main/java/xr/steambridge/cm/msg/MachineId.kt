package xr.steambridge.cm.msg

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Builds the CMsgClientLogon.machine_id blob — a Steam binary-KeyValues "MessageObject" with three
 * SHA-1 fields (BB3, FF2, 3B3), matching SteamKit2's HardwareUtils.
 *
 * Binary KV encoding used here:
 *   0x00 <name\0>          begin object (type None)
 *     0x01 <key\0> <val\0> string entry
 *   0x08                   end object
 *   0x08                   end root
 *
 * The three values MUST be STABLE across logins for machine-auth trust to hold (a machine that keeps
 * changing its id re-triggers Steam Guard and gets risk-scored). So they are derived deterministically
 * from a per-install [seed] the caller persists once (e.g. a random UUID in TokenStore). Same seed ->
 * same blob, forever; a fresh install gets a new one.
 */
object MachineId {

    /**
     * @param seed a stable, per-install random string (persist it — never regenerate per login).
     * @return the machine_id blob bytes for CMsgClientLogon.machine_id.
     */
    fun build(seed: String): ByteArray {
        val out = ByteArrayOutputStream(128)
        out.write(0x00)
        writeCString(out, "MessageObject")
        writeStringEntry(out, "BB3", sha1Hex("$seed:BB3"))
        writeStringEntry(out, "FF2", sha1Hex("$seed:FF2"))
        writeStringEntry(out, "3B3", sha1Hex("$seed:3B3"))
        out.write(0x08)
        out.write(0x08)
        return out.toByteArray()
    }

    private fun writeStringEntry(out: ByteArrayOutputStream, key: String, value: String) {
        out.write(0x01)
        writeCString(out, key)
        writeCString(out, value)
    }

    private fun writeCString(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.UTF_8))
        out.write(0x00)
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }
}

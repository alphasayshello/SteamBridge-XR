package xr.steambridge.cm.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Security
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Encrypts the account password for BeginAuthSessionViaCredentials.
 *
 * Steam's Authentication.GetPasswordRSAPublicKey returns the RSA public key as hex `publickey_mod`
 * and `publickey_exp`. The password (UTF-8 bytes) is encrypted RSA/ECB/PKCS1Padding and Base64'd, then
 * sent as `encrypted_password` alongside the matching `encryption_timestamp`.
 *
 * BouncyCastle is pinned as the provider: OEM JCE providers on Quest/Horizon OS differ in RSA padding
 * defaults and would silently produce a blob Steam rejects.
 */
object PasswordCipher {
    private const val BC = BouncyCastleProvider.PROVIDER_NAME

    init {
        if (Security.getProvider(BC) == null) {
            // insertProviderAt(_, 1) would shadow the platform provider globally; append instead and
            // request BC explicitly per Cipher/KeyFactory so only our calls use it.
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * @param password     plaintext account password.
     * @param modulusHex   `publickey_mod` from GetPasswordRSAPublicKey (big-endian hex).
     * @param exponentHex  `publickey_exp` from GetPasswordRSAPublicKey (hex, typically "010001").
     * @return Base64 of the RSA-PKCS1v1.5 ciphertext, ready for `encrypted_password`.
     */
    fun encrypt(password: String, modulusHex: String, exponentHex: String): String {
        val modulus = BigInteger(1, hexToBytes(modulusHex))
        val exponent = BigInteger(1, hexToBytes(exponentHex))
        val key = KeyFactory.getInstance("RSA", BC)
            .generatePublic(RSAPublicKeySpec(modulus, exponent))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", BC)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ct)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.trim()
        require(s.length % 2 == 0) { "odd-length hex key component" }
        val out = ByteArray(s.length / 2)
        var i = 0
        while (i < s.length) {
            out[i / 2] = ((hexNibble(s[i]) shl 4) or hexNibble(s[i + 1])).toByte()
            i += 2
        }
        return out
    }

    private fun hexNibble(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("bad hex char '$c'")
    }
}

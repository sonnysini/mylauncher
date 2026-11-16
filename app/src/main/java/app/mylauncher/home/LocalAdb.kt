package app.mylauncher.home

import android.content.Context
import android.util.Base64
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * The Fire's own ADB, reached on 127.0.0.1:5555 (network debugging on). Commands run as the
 * shell user, which Android lets bring an app to the front right after Home: an app can't for
 * 5 seconds (app switch protection), and that permission is for system apps only.
 *
 * The first connection makes the TV ask "Allow USB debugging?" for this key ("Always allow").
 * The key lives in the app's private files; the dialog shows its name, [KEY_NAME].
 */
class LocalAdb(context: Context) : AutoCloseable {

    private val keyPair = loadOrCreateKey(File(context.filesDir, "adb"))
    private var dadb: Dadb? = null

    /** Opens a raw (legacy) shell stream: its output, line by line, is in [AdbStream.source]. */
    fun open(command: String): AdbStream = connection().open("shell:$command")

    /** Runs [command] to the end; a broken connection is dropped and retried once. */
    fun run(command: String) {
        repeat(2) { attempt ->
            val ok = runCatching { open(command).use { it.source.readUtf8() } }.isSuccess
            if (ok) return
            close()
            if (attempt == 1) throw IllegalStateException("adb: $command")
        }
    }

    @Synchronized
    private fun connection(): Dadb =
        dadb ?: Dadb.create(HOST, PORT, keyPair, connectTimeout = 3_000).also { dadb = it }

    @Synchronized
    override fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val KEY_NAME = "mylauncher@firetv"
        const val BITS = 2048

        /** PKCS#8 private key + the public key in ADB's own format, both made here once. */
        fun loadOrCreateKey(dir: File): AdbKeyPair {
            val priv = File(dir, "adbkey")
            val pub = File(dir, "adbkey.pub")
            if (!priv.exists() || !pub.exists()) {
                dir.mkdirs()
                val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(BITS) }.genKeyPair()
                priv.writeBytes(pair.private.encoded)
                val adbFormat = Base64.encodeToString(adbPublicKey(pair.public as RSAPublicKey), Base64.NO_WRAP)
                pub.writeText("$adbFormat $KEY_NAME")
            }
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(priv.readBytes()))
            // ADB sends the public key as a NUL-terminated string.
            return AdbKeyPair(privateKey, pub.readBytes() + 0)
        }

        /** android_pubkey layout: words, n0inv, modulus and R² little-endian, exponent. */
        fun adbPublicKey(key: RSAPublicKey): ByteArray {
            val words = BITS / 32
            val r32 = BigInteger.ZERO.setBit(32)
            var n = key.modulus
            var rr = BigInteger.ZERO.setBit(BITS).modPow(BigInteger.valueOf(2), n)
            val n0inv = n.remainder(r32).modInverse(r32)
            val buf = ByteBuffer.allocate(4 + 4 + BITS / 8 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(words)
            buf.putInt(n0inv.negate().toInt())
            repeat(words) {
                val (q, r) = n.divideAndRemainder(r32)
                buf.putInt(r.toInt()); n = q
            }
            repeat(words) {
                val (q, r) = rr.divideAndRemainder(r32)
                buf.putInt(r.toInt()); rr = q
            }
            buf.putInt(key.publicExponent.toInt())
            return buf.array()
        }
    }
}

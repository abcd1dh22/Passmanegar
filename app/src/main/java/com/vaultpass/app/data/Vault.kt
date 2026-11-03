package com.vaultpass.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val site: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** অ্যাপ/ডোমেইন নাম মেলে কিনা দেখে */
fun siteMatches(entrySite: String, query: String): Boolean {
    fun norm(s: String) = s.trim().lowercase()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .substringBefore('/')
    val a = norm(entrySite)
    val b = norm(query)
    return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
}

/** পুরো ভল্ট AES-256-GCM দিয়ে এনক্রিপ্ট করে; key থাকে Android Keystore-এ */
class VaultStore private constructor(ctx: Context) {
    private val file = File(ctx.applicationContext.filesDir, "vault.bin")
    private val alias = "vaultpass_vault_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        val raw = file.readBytes()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        val json = String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(
                id = o.getString("id"),
                title = o.getString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                site = o.optString("site"),
                notes = o.optString("notes"),
                updatedAt = o.optLong("updatedAt")
            )
        }
    }

    @Synchronized
    fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title)
                    .put("username", it.username).put("password", it.password)
                    .put("site", it.site).put("notes", it.notes)
                    .put("updatedAt", it.updatedAt)
            )
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "vault.tmp")
        tmp.writeBytes(out)
        tmp.renameTo(file)
    }

    companion object {
        @Volatile private var inst: VaultStore? = null
        fun get(ctx: Context): VaultStore =
            inst ?: synchronized(this) { inst ?: VaultStore(ctx).also { inst = it } }
    }
}

/** মাস্টার পাসওয়ার্ড (PBKDF2 হ্যাশ) ও বায়োমেট্রিক সেটিং */
class Auth(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun isSet() = p.contains("hash")

    var biometric: Boolean
        get() = p.getBoolean("bio", false)
        set(v) = p.edit().putBoolean("bio", v).apply()

    fun setPassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verify(pw: String): Boolean {
        val salt = Base64.decode(p.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = Base64.decode(p.getString("hash", "") ?: "", Base64.NO_WRAP)
        return MessageDigest.isEqual(want, hash(pw, salt))
    }

    private fun hash(pw: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, 150_000, 256)).encoded
}

object Gen {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGIT = "23456789"
    private const val SYM = "!@#$%^&*-_=+?"

    fun generate(len: Int): String {
        val r = SecureRandom()
        val sets = listOf(LOWER, UPPER, DIGIT, SYM)
        val all = sets.joinToString("")
        val chars = sets.map { it[r.nextInt(it.length)] }.toMutableList()
        while (chars.size < len) chars.add(all[r.nextInt(all.length)])
        chars.shuffle(r)
        return chars.joinToString("")
    }

    /** 0..4 */
    fun strength(p: String): Int {
        if (p.isEmpty()) return 0
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() } && p.length >= 10) s++
        return s
    }
}

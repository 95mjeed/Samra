package com.samra.downloader.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File

/**
 * Per-source credentials, encrypted at rest (EncryptedSharedPreferences, Keystore-backed).
 *
 * Fails CLOSED: if the encrypted store cannot be initialised (Keystore quirk, lib failure),
 * credentials are simply NOT persisted — they stay in memory for the session only. They are
 * NEVER written to a plaintext store (the old plaintext fallback has been removed).
 */
object CredentialStore {

    private fun prefs(ctx: Context): SharedPreferences? = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "samra_creds", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        null // fail closed — never persist credentials in plaintext
    }

    fun save(ctx: Context, id: String, username: String, password: String, cookiePath: String?) {
        // Purge any legacy plaintext store left by older builds.
        runCatching { ctx.deleteSharedPreferences("samra_creds_plain") }
        prefs(ctx)?.edit()
            ?.putString("$id.u", username)
            ?.putString("$id.p", password)
            ?.putString("$id.c", cookiePath ?: "")
            ?.apply()
    }

    fun clear(ctx: Context, id: String) {
        prefs(ctx)?.edit()?.remove("$id.u")?.remove("$id.p")?.remove("$id.c")?.apply()
        // Delete any imported cookie session file so it can't be replayed or backed up.
        runCatching { File(ctx.filesDir, "cookies_$id.txt").delete() }
    }

    data class Cred(val username: String, val password: String, val cookie: String?)

    fun load(ctx: Context, id: String): Cred? {
        val p = prefs(ctx) ?: return null
        if (!p.contains("$id.u") && !p.contains("$id.c")) return null
        return Cred(
            p.getString("$id.u", "") ?: "",
            p.getString("$id.p", "") ?: "",
            p.getString("$id.c", "")?.ifBlank { null },
        )
    }

    /** JSON map: { sourceId: {u, p, c} } for the given connected source ids. */
    fun allJson(ctx: Context, ids: List<String>): String {
        val root = JSONObject()
        for (id in ids) {
            val cr = load(ctx, id) ?: continue
            root.put(id, JSONObject().apply {
                put("u", cr.username)
                put("p", cr.password)
                put("c", cr.cookie ?: "")
            })
        }
        return root.toString()
    }
}

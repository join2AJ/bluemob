package com.bluemob.app.crypto

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * SharedPreferences whose names and values are encrypted on disk. Names are replaced by a keyed hash, values are
 * sealed with AES-256-GCM using a Keystore key ([KeystoreBox]). The file on disk reveals nothing: not your name,
 * contacts, SOS numbers or settings.
 *
 * [open] moves anything an older version stored in plain preferences into the encrypted file, then deletes the old one.
 */
class SecurePrefs private constructor(private val backing: SharedPreferences, private val box: KeystoreBox) : SharedPreferences {
    private val cache = HashMap<String, Any?>()
    private val lock = Any()

    private fun slot(name: String): String = Crypto.encode(Crypto.sha256(("bluemob-pref|" + name).toByteArray())).take(22)

    private fun read(name: String): Any? = synchronized(lock) {
        if (cache.containsKey(name)) return cache[name]
        val raw = backing.getString(slot(name), null)
        val value = raw?.let { Crypto.decode(it) }?.let(box::open)?.let { decodeValue(String(it)) }
        cache[name] = value
        value
    }

    private fun encodeValue(v: Any?): String = when (v) {
        is String -> "s|$v"
        is Boolean -> "b|$v"
        is Int -> "i|$v"
        is Long -> "l|$v"
        is Float -> "f|$v"
        is Set<*> -> "S|" + org.json.JSONArray(v.map { it.toString() }).toString()
        else -> "n|"
    }

    private fun decodeValue(s: String): Any? {
        val t = s.substringBefore("|")
        val p = s.substringAfter("|")
        return when (t) {
            "s" -> p
            "b" -> p.toBoolean()
            "i" -> p.toInt()
            "l" -> p.toLong()
            "f" -> p.toFloat()
            "S" -> org.json.JSONArray(p).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            else -> null
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { HashMap(cache.filterValues { it != null }) }
    override fun getString(key: String, defValue: String?): String? = read(key) as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = (read(key) as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = read(key) as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = read(key) as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = read(key) as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key) as? Boolean ?: defValue
    override fun contains(key: String): Boolean = read(key) != null
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(k: String, v: String?) = apply { changes[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { changes[k] = v?.toSet() }
        override fun putInt(k: String, v: Int) = apply { changes[k] = v }
        override fun putLong(k: String, v: Long) = apply { changes[k] = v }
        override fun putFloat(k: String, v: Float) = apply { changes[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { changes[k] = v }
        override fun remove(k: String) = apply { changes[k] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { write().commit(); return true }
        override fun apply() = write().apply()

        private fun write(): SharedPreferences.Editor = synchronized(lock) {
            val e = backing.edit()
            if (clear) { e.clear(); cache.clear() }
            changes.forEach { (k, v) ->
                cache[k] = v
                if (v == null) e.remove(slot(k)) else e.putString(slot(k), Crypto.encode(box.seal(encodeValue(v).toByteArray())))
            }
            e
        }
    }

    companion object {
        private val box by lazy { KeystoreBox("bluemob_prefs") }

        fun open(context: Context, name: String): SharedPreferences {
            val secure = SecurePrefs(context.getSharedPreferences("$name.enc", Context.MODE_PRIVATE), box)
            // Move data from older, unencrypted versions, then delete the plain file.
            val plain = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val old = plain.all
            if (old.isNotEmpty()) {
                val e = secure.edit()
                old.forEach { (k, v) ->
                    when (v) {
                        is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v)
                        is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v)
                        is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toMutableSet())
                    }
                }
                e.commit()
                plain.edit().clear().commit()
                context.deleteSharedPreferences(name)
            }
            return secure
        }

        /** True if a file on disk still holds readable text, for the self-check in the security screen. */
        fun plainFilesLeft(context: Context): List<String> =
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().map { it.name }
                .filter { !it.endsWith(".enc.xml") && it.endsWith(".xml") && it != "db_key.xml" && !it.startsWith("WebView") && !it.startsWith("com.google") }
    }
}

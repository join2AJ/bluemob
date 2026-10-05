package com.bluemob.app.data

import android.content.Context
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.KeystoreBox
import java.io.File
import java.security.SecureRandom

/**
 * The database is encrypted with SQLCipher (AES-256) using a random 32-byte key. That key is stored sealed by a
 * Keystore key, so the database file is unreadable off the phone. A database left by an older, unencrypted version
 * is converted in place on first launch.
 */
object DbKey {
    private const val PREFS = "db_key"
    private fun box(context: Context) = KeystoreBox("bluemob_db", context.getSharedPreferences("keystore_fallback", Context.MODE_PRIVATE))

    /** The passphrase given to SQLCipher: the key as hex text, so the converter below and Room agree exactly. */
    fun passphrase(context: Context): ByteArray = rawKey(context).joinToString("") { "%02x".format(it) }.toByteArray()

    private fun rawKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val box = box(context)
        prefs.getString("sealed", null)?.let(Crypto::decode)?.let(box::open)?.let { return it }
        // If a sealed key exists but can't be opened, the database can't be read: say so instead of silently starting over.
        check(prefs.getString("sealed", null) == null) { "The database key can't be unlocked on this phone (Keystore changed)" }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        prefs.edit().putString("sealed", Crypto.encode(box.seal(key))).commit()
        return key
    }

    /** True if [file] is a plain (unencrypted) SQLite database. */
    fun isPlain(file: File): Boolean = file.exists() && file.length() >= 16 &&
        file.inputStream().use { s -> ByteArray(16).also { s.read(it) } }.contentEquals("SQLite format 3\u0000".toByteArray())

    /** Encrypts an old plain database into a new file with SQLCipher's export, then swaps it in. */
    fun encryptInPlace(context: Context, name: String, key: ByteArray) {
        val plain = context.getDatabasePath(name)
        if (!isPlain(plain)) return
        System.loadLibrary("sqlcipher")
        val tmp = File(plain.parentFile, "$name.encrypting")
        tmp.delete()
        val pass = String(key)
        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(plain.path, "", null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE, null, null)
        db.rawExecSQL("ATTACH DATABASE '${tmp.path}' AS encrypted KEY '$pass'")
        db.rawExecSQL("SELECT sqlcipher_export('encrypted')")
        db.rawExecSQL("PRAGMA encrypted.user_version = ${db.version}")
        db.rawExecSQL("DETACH DATABASE encrypted")
        db.close()
        listOf("", "-wal", "-shm", "-journal").forEach { File(plain.path + it).delete() }
        tmp.renameTo(plain)
    }
}

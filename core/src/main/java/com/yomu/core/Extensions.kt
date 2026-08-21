package com.yomu.core

import android.content.Context
import android.widget.Toast
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun <T> Flow<T>.handleErrors(onError: (Throwable) -> Unit): Flow<T> {
    return this.catch { e ->
        onError(e)
    }
}

/** SHA-256 hex of a file's contents. Matches HuggingFace's LFS `oid`, so it doubles as integrity
 *  verification for HF-sourced downloads (#90). Streams in 8KB chunks — never loads the file whole. */
fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun Long.toFileSizeString(): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    
    return when {
        this >= gb -> String.format(Locale.US, "%.2f GB", this / gb)
        this >= mb -> String.format(Locale.US, "%.2f MB", this / mb)
        this >= kb -> String.format(Locale.US, "%.2f KB", this / kb)
        else -> "$this B"
    }
}

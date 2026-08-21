package com.yomu.app.translation.hf

import com.yomu.core.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a HuggingFace-authenticated download (#90 part C). */
enum class HfDownloadResult {
    SUCCESS,

    /** 401/403 — the user's HF account has not accepted the model's gate. Prompt terms acceptance,
     *  then retry with the same token. */
    GATED,

    FAILED
}

/**
 * Authenticated, resumable GGUF download from the HuggingFace API under the user's own token
 * (ADR-0009 tiers 2/3, #90 part C). Sends a `Range` header to resume a partial file, a Bearer token
 * for gated repos, and verifies the finished file's SHA-256 against HF's LFS `oid`. A gated 401/403
 * is reported distinctly so the UI can send the user to accept the model's terms and retry.
 */
@Singleton
class HfModelDownloader @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun download(
        url: String,
        dest: File,
        expectedSha256: String,
        token: String,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): HfDownloadResult = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val existing = if (dest.exists()) dest.length() else 0L

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .apply { rangeHeader(existing)?.let { header("Range", it) } }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (_: Exception) {
            return@withContext HfDownloadResult.FAILED
        }

        response.use { resp ->
            if (isGated(resp.code)) return@withContext HfDownloadResult.GATED
            // 416 = the file is already complete; skip straight to verification. Otherwise a
            // non-success code is a hard failure.
            if (resp.code != 416) {
                if (!resp.isSuccessful) return@withContext HfDownloadResult.FAILED
                val body = resp.body ?: return@withContext HfDownloadResult.FAILED
                // 206 means the server honored the Range and is sending the tail; append to what we
                // have. Anything else (200) is the whole file — start clean so we never concatenate a
                // fresh full body onto a stale partial.
                val append = resp.code == 206
                val startBytes = if (append) existing else 0L
                val totalBytes = startBytes + (body.contentLength().coerceAtLeast(0L))
                if (!append && dest.exists()) dest.delete()

                try {
                    RandomAccessFile(dest, "rw").use { out ->
                        out.seek(startBytes)
                        var written = startBytes
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                written += read
                                onProgress(written, totalBytes)
                            }
                        }
                    }
                } catch (_: Exception) {
                    return@withContext HfDownloadResult.FAILED
                }
            }
        }

        // Integrity: HF's LFS oid IS the sha256 of the content, so this both detects corruption and
        // proves we fetched the file we intended. A mismatch deletes the file so a retry starts clean.
        if (dest.exists() && dest.sha256() == expectedSha256) {
            HfDownloadResult.SUCCESS
        } else {
            dest.delete()
            HfDownloadResult.FAILED
        }
    }

    companion object {
        /** Resume header for a partially-downloaded file; null when nothing is on disk yet. */
        fun rangeHeader(existingBytes: Long): String? =
            if (existingBytes > 0) "bytes=$existingBytes-" else null

        /** A gated repo the user's account has not unlocked answers 401 or 403. */
        fun isGated(httpCode: Int): Boolean = httpCode == 401 || httpCode == 403
    }
}

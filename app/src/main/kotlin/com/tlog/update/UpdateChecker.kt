package com.tlog.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the project's GitHub Releases for a newer APK and installs it via the
 * system package installer. Completely free — works against public or private
 * repos (private repos need a token, not wired up here since personal use only
 * needs a public repo).
 */
object UpdateChecker {

    data class Available(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val notes: String
    )

    /** Returns non-null when a strictly newer release exists. */
    suspend fun check(): Available? = withContext(Dispatchers.IO) {
        val owner = BuildConfig.UPDATE_OWNER
        val repo = BuildConfig.UPDATE_REPO
        if (owner.isBlank() || repo.isBlank() || owner == "REPLACE_ME_GITHUB_USER") return@withContext null

        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TLog-Updater")
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trimStart('v')
            if (tag.isBlank()) return@withContext null
            val notes = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            apkUrl ?: return@withContext null

            // Compare versions. tag format is "MAJOR.MINOR.PATCH" or "MAJOR.MINOR.PATCH+CODE"
            val (tagName, tagCode) = parseTag(tag)
            val newer = isNewer(tagName, tagCode, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            if (!newer) return@withContext null

            Available(tagName, tagCode ?: 0, apkUrl, notes)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTag(tag: String): Pair<String, Int?> {
        val plus = tag.indexOf('+')
        return if (plus > 0) tag.substring(0, plus) to tag.substring(plus + 1).toIntOrNull()
        else tag to null
    }

    private fun isNewer(
        remoteName: String, remoteCode: Int?,
        localName: String, localCode: Int
    ): Boolean {
        if (remoteCode != null) return remoteCode > localCode
        return compareSemver(remoteName, localName.substringBefore('-')) > 0
    }

    private fun compareSemver(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** Downloads the APK into cache/updates and returns the file. */
    suspend fun download(context: Context, available: Available): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean out old APKs so cache doesn't bloat.
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "TLog-${available.versionName}.apk")
        (URL(available.downloadUrl).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TLog-Updater")
            connectTimeout = 15000
            readTimeout = 60000
            inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            disconnect()
        }
        out
    }

    /** Launches the system installer for the given APK file. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

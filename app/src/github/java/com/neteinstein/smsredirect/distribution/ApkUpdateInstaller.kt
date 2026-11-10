package com.neteinstein.smsredirect.distribution

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateInstallResult
import com.neteinstein.smsredirect.domain.update.AppUpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK into the cache and opens the system package installer on it.
 *
 * Android itself refuses an update signed with a different key, so a download can never
 * replace this app with someone else's build; the package-name check below just turns that
 * case (or a corrupt download) into a clear error instead of a confusing installer screen.
 */
class ApkUpdateInstaller(
    private val context: Context,
) : AppUpdateInstaller {
    override suspend fun downloadAndInstall(
        release: AppRelease,
        onProgress: (Int?) -> Unit,
    ): AppUpdateInstallResult {
        val apkUrl = release.apkUrl ?: return AppUpdateInstallResult.NoApk

        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return AppUpdateInstallResult.NeedsInstallPermission
        }

        return try {
            val apk = withContext(Dispatchers.IO) { download(apkUrl, onProgress) }
            val archivePackage = context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
            if (archivePackage != context.packageName) {
                apk.delete()
                return AppUpdateInstallResult.Failed
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppUpdateInstallResult.InstallerOpened
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            AppUpdateInstallResult.Failed
        }
    }

    private suspend fun download(
        url: String,
        onProgress: (Int?) -> Unit,
    ): File {
        // Lives under cache-path, which res/xml/file_paths.xml already exposes to FileProvider.
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update.apk")

        // GitHub redirects release downloads to its CDN (HTTPS to HTTPS), which
        // HttpURLConnection follows on its own.
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "SMS-Toolbox-Android")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("APK download returned HTTP $code")
            val total = connection.contentLengthLong
            onProgress(if (total > 0) 0 else null)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        return target
    }

    private companion object {
        const val TAG = "ApkUpdateInstaller"
        const val UPDATE_DIR = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
    }
}

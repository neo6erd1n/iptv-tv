package ru.iptvtv.platform.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import ru.iptvtv.domain.model.AppUpdate

class AppUpdateDownloader(
    private val activity: Activity,
) {
    private val downloadManager =
        activity.getSystemService(DownloadManager::class.java)
    private var activeDownloadId: Long? = null
    private var pendingInstallUri: Uri? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val completedId = intent.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID,
                -1L,
            )
            if (completedId != activeDownloadId) return

            pendingInstallUri = downloadManager.getUriForDownloadedFile(completedId)
            if (pendingInstallUri == null) {
                Toast.makeText(
                    activity,
                    "Не удалось скачать обновление",
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            installIfAllowed()
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            activity,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(downloadReceiver) }
    }

    fun download(update: AppUpdate) {
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("IPTV TV ${update.version}")
            .setDescription("Загрузка обновления")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "IPTV-TV-${update.version}.apk",
            )

        activeDownloadId = downloadManager.enqueue(request)
        Toast.makeText(
            activity,
            "Обновление загружается",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun resumePendingInstall() {
        if (pendingInstallUri != null) installIfAllowed()
    }

    private fun installIfAllowed() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            Toast.makeText(
                activity,
                "Разрешите установку обновлений для IPTV TV",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val uri = pendingInstallUri ?: return
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        pendingInstallUri = null
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

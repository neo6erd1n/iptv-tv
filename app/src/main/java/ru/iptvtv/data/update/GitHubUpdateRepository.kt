package ru.iptvtv.data.update

import org.json.JSONObject
import ru.iptvtv.domain.model.AppUpdate
import ru.iptvtv.domain.repository.UpdateRepository
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateRepository(
    private val repository: String,
) : UpdateRepository {

    override suspend fun getAvailableUpdate(currentVersion: String): AppUpdate? {
        if (repository.isBlank()) return null

        val connection = URL(
            "https://api.github.com/repos/$repository/releases/latest",
        ).openConnection() as HttpURLConnection

        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val remoteVersion = release.getString("tag_name").removePrefix("v")
            if (compareVersions(remoteVersion, currentVersion) <= 0) return null

            val assets = release.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .asSequence()
                .map(assets::getJSONObject)
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: return null

            AppUpdate(
                version = remoteVersion,
                title = release.optString("name").ifBlank { "Версия $remoteVersion" },
                notes = release.optString("body"),
                downloadUrl = apkUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        return (0 until maxOf(a.size, b.size))
            .firstNotNullOfOrNull { index ->
                (a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 })
                    .takeIf { it != 0 }
            } ?: 0
    }
}

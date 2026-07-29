package ru.iptvtv.data.channel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.iptvtv.domain.model.Channel
import ru.iptvtv.domain.repository.ChannelRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class M3uChannelRepository(
    context: Context,
) : ChannelRepository {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

    override suspend fun getChannels(playlistUrl: String): List<Channel> {
        readCache(playlistUrl)?.let { return it }

        val connection = URL(playlistUrl).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Encoding", "identity")

            if (connection.responseCode !in 200..299) {
                error("Сервер вернул код ${connection.responseCode}")
            }

            val content = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .removePrefix("\uFEFF")
                .trimStart()

            if (!content.startsWith("#EXTM3U", ignoreCase = true)) {
                error("Адрес не содержит плейлист M3U")
            }

            if (
                content.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
                content.contains("#EXT-X-STREAM-INF", ignoreCase = true)
            ) {
                return listOf(
                    Channel(
                        id = playlistUrl.hashCode().toString(),
                        name = "Мой канал",
                        streamUrl = playlistUrl,
                        category = DEFAULT_CATEGORY,
                    ),
                )
            }

            parseIptvPlaylist(content, playlistUrl).also {
                if (it.isEmpty()) error("В плейлисте не найдено каналов")
                writeCache(playlistUrl, it)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(playlistUrl: String): List<Channel>? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        if (root.optInt("version") != CACHE_VERSION) return null
        if (root.optString("source") != playlistUrl) return null
        val items = root.getJSONArray("channels")
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Channel(
                id = item.getString("id"),
                name = item.getString("name"),
                streamUrl = item.getString("url"),
                category = item.optString("category").ifBlank { DEFAULT_CATEGORY },
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCache(playlistUrl: String, channels: List<Channel>) {
        runCatching {
            val items = JSONArray()
            channels.forEach { channel ->
                items.put(
                    JSONObject()
                        .put("id", channel.id)
                        .put("name", channel.name)
                        .put("url", channel.streamUrl)
                        .put("category", channel.category),
                )
            }
            cacheFile.writeText(
                JSONObject()
                    .put("version", CACHE_VERSION)
                    .put("source", playlistUrl)
                    .put("channels", items)
                    .toString(),
            )
        }
    }

    private fun parseIptvPlaylist(content: String, playlistUrl: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingCategory = DEFAULT_CATEGORY

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(',', "")
                        .trim()
                        .ifBlank { extractAttribute(line, "tvg-name") }
                        .ifBlank { "Канал ${channels.size + 1}" }
                    pendingCategory = extractAttribute(line, "group-title")
                        .trim()
                        .ifBlank { DEFAULT_CATEGORY }
                }
                line.isNotEmpty() && !line.startsWith("#") && pendingName != null -> {
                    val resolvedUrl = runCatching {
                        URI(playlistUrl).resolve(line).toString()
                    }.getOrDefault(line)
                    channels += Channel(
                        id = "${channels.size}-${resolvedUrl.hashCode()}",
                        name = pendingName.orEmpty(),
                        streamUrl = resolvedUrl,
                        category = pendingCategory,
                    )
                    pendingName = null
                    pendingCategory = DEFAULT_CATEGORY
                }
            }
        }
        return channels
    }

    private fun extractAttribute(line: String, name: String): String =
        Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private companion object {
        const val CACHE_FILE_NAME = "playlist-cache.json"
        const val CACHE_VERSION = 2
        const val DEFAULT_CATEGORY = "Без категории"
        const val USER_AGENT = "IPTV-TV/0.2 Android"
    }
}

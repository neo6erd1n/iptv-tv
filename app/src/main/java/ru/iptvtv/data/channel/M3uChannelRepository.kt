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
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class M3uChannelRepository(
    context: Context,
) : ChannelRepository {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val epgCacheFile = File(context.filesDir, EPG_CACHE_FILE_NAME)

    override suspend fun getChannels(playlistUrl: String, epgUrl: String): List<Channel> {
        readCache(playlistUrl, epgUrl)?.let { return it }

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

            val parsedPlaylist = parseIptvPlaylist(content, playlistUrl)
            if (parsedPlaylist.channels.isEmpty()) {
                error("В плейлисте не найдено каналов")
            }
            parsedPlaylist.channels.map { parsedChannel ->
                parsedChannel.channel.copy(epgId = parsedChannel.epgId)
            }.also { writeCache(playlistUrl, epgUrl, it) }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun loadCurrentPrograms(
        channels: List<Channel>,
        epgUrl: String,
        forceRefresh: Boolean,
    ): List<Channel> {
        if (epgUrl.isBlank()) return channels
        val schedule = if (forceRefresh) {
            loadEpgSchedule(
                epgUrl.split(',').map(String::trim).filter(String::isNotBlank),
            ).restrictTo(channels).also { writeEpgCache(epgUrl, it) }
        } else {
            readEpgCache(epgUrl)?.second ?: return channels
        }
        val now = System.currentTimeMillis()
        return channels.map { channel ->
            val channelKey = normalizeEpgKey(channel.epgId.ifBlank { channel.name })
            val resolvedId = schedule.aliases[channelKey] ?: channelKey
            val program = schedule.programs.firstOrNull {
                normalizeEpgKey(it.channelId) == resolvedId && now in it.start until it.end
            }
            channel.copy(
                currentProgram = program?.title,
                currentProgramStart = program?.start,
                currentProgramEnd = program?.end,
            )
        }
    }

    override suspend fun getLastEpgUpdateAt(epgUrl: String): Long? =
        readEpgCache(epgUrl)?.first

    override suspend fun shouldRefreshEpg(epgUrl: String): Boolean {
        val updatedAt = getLastEpgUpdateAt(epgUrl) ?: return true
        return System.currentTimeMillis() - updatedAt >= EPG_REFRESH_INTERVAL_MS
    }

    private fun readCache(playlistUrl: String, epgUrl: String): List<Channel>? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        if (root.optInt("version") != CACHE_VERSION) return null
        if (root.optString("source") != playlistUrl) return null
        if (root.optString("epgSource") != epgUrl) return null
        val savedAt = root.optLong("savedAt")
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > CACHE_TTL_MS) return null
        val items = root.getJSONArray("channels")
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Channel(
                id = item.getString("id"),
                name = item.getString("name"),
                streamUrl = item.getString("url"),
                category = item.optString("category").ifBlank { DEFAULT_CATEGORY },
                epgId = item.optString("epgId"),
                currentProgram = item.optString("currentProgram").ifBlank { null },
                currentProgramStart = item.optLong("currentProgramStart").takeIf { it > 0L },
                currentProgramEnd = item.optLong("currentProgramEnd").takeIf { it > 0L },
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCache(playlistUrl: String, epgUrl: String, channels: List<Channel>) {
        runCatching {
            val items = JSONArray()
            channels.forEach { channel ->
                items.put(
                    JSONObject()
                        .put("id", channel.id)
                        .put("name", channel.name)
                        .put("url", channel.streamUrl)
                        .put("category", channel.category)
                        .put("epgId", channel.epgId)
                        .put("currentProgram", channel.currentProgram ?: "")
                        .put("currentProgramStart", channel.currentProgramStart ?: 0L)
                        .put("currentProgramEnd", channel.currentProgramEnd ?: 0L),
                )
            }
            cacheFile.writeText(
                JSONObject()
                    .put("version", CACHE_VERSION)
                    .put("source", playlistUrl)
                    .put("epgSource", epgUrl)
                    .put("savedAt", System.currentTimeMillis())
                    .put("channels", items)
                    .toString(),
            )
        }
    }

    private fun parseIptvPlaylist(content: String, playlistUrl: String): ParsedPlaylist {
        val channels = mutableListOf<ParsedChannel>()
        val epgUrls = linkedSetOf<String>()
        content.lineSequence().firstOrNull { it.trim().startsWith("#EXTM3U", true) }
            ?.let { header ->
                listOf("url-tvg", "x-tvg-url").forEach { attribute ->
                    extractAttribute(header, attribute)
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .mapTo(epgUrls) { resolveUrl(playlistUrl, it) }
                }
            }
        var pendingName: String? = null
        var pendingCategory = DEFAULT_CATEGORY
        var pendingEpgId = ""

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
                    pendingEpgId = extractAttribute(line, "tvg-id")
                        .ifBlank { extractAttribute(line, "tvg-name") }
                        .trim()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) &&
                    pendingName != null -> {
                    pendingCategory = line.substringAfter(':')
                        .trim()
                        .ifBlank { DEFAULT_CATEGORY }
                }
                line.isNotEmpty() && !line.startsWith("#") && pendingName != null -> {
                    val resolvedUrl = resolveUrl(playlistUrl, line)
                    val channel = Channel(
                        id = "${channels.size}-${resolvedUrl.hashCode()}",
                        name = pendingName.orEmpty(),
                        streamUrl = resolvedUrl,
                        category = pendingCategory,
                    )
                    channels += ParsedChannel(
                        channel = channel,
                        epgId = pendingEpgId.ifBlank { channel.name },
                    )
                    pendingName = null
                    pendingCategory = DEFAULT_CATEGORY
                    pendingEpgId = ""
                }
            }
        }
        return ParsedPlaylist(channels, epgUrls.toList())
    }

    private fun loadEpgSchedule(epgUrls: List<String>): EpgSchedule {
        val programs = mutableListOf<ScheduledProgram>()
        val aliases = mutableMapOf<String, String>()
        epgUrls.forEach { epgUrl ->
            val schedule = readEpgSchedule(epgUrl)
            programs += schedule.programs
            aliases += schedule.aliases
        }
        if (programs.isEmpty()) error("В EPG не найдено программ")
        return EpgSchedule(programs, aliases)
    }

    private fun readEpgSchedule(epgUrl: String): EpgSchedule {
        val connection = URL(epgUrl).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            if (connection.responseCode !in 200..299) {
                error("EPG-сервер вернул код ${connection.responseCode}")
            }

            val rawInput = PushbackInputStream(connection.inputStream.buffered(), 2)
            val signature = ByteArray(2)
            val signatureSize = rawInput.read(signature)
            if (signatureSize > 0) rawInput.unread(signature, 0, signatureSize)
            val hasGzipSignature =
                signatureSize == 2 &&
                    signature[0] == 0x1f.toByte() &&
                    signature[1] == 0x8b.toByte()
            val input = if (
                connection.contentEncoding.equals("gzip", true) ||
                hasGzipSignature
            ) {
                GZIPInputStream(rawInput)
            } else {
                rawInput
            }
            input.use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                parseEpgSchedule(parser, System.currentTimeMillis())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEpgSchedule(
        parser: XmlPullParser,
        now: Long,
    ): EpgSchedule {
        val programs = mutableListOf<ScheduledProgram>()
        val channelNames = mutableMapOf<String, MutableList<String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                val channelId = parser.getAttributeValue(null, "id").orEmpty()
                var innerEvent = parser.next()
                while (!(innerEvent == XmlPullParser.END_TAG && parser.name == "channel")) {
                    if (innerEvent == XmlPullParser.START_TAG && parser.name == "display-name") {
                        parser.nextText().trim().takeIf(String::isNotBlank)?.let { name ->
                            channelNames.getOrPut(channelId) { mutableListOf() } += name
                        }
                    }
                    innerEvent = parser.next()
                }
            } else if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channelId = parser.getAttributeValue(null, "channel").orEmpty()
                val start = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                val stop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                var title: String? = null
                var innerEvent = parser.next()
                while (!(innerEvent == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (innerEvent == XmlPullParser.START_TAG && parser.name == "title") {
                        title = parser.nextText().trim()
                    }
                    innerEvent = parser.next()
                }
                if (
                    channelId.isNotBlank() &&
                    title?.isNotBlank() == true &&
                    start != null &&
                    stop != null &&
                    stop > now &&
                    start < now + EPG_SCHEDULE_WINDOW_MS
                ) {
                    programs += ScheduledProgram(channelId, title, start, stop)
                }
            }
            event = parser.next()
        }
        val aliases = mutableMapOf<String, String>()
        programs.map(ScheduledProgram::channelId).distinct().forEach { channelId ->
            aliases[normalizeEpgKey(channelId)] = normalizeEpgKey(channelId)
            channelNames[channelId].orEmpty().forEach { name ->
                aliases[normalizeEpgKey(name)] = normalizeEpgKey(channelId)
            }
        }
        return EpgSchedule(programs, aliases)
    }

    private fun readEpgCache(epgUrl: String): Pair<Long, EpgSchedule>? = runCatching {
        if (!epgCacheFile.exists()) return null
        val root = JSONObject(epgCacheFile.readText())
        if (root.optInt("version") != EPG_CACHE_VERSION) return null
        if (root.optString("source") != epgUrl) return null
        val updatedAt = root.optLong("updatedAt").takeIf { it > 0L } ?: return null
        val programItems = root.getJSONArray("programs")
        val programs = List(programItems.length()) { index ->
            val item = programItems.getJSONObject(index)
            ScheduledProgram(
                channelId = item.getString("channel"),
                title = item.getString("title"),
                start = item.getLong("start"),
                end = item.getLong("end"),
            )
        }
        val aliasItems = root.getJSONObject("aliases")
        val aliases = aliasItems.keys().asSequence().associateWith(aliasItems::getString)
        updatedAt to EpgSchedule(programs, aliases)
    }.getOrNull()

    private fun writeEpgCache(epgUrl: String, schedule: EpgSchedule) {
        val programs = JSONArray()
        schedule.programs.forEach { program ->
            programs.put(
                JSONObject()
                    .put("channel", program.channelId)
                    .put("title", program.title)
                    .put("start", program.start)
                    .put("end", program.end),
            )
        }
        val aliases = JSONObject()
        schedule.aliases.forEach(aliases::put)
        val completedAt = System.currentTimeMillis()
        epgCacheFile.writeText(
            JSONObject()
                .put("version", EPG_CACHE_VERSION)
                .put("source", epgUrl)
                .put("updatedAt", completedAt)
                .put("programs", programs)
                .put("aliases", aliases)
                .toString(),
        )
    }

    private fun parseXmlTvTime(value: String?): Long? {
        val normalized = value?.trim().orEmpty()
        val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmss")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }.parse(normalized)?.time
            }.getOrNull()
        }
    }

    private fun resolveUrl(baseUrl: String, value: String): String = runCatching {
        URI(baseUrl).resolve(value).toString()
    }.getOrDefault(value)

    private fun normalizeEpgKey(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun EpgSchedule.restrictTo(channels: List<Channel>): EpgSchedule {
        val wantedIds = channels.map { channel ->
            val key = normalizeEpgKey(channel.epgId.ifBlank { channel.name })
            aliases[key] ?: key
        }.toSet()
        return copy(
            programs = programs.filter { normalizeEpgKey(it.channelId) in wantedIds },
            aliases = aliases.filterValues { it in wantedIds },
        )
    }

    private fun extractAttribute(line: String, name: String): String {
        val match = Regex(
            """(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\s]+))""",
            RegexOption.IGNORE_CASE,
        ).find(line) ?: return ""
        return match.groupValues.drop(1).firstOrNull(String::isNotBlank).orEmpty()
    }

    private companion object {
        const val CACHE_FILE_NAME = "playlist-cache.json"
        const val EPG_CACHE_FILE_NAME = "epg-cache.json"
        const val CACHE_VERSION = 5
        const val EPG_CACHE_VERSION = 1
        const val CACHE_TTL_MS = 5 * 60 * 1_000L
        const val EPG_REFRESH_INTERVAL_MS = 12L * 60 * 60 * 1_000
        const val EPG_SCHEDULE_WINDOW_MS = 36L * 60 * 60 * 1_000
        const val DEFAULT_CATEGORY = "Без категории"
        const val USER_AGENT = "IPTV-TV/0.2 Android"
    }

    private data class ParsedChannel(
        val channel: Channel,
        val epgId: String,
    )

    private data class ParsedPlaylist(
        val channels: List<ParsedChannel>,
        val epgUrls: List<String>,
    )

    private data class ScheduledProgram(
        val channelId: String,
        val title: String,
        val start: Long,
        val end: Long,
    )

    private data class EpgSchedule(
        val programs: List<ScheduledProgram>,
        val aliases: Map<String, String>,
    )
}

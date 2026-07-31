package ru.iptvtv.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.iptvtv.domain.model.Channel
import ru.iptvtv.domain.model.Program
import ru.iptvtv.domain.usecase.CheckForUpdateUseCase
import ru.iptvtv.domain.usecase.GetChannelsUseCase
import ru.iptvtv.domain.usecase.GetEpgUrlUseCase
import ru.iptvtv.domain.usecase.GetStreamUrlUseCase
import ru.iptvtv.domain.usecase.SaveEpgUrlUseCase
import ru.iptvtv.domain.usecase.SaveStreamUrlUseCase
import ru.iptvtv.domain.repository.StreamSettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PlayerViewModel(
    private val getStreamUrl: GetStreamUrlUseCase,
    private val getEpgUrl: GetEpgUrlUseCase,
    private val saveStreamUrl: SaveStreamUrlUseCase,
    private val saveEpgUrl: SaveEpgUrlUseCase,
    private val getChannels: GetChannelsUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val currentVersion: String,
    private val settingsRepository: StreamSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(favoriteChannelIds = settingsRepository.getFavoriteChannelIds())
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadPlaylist(getStreamUrl(), getEpgUrl())
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { checkForUpdate(currentVersion) }
                .onSuccess { update ->
                    _uiState.update { it.copy(availableUpdate = update) }
                }
        }
    }

    fun showChannels() = _uiState.update { it.copy(isChannelPanelVisible = true) }
    fun hideChannels() = _uiState.update { it.copy(isChannelPanelVisible = false) }
    fun showSettings() = _uiState.update {
        it.copy(isSettingsVisible = true, isChannelPanelVisible = false)
    }
    fun hideSettings() = _uiState.update { it.copy(isSettingsVisible = false) }
    fun dismissUpdate() = _uiState.update { it.copy(availableUpdate = null) }

    fun refreshEpgNow() {
        val state = _uiState.value
        if (state.isEpgUpdating || state.epgUrl.isBlank() || state.channels.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            refreshEpg(state.channels, state.epgUrl)
        }
    }

    fun saveSettings(url: String, epgUrl: String) {
        val normalizedUrl = url.trim()
        val normalizedEpgUrl = epgUrl.trim()
        viewModelScope.launch(Dispatchers.IO) {
            saveStreamUrl(normalizedUrl)
            saveEpgUrl(normalizedEpgUrl)
            loadPlaylist(normalizedUrl, normalizedEpgUrl)
        }
        hideSettings()
    }

    fun selectChannel(channel: Channel) {
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                playingProgram = null,
                isArchivePlayback = false,
                isLiveTimeshift = false,
                playbackStartPositionMs = 0L,
                playbackShouldPlay = true,
                playbackRequestId = System.currentTimeMillis(),
            )
        }
        val epgUrl = _uiState.value.epgUrl
        if (epgUrl.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val programs = runCatching {
                    getChannels.getPrograms(channel, epgUrl)
                }.getOrDefault(emptyList())
                _uiState.update { current ->
                    if (current.selectedChannel?.id == channel.id) {
                        current.copy(selectedChannel = channel.copy(programs = programs))
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        val updated = _uiState.value.favoriteChannelIds.toMutableSet().apply {
            if (!add(channel.id)) remove(channel.id)
        }.toSet()
        _uiState.update { it.copy(favoriteChannelIds = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveFavoriteChannelIds(updated)
        }
    }

    fun selectAdjacentChannel(direction: Int) {
        val state = _uiState.value
        val selected = state.selectedChannel ?: return
        val categoryChannels = state.channels.filter { it.category == selected.category }
        if (categoryChannels.isEmpty()) return
        val currentIndex = categoryChannels.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val nextIndex = (currentIndex + direction).mod(categoryChannels.size)
        selectChannel(categoryChannels[nextIndex])
    }

    fun returnToLive() {
        val selected = _uiState.value.selectedChannel ?: return
        _uiState.value.channels.firstOrNull { it.id == selected.id }?.let(::selectChannel)
    }

    fun openArchive(channel: Channel) {
        _uiState.update { it.copy(archiveChannel = channel) }
        val epgUrl = _uiState.value.epgUrl
        if (epgUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val programs = runCatching {
                getChannels.getPrograms(channel, epgUrl)
            }.getOrDefault(emptyList())
            _uiState.update { current ->
                if (current.archiveChannel?.id == channel.id) {
                    current.copy(archiveChannel = channel.copy(programs = programs))
                } else {
                    current
                }
            }
        }
    }

    suspend fun searchPrograms(query: String) = withContext(Dispatchers.IO) {
        getChannels.searchPrograms(_uiState.value.channels, _uiState.value.epgUrl, query)
    }

    fun playProgram(channel: Channel, program: Program) {
        val now = System.currentTimeMillis()
        val liveChannel = _uiState.value.channels
            .firstOrNull { it.id == channel.id }
            ?: channel
        val playbackUrl = if (program.start <= now && now < program.end) {
            liveChannel.streamUrl
        } else {
            buildCatchupUrl(liveChannel, program) ?: return
        }
        _uiState.update {
            it.copy(
                selectedChannel = channel.copy(
                    streamUrl = playbackUrl,
                    catchupSource = liveChannel.catchupSource,
                    catchupType = liveChannel.catchupType,
                ),
                playingProgram = program,
                isArchivePlayback = playbackUrl != liveChannel.streamUrl,
                isLiveTimeshift = false,
                playbackStartPositionMs = 0L,
                playbackShouldPlay = true,
                playbackRequestId = System.currentTimeMillis(),
            )
        }
    }

    fun switchLiveToArchive(
        channel: Channel,
        program: Program,
        positionMs: Long,
        shouldPlay: Boolean,
    ) {
        val liveChannel = _uiState.value.channels
            .firstOrNull { it.id == channel.id }
            ?: channel
        val archiveUrl = buildCatchupUrl(liveChannel, program) ?: return
        _uiState.update {
            it.copy(
                selectedChannel = channel.copy(
                    streamUrl = archiveUrl,
                    catchupSource = liveChannel.catchupSource,
                    catchupType = liveChannel.catchupType,
                ),
                playingProgram = program,
                isArchivePlayback = true,
                isLiveTimeshift = true,
                playbackStartPositionMs = positionMs.coerceAtLeast(0L),
                playbackShouldPlay = shouldPlay,
                playbackRequestId = System.currentTimeMillis(),
            )
        }
    }

    private fun buildCatchupUrl(channel: Channel, program: Program): String? {
        val start = program.start / 1_000
        val end = program.end / 1_000
        val duration = (end - start).coerceAtLeast(1)
        val offset = ((System.currentTimeMillis() / 1_000) - start).coerceAtLeast(0)
        val currentUtc = System.currentTimeMillis() / 1_000
        val catchupType = channel.catchupType.lowercase(Locale.ROOT)
        var template = channel.catchupSource
        var appendToLiveUrl = false
        if (
            template.isBlank() &&
            catchupType in setOf("shift", "siptv", "timeshift")
        ) {
            val separator = if (channel.streamUrl.contains('?')) "&" else "?"
            template = "$separator" + "utc={utc}&lutc={lutc}"
            appendToLiveUrl = true
        }
        if (
            template.isBlank() &&
            catchupType in setOf("flussonic", "fs", "flussonic-hls", "flussonic-ts")
        ) {
            val extension = if (catchupType.endsWith("ts")) ".ts" else ".m3u8"
            template = channel.streamUrl.substringBeforeLast('/') +
                "/timeshift_abs-$start$extension"
        }
        if (template.isBlank() && channel.streamUrl.contains(".m3u8", ignoreCase = true)) {
            val separator = if (channel.streamUrl.contains('?')) "&" else "?"
            template = "$separator" + "utc={utc}&lutc={lutc}"
            appendToLiveUrl = true
        }
        if (template.isBlank()) return null
        template = Regex("""\{duration:(\d+)\}""").replace(template) { match ->
            val divisor = match.groupValues[1].toLongOrNull()?.coerceAtLeast(1) ?: 1
            (duration / divisor).coerceAtLeast(1).toString()
        }
        template = Regex("""\{offset:(\d+)\}""").replace(template) { match ->
            val divisor = match.groupValues[1].toLongOrNull()?.coerceAtLeast(1) ?: 1
            (offset / divisor).toString()
        }
        val utcDate = Date(program.start)
        val replacements = mapOf(
            "{utc}" to start.toString(),
            "{lutc}" to currentUtc.toString(),
            "\${lutc}" to currentUtc.toString(),
            "{start}" to start.toString(),
            "\${start}" to start.toString(),
            "{timestamp}" to start.toString(),
            "\${timestamp}" to start.toString(),
            "{end}" to end.toString(),
            "\${end}" to end.toString(),
            "{utcend}" to end.toString(),
            "{duration}" to duration.toString(),
            "\${duration}" to duration.toString(),
            "{offset}" to offset.toString(),
            "\${offset}" to offset.toString(),
        )
        replacements.forEach { (token, value) -> template = template.replace(token, value) }
        mapOf(
            "{Y}" to "yyyy",
            "{m}" to "MM",
            "{d}" to "dd",
            "{H}" to "HH",
            "{M}" to "mm",
            "{S}" to "ss",
        ).forEach { (token, pattern) ->
            val value = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(utcDate)
            template = template.replace(token, value)
        }
        val resolved = if (
            appendToLiveUrl ||
            catchupType in setOf("append", "shift", "siptv", "timeshift")
        ) {
            channel.streamUrl + template
        } else {
            template
        }
        return resolved.trim().takeIf {
            (it.startsWith("http://", true) || it.startsWith("https://", true)) &&
                !it.contains('{') &&
                !it.contains('}')
        }
    }

    private suspend fun loadPlaylist(url: String, epgUrl: String) {
        if (url.isBlank()) {
            _uiState.update {
                it.copy(
                    streamUrl = "",
                    epgUrl = epgUrl,
                    channels = emptyList(),
                    selectedChannel = null,
                    isPlaylistLoading = false,
                    playlistError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                streamUrl = url,
                epgUrl = epgUrl,
                channels = emptyList(),
                selectedChannel = null,
                isPlaylistLoading = true,
                playlistError = null,
            )
        }

        runCatching { getChannels(url, epgUrl) }
            .onSuccess { channels ->
                _uiState.update {
                    it.copy(
                        channels = channels,
                        selectedChannel = null,
                        isChannelPanelVisible = true,
                        isPlaylistLoading = false,
                    )
                }
                if (epgUrl.isNotBlank()) {
                    val cachedChannels = runCatching {
                        getChannels.loadCurrentPrograms(channels, epgUrl)
                    }.getOrDefault(channels)
                    applyPrograms(cachedChannels)
                    _uiState.update {
                        it.copy(lastEpgUpdateAt = getChannels.getLastEpgUpdateAt(epgUrl))
                    }
                    if (getChannels.shouldRefreshEpg(epgUrl)) {
                        refreshEpg(channels, epgUrl)
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPlaylistLoading = false,
                        playlistError = error.message ?: "Не удалось загрузить плейлист",
                    )
                }
            }
    }

    private suspend fun refreshEpg(channels: List<Channel>, epgUrl: String) {
        _uiState.update { it.copy(isEpgUpdating = true, epgUpdateError = null) }
        runCatching {
            getChannels.loadCurrentPrograms(channels, epgUrl, forceRefresh = true)
        }
            .onSuccess { channelsWithPrograms ->
                applyPrograms(channelsWithPrograms)
                _uiState.update {
                    it.copy(lastEpgUpdateAt = getChannels.getLastEpgUpdateAt(epgUrl))
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        epgUpdateError = error.message ?: "Не удалось обновить EPG",
                    )
                }
            }
        _uiState.update { it.copy(isEpgUpdating = false) }
    }

    private fun applyPrograms(channelsWithPrograms: List<Channel>) {
        _uiState.update { current ->
            val selectedId = current.selectedChannel?.id
            current.copy(
                channels = channelsWithPrograms,
                selectedChannel = channelsWithPrograms
                    .firstOrNull { it.id == selectedId }
                    ?: current.selectedChannel,
            )
        }
    }
}

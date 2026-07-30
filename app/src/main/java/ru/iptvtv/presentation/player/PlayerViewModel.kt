package ru.iptvtv.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.iptvtv.domain.model.Channel
import ru.iptvtv.domain.model.Program
import ru.iptvtv.domain.usecase.CheckForUpdateUseCase
import ru.iptvtv.domain.usecase.GetChannelsUseCase
import ru.iptvtv.domain.usecase.GetEpgUrlUseCase
import ru.iptvtv.domain.usecase.GetStreamUrlUseCase
import ru.iptvtv.domain.usecase.SaveEpgUrlUseCase
import ru.iptvtv.domain.usecase.SaveStreamUrlUseCase

class PlayerViewModel(
    private val getStreamUrl: GetStreamUrlUseCase,
    private val getEpgUrl: GetEpgUrlUseCase,
    private val saveStreamUrl: SaveStreamUrlUseCase,
    private val saveEpgUrl: SaveEpgUrlUseCase,
    private val getChannels: GetChannelsUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val currentVersion: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    init {
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
            it.copy(selectedChannel = channel)
        }
    }

    fun playProgram(channel: Channel, program: Program) {
        val now = System.currentTimeMillis()
        val playbackUrl = if (program.start <= now && now < program.end) {
            channel.streamUrl
        } else {
            buildCatchupUrl(channel.catchupSource, program) ?: return
        }
        _uiState.update {
            it.copy(selectedChannel = channel.copy(streamUrl = playbackUrl))
        }
    }

    private fun buildCatchupUrl(template: String, program: Program): String? {
        if (template.isBlank()) return null
        val start = program.start / 1_000
        val end = program.end / 1_000
        val duration = (end - start).coerceAtLeast(1)
        return template
            .replace("{utc}", start.toString())
            .replace("{start}", start.toString())
            .replace("\${start}", start.toString())
            .replace("{end}", end.toString())
            .replace("\${end}", end.toString())
            .replace("{duration}", duration.toString())
            .replace("\${duration}", duration.toString())
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
                        selectedChannel = channels.firstOrNull(),
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

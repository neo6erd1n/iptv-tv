package ru.iptvtv.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.iptvtv.domain.model.Channel
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
            it.copy(selectedChannel = channel, isChannelPanelVisible = false)
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
                        selectedChannel = channels.firstOrNull(),
                        isPlaylistLoading = false,
                    )
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
}

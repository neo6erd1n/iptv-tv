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
import ru.iptvtv.domain.usecase.GetStreamUrlUseCase
import ru.iptvtv.domain.usecase.SaveStreamUrlUseCase

class PlayerViewModel(
    private val getStreamUrl: GetStreamUrlUseCase,
    private val saveStreamUrl: SaveStreamUrlUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val currentVersion: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            applyStreamUrl(getStreamUrl())
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

    fun saveSettings(url: String) {
        val normalizedUrl = url.trim()
        viewModelScope.launch(Dispatchers.IO) {
            saveStreamUrl(normalizedUrl)
            applyStreamUrl(normalizedUrl)
        }
        hideSettings()
    }

    fun selectChannel(channel: Channel) {
        _uiState.update {
            it.copy(selectedChannel = channel, isChannelPanelVisible = false)
        }
    }

    private fun applyStreamUrl(url: String) {
        val channel = url.takeIf(String::isNotBlank)?.let {
            Channel(id = "user-stream", name = "Мой канал", streamUrl = it)
        }
        _uiState.update {
            it.copy(
                streamUrl = url,
                channels = listOfNotNull(channel),
                selectedChannel = channel,
            )
        }
    }
}

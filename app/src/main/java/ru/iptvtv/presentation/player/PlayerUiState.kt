package ru.iptvtv.presentation.player

import ru.iptvtv.domain.model.AppUpdate
import ru.iptvtv.domain.model.Channel

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val isChannelPanelVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val streamUrl: String = "",
    val epgUrl: String = "",
    val isPlaylistLoading: Boolean = false,
    val playlistError: String? = null,
    val availableUpdate: AppUpdate? = null,
)

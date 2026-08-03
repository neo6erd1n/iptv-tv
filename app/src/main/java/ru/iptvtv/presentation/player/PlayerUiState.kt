package ru.iptvtv.presentation.player

import ru.iptvtv.domain.model.AppUpdate
import ru.iptvtv.domain.model.Channel
import ru.iptvtv.domain.model.Program

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val selectedChannel: Channel? = null,
    val archiveChannel: Channel? = null,
    val playingProgram: Program? = null,
    val isArchivePlayback: Boolean = false,
    val playbackStartPositionMs: Long = 0L,
    val playbackShouldPlay: Boolean = true,
    val playbackRequestId: Long = 0L,
    val isChannelPanelVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val streamUrl: String = "",
    val epgUrl: String = "",
    val isPlaylistLoading: Boolean = false,
    val playlistError: String? = null,
    val isEpgUpdating: Boolean = false,
    val lastEpgUpdateAt: Long? = null,
    val epgUpdateError: String? = null,
    val availableUpdate: AppUpdate? = null,
)

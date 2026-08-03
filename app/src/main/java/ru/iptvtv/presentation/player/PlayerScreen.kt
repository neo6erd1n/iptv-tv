@file:androidx.media3.common.util.UnstableApi

package ru.iptvtv.presentation.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ru.iptvtv.domain.model.AppUpdate
import ru.iptvtv.domain.model.Channel
import ru.iptvtv.domain.model.Program
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableLongStateOf(0L) }
    var exitArmedUntil by remember { mutableLongStateOf(0L) }
    var exitHintVisible by remember { mutableStateOf(false) }
    var audioDialogVisible by remember { mutableStateOf(false) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var displayedArchivePlayback by remember { mutableStateOf(false) }
    var focusedControlIndex by remember { mutableIntStateOf(0) }
    var controlBarFocused by remember { mutableStateOf(false) }
    val controlActions = remember(state.isArchivePlayback) {
        buildList {
            if (state.isArchivePlayback) add(StreamControlAction.RETURN_TO_LIVE)
            add(StreamControlAction.AUDIO)
            add(StreamControlAction.DIAGNOSTICS)
        }
    }
    val watchedProgram = state.playingProgram ?: state.selectedChannel?.let { channel ->
        val start = channel.currentProgramStart
        val end = channel.currentProgramEnd
        if (start != null && end != null && channel.currentProgram != null) {
            Program(channel.currentProgram, "", start, end)
        } else {
            null
        }
    }

    LaunchedEffect(controlsInteraction, controlsVisible) {
        if (controlsVisible) {
            delay(5_000)
            controlsVisible = false
        }
    }

    DisposableEffect(
        activePlayer,
        state.playbackRequestId,
        state.isArchivePlayback,
    ) {
        val player = activePlayer
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.message
            }

            override fun onRenderedFirstFrame() {
                displayedArchivePlayback = state.isArchivePlayback
            }
        }
        player?.addListener(listener)
        currentTracks = player?.currentTracks ?: Tracks.EMPTY
        playbackError = player?.playerError?.message
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(exitHintVisible) {
        if (exitHintVisible) {
            delay(EXIT_CONFIRMATION_MS)
            exitHintVisible = false
        }
    }

    BackHandler {
        when {
            audioDialogVisible -> audioDialogVisible = false
            diagnosticsVisible -> diagnosticsVisible = false
            state.isSettingsVisible -> viewModel.hideSettings()
            state.isChannelPanelVisible -> viewModel.hideChannels()
            controlsVisible -> controlsVisible = false
            System.currentTimeMillis() <= exitArmedUntil -> onExit()
            else -> {
                exitArmedUntil = System.currentTimeMillis() + EXIT_CONFIRMATION_MS
                exitHintVisible = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MENU -> {
                            viewModel.showSettings()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (controlsVisible) {
                                if (controlBarFocused) {
                                    focusedControlIndex =
                                        (focusedControlIndex - 1).mod(controlActions.size)
                                } else {
                                    val player = activePlayer
                                    if (
                                        !state.isArchivePlayback &&
                                        player != null &&
                                        watchedProgram != null
                                    ) {
                                        player.pause()
                                        viewModel.switchLiveToArchive(
                                            channel = state.selectedChannel!!,
                                            program = watchedProgram,
                                            positionMs = (
                                                currentProgramPosition(player, watchedProgram) -
                                                    SEEK_STEP_MS
                                                ).coerceAtLeast(0L),
                                            shouldPlay = true,
                                        )
                                    } else {
                                        player?.seekTo(
                                            (player.currentPosition - SEEK_STEP_MS)
                                                .coerceAtLeast(0L),
                                        )
                                    }
                                }
                                controlsInteraction = System.currentTimeMillis()
                                true
                            } else if (state.isChannelPanelVisible) {
                                false
                            } else {
                                viewModel.showChannels()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (controlsVisible) {
                                if (controlBarFocused) {
                                    focusedControlIndex =
                                        (focusedControlIndex + 1).mod(controlActions.size)
                                } else {
                                    activePlayer?.let { player ->
                                        val target = player.currentPosition + SEEK_STEP_MS
                                        player.seekTo(
                                            if (player.duration > 0 && player.duration != C.TIME_UNSET) {
                                                target.coerceAtMost(player.duration)
                                            } else {
                                                target
                                            },
                                        )
                                    }
                                }
                                controlsInteraction = System.currentTimeMillis()
                                true
                            } else if (state.isChannelPanelVisible) {
                                false
                            } else {
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (controlsVisible) {
                                controlBarFocused = false
                                controlsInteraction = System.currentTimeMillis()
                                true
                            } else if (!state.isChannelPanelVisible) {
                                viewModel.selectAdjacentChannel(-1)
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (controlsVisible) {
                                controlBarFocused = true
                                focusedControlIndex = focusedControlIndex
                                    .coerceIn(0, controlActions.lastIndex)
                                controlsInteraction = System.currentTimeMillis()
                                true
                            } else if (!state.isChannelPanelVisible) {
                                viewModel.selectAdjacentChannel(1)
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_PROG_RED -> {
                            if (controlsVisible && state.isArchivePlayback) {
                                viewModel.returnToLive()
                                controlsVisible = false
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            if (!state.isChannelPanelVisible && state.selectedChannel != null) {
                                if (!controlsVisible) {
                                    controlsVisible = true
                                    controlBarFocused = false
                                    focusedControlIndex = 0
                                    controlsInteraction = System.currentTimeMillis()
                                } else if (!controlBarFocused) {
                                    activePlayer?.let { player ->
                                            if (!state.isArchivePlayback && watchedProgram != null) {
                                                player.pause()
                                                viewModel.switchLiveToArchive(
                                                    channel = state.selectedChannel!!,
                                                    program = watchedProgram,
                                                    positionMs = currentProgramPosition(
                                                        player,
                                                        watchedProgram,
                                                    ),
                                                    shouldPlay = false,
                                                )
                                            } else {
                                                player.playWhenReady = !player.playWhenReady
                                            }
                                    }
                                    controlsInteraction = System.currentTimeMillis()
                                } else {
                                    when (controlActions[focusedControlIndex]) {
                                        StreamControlAction.AUDIO -> audioDialogVisible = true
                                        StreamControlAction.RETURN_TO_LIVE -> {
                                            activePlayer?.pause()
                                            viewModel.returnToLive()
                                            controlsVisible = false
                                        }
                                        StreamControlAction.DIAGNOSTICS -> diagnosticsVisible = true
                                    }
                                    controlsInteraction = System.currentTimeMillis()
                                }
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            },
    ) {
        state.selectedChannel?.let { channel ->
            VideoPlayer(
                channel = channel,
                startPositionMs = state.playbackStartPositionMs,
                shouldPlay = state.playbackShouldPlay,
                requestId = state.playbackRequestId,
                onPlayerChanged = { activePlayer = it },
            )
        } ?: EmptyPlayerState(
            isLoading = state.isPlaylistLoading,
            error = state.playlistError,
            onOpenSettings = viewModel::showSettings,
        )
        DeviceClock(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 32.dp),
        )
        if (controlsVisible) {
            StreamControls(
                player = activePlayer,
                channel = state.selectedChannel,
                program = watchedProgram,
                isArchive = displayedArchivePlayback,
                actions = controlActions,
                focusedActionIndex = focusedControlIndex.takeIf { controlBarFocused },
            )
        }
        if (exitHintVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xEB141B2A),
                shadowElevation = 12.dp,
            ) {
                Text(
                    "Нажмите «Назад» ещё раз, чтобы выйти",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }

        AnimatedVisibility(
            visible = state.isChannelPanelVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelPanel(
                channels = state.channels,
                selected = state.selectedChannel,
                archiveChannel = state.archiveChannel,
                watchedProgramStart = watchedProgram?.start,
                favoriteChannelIds = state.favoriteChannelIds,
                isEpgUpdating = state.isEpgUpdating,
                onDismiss = viewModel::hideChannels,
                onSelect = viewModel::selectChannel,
                onOpenArchive = viewModel::openArchive,
                onPlayProgram = viewModel::playProgram,
                onToggleFavorite = viewModel::toggleFavorite,
                searchPrograms = viewModel::searchPrograms,
            )
        }
    }

    if (state.isSettingsVisible) {
        SettingsDialog(
            initialUrl = state.streamUrl,
            initialEpgUrl = state.epgUrl,
            isEpgUpdating = state.isEpgUpdating,
            lastEpgUpdateAt = state.lastEpgUpdateAt,
            epgUpdateError = state.epgUpdateError,
            onDismiss = viewModel::hideSettings,
            onSave = viewModel::saveSettings,
            onRefreshEpg = viewModel::refreshEpgNow,
        )
    }
    if (audioDialogVisible) {
        AudioTrackDialog(
            player = activePlayer,
            tracks = currentTracks,
            onDismiss = { audioDialogVisible = false },
        )
    }
    if (diagnosticsVisible) {
        DiagnosticsDialog(
            channel = state.selectedChannel,
            tracks = currentTracks,
            error = playbackError,
            onDismiss = { diagnosticsVisible = false },
        )
    }

    state.availableUpdate?.let { update ->
        UpdateDialog(
            update = update,
            onDismiss = viewModel::dismissUpdate,
            onDownload = {
                viewModel.dismissUpdate()
                onDownloadUpdate(update)
            },
        )
    }
}

@Composable
private fun EmptyPlayerState(
    isLoading: Boolean,
    error: String?,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                isLoading -> "Загружаем список каналов…"
                error != null -> "Не удалось загрузить каналы"
                else -> "Добавьте ссылку на IPTV-плейлист"
            },
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: if (isLoading) {
                "Это может занять несколько секунд"
            } else {
                "Нажмите Menu на пульте или откройте настройки"
            },
            color = Color.White.copy(alpha = 0.62f),
        )
        if (!isLoading) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (error == null) "Настройки" else "Проверить ссылку")
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    initialUrl: String,
    initialEpgUrl: String,
    isEpgUpdating: Boolean,
    lastEpgUpdateAt: Long?,
    epgUpdateError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRefreshEpg: () -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var epgUrl by remember(initialEpgUrl) { mutableStateOf(initialEpgUrl) }
    var showError by remember { mutableStateOf(false) }
    var showEpgError by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val isValid = url.trim().let {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }
    val isEpgValid = epgUrl.trim().let {
        it.isBlank() ||
            it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        inputFocus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xF21A2232),
        icon = {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Настройки IPTV") },
        text = {
            Column {
                Text(
                    "Ссылка хранится только на этом устройстве и сохраняется после обновления.",
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocus),
                    label = { Text("Ссылка на M3U-плейлист") },
                    placeholder = { Text("https://…/playlist.m3u") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = showError,
                    supportingText = {
                        if (showError) {
                            Text("Ссылка должна начинаться с http:// или https://")
                        } else {
                            Text("Поддерживаются IPTV M3U и прямые HLS/M3U8")
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = {
                        epgUrl = it
                        showEpgError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка на EPG (XMLTV)") },
                    placeholder = { Text("https://…/epg.xml.gz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = showEpgError,
                    supportingText = {
                        if (showEpgError) {
                            Text("Ссылка должна начинаться с http:// или https://")
                        } else {
                            Text("Можно оставить пустой — тогда адрес берётся из M3U")
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        lastEpgUpdateAt?.let {
                            "Обновлено: ${formatEpgUpdateTime(it)}"
                        } ?: "EPG ещё не обновлялся",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                    )
                    TextButton(
                        onClick = onRefreshEpg,
                        enabled = !isEpgUpdating && epgUrl.isNotBlank(),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEpgUpdating) "Обновляется…" else "Обновить сейчас")
                    }
                }
                epgUpdateError?.let {
                    Text(
                        "Ошибка EPG: $it",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showError = !isValid
                    showEpgError = !isEpgValid
                    if (isValid && isEpgValid) onSave(url, epgUrl)
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun VideoPlayer(
    channel: Channel,
    startPositionMs: Long,
    shouldPlay: Boolean,
    requestId: Long,
    onPlayerChanged: (ExoPlayer?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val loadControl = DefaultLoadControl.Builder()
            // The projector gives the app a 128 MB heap. A 4K HLS stream can make
            // Media3's default 50-second buffer consume almost all of it.
            .setBufferDurationsMs(
                1_500,
                8_000,
                500,
                1_000,
            )
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
            playWhenReady = true
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    LaunchedEffect(player) {
        onPlayerChanged(player)
    }

    LaunchedEffect(channel.streamUrl, requestId) {
        runCatching {
            val item = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .build()
            player.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = shouldPlay
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onPlayerChanged(null)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
        )

        // The Hi3751V350 HEVC overlay exposes the 16 alignment rows below the
        // 3840x2160 crop for this stream. Cover only that device-specific edge;
        // changing renderer or surface type makes 4K playback less reliable.
        if (channel.name.equals("BCU Kids 4K", ignoreCase = true)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun DeviceClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White,
        fontSize = 20.sp,
    )
}

@Composable
private fun StreamControls(
    player: ExoPlayer?,
    channel: Channel?,
    program: Program?,
    isArchive: Boolean,
    actions: List<StreamControlAction>,
    focusedActionIndex: Int?,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var playerPosition by remember { mutableLongStateOf(0L) }
    var liveOffset by remember { mutableLongStateOf(C.TIME_UNSET) }
    LaunchedEffect(player) {
        while (true) {
            now = System.currentTimeMillis()
            playerPosition = player?.currentPosition ?: 0L
            liveOffset = player?.currentLiveOffset ?: C.TIME_UNSET
            delay(250)
        }
    }
    val start = program?.start ?: channel?.currentProgramStart
    val end = program?.end ?: channel?.currentProgramEnd
    val duration = if (start != null && end != null) {
        (end - start).coerceAtLeast(1L)
    } else {
        player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: 1L
    }
    val position = when {
        isArchive -> playerPosition
        start != null -> {
            val streamNow = if (liveOffset != C.TIME_UNSET && liveOffset >= 0) {
                now - liveOffset
            } else {
                now
            }
            streamNow - start
        }
        else -> playerPosition
    }.coerceIn(0L, duration)
    val progress = position.toFloat() / duration.toFloat()
    val remaining = (duration - position).coerceAtLeast(0L)

    Box(modifier = Modifier.fillMaxSize()) {
        if (player?.playWhenReady == false) {
            Icon(
                imageVector = Icons.Rounded.PauseCircle,
                contentDescription = "Пауза",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(88.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 56.dp, end = 56.dp, bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xE6141B2A),
            shadowElevation = 18.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    channel?.logoUrl?.takeIf(String::isNotBlank)?.let {
                        ChannelLogo(it)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel?.name.orEmpty(),
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                        )
                        Text(
                            program?.title ?: channel?.currentProgram.orEmpty(),
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                    actions.forEachIndexed { index, action ->
                        val focused = index == focusedActionIndex
                        Spacer(Modifier.width(8.dp))
                        Text(
                            action.controlLabel(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        focused -> MaterialTheme.colorScheme.primary
                                        action == StreamControlAction.RETURN_TO_LIVE ->
                                            Color(0xFFE53935).copy(alpha = 0.72f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                )
                                .then(
                                    if (focused) {
                                        Modifier.border(
                                            2.dp,
                                            Color.White,
                                            RoundedCornerShape(8.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isArchive) "АРХИВ" else "ЭФИР",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isArchive) Color(0xFFFFC107) else Color(0xFFE53935),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (isArchive) Color(0xFF241A00) else Color.White,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.24f),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (maxWidth - 14.dp) * progress)
                            .size(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White),
                    )
                }
                Text(
                    text = "До конца ${formatDuration(remaining)}",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private enum class StreamControlAction {
    AUDIO,
    RETURN_TO_LIVE,
    DIAGNOSTICS,
}

private fun StreamControlAction.controlLabel(): String = when (this) {
    StreamControlAction.AUDIO -> "Аудио"
    StreamControlAction.RETURN_TO_LIVE -> "Вернуться в эфир"
    StreamControlAction.DIAGNOSTICS -> "Диагностика"
}

private fun currentProgramPosition(player: ExoPlayer, program: Program): Long {
    val streamNow = if (player.currentLiveOffset != C.TIME_UNSET && player.currentLiveOffset >= 0) {
        System.currentTimeMillis() - player.currentLiveOffset
    } else {
        System.currentTimeMillis()
    }
    return (streamNow - program.start).coerceIn(0L, (program.end - program.start).coerceAtLeast(1L))
}

@Composable
private fun AudioTrackDialog(
    player: ExoPlayer?,
    tracks: Tracks,
    onDismiss: () -> Unit,
) {
    val options = remember(tracks) {
        buildList {
            tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                repeat(group.length) { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        AudioTrackOption(
                            group = group,
                            trackIndex = trackIndex,
                            title = format.label
                                ?: format.language?.let { language ->
                                    runCatching {
                                        Locale.forLanguageTag(language).displayLanguage
                                    }.getOrNull()
                                }
                                ?: "Аудиодорожка ${size + 1}",
                            details = listOfNotNull(
                                format.sampleMimeType?.substringAfter('/'),
                                format.channelCount.takeIf { it > 0 }?.let { "$it каналов" },
                            ).joinToString(" · "),
                            selected = group.isTrackSelected(trackIndex),
                        ),
                    )
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF21A2232),
        shape = RoundedCornerShape(28.dp),
        title = { Text("Аудиодорожки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (options.isEmpty()) {
                    Text("Дополнительные аудиодорожки не найдены")
                }
                options.forEach { option ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            player?.trackSelectionParameters = player
                                ?.trackSelectionParameters
                                ?.buildUpon()
                                ?.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                ?.addOverride(
                                    TrackSelectionOverride(
                                        option.group.mediaTrackGroup,
                                        option.trackIndex,
                                    ),
                                )
                                ?.build()
                                ?: return@TextButton
                            onDismiss()
                        },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (if (option.selected) "● " else "○ ") + option.title,
                                color = if (option.selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            )
                            if (option.details.isNotBlank()) {
                                Text(
                                    option.details,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun DiagnosticsDialog(
    channel: Channel?,
    tracks: Tracks,
    error: String?,
    onDismiss: () -> Unit,
) {
    val video = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        ?.let { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)
                ?.let(group::getTrackFormat)
        }
    val audio = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        ?.let { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)
                ?.let(group::getTrackFormat)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF21A2232),
        shape = RoundedCornerShape(28.dp),
        title = { Text("Диагностика потока") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticRow("Канал", channel?.name.orEmpty())
                DiagnosticRow("URL", channel?.streamUrl.orEmpty())
                DiagnosticRow(
                    "Видео",
                    listOfNotNull(video?.sampleMimeType, video?.codecs).joinToString(" · ")
                        .ifBlank { "Не определено" },
                )
                DiagnosticRow(
                    "Разрешение",
                    video?.takeIf { it.width > 0 && it.height > 0 }
                        ?.let { "${it.width}×${it.height}" }
                        ?: "Не определено",
                )
                DiagnosticRow(
                    "Аудио",
                    listOfNotNull(audio?.sampleMimeType, audio?.codecs, audio?.language)
                        .joinToString(" · ")
                        .ifBlank { "Не определено" },
                )
                DiagnosticRow("Ошибка", error ?: "Нет")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

private data class AudioTrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val title: String,
    val details: String,
    val selected: Boolean,
)

private const val SEEK_STEP_MS = 30_000L
private const val EXIT_CONFIRMATION_MS = 2_500L

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun archiveDayStart(daysAgo: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis
}

@Composable
private fun SearchRail(focused: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.2f))
            .then(
                if (focused) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Поиск",
            modifier = Modifier
                .padding(top = 32.dp)
                .size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchDialog(
    channels: List<Channel>,
    archiveChannel: Channel?,
    searchPrograms: suspend (String) -> List<ru.iptvtv.domain.model.ProgramSearchResult>,
    onDismiss: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onPlayProgram: (Channel, Program) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    val normalizedQuery = submittedQuery.trim()
    val epgResults by produceState(
        initialValue = emptyList<ru.iptvtv.domain.model.ProgramSearchResult>(),
        normalizedQuery,
    ) {
        value = emptyList()
        if (normalizedQuery.length >= 2) {
            value = runCatching { searchPrograms(normalizedQuery) }.getOrDefault(emptyList())
        }
    }
    val results = remember(channels, archiveChannel, normalizedQuery, epgResults) {
        if (normalizedQuery.length < 2) {
            emptyList()
        } else {
            buildList {
                channels
                    .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
                    .forEach { add(SearchResult(it, null)) }
                channels
                    .filter {
                        it.currentProgram?.contains(normalizedQuery, ignoreCase = true) == true
                    }
                    .forEach { channel ->
                        val start = channel.currentProgramStart
                        val end = channel.currentProgramEnd
                        if (start != null && end != null) {
                            add(
                                SearchResult(
                                    channel,
                                    Program(channel.currentProgram.orEmpty(), "", start, end),
                                ),
                            )
                        }
                    }
                archiveChannel?.programs
                    .orEmpty()
                    .filter { it.title.contains(normalizedQuery, ignoreCase = true) }
                    .forEach { add(SearchResult(archiveChannel!!, it)) }
                epgResults.forEach { add(SearchResult(it.channel, it.program)) }
            }.distinctBy { "${it.channel.id}:${it.program?.start ?: 0L}" }.take(30)
        }
    }
    val channelResults = results.filter { it.program == null }
    val programResults = results.filter { it.program != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF21A2232),
        shape = RoundedCornerShape(28.dp),
        title = { Text("Поиск каналов и передач") },
        text = {
            Column(modifier = Modifier.height(480.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Название") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { submittedQuery = query.trim() },
                        enabled = query.trim().length >= 2,
                    ) {
                        Text("Поиск")
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (channelResults.isNotEmpty()) {
                        item { SearchSectionTitle("Каналы") }
                    }
                    itemsIndexed(
                        channelResults,
                        key = { _, result ->
                            "${result.channel.id}:${result.program?.start ?: 0L}"
                        },
                    ) { _, result ->
                        SearchResultItem(result, onSelectChannel, onPlayProgram)
                    }
                    if (programResults.isNotEmpty()) {
                        item { SearchSectionTitle("Передачи") }
                    }
                    itemsIndexed(
                        programResults,
                        key = { _, result -> "program:${result.channel.id}:${result.program?.start}" },
                    ) { _, result ->
                        SearchResultItem(result, onSelectChannel, onPlayProgram)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
    )
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onSelectChannel: (Channel) -> Unit,
    onPlayProgram: (Channel, Program) -> Unit,
) {
    TextButton(
        onClick = {
            result.program?.let { onPlayProgram(result.channel, it) }
                ?: onSelectChannel(result.channel)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                result.program?.title ?: result.channel.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = Color.White,
            )
            result.program?.let {
                Text(
                    result.channel.name,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ChannelPanel(
    channels: List<Channel>,
    selected: Channel?,
    archiveChannel: Channel?,
    watchedProgramStart: Long?,
    favoriteChannelIds: Set<String>,
    isEpgUpdating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Channel) -> Unit,
    onOpenArchive: (Channel) -> Unit,
    onPlayProgram: (Channel, Program) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    searchPrograms: suspend (String) -> List<ru.iptvtv.domain.model.ProgramSearchResult>,
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val categories = remember(channels, favoriteChannelIds) {
        listOf(
            ChannelCategory(
                name = "Избранное",
                channels = channels.filter { it.id in favoriteChannelIds },
            ),
            ChannelCategory(
                name = "Все каналы",
                channels = channels,
            ),
        ) + channels
            .groupBy(Channel::category)
            .map { (name, categoryChannels) ->
                ChannelCategory(name = name, channels = categoryChannels)
            }
    }
    var favoriteLongPressHandled by remember { mutableStateOf(false) }
    var channelPressStarted by remember { mutableStateOf(false) }
    val initialCategoryIndex = categories.indexOfFirst {
        it.name == selected?.category
    }.coerceAtLeast(0)
    var panelLevel by remember {
        mutableStateOf(
            if (selected == null) PanelLevel.CATEGORIES else PanelLevel.CHANNELS,
        )
    }
    var focusedCategoryIndex by remember {
        mutableIntStateOf(initialCategoryIndex)
    }
    var activeCategoryIndex by remember {
        mutableIntStateOf(initialCategoryIndex)
    }
    val visibleChannels = categories[activeCategoryIndex].channels
    val initialChannelIndex = visibleChannels
        .indexOfFirst { it.id == selected?.id }
        .coerceAtLeast(0)
    var focusedChannelIndex by remember(visibleChannels, selected?.id) {
        mutableIntStateOf(initialChannelIndex)
    }
    val programs = archiveChannel?.programs.orEmpty()
    var archiveDayIndex by remember(selected?.id) { mutableIntStateOf(0) }
    val selectedDayStart = archiveDayStart(archiveDayIndex)
    val selectedDayEnd = archiveDayStart(archiveDayIndex - 1)
    val dayPrograms = remember(programs, selectedDayStart, selectedDayEnd) {
        val now = System.currentTimeMillis()
        programs
            .asSequence()
            .filter { it.start in selectedDayStart until selectedDayEnd && it.start <= now }
            .sortedByDescending(Program::start)
            .toList()
    }
    var focusedProgramIndex by remember(selected?.id, archiveDayIndex) {
        mutableIntStateOf(0)
    }
    LaunchedEffect(dayPrograms, panelLevel) {
        if (panelLevel == PanelLevel.PROGRAMS && dayPrograms.isNotEmpty()) {
            focusedProgramIndex = dayPrograms.indexOfFirst {
                System.currentTimeMillis() in it.start until it.end
            }.coerceAtLeast(0)
        }
    }
    val panelFocus = remember { FocusRequester() }
    val categoryListState = rememberLazyListState(
        initialFirstVisibleItemIndex = max(0, initialCategoryIndex - 3),
    )
    val channelListState = rememberLazyListState(
        initialFirstVisibleItemIndex = max(0, initialChannelIndex - 3),
    )

    LaunchedEffect(Unit) {
        panelFocus.requestFocus()
    }

    LaunchedEffect(focusedCategoryIndex, panelLevel) {
        if (panelLevel == PanelLevel.CATEGORIES) {
            categoryListState.ensureItemVisible(focusedCategoryIndex)
        }
    }

    LaunchedEffect(focusedChannelIndex, panelLevel) {
        if (panelLevel == PanelLevel.CHANNELS) {
            channelListState.ensureItemVisible(focusedChannelIndex)
        }
    }

    Surface(
        modifier = Modifier
            .padding(24.dp)
            .width(if (panelLevel == PanelLevel.PROGRAMS) 1184.dp else 424.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .focusRequester(panelFocus)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    if (
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                    ) {
                        if (
                            panelLevel == PanelLevel.CHANNELS &&
                            channelPressStarted &&
                            !favoriteLongPressHandled
                        ) {
                            visibleChannels.getOrNull(focusedChannelIndex)?.let(onSelect)
                        }
                        favoriteLongPressHandled = false
                        channelPressStarted = false
                        return@onPreviewKeyEvent panelLevel == PanelLevel.CHANNELS
                    }
                    false
                } else {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (searchFocused) return@onPreviewKeyEvent true
                            if (panelLevel == PanelLevel.CATEGORIES) {
                                focusedCategoryIndex = max(0, focusedCategoryIndex - 1)
                            } else if (panelLevel == PanelLevel.CHANNELS) {
                                focusedChannelIndex = max(0, focusedChannelIndex - 1)
                            } else {
                                focusedProgramIndex = if (focusedProgramIndex <= 0) {
                                    dayPrograms.lastIndex.coerceAtLeast(0)
                                } else {
                                    focusedProgramIndex - 1
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (searchFocused) return@onPreviewKeyEvent true
                            if (panelLevel == PanelLevel.CATEGORIES) {
                                focusedCategoryIndex = minOf(
                                    categories.lastIndex,
                                    focusedCategoryIndex + 1,
                                )
                            } else if (panelLevel == PanelLevel.CHANNELS) {
                                focusedChannelIndex = minOf(
                                    visibleChannels.lastIndex,
                                    focusedChannelIndex + 1,
                                )
                            } else {
                                focusedProgramIndex =
                                    if (focusedProgramIndex >= dayPrograms.lastIndex) {
                                        0
                                    } else {
                                        focusedProgramIndex + 1
                                    }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            if (
                                panelLevel == PanelLevel.CHANNELS &&
                                event.nativeKeyEvent.repeatCount == 0
                            ) {
                                channelPressStarted = true
                                return@onPreviewKeyEvent true
                            }
                            if (
                                panelLevel == PanelLevel.CHANNELS &&
                                event.nativeKeyEvent.repeatCount > 0
                            ) {
                                if (!favoriteLongPressHandled) {
                                    visibleChannels.getOrNull(focusedChannelIndex)
                                        ?.let(onToggleFavorite)
                                    favoriteLongPressHandled = true
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (searchFocused) {
                                showSearch = true
                            } else if (panelLevel == PanelLevel.CATEGORIES) {
                                activeCategoryIndex = focusedCategoryIndex
                                val categoryChannels =
                                    categories[activeCategoryIndex].channels
                                focusedChannelIndex = categoryChannels
                                    .indexOfFirst { it.id == selected?.id }
                                    .coerceAtLeast(0)
                                panelLevel = PanelLevel.CHANNELS
                            } else {
                                archiveChannel?.let { channel ->
                                    dayPrograms.getOrNull(focusedProgramIndex)?.let { program ->
                                        onPlayProgram(channel, program)
                                    }
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_PROG_YELLOW -> {
                            if (panelLevel == PanelLevel.CHANNELS) {
                                visibleChannels.getOrNull(focusedChannelIndex)
                                    ?.let(onToggleFavorite)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (searchFocused) {
                                searchFocused = false
                            } else if (panelLevel == PanelLevel.PROGRAMS) {
                                archiveDayIndex = minOf(6, archiveDayIndex + 1)
                                focusedProgramIndex = 0
                            } else if (panelLevel == PanelLevel.CHANNELS) {
                                visibleChannels.getOrNull(focusedChannelIndex)?.let { channel ->
                                    onOpenArchive(channel)
                                    archiveDayIndex = 0
                                    focusedProgramIndex = 0
                                    panelLevel = PanelLevel.PROGRAMS
                                }
                            } else if (panelLevel == PanelLevel.CATEGORIES) {
                                activeCategoryIndex = focusedCategoryIndex
                                panelLevel = PanelLevel.CHANNELS
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (panelLevel == PanelLevel.PROGRAMS) {
                                if (archiveDayIndex > 0) {
                                    archiveDayIndex -= 1
                                    focusedProgramIndex = 0
                                } else {
                                    panelLevel = PanelLevel.CHANNELS
                                }
                            } else if (panelLevel == PanelLevel.CHANNELS) {
                                panelLevel = PanelLevel.CATEGORIES
                            } else {
                                if (searchFocused) {
                                    onDismiss()
                                } else {
                                    searchFocused = true
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable(),
        color = Color(0xD9141B2A),
        contentColor = Color.White,
        shadowElevation = 18.dp,
    ) {
        Row {
        SearchRail(
            focused = searchFocused,
            onClick = { showSearch = true },
        )
        Column(modifier = Modifier.padding(top = 28.dp).width(360.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (panelLevel == PanelLevel.CATEGORIES) {
                            "Категории"
                        } else {
                            categories[activeCategoryIndex].name
                        },
                        fontSize = 24.sp,
                        maxLines = 1,
                    )
                    Text(
                        if (panelLevel == PanelLevel.CATEGORIES) {
                            "${categories.size} доступно"
                        } else {
                            "${visibleChannels.size} каналов"
                        },
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                    )
                }
                if (isEpgUpdating) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "EPG обновляется",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (panelLevel == PanelLevel.CATEGORIES) {
                LazyColumn(
                    state = categoryListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(
                        categories,
                        key = { index, category -> "$index-${category.name}" },
                    ) { index, category ->
                        PanelListItem(
                            name = category.name,
                            selected = index == initialCategoryIndex,
                            focused = index == focusedCategoryIndex,
                            showChevron = true,
                            compact = true,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = channelListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        visibleChannels,
                        key = { _, channel -> channel.id },
                    ) { index, channel ->
                        PanelListItem(
                            name = channel.name,
                            logoUrl = channel.logoUrl,
                            subtitle = channel.currentProgram,
                            programStart = channel.currentProgramStart,
                            programEnd = channel.currentProgramEnd,
                            selected = channel.id == selected?.id,
                            focused = index == focusedChannelIndex,
                            favorite =
                                channel.id in favoriteChannelIds &&
                                    categories[activeCategoryIndex].name != "Избранное",
                        )
                    }
                }
            }
        }
        if (panelLevel == PanelLevel.PROGRAMS) {
            ProgramArchivePanel(
                programs = dayPrograms,
                focusedIndex = focusedProgramIndex,
                watchedProgramStart = watchedProgramStart,
                selectedDayStart = selectedDayStart,
                channelName = archiveChannel?.name.orEmpty(),
            )
            ProgramDetailsPanel(dayPrograms.getOrNull(focusedProgramIndex))
        }
        }
    }
    if (showSearch) {
        SearchDialog(
            channels = channels,
            archiveChannel = archiveChannel,
            searchPrograms = searchPrograms,
            onDismiss = { showSearch = false },
            onSelectChannel = {
                onSelect(it)
                showSearch = false
            },
            onPlayProgram = { channel, program ->
                onPlayProgram(channel, program)
                showSearch = false
            },
        )
    }
}

@Composable
private fun ProgramArchivePanel(
    programs: List<Program>,
    focusedIndex: Int,
    watchedProgramStart: Long?,
    selectedDayStart: Long,
    channelName: String,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (programs.isNotEmpty()) listState.ensureItemVisible(focusedIndex)
    }
    Column(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(top = 28.dp),
    ) {
        Text(
            channelName.ifBlank { "Архив" },
            modifier = Modifier.padding(horizontal = 24.dp),
            fontSize = 22.sp,
        )
        ArchiveDayTabs(selectedDayStart)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                programs,
                key = { _, program -> program.start },
            ) { index, program ->
                ProgramArchiveItem(
                    program = program,
                    focused = index == focusedIndex,
                    watched = program.start == watchedProgramStart,
                )
            }
        }
    }
}

@Composable
private fun ProgramArchiveItem(program: Program, focused: Boolean, watched: Boolean) {
    val now = System.currentTimeMillis()
    val isLive = now in program.start until program.end
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.18f)
                    watched -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (focused || watched) {
                    Modifier.border(
                        if (focused) 2.dp else 1.dp,
                        MaterialTheme.colorScheme.primary,
                        shape,
                    )
                }
                else Modifier,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatProgramTime(program.start),
            modifier = Modifier.width(46.dp),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
        )
        Text(
            program.title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            maxLines = 1,
        )
        if (isLive) {
            Text(
                "ЭФИР",
                color = Color(0xFFFF5252),
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (watched && !isLive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "${((program.end - program.start) / 60_000).coerceAtLeast(1)} мин",
            modifier = Modifier.width(48.dp),
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ArchiveDayTabs(selectedTimestamp: Long?) {
    val dayStarts = remember {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        List(7) { offset ->
            (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }.timeInMillis
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(7) { offset ->
            val dayStart = dayStarts[offset]
            val nextDayStart = if (offset == 0) {
                (Calendar.getInstance().apply {
                    timeInMillis = dayStart
                    add(Calendar.DAY_OF_YEAR, 1)
                }).timeInMillis
            } else {
                dayStarts[offset - 1]
            }
            val selected = selectedTimestamp != null &&
                selectedTimestamp >= dayStart &&
                selectedTimestamp < nextDayStart
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
            ) {
                Text(
                    when (offset) {
                        0 -> "Сегодня"
                        1 -> "Вчера"
                        else -> SimpleDateFormat("dd.MM", Locale.getDefault())
                            .format(Date(dayStart))
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProgramDetailsPanel(program: Program?) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(24.dp),
    ) {
        Text("Подробнее", fontSize = 20.sp)
        Spacer(Modifier.height(22.dp))
        if (program != null) {
            Text(program.title, fontSize = 22.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "${formatProgramTime(program.start)}–${formatProgramTime(program.end)} · " +
                    "${((program.end - program.start) / 60_000).coerceAtLeast(1)} мин",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                program.description.ifBlank { "Описание отсутствует" },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun PanelListItem(
    name: String,
    logoUrl: String = "",
    subtitle: String? = null,
    programStart: Long? = null,
    programEnd: Long? = null,
    selected: Boolean,
    focused: Boolean,
    showChevron: Boolean = false,
    compact: Boolean = false,
    favorite: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    val background = when {
        focused -> Color.White.copy(alpha = 0.18f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when {
                    compact -> 54.dp
                    subtitle == null -> 68.dp
                    else -> 84.dp
                },
            )
            .clip(shape)
            .background(background)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
        Spacer(Modifier.width(16.dp))
        ChannelLogo(logoUrl)
        if (logoUrl.isNotBlank()) Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 17.sp,
                    maxLines = 1,
                )
                if (favorite) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "В избранном",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFFFC107),
                    )
                }
            }
            subtitle?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                ProgramProgress(
                    start = programStart,
                    end = programEnd,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(10.dp))
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ChannelLogo(url: String) {
    if (url.isBlank()) return
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = ChannelLogoMemoryCache.get(url),
        url,
    ) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("User-Agent", "IPTV-TV/0.2 Android")
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            },
                        )?.also { bitmap ->
                            ChannelLogoMemoryCache.put(url, bitmap)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

private object ChannelLogoMemoryCache {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
            value.byteCount / 1024
    }

    fun get(url: String): android.graphics.Bitmap? = cache.get(url)
    fun put(url: String, bitmap: android.graphics.Bitmap) {
        cache.put(url, bitmap)
    }
}

@Composable
private fun ProgramProgress(start: Long?, end: Long?) {
    if (start == null || end == null || end <= start) return
    var now by remember(start, end) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start, end) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val progress = ((now - start).toFloat() / (end - start).toFloat())
        .coerceIn(0f, 1f)
    Spacer(Modifier.height(5.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.16f),
        )
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - 8.dp) * progress)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}

private suspend fun LazyListState.ensureItemVisible(index: Int) {
    if (index < 0) return
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    val safeFirst = visibleItems.getOrNull(1)?.index ?: visibleItems.first().index
    val safeLast = visibleItems.getOrNull(visibleItems.lastIndex - 1)?.index
        ?: visibleItems.last().index
    if (index in safeFirst..safeLast) return
    val itemSize = visibleItems.firstOrNull { it.index == index }?.size
        ?: visibleItems.first().size
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val centerOffset = -((viewportSize - itemSize) / 2)
    scrollToItem(index, centerOffset)
}

private enum class PanelLevel {
    CATEGORIES,
    CHANNELS,
    PROGRAMS,
}

private data class ChannelCategory(
    val name: String,
    val channels: List<Channel>,
)

private data class SearchResult(
    val channel: Channel,
    val program: Program?,
)

private fun formatEpgUpdateTime(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatProgramDay(timestamp: Long): String =
    SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date(timestamp))

private fun formatProgramTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xF21A2232),
        icon = {
            Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Доступно обновление ${update.version}") },
        text = {
            Column {
                Text(update.title)
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        update.notes,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 6,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text("Скачать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Позже")
            }
        },
    )
}

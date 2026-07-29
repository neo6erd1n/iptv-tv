package ru.iptvtv.presentation.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ru.iptvtv.domain.model.AppUpdate
import ru.iptvtv.domain.model.Channel
import kotlin.math.max

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onDownloadUpdate: (AppUpdate) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.isChannelPanelVisible) {
        viewModel.hideChannels()
    }
    BackHandler(enabled = state.isSettingsVisible) {
        viewModel.hideSettings()
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
                            viewModel.showChannels()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            viewModel.hideChannels()
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        state.selectedChannel?.let { channel ->
            VideoPlayer(channel = channel)
        } ?: EmptyPlayerState(
            isLoading = state.isPlaylistLoading,
            error = state.playlistError,
            onOpenSettings = viewModel::showSettings,
        )

        AnimatedVisibility(
            visible = state.isChannelPanelVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelPanel(
                channels = state.channels,
                selected = state.selectedChannel,
                onDismiss = viewModel::hideChannels,
                onSelect = viewModel::selectChannel,
            )
        }
    }

    if (state.isSettingsVisible) {
        SettingsDialog(
            initialUrl = state.streamUrl,
            onDismiss = viewModel::hideSettings,
            onSave = viewModel::saveSettings,
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
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var showError by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val isValid = url.trim().let {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) onSave(url) else showError = true
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
private fun VideoPlayer(channel: Channel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(channel.streamUrl) {
        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    DisposableEffect(Unit) {
        onDispose(player::release)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
    )
}

@Composable
private fun ChannelPanel(
    channels: List<Channel>,
    selected: Channel?,
    onDismiss: () -> Unit,
    onSelect: (Channel) -> Unit,
) {
    val selectedIndex = channels.indexOfFirst { it.id == selected?.id }
        .coerceAtLeast(0)
    var focusedIndex by remember(channels, selected?.id) {
        mutableIntStateOf(selectedIndex)
    }
    val panelFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = max(0, selectedIndex - 3),
    )

    LaunchedEffect(Unit) {
        panelFocus.requestFocus()
    }

    LaunchedEffect(focusedIndex) {
        val isVisible = listState.layoutInfo.visibleItemsInfo.any {
            it.index == focusedIndex
        }
        if (!isVisible) {
            listState.scrollToItem(max(0, focusedIndex - 3))
        }
    }

    Surface(
        modifier = Modifier
            .padding(24.dp)
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .focusRequester(panelFocus)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedIndex = max(0, focusedIndex - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedIndex = minOf(channels.lastIndex, focusedIndex + 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            channels.getOrNull(focusedIndex)?.let(onSelect)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_BACK,
                        -> {
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
        Column(modifier = Modifier.padding(top = 28.dp)) {
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
                    Text("Каналы", fontSize = 24.sp)
                    Text(
                        "${channels.size} доступно",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelItem(
                        channel = channel,
                        selected = channel.id == selected?.id,
                        focused = index == focusedIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    selected: Boolean,
    focused: Boolean,
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
            .height(68.dp)
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
        Text(channel.name, color = Color.White, fontSize = 17.sp)
    }
}

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

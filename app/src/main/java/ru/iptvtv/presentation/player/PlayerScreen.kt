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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenUpdate: (String) -> Unit,
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
        } ?: EmptyPlayerState(onOpenSettings = viewModel::showSettings)

        AnimatedVisibility(
            visible = state.isChannelPanelVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelPanel(
                channels = state.channels,
                selected = state.selectedChannel,
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
                onOpenUpdate(update.downloadUrl)
            },
        )
    }
}

@Composable
private fun EmptyPlayerState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Добавьте ссылку на видеопоток", fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажмите Menu на пульте или откройте настройки",
            color = Color.White.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Настройки")
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
        title = { Text("Настройки видеопотока") },
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
                    label = { Text("Ссылка на HLS-поток") },
                    placeholder = { Text("https://…/playlist.m3u8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = showError,
                    supportingText = {
                        if (showError) {
                            Text("Ссылка должна начинаться с http:// или https://")
                        } else {
                            Text("Поддерживается HLS/M3U8")
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
    onSelect: (Channel) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocus.requestFocus()
    }

    Surface(
        modifier = Modifier
            .padding(24.dp)
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp)),
        color = Color(0xD9141B2A),
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels, key = Channel::id) { channel ->
                    ChannelItem(
                        channel = channel,
                        selected = channel.id == selected?.id,
                        modifier = if (channel == channels.firstOrNull()) {
                            Modifier.focusRequester(firstItemFocus)
                        } else {
                            Modifier
                        },
                        onClick = { onSelect(channel) },
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val background = when {
        focused -> Color.White.copy(alpha = 0.18f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(background)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
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
        Text(channel.name, fontSize = 17.sp)
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

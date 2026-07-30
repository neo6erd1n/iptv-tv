@file:androidx.media3.common.util.UnstableApi

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
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            initialEpgUrl = state.epgUrl,
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
    initialEpgUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
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
private fun VideoPlayer(channel: Channel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val categories = remember(channels) {
        listOf(
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
                            if (panelLevel == PanelLevel.CATEGORIES) {
                                focusedCategoryIndex = max(0, focusedCategoryIndex - 1)
                            } else {
                                focusedChannelIndex = max(0, focusedChannelIndex - 1)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (panelLevel == PanelLevel.CATEGORIES) {
                                focusedCategoryIndex = minOf(
                                    categories.lastIndex,
                                    focusedCategoryIndex + 1,
                                )
                            } else {
                                focusedChannelIndex = minOf(
                                    visibleChannels.lastIndex,
                                    focusedChannelIndex + 1,
                                )
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        -> {
                            if (panelLevel == PanelLevel.CATEGORIES) {
                                activeCategoryIndex = focusedCategoryIndex
                                val categoryChannels =
                                    categories[activeCategoryIndex].channels
                                focusedChannelIndex = categoryChannels
                                    .indexOfFirst { it.id == selected?.id }
                                    .coerceAtLeast(0)
                                panelLevel = PanelLevel.CHANNELS
                            } else {
                                visibleChannels
                                    .getOrNull(focusedChannelIndex)
                                    ?.let(onSelect)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_BACK,
                        -> {
                            if (panelLevel == PanelLevel.CHANNELS) {
                                panelLevel = PanelLevel.CATEGORIES
                            } else {
                                onDismiss()
                            }
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
            }
            Spacer(Modifier.height(18.dp))
            if (panelLevel == PanelLevel.CATEGORIES) {
                LazyColumn(
                    state = categoryListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            subtitle = channel.currentProgram,
                            selected = channel.id == selected?.id,
                            focused = index == focusedChannelIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelListItem(
    name: String,
    subtitle: String? = null,
    selected: Boolean,
    focused: Boolean,
    showChevron: Boolean = false,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = Color.White,
                fontSize = 17.sp,
                maxLines = 1,
            )
            subtitle?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(10.dp))
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
        }
    }
}

private suspend fun LazyListState.ensureItemVisible(index: Int) {
    if (index < 0) return
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo == null) {
        animateScrollToItem(index)
        return
    }
    val scrollDelta = when {
        itemInfo.offset < layoutInfo.viewportStartOffset ->
            itemInfo.offset - layoutInfo.viewportStartOffset
        itemInfo.offset + itemInfo.size > layoutInfo.viewportEndOffset ->
            itemInfo.offset + itemInfo.size - layoutInfo.viewportEndOffset
        else -> 0
    }
    if (scrollDelta != 0) animateScrollBy(scrollDelta.toFloat())
}

private enum class PanelLevel {
    CATEGORIES,
    CHANNELS,
}

private data class ChannelCategory(
    val name: String,
    val channels: List<Channel>,
)

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

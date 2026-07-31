package ru.iptvtv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.iptvtv.data.channel.M3uChannelRepository
import ru.iptvtv.data.settings.SharedPreferencesStreamSettingsRepository
import ru.iptvtv.data.update.GitHubUpdateRepository
import ru.iptvtv.domain.usecase.CheckForUpdateUseCase
import ru.iptvtv.domain.usecase.GetStreamUrlUseCase
import ru.iptvtv.domain.usecase.GetEpgUrlUseCase
import ru.iptvtv.domain.usecase.GetChannelsUseCase
import ru.iptvtv.domain.usecase.SaveStreamUrlUseCase
import ru.iptvtv.domain.usecase.SaveEpgUrlUseCase
import ru.iptvtv.presentation.player.PlayerScreen
import ru.iptvtv.presentation.player.PlayerViewModel
import ru.iptvtv.presentation.theme.IptvTheme
import ru.iptvtv.platform.update.AppUpdateDownloader

class MainActivity : ComponentActivity() {
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var updateDownloader: AppUpdateDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerViewModel = ViewModelProvider(
            this,
            PlayerViewModelFactory(applicationContext),
        )[PlayerViewModel::class.java]
        updateDownloader = AppUpdateDownloader(this).also { it.register() }

        setContent {
            IptvTheme {
                PlayerScreen(
                    viewModel = playerViewModel,
                    onDownloadUpdate = updateDownloader::download,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateDownloader.isInitialized) {
            updateDownloader.resumePendingInstall()
        }
    }

    override fun onDestroy() {
        if (::updateDownloader.isInitialized) {
            updateDownloader.unregister()
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            playerViewModel.showSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private class PlayerViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val settingsRepository = SharedPreferencesStreamSettingsRepository(context)
        return PlayerViewModel(
            getStreamUrl = GetStreamUrlUseCase(settingsRepository),
            getEpgUrl = GetEpgUrlUseCase(settingsRepository),
            saveStreamUrl = SaveStreamUrlUseCase(settingsRepository),
            saveEpgUrl = SaveEpgUrlUseCase(settingsRepository),
            getChannels = GetChannelsUseCase(M3uChannelRepository(context)),
            checkForUpdate = CheckForUpdateUseCase(
                GitHubUpdateRepository(BuildConfig.GITHUB_REPOSITORY),
            ),
            currentVersion = BuildConfig.VERSION_NAME,
            settingsRepository = settingsRepository,
        ) as T
    }
}

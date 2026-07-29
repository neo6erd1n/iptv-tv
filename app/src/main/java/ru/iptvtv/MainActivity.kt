package ru.iptvtv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.iptvtv.data.settings.SharedPreferencesStreamSettingsRepository
import ru.iptvtv.data.update.GitHubUpdateRepository
import ru.iptvtv.domain.usecase.CheckForUpdateUseCase
import ru.iptvtv.domain.usecase.GetStreamUrlUseCase
import ru.iptvtv.domain.usecase.SaveStreamUrlUseCase
import ru.iptvtv.presentation.player.PlayerScreen
import ru.iptvtv.presentation.player.PlayerViewModel
import ru.iptvtv.presentation.theme.IptvTheme

class MainActivity : ComponentActivity() {
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerViewModel = ViewModelProvider(
            this,
            PlayerViewModelFactory(applicationContext),
        )[PlayerViewModel::class.java]

        setContent {
            IptvTheme {
                PlayerScreen(
                    viewModel = playerViewModel,
                    onOpenUpdate = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
        }
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
            saveStreamUrl = SaveStreamUrlUseCase(settingsRepository),
            checkForUpdate = CheckForUpdateUseCase(
                GitHubUpdateRepository(BuildConfig.GITHUB_REPOSITORY),
            ),
            currentVersion = BuildConfig.VERSION_NAME,
        ) as T
    }
}

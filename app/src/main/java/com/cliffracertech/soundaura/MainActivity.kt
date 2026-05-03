/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cliffracertech.soundaura.addbutton.AddButton
import com.cliffracertech.soundaura.appbar.SoundAuraAppBar
import com.cliffracertech.soundaura.background.BackgroundCollectionScreen
import com.cliffracertech.soundaura.background.BackgroundCollectionType
import com.cliffracertech.soundaura.background.BackgroundEditorScreen
import com.cliffracertech.soundaura.background.BackgroundImportButton
import com.cliffracertech.soundaura.background.BackgroundSurface
import com.cliffracertech.soundaura.background.BackgroundRepository
import com.cliffracertech.soundaura.background.BackgroundSaveButton
import com.cliffracertech.soundaura.background.LocalMainBackground
import com.cliffracertech.soundaura.launchIO
import com.cliffracertech.soundaura.library.SoundAuraLibraryView
import com.cliffracertech.soundaura.mediacontroller.MediaControllerSizes
import com.cliffracertech.soundaura.mediacontroller.SoundAuraMediaController
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.UpdateChecker
import com.cliffracertech.soundaura.model.UpdateCheckResult
import com.cliffracertech.soundaura.model.UpdateInfo
import com.cliffracertech.soundaura.settings.AppLanguage
import com.cliffracertech.soundaura.settings.AppSettings
import com.cliffracertech.soundaura.settings.AppTheme
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.cliffracertech.soundaura.ui.tweenDuration
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

sealed interface MainContentScreen {
    val depth: Int

    data object Library : MainContentScreen {
        override val depth = 0
    }

    data object Settings : MainContentScreen {
        override val depth = 1
    }

    data class BackgroundCollection(
        val collection: BackgroundCollectionType,
    ) : MainContentScreen {
        override val depth = 2
    }

    data class BackgroundEditor(
        val collection: BackgroundCollectionType,
        val imageId: Long,
    ) : MainContentScreen {
        override val depth = 3
    }
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    messageHandler: MessageHandler,
    private val dataStore: DataStore<Preferences>,
    private val navigationState: NavigationState,
    private val playbackState: PlaybackState,
    private val searchQueryState: SearchQueryState,
    private val updateChecker: UpdateChecker,
    backgroundRepository: BackgroundRepository,
) : ViewModel() {
    private val scope = viewModelScope + Dispatcher.Immediate
    val messages = messageHandler.messages
    val showingAppSettings get() = navigationState.showingAppSettings
    val showingPresetSelector get() = navigationState.mediaControllerState.isExpanded
    val showingBackgroundCollectionPage get() = navigationState.showingBackgroundCollectionPage
    val backgroundCollectionType get() = navigationState.backgroundCollectionType
    val currentContentScreen get() = when {
        navigationState.showingBackgroundEditor ->
            MainContentScreen.BackgroundEditor(
                collection = navigationState.backgroundCollectionType ?: BackgroundCollectionType.Main,
                imageId = navigationState.backgroundEditorImageId ?: 0L,
            )
        navigationState.showingBackgroundCollectionPage ->
            MainContentScreen.BackgroundCollection(
                navigationState.backgroundCollectionType ?: BackgroundCollectionType.Main,
            )
        navigationState.showingAppSettings -> MainContentScreen.Settings
        else -> MainContentScreen.Library
    }

    private val appThemeKey = intPreferencesKey(PrefKeys.appTheme)
    val appTheme by runBlocking {
        dataStore.awaitEnumPreferenceState<AppTheme>(appThemeKey, scope)
    }
    private val frostedGlassOpacityPercentKey =
        intPreferencesKey(PrefKeys.frostedGlassOpacityPercent)
    val frostedGlassOpacityPercent by runBlocking {
        dataStore.awaitPreferenceState(
            key = frostedGlassOpacityPercentKey,
            defaultValue = 72,
            scope = scope,
        )
    }

    private val lastLaunchedVersionCodeKey = intPreferencesKey(PrefKeys.lastLaunchedVersionCode)
    val lastLaunchedVersionCode by dataStore.preferenceState(
        key = lastLaunchedVersionCodeKey,
        initialValue = 0,
        defaultValue = 9,
        scope = scope,
    )

    val currentMainBackground by backgroundRepository
        .currentBackgroundFlow(BackgroundCollectionType.Main)
        .collectAsState(null, scope)

    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    init {
        scope.launch {
            val result = updateChecker.checkAutomatically(BuildConfig.VERSION_NAME)
            if (result is UpdateCheckResult.UpdateAvailable)
                availableUpdate = result.update
        }
    }

    fun onNewVersionDialogDismiss() {
        dataStore.edit(lastLaunchedVersionCodeKey, BuildConfig.VERSION_CODE, scope)
    }

    fun onUpdateDialogDismiss(ignoreThisUpdate: Boolean) {
        val update = availableUpdate ?: return
        availableUpdate = null
        if (ignoreThisUpdate)
            scope.launchIO { updateChecker.ignoreUpdate(update.tagName) }
    }

    fun onBackButtonClick(): Boolean {
        navigationState.currentSearchScope?.let { scope ->
            if (searchQueryState.isActive(scope)) {
                searchQueryState.clear(scope)
                return true
            }
        }
        return navigationState.onBackButtonClick()
    }

    fun onKeyDown(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            playbackState.toggleIsPlaying()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        }
        KeyEvent.KEYCODE_MEDIA_STOP -> {
            if (playbackState.isPlaying) {
                playbackState.toggleIsPlaying()
                true
            } else false
        }
        else -> false
    }
}

val LocalWindowSizeClass = compositionLocalOf {
    WindowSizeClass.calculateFromSize(DpSize(0.dp, 0.dp))
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (!viewModel.onBackButtonClick())
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultAppLanguage()

        setContentWithTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        message.showAsSnackbar(this@MainActivity, snackbarHostState)
                    }
                }

                NewVersionDialogShower(
                    lastLaunchedVersionCode = viewModel.lastLaunchedVersionCode,
                    onDialogDismissed = viewModel::onNewVersionDialogDismiss,
                )
                viewModel.availableUpdate?.let { update ->
                    val uriHandler = LocalUriHandler.current
                    UpdateAvailableDialog(
                        update = update,
                        showIgnoreOption = true,
                        onUpdateClick = {
                            uriHandler.openUri(update.downloadUrl)
                            viewModel.onUpdateDialogDismiss(ignoreThisUpdate = false)
                        },
                        onDismissClick = viewModel::onUpdateDialogDismiss,
                    )
                }

                BackgroundSurface(
                    background = viewModel.currentMainBackground,
                    storage = com.cliffracertech.soundaura.background.rememberBackgroundStorage(),
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    fallbackColor = MaterialTheme.colors.background,
                    overlayColor = MaterialTheme.colors.background.copy(alpha = 0.32f),
                ) {
                    Column {
                        SoundAuraAppBar()
                        val mainContentPadding = rememberWindowInsetsPaddingValues(
                            insets = WindowInsets.navigationBars,
                            additionalTop = 8.dp,
                            additionalStart = 8.dp,
                            additionalBottom = MediaControllerSizes.defaultMinThicknessDp.dp + 16.dp,
                            additionalEnd = 8.dp,
                        )
                        MainContent(mainContentPadding)
                    }
                }

                val floatingButtonPadding = rememberWindowInsetsPaddingValues(
                    insets = WindowInsets.systemBars,
                    additionalStart = 8.dp,
                    additionalEnd = 8.dp,
                    additionalBottom = 8.dp,
                    additionalTop = 8.dp + 56.dp,
                )

                SoundAuraMediaController(padding = floatingButtonPadding)

                AddTrackButton(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(floatingButtonPadding),
                )

                viewModel.backgroundCollectionType
                    ?.takeIf { viewModel.showingBackgroundCollectionPage }
                    ?.let { collection ->
                        BackgroundSaveButton(
                            collection = collection,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(floatingButtonPadding),
                        )
                        BackgroundImportButton(
                            collection = collection,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(floatingButtonPadding),
                        )
                    }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(floatingButtonPadding),
                )
            }
        }
    }

    private fun ensureDefaultAppLanguage() {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags().isBlank())
            AppCompatDelegate.setApplicationLocales(AppLanguage.English.localeList())
    }

    private fun setContentWithTheme(
        parent: CompositionContext? = null,
        content: @Composable () -> Unit,
    ) = setContent(parent) {
        val themePreference = viewModel.appTheme
        val frostedGlassOpacityPercent = viewModel.frostedGlassOpacityPercent

        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        SoundAuraTheme(
            appTheme = themePreference,
            frostedGlassOpacityPercent = frostedGlassOpacityPercent,
        ) {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(
                LocalWindowSizeClass provides windowSizeClass,
                LocalMainBackground provides viewModel.currentMainBackground,
            ) {
                content()
            }
        }
    }

    @Composable
    private fun MainContent(padding: PaddingValues) {
        val trackListState = rememberLazyListState()
        val mainBackgroundListState = rememberLazyListState()
        val cardBackgroundListState = rememberLazyListState()
        val currentScreen = viewModel.currentContentScreen
        var previousScreen by remember { mutableStateOf(currentScreen) }

        SlideAnimatedContent(
            targetState = currentScreen,
            leftToRight = previousScreen.depth > currentScreen.depth,
            modifier = Modifier.fillMaxSize(),
        ) { screen ->
            when (screen) {
                MainContentScreen.Library -> SoundAuraLibraryView(
                    padding = padding,
                    state = trackListState,
                )
                MainContentScreen.Settings -> AppSettings(padding)
                is MainContentScreen.BackgroundCollection -> BackgroundCollectionScreen(
                    collection = screen.collection,
                    contentPadding = padding,
                    state = screen.collection.listState(
                        mainState = mainBackgroundListState,
                        cardState = cardBackgroundListState,
                    ),
                )
                is MainContentScreen.BackgroundEditor -> BackgroundEditorScreen(
                    contentPadding = padding,
                )
            }
        }

        LaunchedEffect(currentScreen) {
            previousScreen = currentScreen
        }
    }

    @Composable
    private fun AddTrackButton(modifier: Modifier = Modifier) {
        val showingPresetSelector = viewModel.showingPresetSelector
        val addButtonXDpOffset by animateDpAsState(
            targetValue = if (showingPresetSelector) (-16).dp else 0.dp,
            label = "Add button x offset animation",
            animationSpec = tween(tweenDuration * 5 / 4, 0, LinearOutSlowInEasing),
        )
        val addButtonYDpOffset by animateDpAsState(
            targetValue = if (showingPresetSelector) (-16).dp else 0.dp,
            label = "Add button y offset animation",
            animationSpec = tween(tweenDuration, 0, LinearOutSlowInEasing),
        )

        AddButton(
            backgroundColor = MaterialTheme.colors.secondaryVariant,
            visible = !viewModel.showingAppSettings,
            modifier = modifier.graphicsLayer {
                translationX = addButtonXDpOffset.toPx()
                translationY = addButtonYDpOffset.toPx()
            },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        if (viewModel.onKeyDown(keyCode)) true
        else super.onKeyDown(keyCode, event)
}

private fun BackgroundCollectionType.listState(
    mainState: LazyListState,
    cardState: LazyListState,
) = when (this) {
    BackgroundCollectionType.Main -> mainState
    BackgroundCollectionType.Card -> cardState
}

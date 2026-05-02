/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.appbar

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.addbutton.getDisplayName
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.enumPreferenceState
import com.cliffracertech.soundaura.launchIO
import com.cliffracertech.soundaura.library.FolderDialog
import com.cliffracertech.soundaura.library.FolderDialogShower
import com.cliffracertech.soundaura.model.FolderUseCases
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.SearchScope
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.SimpleIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A [ViewModel] that contains state and callbacks for the application's top app bar.
 *
 * The properties [onBackButtonClick], [title], [showIconButtons],
 * [searchQueryViewState], and [sortMenuState] can be used as the same-named
 * parameters in a [ListAppBar]. In addition to these properties, the property
 * [showActivePlaylistsFirstSwitchState] should be used as the state for a
 * 'Show active playlists first' switch within the [ListAppBar]'s sort popup
 * menu (i.e. through its [ListAppBar.otherSortMenuContent] parameter). The
 * [ListAppBar.otherIconButtons] parameter should contain a settings icon
 * button that uses the property [onSettingsButtonClick] as its onClick action.
 */
@HiltViewModel class AppBarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val navigationState: NavigationState,
    private val searchQuery: SearchQueryState,
    private val folderUseCases: FolderUseCases,
    private val messageHandler: MessageHandler,
) : ViewModel() {
    private val scope = viewModelScope + Dispatcher.Immediate
    var shownFolderDialog by mutableStateOf<FolderDialog?>(null)
        private set

    private var pendingLocalUris = emptyList<Uri>()
    private var pendingFolderName = ""
    private fun dismissFolderDialog() { shownFolderDialog = null }
    private val currentSearchScope get() = navigationState.currentSearchScope

    val onBackButtonClick: (() -> Unit)? get() = currentSearchScope
        ?.takeIf(searchQuery::isActive)
        ?.let { scope ->
            { searchQuery.clear(scope) }
        }
        ?: when {
        navigationState.showingBackgroundEditor -> {
            { navigationState.onBackgroundEditorBackClick() }
        }
        navigationState.showingBackgroundCollectionPage -> {
            { navigationState.onBackButtonClick() }
        }
        navigationState.showingAppSettings -> {
            { navigationState.onBackButtonClick() }
        } navigationState.openFolderId != null -> {
            { navigationState.onBackButtonClick() }
        } else -> null
    }

    val title get() = when {
        navigationState.showingBackgroundEditor ->
            StringResource(navigationState.backgroundCollectionType?.editorTitleResId ?: R.string.app_name)
        navigationState.showingBackgroundCollectionPage ->
            StringResource(navigationState.backgroundCollectionType?.pageTitleResId ?: R.string.app_name)
        navigationState.showingAppSettings ->
            StringResource(R.string.app_settings_description)
        navigationState.openFolderId != null ->
            StringResource(navigationState.openFolderName.orEmpty())
        else -> StringResource(R.string.app_name)
    }

    val showSearchButton get() = currentSearchScope != null
    val showSortButton get() =
        !navigationState.showingAppSettings &&
            !navigationState.showingBackgroundCollectionPage &&
            !navigationState.showingBackgroundEditor
    val showLibraryButtons get() = showSortButton

    val searchQueryViewState = SearchQueryViewState(
        getQuery = {
            currentSearchScope?.let(searchQuery::value)
        },
        onQueryChange = { query ->
            currentSearchScope?.let { scope -> searchQuery.set(scope, query) }
        },
        onButtonClick = {
            currentSearchScope?.let(searchQuery::toggleIsActive)
        },
        getIcon = {
            if (currentSearchScope?.let(searchQuery::isActive) == true)
                SearchQueryViewState.Icon.Close
            else                      SearchQueryViewState.Icon.Search
        })

    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val playlistSort by dataStore.enumPreferenceState<Playlist.Sort>(playlistSortKey, scope)
    private val playlistSortOptions = Playlist.Sort.entries
    val sortMenuState = SortMenuState(
        optionNames = @Composable { context ->
            // The locale is used as a key so that configuration changes won't
            // leave the sort option names as their previous-locale values
            remember(context.resources.configuration.locales.get(0)) {
                List(playlistSortOptions.size) {
                    playlistSortOptions[it].name(context)
                }.toImmutableList()
            }
        }, getCurrentOptionIndex = { playlistSort.ordinal },
        onOptionClick = { dataStore.edit(playlistSortKey, it, scope) })

    private val showActivePlaylistsFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val showActivePlaylistsFirst by dataStore.preferenceState(showActivePlaylistsFirstKey, false, scope)
    val showActivePlaylistsFirstSwitchState = SwitchState(
        getChecked = ::showActivePlaylistsFirst,
        onClick = {
            sortMenuState.onPopupDismissRequest()
            dataStore.edit(showActivePlaylistsFirstKey, !showActivePlaylistsFirst, scope)
        })

    fun onSettingsButtonClick() {
        currentSearchScope?.takeIf(searchQuery::isActive)?.let(searchQuery::clear)
        navigationState.showAppSettings()
    }

    fun onCreateFolderClick() {
        shownFolderDialog = FolderDialog.SourceChoice(
            onDismissRequest = ::dismissFolderDialog,
            onLocalAudioClick = ::showLocalAudioChooser,
            onCurrentAudioClick = ::showNameCurrentAudioFolder)
    }

    private fun showLocalAudioChooser() {
        shownFolderDialog = FolderDialog.SelectingFiles(
            onDismissRequest = ::dismissFolderDialog,
            onFilesSelected = { uris ->
                pendingLocalUris = uris
                val initialName = uris.firstOrNull()?.getDisplayName(context).orEmpty()
                showNameFolder(initialName) { name ->
                    createFolderFromLocalFiles(name, uris)
                }
            })
    }

    private fun showNameCurrentAudioFolder() {
        scope.launchIO {
            val playlists = folderUseCases.activeLibraryPlaylists()
            withContext(Dispatcher.Immediate) {
                if (playlists.isEmpty()) {
                    dismissFolderDialog()
                    messageHandler.postMessage(
                        R.string.folder_no_current_audio_warning,
                        SnackbarDuration.Long)
                } else showNameFolder(playlists.first().name) { name ->
                    createFolderFromCurrentAudio(name, playlists.map { it.id })
                }
            }
        }
    }

    private fun showNameFolder(
        initialName: String,
        onNameValidated: suspend (String) -> Unit,
    ) {
        shownFolderDialog = FolderDialog.NameFolder(
            onDismissRequest = ::dismissFolderDialog,
            namingState = folderUseCases.newFolderNamingState(
                scope = scope,
                initialName = initialName,
                onNameValidated = onNameValidated))
    }

    private suspend fun createFolderFromCurrentAudio(name: String, playlistIds: List<Long>) {
        when (folderUseCases.createFolderFromPlaylists(name, playlistIds)) {
            FolderUseCases.Result.Success -> dismissFolderDialog()
            FolderUseCases.Result.EmptySelection -> {
                dismissFolderDialog()
                messageHandler.postMessage(
                    R.string.folder_no_current_audio_warning,
                    SnackbarDuration.Long)
            }
            is FolderUseCases.Result.Failure -> Unit
        }
    }

    private suspend fun createFolderFromLocalFiles(name: String, uris: List<Uri>) {
        pendingFolderName = name
        when (val result = folderUseCases.createFolderFromLocalFiles(name, uris)) {
            FolderUseCases.Result.Success -> dismissFolderDialog()
            FolderUseCases.Result.EmptySelection -> dismissFolderDialog()
            is FolderUseCases.Result.Failure -> showRequestStoragePermission(result)
        }
    }

    private fun showRequestStoragePermission(result: FolderUseCases.Result.Failure) {
        shownFolderDialog = FolderDialog.RequestStoragePermissionExplanation(
            permissionsUsed = result.permissionsUsed,
            permissionsAllowed = result.permissionAllowance,
            onDismissRequest = ::dismissFolderDialog,
            onOkClick = {
                shownFolderDialog = FolderDialog.RequestStoragePermission(
                    onDismissRequest = ::dismissFolderDialog,
                    onResult = { granted ->
                        if (granted) scope.launchIO {
                            createFolderFromLocalFiles(pendingFolderName, pendingLocalUris)
                        } else {
                            messageHandler.postMessage(
                                R.string.cant_add_tracks_warning,
                                SnackbarDuration.Long)
                            dismissFolderDialog()
                        }
                    })
            })
    }
}

/** Compose a [ListAppBar] with state provided by an instance of [AppBarViewModel]. */
@Composable fun SoundAuraAppBar(
    modifier: Modifier = Modifier,
) {
    val viewModel: AppBarViewModel = viewModel()
    val context = LocalContext.current
    val title = remember(viewModel.title) {
        viewModel.title.resolve(context)
    }
    ListAppBar(
        modifier = modifier,
        onBackButtonClick = viewModel.onBackButtonClick,
        title = title,
        showSearchButton = viewModel.showSearchButton,
        showSortButton = viewModel.showSortButton,
        searchQueryState = viewModel.searchQueryViewState,
        sortMenuState = viewModel.sortMenuState,
        otherSortMenuContent = {
            DropdownMenuItem(onClick = viewModel.showActivePlaylistsFirstSwitchState.onClick) {
                Text(stringResource(R.string.show_active_playlists_first),
                     style = MaterialTheme.typography.button)
                Spacer(Modifier.weight(1f).widthIn(12.dp))
                Switch(viewModel.showActivePlaylistsFirstSwitchState.checked,
                       onCheckedChange = null)
            }
            Divider()
        }, leadingIconButtons = {
            if (viewModel.showLibraryButtons)
                SimpleIconButton(
                    icon = Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.create_folder_button_description),
                    onClick = viewModel::onCreateFolderClick)
        }, otherIconButtons = {
            if (viewModel.showLibraryButtons)
                SimpleIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.app_settings_description),
                    onClick = viewModel::onSettingsButtonClick)
        })
    FolderDialogShower(viewModel.shownFolderDialog)
}

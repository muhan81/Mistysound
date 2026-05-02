/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.launchIO
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.FolderUseCases
import com.cliffracertech.soundaura.model.ModifyLibraryUseCase
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.ReadLibraryUseCase
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.screenSizeBasedHorizontalPadding
import com.cliffracertech.soundaura.ui.tweenDuration
import com.cliffracertech.soundaura.ui.theme.AppCardSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.plus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

/** LibraryState's subtypes, [Loading], [Empty], and [Content], represent
 * the possible states for an asynchronously loaded library of [Playlist]s. */
sealed class LibraryState {
    /** The library's contents are still being loaded */
    data object Loading: LibraryState()

    /** The library is empty. [message] can be resolved to obtain a [String]
     * describing the empty content. This can be used to, e.g., explain that
     * the library is empty due to the current search criteria. */
    class Empty(val message: StringResource): LibraryState()

    /** The library's content has been loaded, and can be obtained through the
     * property [playlists]. The value of the property [playlistViewCallback]
     * can be used as an item callback in, e.g., a [PlaylistView]. */
    class Content(
        private val getPlaylists: () -> ImmutableList<Playlist>?,
        private val getFolders: () -> ImmutableList<Folder>?,
        private val getFolderPlaylists: () -> ImmutableList<Playlist>?,
        private val isFolderSearchActive: () -> Boolean,
        val playlistViewCallback: PlaylistViewCallback,
        val folderPlaylistViewCallback: PlaylistViewCallback,
        val folderViewCallback: FolderViewCallback,
        val onFolderPlaylistMove: (fromIndex: Int, toIndex: Int) -> Unit,
    ): LibraryState() {
        val playlists get() = getPlaylists()
        val folders get() = getFolders()
        val folderPlaylists get() = getFolderPlaylists()
        val folderSearchActive get() = isFolderSearchActive()
    }
}

/**
 * A [LazyColumn] to display all of the provided [Playlist]s with instances of [PlaylistView].
 *
 * @param modifier The [Modifier] that will be used for the TrackList
 * @param lazyListState The [LazyListState] used for the library's scrolling state
 * @param contentPadding The [PaddingValues] instance that will be used as
 *     the content padding for the list of items
 * @param shownDialog A [PlaylistDialog] instance that describes the [Playlist]
 *     related dialog that should be shown, or null if no dialog needs to be shown
 * @param libraryState A [LibraryState] instance that describes the UI state of the LibraryView
 */
@Composable fun LibraryView(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    shownDialog: PlaylistDialog?,
    shownFolderDialog: FolderDialog?,
    openFolderId: Long?,
    libraryState: LibraryState,
) {
    PlaylistDialogShower(shownDialog)
    FolderDialogShower(shownFolderDialog)

    Crossfade(
        targetState = libraryState,
        modifier = modifier,
        animationSpec = tween(tweenDuration),
        label = "LibraryView loading/empty/content crossfade",
    ) { viewState ->
        when (viewState) {
            is LibraryState.Loading -> {
                // The CircularProgressIndicator is not center aligned properly when
                // Modifier.wrapContentSize() is used, so a fillMaxSize box is used instead
                Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
                    CircularProgressIndicator(strokeCap = StrokeCap.Round)
                }
            }
            is LibraryState.Empty -> {
                val context = LocalContext.current
                val text = remember(viewState) { viewState.message.resolve(context) }
                Text(text = text,
                     modifier = Modifier
                         .fillMaxSize()
                         .padding(contentPadding)
                         .screenSizeBasedHorizontalPadding(48.dp)
                         .wrapContentSize(),
                     textAlign = TextAlign.Justify)
            }
            is LibraryState.Content -> {
                if (openFolderId == null) {
                    val folders = viewState.folders.orEmpty()
                    val playlists = viewState.playlists.orEmpty()
                    LazyColumn(
                        Modifier, lazyListState, contentPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(folders, key = Folder::id::get) { folder ->
                            FolderView(folder, viewState.folderViewCallback,
                                       Modifier.animateItem())
                        }
                        items(playlists, key = Playlist::name::get) { playlist ->
                            PlaylistView(playlist, viewState.playlistViewCallback,
                                         Modifier.animateItem())
                        }
                    }
                } else FolderDetailView(
                    folderId = openFolderId,
                    contentPadding = contentPadding,
                    libraryState = viewState,
                    lazyListState = lazyListState)
            }
        }
    }
}

@Composable private fun FolderDetailView(
    folderId: Long,
    contentPadding: PaddingValues,
    libraryState: LibraryState.Content,
    lazyListState: LazyListState,
) {
    val playlists = libraryState.folderPlaylists
    var showLoadingPlaceholder by remember(folderId) { mutableStateOf(false) }

    LaunchedEffect(folderId, playlists == null) {
        if (playlists == null) {
            showLoadingPlaceholder = false
            delay(140L)
            showLoadingPlaceholder = true
        } else showLoadingPlaceholder = false
    }

    if (playlists == null) {
        if (showLoadingPlaceholder)
            FolderDetailLoadingPlaceholder(contentPadding, lazyListState)
        else Box(Modifier.fillMaxSize().padding(contentPadding))
    } else if (playlists.isEmpty()) {
        Text(
            text = stringResource(
                if (libraryState.folderSearchActive)
                    R.string.no_search_results_message
                else
                    R.string.empty_folder_message),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .screenSizeBasedHorizontalPadding(48.dp)
                .wrapContentSize(),
            textAlign = TextAlign.Justify)
    } else {
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            libraryState.onFolderPlaylistMove(from.index, to.index)
        }
        LazyColumn(
            Modifier,
            lazyListState,
            contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
                ReorderableItem(reorderableState, key = playlist.id) {
                    PlaylistView(
                        playlist = playlist,
                        callback = libraryState.folderPlaylistViewCallback,
                        modifier = Modifier
                            .animateItem()
                            .longPressDraggableHandle())
                }
            }
        }
    }
}

@Composable private fun FolderDetailLoadingPlaceholder(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(4) { FolderDetailLoadingPlaceholderRow() }
    }
}

@Composable private fun FolderDetailLoadingPlaceholderRow() = AppCardSurface(
    shape = MaterialTheme.shapes.large,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    MaterialTheme.colors.onSurface.copy(alpha = 0.18f),
                    CircleShape))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(18.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.18f)))
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(12.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 18.dp)
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))
    }
}

/**
 * A [ViewModel] to provide state and callbacks for an instance of LibraryView.
 *
 * The most recent list of all playlists is provided via the property
 * [playlists]. The [PlaylistViewCallback] that should be used for item
 * interactions is provided via the property [itemCallback]. The state
 * of any dialogs that should be shown are provided via the property
 * [shownDialog].
 */
@HiltViewModel class LibraryViewModel @Inject constructor(
    private val readLibrary: ReadLibraryUseCase,
    private val modifyLibrary: ModifyLibraryUseCase,
    private val folderUseCases: FolderUseCases,
    private val navigationState: NavigationState,
    private val searchQueryState: SearchQueryState,
    private val messageHandler: MessageHandler,
    playbackState: PlaybackState,
) : ViewModel() {
    private val scope = viewModelScope + Dispatcher.Immediate

    var shownDialog by mutableStateOf<PlaylistDialog?>(null)
    private fun dismissDialog() { shownDialog = null }
    var shownFolderDialog by mutableStateOf<FolderDialog?>(null)
    private fun dismissFolderDialog() { shownFolderDialog = null }
    private var folderPlaylistJob: Job? = null
    private var folderPlaylists by mutableStateOf<ImmutableList<Playlist>?>(null)
    private var pendingFolderId = 0L
    private var pendingFolderUris = emptyList<Uri>()

    private val itemCallback = object : PlaylistViewCallback {
        override fun onAddRemoveButtonClick(playlist: Playlist) {
            scope.launchIO { modifyLibrary.togglePlaylistIsActive(playlist.id) }
        }
        override fun onVolumeChange(playlist: Playlist, volume: Float) {
            playbackState.setPlaylistVolume(playlist.id, volume)
        }
        override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) {
            scope.launchIO { modifyLibrary.setPlaylistVolume(playlist.id, volume) }
        }
        override fun getProgress(playlist: Playlist) =
            playbackState.getPlaylistProgress(playlist.id)
        override fun onSeek(playlist: Playlist, positionMillis: Int) {
            playbackState.seekPlaylistTo(playlist.id, positionMillis)
        }
        override fun onPlaybackSpeedClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.PlaybackSpeed(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirm = { speed ->
                    dismissDialog()
                    playbackState.setPlaylistPlaybackSpeed(playlist.id, speed)
                    scope.launchIO { modifyLibrary.setPlaylistPlaybackSpeed(playlist.id, speed) }
                })
        }
        override fun onRenameClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Rename(
                target = playlist,
                namingState = modifyLibrary.renameState(
                    playlist.id, playlist.name, scope, ::dismissDialog),
                onDismissRequest = ::dismissDialog)
        }
        override fun onExtraOptionsClick(playlist: Playlist) {
            scope.launchIO {
                val existingTracks = readLibrary.getPlaylistTracks(playlist.id)
                val shuffleEnabled = readLibrary.getPlaylistShuffle(playlist.id)
                assert((existingTracks.size == 1) == playlist.isSingleTrack)
                withContext(Dispatcher.Immediate) {
                    if (playlist.isSingleTrack)
                        showFileChooser(playlist, existingTracks)
                    else showPlaylistOptions(playlist, existingTracks, shuffleEnabled)
                }
            }
        }
        override fun onVolumeBoostClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.BoostVolume(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirm = { volumeBoostDb ->
                    dismissDialog()
                    scope.launchIO {
                        modifyLibrary.setPlaylistVolumeBoostDb(playlist.id, volumeBoostDb)
                    }
                })
        }
        override fun onRemoveClick(playlist: Playlist) {
            if (playlist.hasError)
                scope.launchIO { modifyLibrary.removePlaylist(playlist.id) }
            else shownDialog = PlaylistDialog.Remove(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    dismissDialog()
                    scope.launchIO { modifyLibrary.removePlaylist(playlist.id) }
                })
        }
    }

    private val folderPlaylistCallback = object : PlaylistViewCallback {
        override fun onAddRemoveButtonClick(playlist: Playlist) =
            itemCallback.onAddRemoveButtonClick(playlist)
        override fun onVolumeChange(playlist: Playlist, volume: Float) =
            itemCallback.onVolumeChange(playlist, volume)
        override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) =
            itemCallback.onVolumeChangeFinished(playlist, volume)
        override fun getProgress(playlist: Playlist) =
            itemCallback.getProgress(playlist)
        override fun onSeek(playlist: Playlist, positionMillis: Int) =
            itemCallback.onSeek(playlist, positionMillis)
        override fun onPlaybackSpeedClick(playlist: Playlist) =
            itemCallback.onPlaybackSpeedClick(playlist)
        override fun onRenameClick(playlist: Playlist) =
            itemCallback.onRenameClick(playlist)
        override fun onExtraOptionsClick(playlist: Playlist) =
            itemCallback.onExtraOptionsClick(playlist)
        override fun onVolumeBoostClick(playlist: Playlist) =
            itemCallback.onVolumeBoostClick(playlist)
        override fun onRemoveClick(playlist: Playlist) {
            val folderId = navigationState.openFolderId ?: return
            shownDialog = PlaylistDialog.RemoveFromFolder(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    dismissDialog()
                    scope.launchIO {
                        folderUseCases.removePlaylistFromFolder(folderId, playlist.id)
                    }
                })
        }
    }

    private val folderCallback = object : FolderViewCallback {
        override fun onAddRemoveButtonClick(folder: Folder) {
            scope.launchIO { folderUseCases.toggleFolderIsActive(folder.id) }
        }

        override fun onOpenFolder(folder: Folder) {
            navigationState.openFolder(folder.id, folder.name)
            folderPlaylistJob?.cancel()
            folderPlaylists = null
            folderPlaylistJob = readLibrary.folderPlaylistsFlow(folder.id)
                .onEach { folderPlaylists = it }
                .launchIn(scope)
        }

        override fun onRenameClick(folder: Folder) {
            shownFolderDialog = FolderDialog.Rename(
                onDismissRequest = ::dismissFolderDialog,
                namingState = folderUseCases.renameState(
                    folder.id, folder.name, scope, ::dismissFolderDialog))
        }

        override fun onAddAudioClick(folder: Folder) {
            showAddToFolderSourceChoice(folder.id)
        }

        override fun onShuffleClick(folder: Folder) {
            scope.launchIO {
                val ids = readLibrary.getFolderPlaylistIds(folder.id)
                folderUseCases.setFolderShuffleAndPlaylists(
                    folder.id, !folder.shuffle, ids)
            }
        }

        override fun onRemoveClick(folder: Folder) {
            shownFolderDialog = FolderDialog.Remove(
                folder = folder,
                onDismissRequest = ::dismissFolderDialog,
                onConfirmClick = {
                    scope.launchIO { folderUseCases.removeFolder(folder.id) }
                    if (navigationState.openFolderId == folder.id)
                        navigationState.closeFolder()
                })
        }
    }

    private val playlists by readLibrary.playlistsFlow.collectAsState(null, scope)
    private val folders by readLibrary.foldersFlow.collectAsState(null, scope)
    private val noSearchResultsState = LibraryState.Empty(StringResource(R.string.no_search_results_message))
    private val emptyLibraryState = LibraryState.Empty(StringResource(R.string.empty_library_message))
    private val contentState = LibraryState.Content(
        ::playlists,
        ::folders,
        ::folderPlaylists,
        { searchQueryState.isActive(com.cliffracertech.soundaura.model.SearchScope.Folder) },
        itemCallback,
        folderPlaylistCallback,
        folderCallback,
        ::moveFolderPlaylist)
    val openFolderId get() = navigationState.openFolderId

    val viewState get() = when {
        playlists == null || folders == null ->
            LibraryState.Loading
        playlists?.isEmpty() == true && folders?.isEmpty() == true -> {
            if (searchQueryState.isActive(com.cliffracertech.soundaura.model.SearchScope.Library))
                noSearchResultsState
            else emptyLibraryState
        } else -> contentState
    }

    private fun showAddToFolderSourceChoice(folderId: Long) {
        shownFolderDialog = FolderDialog.SourceChoice(
            onDismissRequest = ::dismissFolderDialog,
            onLocalAudioClick = {
                shownFolderDialog = FolderDialog.SelectingFiles(
                    onDismissRequest = ::dismissFolderDialog,
                    onFilesSelected = { uris ->
                        pendingFolderId = folderId
                        pendingFolderUris = uris
                        scope.launchIO { addLocalFilesToFolder(folderId, uris) }
                    })
            },
            onCurrentAudioClick = {
                scope.launchIO {
                    val playlists = folderUseCases.activeLibraryPlaylists()
                    withContext(Dispatcher.Immediate) {
                        if (playlists.isEmpty()) {
                            dismissFolderDialog()
                            messageHandler.postMessage(
                                R.string.folder_no_current_audio_warning,
                                SnackbarDuration.Long)
                        } else scope.launchIO {
                            addPlaylistsToFolder(folderId, playlists.map { it.id })
                        }
                    }
                }
            })
    }

    private suspend fun addPlaylistsToFolder(folderId: Long, playlistIds: List<Long>) {
        when (folderUseCases.addPlaylistsToFolder(folderId, playlistIds)) {
            FolderUseCases.Result.Success -> dismissFolderDialog()
            FolderUseCases.Result.EmptySelection -> dismissFolderDialog()
            is FolderUseCases.Result.Failure -> Unit
        }
    }

    private suspend fun addLocalFilesToFolder(folderId: Long, uris: List<Uri>) {
        when (val result = folderUseCases.addLocalFilesToFolder(folderId, uris)) {
            FolderUseCases.Result.Success -> dismissFolderDialog()
            FolderUseCases.Result.EmptySelection -> dismissFolderDialog()
            is FolderUseCases.Result.Failure -> showFolderStoragePermissionRequest(result)
        }
    }

    private fun showFolderStoragePermissionRequest(result: FolderUseCases.Result.Failure) {
        shownFolderDialog = FolderDialog.RequestStoragePermissionExplanation(
            permissionsUsed = result.permissionsUsed,
            permissionsAllowed = result.permissionAllowance,
            onDismissRequest = ::dismissFolderDialog,
            onOkClick = {
                shownFolderDialog = FolderDialog.RequestStoragePermission(
                    onDismissRequest = ::dismissFolderDialog,
                    onResult = { granted ->
                        if (granted) scope.launchIO {
                            addLocalFilesToFolder(pendingFolderId, pendingFolderUris)
                        } else {
                            messageHandler.postMessage(
                                R.string.cant_add_playlist_tracks_warning,
                                SnackbarDuration.Long)
                            dismissFolderDialog()
                        }
                    })
            })
    }

    private fun moveFolderPlaylist(fromIndex: Int, toIndex: Int) {
        val folderId = navigationState.openFolderId ?: return
        val current = folderPlaylists?.toMutableList() ?: return
        if (fromIndex !in current.indices || toIndex !in current.indices)
            return
        current.add(toIndex, current.removeAt(fromIndex))
        folderPlaylists = current.toImmutableList()
        scope.launchIO {
            folderUseCases.setFolderShuffleAndPlaylists(
                folderId,
                readLibrary.getFolderShuffle(folderId),
                current.map { it.id })
        }
    }

    private fun showFileChooser(
        target: Playlist,
        existingTracks: List<Track>,
        shuffleEnabled: Boolean = false,
    ) {
        shownDialog = PlaylistDialog.FileChooser(
            target, messageHandler, existingTracks,
            onDismissRequest = {
                // If the file chooser was arrived at by selecting the 'create playlist'
                // option for a single track playlist, we want the back button/gesture to
                // completely dismiss the dialog. If the file chooser was arrived at by
                // selecting the 'add more files' button in the playlist options dialog
                // of an existing multi-track playlist, then we want the back button/
                // gesture to go back to the playlist options dialog for that playlist.
                if (existingTracks.size == 1)
                    dismissDialog()
                else showPlaylistOptions(target, existingTracks, shuffleEnabled)
            }, onChosenFilesValidated = { validatedFiles ->
                val newTrackList = existingTracks + validatedFiles.map(::Track)
                showPlaylistOptions(target, newTrackList, shuffleEnabled)
            })
    }

    private fun showPlaylistOptions(
        target: Playlist,
        existingTracks: List<Track>,
        shuffleEnabled: Boolean,
    ) {
        shownDialog = PlaylistDialog.PlaylistOptions(
            target, existingTracks, shuffleEnabled, ::dismissDialog,
            onAddFilesClick = {
                showFileChooser(target, existingTracks, shuffleEnabled)
            }, onConfirm = { newShuffle, newTracks ->
                scope.launchIO {
                    val result = modifyLibrary.setPlaylistShuffleAndTracks(
                        target.id, newShuffle, newTracks)
                    withContext(Dispatcher.Immediate) {
                        when (result) {
                            is ModifyLibraryUseCase.Result.Success ->
                                dismissDialog()
                            is ModifyLibraryUseCase.Result.NewTracksNotAdded ->
                                showRequestStoragePermission(
                                    target, shuffleEnabled, newTracks, result)
                        }
                    }
                }
            })
    }

    private fun showRequestStoragePermission(
        target: Playlist,
        shuffleEnabled: Boolean,
        existingTracks: List<Track>,
        result: ModifyLibraryUseCase.Result.NewTracksNotAdded,
    ) {
        shownDialog = PlaylistDialog.RequestStoragePermissionExplanation(
            target = target,
            permissionsUsed = result.permissionsUsed,
            permissionsAllowed = result.permissionAllowance,
            onDismissRequest = ::dismissDialog,
            onOkClick = {
                shownDialog = PlaylistDialog.RequestStoragePermission(
                    target = target,
                    onDismissRequest = ::dismissDialog,
                    onResult = { permissionGranted ->
                        dismissDialog()
                        if (permissionGranted) scope.launchIO {
                            modifyLibrary.setPlaylistShuffleAndTracks(
                                target.id, shuffleEnabled,
                                existingTracks + result.unaddedUris.map(::Track))
                        } else messageHandler.postMessage(
                            stringResId = R.string.cant_add_playlist_tracks_warning,
                            duration = SnackbarDuration.Long)
                    })
            })
    }
}

/**
 * Show a [LibraryView] that uses an instance of [LibraryViewModel] for its state.
 *
 * @param modifier The [Modifier] that will be used for the TrackList.
 * @param padding A [PaddingValues] instance whose values will be
 *     as the contentPadding for the TrackList
*  @param state The [LazyListState] used for the TrackList. state
 *     defaults to an instance of LazyListState returned from a
 *     [rememberLazyListState] call, but can be overridden here in
 *     case, e.g., the scrolling position needs to be remembered
 *     even when the SoundAuraTrackList leaves the composition.
 */
@Composable fun SoundAuraLibraryView(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
) {
    val viewModel: LibraryViewModel = viewModel()
    LibraryView(
        modifier = modifier.fillMaxSize(),
        lazyListState = state,
        contentPadding = padding,
        shownDialog = viewModel.shownDialog,
        shownFolderDialog = viewModel.shownFolderDialog,
        openFolderId = viewModel.openFolderId,
        libraryState = viewModel.viewState)
}

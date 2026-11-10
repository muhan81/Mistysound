/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.library.Folder
import com.cliffracertech.soundaura.library.GradientSlider
import com.cliffracertech.soundaura.library.GradientSliderDefaults
import com.cliffracertech.soundaura.library.Playlist
import com.cliffracertech.soundaura.library.formatMillis
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.service.PlaybackProgress
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.theme.AppCardSurface
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderColors
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import java.time.Duration
import javax.inject.Inject
import kotlin.math.roundToInt

/** The height of the playback panel's title row. */
private val HeaderHeight = 56.dp

/** The minimum height of a single entry in the playback panel. */
private val RowHeight = 96.dp

/** The default maximum number of rows that the playback panel displays before
 * the list inside it becomes scrollable. */
const val DefaultPlaybackPanelMaxRows = 3

/** The smallest allowed value of the playback panel's maximum row count. */
const val MinPlaybackPanelMaxRows = 1

/** The largest allowed value of the playback panel's maximum row count. */
const val MaxPlaybackPanelMaxRows = 6

/** Return the playback key used to identify the [Playlist] with [id]. The
 * format matches the playback keys used by the player service. */
fun playlistPlaybackKey(id: Long) = "playlist:$id"

/** Return the playback key used to identify the [Folder] with [id]. */
fun folderPlaybackKey(id: Long) = "folder:$id"

/**
 * A [ViewModel] that contains the state and callbacks for a [PlaybackPanel].
 *
 * The properties [minimized], [maxRows], [playButtonState], and [shownDialog]
 * can be used as the same-named parameters of [PlaybackPanel]. The pause state
 * of an individual entry can be read with [isPlaybackPaused] and toggled with
 * [onTogglePlaybackPaused].
 */
@HiltViewModel class PlaybackPanelViewModel @Inject constructor(
    private val navigationState: NavigationState,
    private val playbackState: PlaybackState,
    dataStore: DataStore<Preferences>,
) : ViewModel() {
    private val scope = viewModelScope + Dispatcher.Immediate

    /** A dialog that should be shown to the user, or null if none is needed. */
    var shownDialog by mutableStateOf<DialogType?>(null)
        private set
    private fun dismissDialog() { shownDialog = null }

    /** Whether the panel is collapsed down to its title row. */
    val minimized get() = navigationState.playbackPanelMinimized

    /** Collapse or expand the panel. */
    fun onToggleMinimized() = navigationState.togglePlaybackPanelMinimized()

    private val maxRowsKey = intPreferencesKey(PrefKeys.playbackPanelMaxRows)
    /** The maximum number of entries shown before the panel's list scrolls. */
    val maxRows by dataStore.preferenceState(
        maxRowsKey, DefaultPlaybackPanelMaxRows, scope)

    /** The state of the play/pause button shown in the panel's title row. */
    val playButtonState = PlayButtonState(
        isPlayingProvider = playbackState::isPlaying,
        onClick = playbackState::toggleIsPlaying,
        onLongClick = {
            shownDialog = DialogType.SetAutoStopTimer(
                onDismissRequest = ::dismissDialog,
                onConfirmClick = { duration ->
                    if (duration > Duration.ZERO)
                        playbackState.setTimer(duration)
                    dismissDialog()
                })
        }, clickLabelResIdProvider = { isPlaying: Boolean ->
            if (isPlaying) R.string.pause_button_description
            else           R.string.play_button_description
        }, longClickLabelResId = R.string.play_pause_button_long_click_description)

    /** Return whether the playback identified by [key] is individually paused. */
    fun isPlaybackPaused(key: String) = playbackState.isPlaybackPaused(key)

    /** Pause or resume the playback identified by [key] on its own. */
    fun onTogglePlaybackPaused(key: String) {
        playbackState.setPlaybackPaused(key, !playbackState.isPlaybackPaused(key))
    }

    /** Return the current playback progress of [playlist]. */
    fun getProgress(playlist: Playlist) = playbackState.getPlaylistProgress(playlist.id)

    /** Seek the playback of [playlist] to [positionMillis]. */
    fun onSeek(playlist: Playlist, positionMillis: Int) =
        playbackState.seekPlaylistTo(playlist.id, positionMillis)
}

/**
 * A panel shown at the top of the library that displays every playlist and
 * folder that is currently part of the sound mix. Each entry provides its own
 * volume slider (playlists only), an individual pause/resume button, and a
 * button that removes it from the mix.
 *
 * The panel is always visible, even when the mix is empty, in which case it
 * displays [R.string.playback_panel_empty_message]. It can be collapsed down to
 * its title row with the minimize button; clicking the collapsed title row
 * expands it again.
 *
 * @param contentPadding The padding of the surrounding content, used so that
 *     the panel lines up with the library list below it.
 * @param playlists The [Playlist]s that are currently part of the sound mix.
 * @param folders The [Folder]s that are currently part of the sound mix.
 * @param maxRows The maximum number of entries to display before the list
 *     inside the panel becomes scrollable.
 * @param minimized Whether the panel is collapsed to its title row.
 * @param playButton The state of the panel's play/pause button, which controls
 *     the sound mix as a whole.
 * @param isPlaybackPaused Returns whether the entry with the provided playback
 *     key is individually paused.
 * @param onTogglePlaybackPaused Toggles the individual pause of the entry with
 *     the provided playback key.
 * @param onMinimizeClick Collapses or expands the panel.
 * @param onVolumeChange The callback invoked as a playlist's volume slider is dragged.
 * @param onVolumeChangeFinished The callback invoked when a volume slider is released.
 * @param getProgress Returns the current playback progress of the provided [Playlist].
 * @param onSeek Seeks the playback of the provided [Playlist] to the provided position.
 * @param onPlaylistRemoveClick Removes the provided [Playlist] from the sound mix.
 * @param onFolderRemoveClick Removes the provided [Folder] from the sound mix.
 */
@Composable fun PlaybackPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    playlists: List<Playlist>,
    folders: List<Folder>,
    maxRows: Int,
    minimized: Boolean,
    playButton: PlayButtonState,
    isPlaybackPaused: (String) -> Boolean,
    onTogglePlaybackPaused: (String) -> Unit,
    onMinimizeClick: () -> Unit,
    onVolumeChange: (Playlist, Float) -> Unit,
    onVolumeChangeFinished: (Playlist, Float) -> Unit,
    getProgress: (Playlist) -> PlaybackProgress,
    onSeek: (Playlist, Int) -> Unit,
    onPlaylistRemoveClick: (Playlist) -> Unit,
    onFolderRemoveClick: (Folder) -> Unit,
) {
    val ld = LocalLayoutDirection.current
    val itemCount = playlists.size + folders.size
    AppCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(ld) + 8.dp,
                end = contentPadding.calculateEndPadding(ld) + 8.dp,
                top = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            PlaybackPanelHeader(
                itemCount = itemCount,
                minimized = minimized,
                playButton = playButton,
                onMinimizeClick = onMinimizeClick)
            when {
                minimized -> Unit // The panel is collapsed to its title row.
                itemCount == 0 -> Text(
                    text = stringResource(R.string.playback_panel_empty_message),
                    style = MaterialTheme.typography.body1,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
                else -> LazyColumn(Modifier.heightIn(max = RowHeight * maxRows)) {
                    items(playlists, key = { playlistPlaybackKey(it.id) }) { playlist ->
                        val key = playlistPlaybackKey(playlist.id)
                        PlaylistPlaybackRow(
                            playlist = playlist,
                            mixIsPlaying = playButton.isPlaying,
                            individuallyPaused = isPlaybackPaused(key),
                            onTogglePauseClick = { onTogglePlaybackPaused(key) },
                            onRemoveClick = { onPlaylistRemoveClick(playlist) },
                            onVolumeChange = { onVolumeChange(playlist, it) },
                            onVolumeChangeFinished = { onVolumeChangeFinished(playlist, it) },
                            getProgress = { getProgress(playlist) },
                            onSeek = { onSeek(playlist, it) })
                    }
                    items(folders, key = { folderPlaybackKey(it.id) }) { folder ->
                        val key = folderPlaybackKey(folder.id)
                        FolderPlaybackRow(
                            folder = folder,
                            mixIsPlaying = playButton.isPlaying,
                            individuallyPaused = isPlaybackPaused(key),
                            onTogglePauseClick = { onTogglePlaybackPaused(key) },
                            onRemoveClick = { onFolderRemoveClick(folder) })
                    }
                }
            }
        }
    }
}

/**
 * Show the playback panel's title row: the panel title with the number of
 * entries in the mix, the play/pause button for the mix as a whole, and the
 * minimize/expand button.
 */
@Composable private fun PlaybackPanelHeader(
    itemCount: Int,
    minimized: Boolean,
    playButton: PlayButtonState,
    onMinimizeClick: () -> Unit,
) {
    val title = stringResource(R.string.playback_panel_title)
    val expandLabel = stringResource(R.string.expand_playback_panel_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderHeight)
            .then(
                if (minimized) Modifier.clickable(
                    onClickLabel = expandLabel,
                    onClick = onMinimizeClick)
                else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (itemCount > 0) "$title · $itemCount" else title,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        PlayButton(state = playButton, modifier = Modifier.size(48.dp))
        IconButton(onMinimizeClick) {
            Icon(
                imageVector = if (minimized) Icons.Default.ExpandMore
                              else           Icons.Default.Remove,
                contentDescription = stringResource(
                    if (minimized) R.string.expand_playback_panel_description
                    else           R.string.minimize_playback_panel_description))
        }
    }
}

/** Show a single [Playlist] entry in the playback panel. */
@Composable private fun PlaylistPlaybackRow(
    playlist: Playlist,
    mixIsPlaying: Boolean,
    individuallyPaused: Boolean,
    onTogglePauseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (Float) -> Unit,
    getProgress: () -> PlaybackProgress,
    onSeek: (Int) -> Unit,
) {
    var volume by remember(playlist.volume) { mutableFloatStateOf(playlist.volume) }
    var progress by remember(playlist.id) { mutableStateOf(getProgress()) }
    var progressSliderValue by remember(playlist.id) { mutableFloatStateOf(0f) }
    val sliderPalette = rememberOverlaySliderPalette()
    val sliderColors = rememberOverlaySliderColors()
    val progressInteractionSource = remember { MutableInteractionSource() }
    val progressIsPressed by progressInteractionSource.collectIsPressedAsState()
    val progressIsDragged by progressInteractionSource.collectIsDraggedAsState()
    val isSeeking = progressIsPressed || progressIsDragged

    LaunchedEffect(playlist.id, isSeeking) {
        while (true) {
            val latest = getProgress()
            progress = latest
            if (!isSeeking) progressSliderValue = latest.positionMillis.toFloat()
            delay(500L)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MarqueeText(text = playlist.name, style = MaterialTheme.typography.subtitle1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = sliderPalette.accentStart)
                Spacer(Modifier.width(6.dp))
                GradientSlider(
                    value = volume,
                    onValueChange = { newVolume ->
                        volume = newVolume
                        onVolumeChange(newVolume)
                    },
                    onValueChangeFinished = { onVolumeChangeFinished(volume) },
                    colors = GradientSliderDefaults.colors(
                        thumbColor = sliderPalette.accentStart,
                        thumbColorEnd = sliderPalette.accentEnd,
                        inactiveTrackColor = sliderPalette.inactiveTrackColor,
                        activeTrackBrush = Brush.horizontalGradient(
                            listOf(sliderPalette.accentStart, sliderPalette.accentEnd))),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = (volume * 100).roundToInt().toString(),
                    style = MaterialTheme.typography.caption,
                    color = LocalContentColor.current.copy(alpha = 0.74f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${progress.positionMillis.formatMillis()}/" +
                           progress.durationMillis.formatMillis(),
                    style = MaterialTheme.typography.caption,
                    color = LocalContentColor.current.copy(alpha = 0.74f))
                Spacer(Modifier.width(8.dp))
                val progressMax = progress.durationMillis.coerceAtLeast(1).toFloat()
                Slider(
                    value = progressSliderValue.coerceIn(0f, progressMax),
                    onValueChange = { progressSliderValue = it },
                    onValueChangeFinished = {
                        onSeek(progressSliderValue.roundToInt())
                    },
                    valueRange = 0f..progressMax,
                    enabled = progress.durationMillis > 0,
                    interactionSource = progressInteractionSource,
                    colors = sliderColors,
                    modifier = Modifier.weight(1f))
            }
        }
        IndividualPauseButton(
            mixIsPlaying = mixIsPlaying,
            individuallyPaused = individuallyPaused,
            name = playlist.name,
            onClick = onTogglePauseClick)
        IconButton(onRemoveClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(
                    R.string.set_playlist_inactive_description, playlist.name))
        }
    }
}

/** Show a single [Folder] entry in the playback panel. Folders have no volume
 * of their own; the playlists they contain carry their own volumes. */
@Composable private fun FolderPlaybackRow(
    folder: Folder,
    mixIsPlaying: Boolean,
    individuallyPaused: Boolean,
    onTogglePauseClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MarqueeText(text = folder.name, style = MaterialTheme.typography.subtitle1)
            Text(
                text = stringResource(
                    R.string.folder_item_count_description, folder.playlistCount),
                style = MaterialTheme.typography.caption,
                color = LocalContentColor.current.copy(alpha = 0.74f))
        }
        IndividualPauseButton(
            mixIsPlaying = mixIsPlaying,
            individuallyPaused = individuallyPaused,
            name = folder.name,
            onClick = onTogglePauseClick)
        IconButton(onRemoveClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(
                    R.string.set_folder_inactive_description, folder.name))
        }
    }
}

/**
 * Show the individual pause/resume button of a single playback panel entry.
 * The button is only enabled while the mix as a whole is playing, since
 * pausing a single entry only makes sense during playback.
 */
@Composable private fun IndividualPauseButton(
    mixIsPlaying: Boolean,
    individuallyPaused: Boolean,
    name: String,
    onClick: () -> Unit,
) {
    val isPaused = individuallyPaused || !mixIsPlaying
    IconButton(onClick, enabled = mixIsPlaying) {
        Icon(
            imageVector = if (isPaused) Icons.Default.PlayArrow
                          else          Icons.Default.Pause,
            contentDescription = stringResource(
                if (isPaused) R.string.resume_playlist_description
                else          R.string.pause_playlist_description, name))
    }
}

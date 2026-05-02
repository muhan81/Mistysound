/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.AppCardSurface
import com.cliffracertech.soundaura.ui.theme.OverlayActionStyle
import com.cliffracertech.soundaura.ui.theme.rememberOverlayActionColors
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderColors
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderPalette
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.cliffracertech.soundaura.service.PlaybackProgress
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A data class whose values describe the attributes of a playlist in a library of playlists.
 *
 * @param id The [Long] value that uniquely identifies the playlist
 * @param name The name of the playlist
 * @param isActive Whether or not the playlist is currently playing
 * @param isSingleTrack Whether or not the playlist has only one track.
 *     This value can be used in case single-track playlists need to
 *     appear differently from multi-track playlists (e.g. by referring
 *     to them as 'tracks' instead of 'playlists').
 * @param volume The volume of the playlist
 * @param volumeBoostDb The additional volume boost that will be applied
 *     to the playlist before the volume is applied
 * @param hasError Whether or not there is an error (e.g. a playback
 *     problem) with the playlist
 */
data class Playlist(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val isSingleTrack: Boolean,
    val volume: Float = 1.0f,
    val volumeBoostDb: Int = 0,
    val playbackSpeed: Float = 1f,
    val hasError: Boolean = false)

/** A collection of callbacks for [PlaylistView] interactions. The first parameter
 * for each of the callbacks is the [Playlist.name] for the [Playlist]. */
interface PlaylistViewCallback {
    /** The callback that will be invoked when the add/remove button is clicked */
    fun onAddRemoveButtonClick(playlist: Playlist)
    /** The callback that will be invoked when the volume slider's value is changing */
    fun onVolumeChange(playlist: Playlist, volume: Float)
    /** The callback that will be invoked when the volume slider's handle is released */
    fun onVolumeChangeFinished(playlist: Playlist, volume: Float)
    fun getProgress(playlist: Playlist): PlaybackProgress
    fun onSeek(playlist: Playlist, positionMillis: Int)
    fun onPlaybackSpeedClick(playlist: Playlist)
    /** The callback that will be invoked when the 'rename'
     * option of the playlist's options menu is clicked */
    fun onRenameClick(playlist: Playlist)
    /** The callback that will be invoked when the 'playlist options' or
     * 'create playlist' option of the playlist's options menu is clicked */
    fun onExtraOptionsClick(playlist: Playlist)
    /** The callback that will be invoked when the 'volume boost'
     * option in the playlist's options menu is clicked */
    fun onVolumeBoostClick(playlist: Playlist)
    /** The callback that will be invoked when the 'remove'
     * option of the playlist's options menu is clicked */
    fun onRemoveClick(playlist: Playlist)
}

/** Return a remembered [PlaylistViewCallback] implementation. */
@Composable fun rememberPlaylistViewCallback(
    onAddRemoveButtonClick: (Playlist) -> Unit = { _ -> },
    onVolumeChange: (Playlist, Float) -> Unit = { _, _ -> },
    onVolumeChangeFinished: (Playlist, Float) -> Unit = { _, _ -> },
    getProgress: (Playlist) -> PlaybackProgress = { PlaybackProgress() },
    onSeek: (Playlist, Int) -> Unit = { _, _ -> },
    onPlaybackSpeedClick: (Playlist) -> Unit = {},
    onRenameClick: (Playlist) -> Unit = {},
    onExtraOptionsClick: (Playlist) -> Unit = {},
    onVolumeBoostClick: (Playlist) -> Unit = {},
    onRemoveClick: (Playlist) -> Unit = {}
) = remember { object: PlaylistViewCallback {
    override fun onAddRemoveButtonClick(playlist: Playlist) = onAddRemoveButtonClick(playlist)
    override fun onVolumeChange(playlist: Playlist, volume: Float) = onVolumeChange(playlist, volume)
    override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) = onVolumeChangeFinished(playlist, volume)
    override fun getProgress(playlist: Playlist) = getProgress(playlist)
    override fun onSeek(playlist: Playlist, positionMillis: Int) = onSeek(playlist, positionMillis)
    override fun onPlaybackSpeedClick(playlist: Playlist) = onPlaybackSpeedClick(playlist)
    override fun onRenameClick(playlist: Playlist) = onRenameClick(playlist)
    override fun onExtraOptionsClick(playlist: Playlist) = onExtraOptionsClick(playlist)
    override fun onVolumeBoostClick(playlist: Playlist) = onVolumeBoostClick(playlist)
    override fun onRemoveClick(playlist: Playlist) = onRemoveClick(playlist)
}}

/**
 * A view that displays an add/remove button, a title, a volume slider, and a
 * more options button for the provided [Playlist] Instance. If the [Playlist.hasError]
 * field is true, then an error icon will be displayed instead of the add/remove
 * button, an error message will be displayed in place of the volume slider, and
 * the more options menu button will be replaced by a delete icon. The more
 * options button will also be replaced by a numerical display of the track's
 * volume when the volume slider is being dragged.
 *
 * @param playlist The [Playlist] instance that is being represented
 * @param callback The [PlaylistViewCallback] that describes how to respond to user interactions
 */
@Composable fun PlaylistView(
    playlist: Playlist,
    callback: PlaylistViewCallback,
    modifier: Modifier = Modifier
) = AppCardSurface(modifier, MaterialTheme.shapes.large) {
        val neutralActionColors = rememberOverlayActionColors(OverlayActionStyle.Neutral)
        val primaryActionColors = rememberOverlayActionColors(OverlayActionStyle.Primary)
        val secondaryActionColors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AddRemoveButtonOrErrorIcon(
                showError = playlist.hasError,
                isAdded = playlist.isActive,
                contentDescription = if (playlist.hasError) null else {
                    val id = if (playlist.isActive)
                        R.string.set_playlist_inactive_description
                    else R.string.set_playlist_active_description
                    stringResource(id, playlist.name)
                },
                backgroundColor = neutralActionColors.containerColor,
                tint = primaryActionColors.containerColor,
                onAddRemoveClick = {
                    callback.onAddRemoveButtonClick(playlist)
                })

            val volumeSliderInteractionSource = remember { MutableInteractionSource() }
            var volumeSliderValue by remember(playlist.volume) { mutableFloatStateOf(playlist.volume) }
            val volumeSliderIsBeingPressed by volumeSliderInteractionSource.collectIsPressedAsState()
            val volumeSliderIsBeingDragged by volumeSliderInteractionSource.collectIsDraggedAsState()

            Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp)) {
                MarqueeText(text = playlist.name,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(start = 1.dp))
                VolumeSliderOrErrorMessage(
                    volume = volumeSliderValue,
                    onVolumeChange = { volume ->
                        volumeSliderValue = volume
                        callback.onVolumeChange(playlist, volume)
                    }, onVolumeChangeFinished = {
                        callback.onVolumeChangeFinished(playlist, volumeSliderValue)
                    }, modifier = Modifier.fillMaxWidth(),
                    sliderInteractionSource = volumeSliderInteractionSource,
                    errorMessage = if (!playlist.hasError) null else
                        stringResource(R.string.playlist_error_message))
                if (!playlist.hasError)
                    PlaylistProgressAndSpeed(
                        playlist = playlist,
                        callback = callback)
            }
            PlaylistViewEndContent(
                content = when {
                    playlist.hasError ->
                        PlaylistViewEndContentType.DeleteButton
                    volumeSliderIsBeingPressed || volumeSliderIsBeingDragged ->
                        PlaylistViewEndContentType.VolumeDisplay
                    else ->
                        PlaylistViewEndContentType.MoreOptionsButton
                }, playlist = playlist,
                volume = volumeSliderValue,
                onRenameClick = { callback.onRenameClick(playlist) },
                onPlaylistOptionsClick = { callback.onExtraOptionsClick(playlist) },
                onVolumeBoostClick = { callback.onVolumeBoostClick(playlist) },
                onRemoveClick = { callback.onRemoveClick(playlist) },
                tint = secondaryActionColors.containerColor)
        }
}

/**
 * Show either an add/remove button or an error icon. The error icon can be
 * shown when some error prevents the added/removed state from being changed.
 *
 * @param showError Whether an error icon will be shown instead of the
 *     add/remove button.
 * @param isAdded Whether the add/remove button will display itself as already
 *     added when it is shown at all.
 * @param contentDescription The content description that will be used for the
 *     error icon or add/remove button.
 * @param backgroundColor The color that is being displayed behind the
 *     [AddRemoveButtonOrErrorIcon]. This is used so that the button can appear
 *     empty when isAdded is false.
 * @param onAddRemoveClick The callback that will be invoked when [showError] is
 *     false and the add/remove button is clicked.
 */
@Composable private fun AddRemoveButtonOrErrorIcon(
    showError: Boolean,
    isAdded: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.surface,
    tint: Color = MaterialTheme.colors.primaryVariant,
    onAddRemoveClick: () -> Unit
) = AnimatedContent(
    targetState = showError,
    label = "PlaylistView add/remove button / error icon crossfade"
) {
    if (it) Icon(
        imageVector = Icons.Default.Error,
        contentDescription = contentDescription,
        modifier = Modifier.size(48.dp).padding(10.dp),
        tint = MaterialTheme.colors.error)
    else AddRemoveButton(
        added = isAdded,
        contentDescription = contentDescription,
        backgroundColor = backgroundColor,
        tint = tint,
        onClick = onAddRemoveClick)
}

/**
 * Show a slider to change a volume level or an error message
 * that explains why the volume level can not be changed.
 *
 * @param volume The current volume level, in the range of 0.0f to 1.0f.
 * @param onVolumeChange The callback that will be invoked as the slider
 *     is being dragged.
 * @param modifier The modifier for the slider/error message.
 * @param sliderInteractionSource The MutableInteractionSource that will
 *     be used for the volume slider. If not provided, will default to a
 *     remembered new instance of MutableInteractionSource.
 * @param errorMessage The error message that will be shown
 *     if not null instead of the volume slider.
 * @param onVolumeChangeFinished The callback that will be invoked
 *     when the slider's thumb has been released after a drag event.
 */
@Composable private fun VolumeSliderOrErrorMessage(
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sliderInteractionSource: MutableInteractionSource =
        remember { MutableInteractionSource() },
    errorMessage: String? = null,
    onVolumeChangeFinished: (() -> Unit)? = null,
) = AnimatedContent(
    targetState = errorMessage != null,
    modifier = modifier.minTouchTargetSize(),
    label = "PlaylistView error message fade in/out",
) { hasError ->
    // For some reason setting the contentAlignment on the AnimatedContent doesn't
    // seem to work, so this inner box is necessary to make the error message
    // appear vertically centered in the space where the volume slider would be
    Row(verticalAlignment = Alignment.CenterVertically) {
        val sliderPalette = rememberOverlaySliderPalette()
        if (hasError) Text(
            text = errorMessage ?: "",
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.body1,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            // 1dp start padding is required to make the text
            // align with where the volume icon would appear if
            // there were no error message.
            modifier = Modifier.padding(start = 1.dp))
        else {
            Icon(imageVector = Icons.Default.VolumeUp,
                 contentDescription = null,
                 modifier = Modifier.size(20.dp),
                 tint = sliderPalette.accentStart)
            GradientSlider(
                value = volume,
                onValueChange = onVolumeChange,
                onValueChangeFinished = { onVolumeChangeFinished?.invoke() },
                interactionSource = sliderInteractionSource,
                colors = GradientSliderDefaults.colors(
                    thumbColor = sliderPalette.accentStart,
                    thumbColorEnd = sliderPalette.accentEnd,
                    inactiveTrackColor = sliderPalette.inactiveTrackColor,
                    activeTrackBrush = Brush.horizontalGradient(
                        listOf(sliderPalette.accentStart,
                               sliderPalette.accentEnd))))
        }
    }
}

@Composable private fun PlaylistProgressAndSpeed(
    playlist: Playlist,
    callback: PlaylistViewCallback,
) {
    var progress by remember(playlist.id) { mutableStateOf(callback.getProgress(playlist)) }
    var sliderValue by remember(playlist.id) { mutableFloatStateOf(0f) }
    val sliderPalette = rememberOverlaySliderPalette()
    val sliderColors = rememberOverlaySliderColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isSeeking = isPressed || isDragged

    LaunchedEffect(playlist.id, playlist.isActive, isSeeking) {
        while (true) {
            val latest = callback.getProgress(playlist)
            progress = latest
            if (!isSeeking)
                sliderValue = latest.positionMillis.toFloat()
            delay(500L)
        }
    }

    val max = progress.durationMillis.coerceAtLeast(1).toFloat()
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${progress.positionMillis.formatMillis()}/${progress.durationMillis.formatMillis()}",
            style = MaterialTheme.typography.caption,
            color = LocalContentColor.current.copy(alpha = 0.74f))
        Spacer(Modifier.width(8.dp))
        Slider(
            value = sliderValue.coerceIn(0f, max),
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                callback.onSeek(playlist, sliderValue.roundToInt())
            },
            valueRange = 0f..max,
            interactionSource = interactionSource,
            colors = sliderColors,
            modifier = Modifier.weight(1f))
        TextButton(
            onClick = { callback.onPlaybackSpeedClick(playlist) },
            modifier = Modifier.width(56.dp)
        ) {
            Text(
                text = playlist.playbackSpeed.formatSpeed(),
                color = sliderPalette.accentEnd,
                style = MaterialTheme.typography.caption)
        }
    }
}

private fun Int.formatMillis(): String {
    val totalSeconds = this.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun Float.formatSpeed(): String {
    val coerced = coerceIn(0.1f, 5f)
    val rounded = (coerced * 10).roundToInt() / 10f
    return if (rounded % 1f == 0f)
        "${rounded.toInt()}x"
    else "${rounded}x"
}

/** An enum detailing the possible content for the end of a [PlaylistView]'s layout. */
private enum class PlaylistViewEndContentType {
    /** The TrackView will display a more options
     * button that opens a popup menu when clicked. */
    MoreOptionsButton,
    /** The TrackView will display a delete button that calls
     * the TrackView's callback's onDeleteRequest when clicked. */
    DeleteButton,
    /** The TrackView will display the track's volume
     * as an integer string in the range of 0 - 100. */
    VolumeDisplay
}

/**
 * Show the end content for a [PlaylistView]. This content will change
 * according to the value of the parameter [content] to match one of
 * the possible values for the [PlaylistViewEndContentType] enum.
 *
 * @param content The value of [PlaylistViewEndContentType] that describes
 *     what will be displayed. The visible content will be cross-faded
 *     between when this value changes.
 * @param playlist The [Playlist] that is being interacted with
 * @param volume The volume of the [Playlist] that is being interacted with
 * @param onRenameClick The callback that will be invoked when the
 *     playlist's rename option is clicked
 * @param onPlaylistOptionsClick The callback that will be invoked when a
 *     multi-track playlist's 'playlist options' option is clicked or a
 *     single-track playlist's 'create playlist' option is clicked
 * @param onRemoveClick The callback that will be invoked when the playlist's
 *     remove option is clicked, or when the [content] parameter is equal
 *     to [PlaylistViewEndContentType.DeleteButton] and the delete button
 *     is clicked
 * @param tint The tint that will be used for the more options button
 *     and the volume display. The delete button will use the value
 *     of the local theme's MaterialTheme.colors.error value instead.
 */
@Composable private fun PlaylistViewEndContent(
    content: PlaylistViewEndContentType,
    playlist: Playlist,
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    onRenameClick: () -> Unit,
    onPlaylistOptionsClick: () -> Unit,
    onVolumeBoostClick: () -> Unit,
    onRemoveClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) = Crossfade(
    targetState = content,
    label = "PlaylistView end content crossfade"
) { when(it) {
    PlaylistViewEndContentType.MoreOptionsButton -> {
        var showingOptionsMenu by rememberMutableStateOf(false)

        IconButton({ showingOptionsMenu = true }) {
            Icon(imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(
                    R.string.item_options_button_description, playlist.name),
                tint = tint)
        }

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onRenameClick()
            }) { Text(stringResource(R.string.rename)) }

            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onPlaylistOptionsClick()
            }) {
                Text(stringResource(
                    if (playlist.isSingleTrack) R.string.create_playlist_title
                    else                        R.string.playlist_options_title))
            }
            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onVolumeBoostClick()
            }) { Text(stringResource(R.string.volume_boost_description)) }

            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onRemoveClick()
            }) { Text(stringResource(R.string.remove)) }
        }
    }
    PlaylistViewEndContentType.VolumeDisplay -> {
        Box(Modifier.minTouchTargetSize(), Alignment.Center) {
            Text(text = (volume * 100).roundToInt().toString(),
                color = tint,
                style = MaterialTheme.typography.subtitle2)
        }
    }
    PlaylistViewEndContentType.DeleteButton -> {
        IconButton(onRemoveClick) {
            Icon(imageVector = Icons.Default.Delete,
                contentDescription = stringResource(
                    R.string.remove_item_description, playlist.name),
                tint = MaterialTheme.colors.error)
        }
    }
}}

@Preview @Composable
fun LightTrackViewPreview() = SoundAuraTheme(darkTheme = false) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            id = 0,
            name = "Playlist 1",
            isActive = false,
            isSingleTrack = true,
            volume = 0.5f))
}

@Preview(showBackground = true) @Composable
fun DarkTrackViewPreview() = SoundAuraTheme(darkTheme = true) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            id = 0,
            name = "Playlist 2",
            isActive = true,
            isSingleTrack = false,
            volume = 0.25f))
}

@Preview @Composable
fun LightTrackErrorPreview() = SoundAuraTheme(darkTheme = false) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            id = 0,
            name = "Playlist 3",
            isActive = false,
            isSingleTrack = true,
            hasError = true))
}

@Preview(showBackground = true) @Composable
fun DarkTrackErrorPreview() = SoundAuraTheme(darkTheme = true) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            id = 0,
            name = "Playlist 4",
            isActive = false,
            isSingleTrack = true,
            hasError = true))
}

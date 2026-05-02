/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import java.io.IOException

data class ActivePlaylistSummary(
    val id: Long,
    val shuffle: Boolean,
    val volume: Float,
    val volumeBoostDb: Int,
    val playbackSpeed: Float)

data class ActiveFolderPlaylistSummary(
    val folderId: Long,
    val folderShuffle: Boolean,
    val folderOrder: Int,
    val playlistId: Long,
    val playlistShuffle: Boolean,
    val volume: Float,
    val volumeBoostDb: Int,
    val playbackSpeed: Float)

data class PlaybackProgress(
    val positionMillis: Int = 0,
    val durationMillis: Int = 0)

data class PlaybackTrack(
    val playlistId: Long,
    val uri: Uri,
    val volume: Float,
    val volumeBoostDb: Int,
    val playbackSpeed: Float)

data class ActivePlayback(
    val key: String,
    val shuffle: Boolean,
    val tracks: List<PlaybackTrack>)

/**
 * A [MediaPlayer] wrapper that allows for seamless looping of the provided
 * [ActivePlayback]. The [update] method can be used when the [ActivePlayback]'s
 * properties change. The property [volume] describes the current volume for
 * both audio channels, and is initialized from the current [PlaybackTrack]'s [volume]
 * field.
 *
 * The methods [play], [pause], and [stop] can be used to control playback of
 * the Player. These methods correspond to the [MediaPlayer] methods of the
 * same name, except for [stop]. [Player]'s [stop] method is functionally the
 * same as pausing while seeking to the start of the media.
 *
 * If there is a problem with one or more [Uri]s within the [ActivePlayback],
 * playback of the next track will be attempted until one is found
 * that can be played. If [MediaPlayer] creation fails for all of the tracks,
 * no playback will occur, and calling [play] will have no effect. When one or
 * more tracks fail to play, the provided callback [onPlaybackFailure] will be
 * invoked.
 *
 * @param context A [Context] instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a [Context] that the Player might outlive.
 * @param playback The [ActivePlayback] whose contents will be played
 * @param startImmediately Whether or not the Player should start playback
 *     as soon as it is ready
 * @param onPlaybackFailure A callback that will be invoked if MediaPlayer
 *     creation fails for one or more [Uri]s in the playlist
 */
class Player(
    private val context: Context,
    private var playback: ActivePlayback,
    startImmediately: Boolean = false,
    private val onPlaybackFailure: (List<Uri>) -> Unit,
) {
    private var trackIterator = trackIterator(playback)
    private var mediaPlayer: MediaPlayer? = null
    private var volumeBooster: LoudnessEnhancer? = null
    private var currentTrack: PlaybackTrack? = null
    // Without tracking the intended playing/paused state in this property,
    // an issue can occur if an attempt to pause is made when the internal
    // MediaPlayer is in the process of switching to the next track in a
    // playlist that will cause the pause command to be ignored. This is
    // resolved by using the value of isPlaying in the on completion listener.
    private var isPlaying = startImmediately

    private val onCompletionListener = MediaPlayer.OnCompletionListener {
        initializePlayerForNextTrack(startImmediately = isPlaying)
    }

    init {
        initializePlayerForNextTrack(startImmediately)
    }

    fun play() {
        isPlaying = true
        mediaPlayer?.start()
    }

    fun pause() {
        isPlaying = false
        mediaPlayer?.pause()
    }

    fun stop() {
        isPlaying = false
        if (playback.tracks.size < 2) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
        } else {
            trackIterator = trackIterator(playback)
            initializePlayerForNextTrack(startImmediately = false)
        }
    }

    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.setPlaybackSpeed(speed)
    }

    fun setPlaylistVolume(playlistId: Long, volume: Float) {
        val coercedVolume = volume.coerceIn(0f, 1f)
        playback = playback.copy(tracks = playback.tracks.map { track ->
            if (track.playlistId == playlistId)
                track.copy(volume = coercedVolume)
            else track
        })
        if (currentTrack?.playlistId == playlistId) {
            currentTrack = currentTrack?.copy(volume = coercedVolume)
            setVolume(coercedVolume)
        }
    }

    fun setPlaylistSpeed(playlistId: Long, speed: Float) {
        val coercedSpeed = speed.coerceIn(0.1f, 5f)
        playback = playback.copy(tracks = playback.tracks.map { track ->
            if (track.playlistId == playlistId)
                track.copy(playbackSpeed = coercedSpeed)
            else track
        })
        if (currentTrack?.playlistId == playlistId) {
            currentTrack = currentTrack?.copy(playbackSpeed = coercedSpeed)
            setPlaybackSpeed(coercedSpeed)
        }
    }

    fun progressFor(playlistId: Long): PlaybackProgress? {
        val player = mediaPlayer ?: return null
        if (currentTrack?.playlistId != playlistId) return null
        return PlaybackProgress(
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration.coerceAtLeast(0))
    }

    fun seekTo(playlistId: Long, positionMillis: Int) {
        val player = mediaPlayer ?: return
        if (currentTrack?.playlistId != playlistId) return
        player.seekTo(positionMillis.coerceIn(0, player.duration.coerceAtLeast(0)))
    }

    /** Reset the Player to play the [newPlayback]. */
    fun update(newPlayback: ActivePlayback, startImmediately: Boolean) {
        isPlaying = startImmediately

        if (!newPlayback.hasSameSequenceAs(playback)) {
            playback = newPlayback
            trackIterator = trackIterator(newPlayback)
            initializePlayerForNextTrack(startImmediately)
        } else {
            val newCurrentTrack = currentTrack?.let { track ->
                newPlayback.tracks.find {
                    it.playlistId == track.playlistId && it.uri == track.uri
                }
            }
            newCurrentTrack?.let { mediaPlayer?.initializeFor(it, newPlayback.tracks.size < 2) }
            if (startImmediately)
                mediaPlayer?.start()
            playback = newPlayback
            currentTrack = newCurrentTrack
        }
    }

    fun release() {
        mediaPlayer?.reset()
        mediaPlayer?.release()
    }

    private fun trackIterator(playback: ActivePlayback) = (
            if (!playback.shuffle)
                InfiniteSequence(playback.tracks)
            else ShuffledInfiniteSequence(
                unshuffledValues = playback.tracks,
                memorySize = maxOf(1, playback.tracks.size / 3))
        ).iterator()

    private fun ActivePlayback.hasSameSequenceAs(other: ActivePlayback) =
        shuffle == other.shuffle &&
        tracks.map { it.playlistId to it.uri } ==
            other.tracks.map { it.playlistId to it.uri }

    private fun PlaybackTrack.withLatestSettings() =
        playback.tracks.find { it.playlistId == playlistId && it.uri == uri } ?: this

    /**
     * Determine the next target [PlaybackTrack], and either create a new [MediaPlayer]
     * instance if [mediaPlayer] is null, or attempt to reset the existing
     * player to use the target [PlaybackTrack.uri] as a data source. When the new or
     * existing player is prepared, playback will start immediately if
     * [startImmediately] is true.
     *
     * initializePlayerForNextTrack must be called once for each [Uri] that is
     * to be played. A single track playlist only needs to call it once, but
     * a multi-track playlist will need to have it called for each track.
     */
    private fun initializePlayerForNextTrack(startImmediately: Boolean) {
        // The number of player creation/data source setting attempts is
        // recorded and compared to the playlist's track count so that we
        // know when we have done one full loop of the playlist's tracks
        var attempts = 0
        var failedUris: MutableList<Uri>? = null
        var newPlayer: MediaPlayer? = null

        var newTrack: PlaybackTrack? = null
        while (newPlayer == null && ++attempts <= playback.tracks.size) {
            val track = trackIterator.next().withLatestSettings()
            val uri = track.uri
            newPlayer = mediaPlayer.let {
                if (it == null)
                    MediaPlayer.create(context, uri)
                else try {
                    it.reset()
                    it.setDataSource(context, uri)
                    it.prepare(); it
                } catch(e: IOException) { null }
            }
            if (newPlayer == null) {
                if (failedUris == null)
                    failedUris = mutableListOf(uri)
                else failedUris.add(uri)
            } else if (startImmediately)
                newPlayer.start()
            if (newPlayer != null)
                newTrack = track
        }
        failedUris?.let(onPlaybackFailure)
        mediaPlayer = newPlayer
        currentTrack = newTrack
        newTrack?.let { newPlayer?.initializeFor(it, playback.tracks.size < 2) }
    }

    /**
     * Set the receiver's volume, [MediaPlayer.isLooping] property, and
     * [MediaPlayer.setOnCompletionListener] to their appropriate values
     * to play the content of [track]. init must be called only once
     * for each [PlaybackTrack].
     */
    private fun MediaPlayer.initializeFor(track: PlaybackTrack, looping: Boolean) {
        setVolume(track.volume, track.volume)
        isLooping = looping
        setPlaybackSpeed(track.playbackSpeed)
        boostVolume(track.volumeBoostDb)
        setOnCompletionListener(
            if (looping) null
            else onCompletionListener)
    }

    private fun MediaPlayer.setPlaybackSpeed(speed: Float) {
        playbackParams = playbackParams.setSpeed(speed.coerceIn(0.1f, 5f))
    }

    private fun MediaPlayer.boostVolume(dbBoost: Int) {
        volumeBooster?.enabled = false
        volumeBooster = if (dbBoost == 0) null else
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(dbBoost * 100)
                enabled = true
            }
    }
}

/**
 * A collection of [Player] instances.
 *
 * [PlayerMap] manages a collection of [Player] instances for a collection
 * of [ActivePlayback]s. The collection of [Player]s is updated via the
 * method [update]. Whether or not the collection of players is empty can
 * be queried with the property [isEmpty].
 *
 * The playing/paused/stopped state can be set for all [Player]s at once
 * with the methods [play], [pause], and [stop]. The volume for individual
 * playlists can be set with the method [setPlayerVolume]. The method
 * [releaseAll] should be called before the PlayerMap is destroyed so that
 * all [Player] instances can be released first.
 *
 * @param context A [Context] instance. Note that the context instance
 *     will be held onto for the lifetime of the [PlayerSet].
 * @param onPlaybackFailure The callback that will be invoked
 *     when playback for the provided list of [Uri]s has failed
 */
class PlayerMap(
    private val context: Context,
    private val onPlaybackFailure: (uris: List<Uri>) -> Unit,
) {
    private var playerMap: MutableMap<String, Player> = hashMapOf()

    val isEmpty get() = playerMap.isEmpty()

    fun play() = playerMap.values.forEach(Player::play)
    fun pause() = playerMap.values.forEach(Player::pause)
    fun stop() = playerMap.values.forEach(Player::stop)

    fun setPlayerVolume(playlistId: Long, volume: Float) =
        playerMap.values.forEach { it.setPlaylistVolume(playlistId, volume) }

    fun setPlayerSpeed(playlistId: Long, speed: Float) =
        playerMap.values.forEach { it.setPlaylistSpeed(playlistId, speed) }

    fun progressFor(playlistId: Long): PlaybackProgress? =
        playerMap.values.firstNotNullOfOrNull { it.progressFor(playlistId) }

    fun seekTo(playlistId: Long, positionMillis: Int) =
        playerMap.values.forEach { it.seekTo(playlistId, positionMillis) }

    fun releaseAll() = playerMap.values.forEach(Player::release)

    /** Update the PlayerSet with new [Player]s to match the provided [playlists].
     * If [startPlaying] is true, playback will start immediately. Otherwise, the
     * [Player]s will begin paused. */
    fun update(playbacks: List<ActivePlayback>, startPlaying: Boolean) {
        val oldMap = playerMap
        playerMap = HashMap(playbacks.size)

        for (playback in playbacks) {
            val existingPlayer = oldMap
                .remove(playback.key)
                ?.apply { update(playback, startPlaying) }

            playerMap[playback.key] = existingPlayer ?:
                Player(context, playback, startPlaying, onPlaybackFailure)
        }
        oldMap.values.forEach(Player::release)
    }
}

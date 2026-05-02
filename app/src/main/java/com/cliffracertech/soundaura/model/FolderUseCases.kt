/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.content.Context
import android.net.Uri
import com.cliffracertech.soundaura.addbutton.getDisplayName
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.folderRenameValidator
import com.cliffracertech.soundaura.model.database.newFolderNameValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class FolderUseCases(
    private val context: Context,
    private val permissionHandler: UriPermissionHandler,
    private val dao: PlaylistDao,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        permissionHandler: AndroidUriPermissionHandler,
        dao: PlaylistDao,
    ): this(context, permissionHandler as UriPermissionHandler, dao)

    sealed class Result {
        data object Success: Result()
        data object EmptySelection: Result()
        data class Failure(
            val permissionsUsed: Int,
            val permissionAllowance: Int
        ): Result()
    }

    fun newFolderNamingState(
        scope: CoroutineScope,
        initialName: String,
        onNameValidated: suspend (String) -> Unit,
    ) = ValidatedNamingState(
        validator = newFolderNameValidator(dao, scope, initialName),
        coroutineScope = scope,
        onNameValidated = onNameValidated)

    fun renameState(
        folderId: Long,
        oldName: String,
        scope: CoroutineScope,
        onFinished: () -> Unit
    ) = ValidatedNamingState(
        validator = folderRenameValidator(dao, oldName, scope),
        coroutineScope = scope,
        onNameValidated = { newName ->
            if (newName != oldName)
                dao.renameFolder(folderId, newName)
            onFinished()
        })

    suspend fun createFolderFromPlaylists(name: String, playlistIds: List<Long>) =
        if (playlistIds.isEmpty()) Result.EmptySelection
        else {
            dao.insertFolder(name, playlistIds.distinct())
            Result.Success
        }

    suspend fun createFolderFromLocalFiles(name: String, uris: List<Uri>): Result {
        if (uris.isEmpty()) return Result.EmptySelection
        val ids = addLocalFilesAsPlaylists(uris) ?: return Result.Failure(
            permissionsUsed = permissionHandler.usedAllowance,
            permissionAllowance = permissionHandler.totalAllowance)
        dao.insertFolder(name, ids)
        return Result.Success
    }

    suspend fun addPlaylistsToFolder(folderId: Long, playlistIds: List<Long>): Result {
        if (playlistIds.isEmpty()) return Result.EmptySelection
        val existingIds = dao.getFolderPlaylistIds(folderId)
        val newIds = existingIds + playlistIds.filterNot(existingIds::contains)
        dao.setFolderShuffleAndPlaylists(folderId, dao.getFolderShuffle(folderId), newIds)
        return Result.Success
    }

    suspend fun addLocalFilesToFolder(folderId: Long, uris: List<Uri>): Result {
        if (uris.isEmpty()) return Result.EmptySelection
        val ids = addLocalFilesAsPlaylists(uris) ?: return Result.Failure(
            permissionsUsed = permissionHandler.usedAllowance,
            permissionAllowance = permissionHandler.totalAllowance)
        return addPlaylistsToFolder(folderId, ids)
    }

    suspend fun setFolderShuffleAndPlaylists(
        folderId: Long,
        shuffle: Boolean,
        playlistIds: List<Long>,
    ) {
        dao.setFolderShuffleAndPlaylists(folderId, shuffle, playlistIds)
    }

    suspend fun toggleFolderIsActive(folderId: Long) {
        dao.toggleFolderIsActive(folderId)
    }

    suspend fun removeFolder(folderId: Long) {
        dao.deleteFolder(folderId)
    }

    suspend fun activeLibraryPlaylists() = dao.getActiveLibraryPlaylists()

    private suspend fun addLocalFilesAsPlaylists(uris: List<Uri>): List<Long>? {
        val newUris = dao.filterNewUris(uris)
        if (!permissionHandler.acquirePermissionsFor(newUris))
            return null
        val names = uniquePlaylistNames(uris)
        return dao.insertSingleTrackPlaylistsReturningIds(names, uris, newUris)
    }

    private suspend fun uniquePlaylistNames(uris: List<Uri>): List<String> {
        val usedNames = dao.getPlaylistNames().toMutableSet()
        return uris.map { uri ->
            val baseName = uri.getDisplayName(context).ifBlank { "Audio" }
            var name = baseName
            var suffix = 2
            while (name in usedNames)
                name = "$baseName ${suffix++}"
            usedNames.add(name)
            name
        }
    }
}

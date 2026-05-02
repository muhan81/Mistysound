/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cliffracertech.soundaura.background.BackgroundCollectionType
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.mediacontroller.MediaControllerState
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

enum class SearchScope { Library, Folder, MainBackground, CardBackground }

@ActivityRetainedScoped
class NavigationState @Inject constructor() {
    var showingAppSettings by mutableStateOf(false)
        private set
    var openFolderId by mutableStateOf<Long?>(null)
        private set
    var openFolderName by mutableStateOf<String?>(null)
        private set
    var mediaControllerState by mutableStateOf(MediaControllerState.Visibility.Collapsed)
        private set
    var backgroundCollectionType by mutableStateOf<BackgroundCollectionType?>(null)
        private set
    var backgroundEditorImageId by mutableStateOf<Long?>(null)
        private set
    var backgroundEditorHasUnsavedChanges by mutableStateOf(false)
        private set
    var showingBackgroundDiscardDialog by mutableStateOf(false)
        private set

    val showingBackgroundCollectionPage get() =
        backgroundCollectionType != null && backgroundEditorImageId == null
    val showingBackgroundEditor get() =
        backgroundCollectionType != null && backgroundEditorImageId != null
    val currentSearchScope get() = when {
        showingBackgroundCollectionPage -> when (backgroundCollectionType) {
            BackgroundCollectionType.Main -> SearchScope.MainBackground
            BackgroundCollectionType.Card -> SearchScope.CardBackground
            null -> null
        }
        showingAppSettings -> null
        openFolderId != null -> SearchScope.Folder
        else -> SearchScope.Library
    }

    fun showAppSettings() {
        showingAppSettings = true
        openFolderId = null
        openFolderName = null
        backgroundCollectionType = null
        backgroundEditorImageId = null
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
        mediaControllerState = MediaControllerState.Visibility.Hidden
    }

    fun hideAppSettings() {
        showingAppSettings = false
        backgroundCollectionType = null
        backgroundEditorImageId = null
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun openFolder(id: Long, name: String) {
        if (showingAppSettings)
            return
        openFolderId = id
        openFolderName = name
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun closeFolder() {
        openFolderId = null
        openFolderName = null
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun openBackgroundCollection(type: BackgroundCollectionType) {
        if (!showingAppSettings)
            return
        backgroundCollectionType = type
        backgroundEditorImageId = null
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
    }

    fun closeBackgroundCollection() {
        backgroundCollectionType = null
        backgroundEditorImageId = null
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
    }

    fun openBackgroundEditor(imageId: Long) {
        if (backgroundCollectionType == null)
            return
        backgroundEditorImageId = imageId
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
    }

    fun closeBackgroundEditor() {
        backgroundEditorImageId = null
        backgroundEditorHasUnsavedChanges = false
        showingBackgroundDiscardDialog = false
    }

    fun updateBackgroundEditorDirtyState(hasUnsavedChanges: Boolean) {
        backgroundEditorHasUnsavedChanges = hasUnsavedChanges
    }

    fun onBackgroundEditorBackClick() {
        if (backgroundEditorHasUnsavedChanges)
            showingBackgroundDiscardDialog = true
        else closeBackgroundEditor()
    }

    fun dismissBackgroundDiscardDialog() {
        showingBackgroundDiscardDialog = false
    }

    fun showPresetSelector() {
        if (showingAppSettings)
            return
        mediaControllerState = MediaControllerState.Visibility.Expanded
    }

    fun hidePresetSelector() {
        if (showingAppSettings)
            return
        mediaControllerState = MediaControllerState.Visibility.Collapsed
    }

    fun onBackButtonClick(): Boolean = when {
        showingBackgroundEditor -> {
            onBackgroundEditorBackClick(); true
        }
        showingBackgroundCollectionPage -> {
            closeBackgroundCollection(); true
        }
        showingAppSettings -> {
            hideAppSettings(); true
        } openFolderId != null -> {
            closeFolder(); true
        } mediaControllerState.isExpanded -> {
            hidePresetSelector(); true
        } else -> false
    }
}

/**
 * A state holder for a search query entry.
 *
 * The query itself can be accessed through the property [value] or as a
 * [Flow] through the [flow] property. The method [set] can be used to
 * update an active search query, or [clear] can be used to reset it to null.
 * The convenience method [toggleIsActive] will set the query to null if it
 * is not null, or to a blank string if it is null.
 */
@ActivityRetainedScoped
class SearchQueryState @Inject constructor() {
    private class ScopedQueryState(initialValue: String? = null) {
        var value by mutableStateOf(initialValue)
        val flow = MutableStateFlow(initialValue)
    }

    private val library = ScopedQueryState()
    private val folder = ScopedQueryState()
    private val mainBackground = ScopedQueryState()
    private val cardBackground = ScopedQueryState()

    fun value(scope: SearchScope) = stateFor(scope).value
    fun flow(scope: SearchScope) = stateFor(scope).flow
    fun isActive(scope: SearchScope) = value(scope) != null

    fun toggleIsActive(scope: SearchScope) {
        if (isActive(scope)) clear(scope)
        else                 set(scope, "")
    }

    fun set(scope: SearchScope, newQuery: String?) {
        val state = stateFor(scope)
        state.value = newQuery
        state.flow.value = newQuery
    }

    fun clear(scope: SearchScope) = set(scope, null)

    private fun stateFor(scope: SearchScope) = when (scope) {
        SearchScope.Library -> library
        SearchScope.Folder -> folder
        SearchScope.MainBackground -> mainBackground
        SearchScope.CardBackground -> cardBackground
    }
}

/**
 * ActivePresetState holds the state of a currently active [Preset]. The name
 * of the currently active [Preset] can be collected from the [Flow]`<String?>`
 * property [name]. Whether or not the active [Preset] is modified can be
 * collected from the [Flow]`<Boolean>` property [isModified]. The active
 * [Preset] can be changed or cleared with the methods [setName] and [clear].
 */
@ActivityRetainedScoped
class ActivePresetState @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val presetDao: PresetDao,
) {
    private val nameKey = stringPreferencesKey(PrefKeys.activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val name = dataStore.data.map { prefs ->
        val value = prefs[nameKey]
        if (value.isNullOrBlank() || !presetDao.exists(value))
            null
        else value
    }

    val isModified = name.transformLatest { activePresetName ->
        if (activePresetName == null) emit(false)
        else emitAll(presetDao.getPresetIsModified(activePresetName))
    }

    /** Set the active preset to the one whose name matches [name]. */
    suspend fun setName(name: String) = dataStore.edit(nameKey, name)

    /** Clear the active preset. */
    suspend fun clear() {
        dataStore.edit { it.remove(nameKey) }
    }
}

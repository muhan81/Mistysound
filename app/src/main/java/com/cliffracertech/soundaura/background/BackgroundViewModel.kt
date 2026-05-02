package com.cliffracertech.soundaura.background

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.launchIO
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.SearchScope
import com.cliffracertech.soundaura.model.database.BackgroundDao
import com.cliffracertech.soundaura.model.database.backgroundRenameValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackgroundEditorDraft(
    val imageId: Long,
    val collection: BackgroundCollectionType,
    val name: String,
    val imagePath: String,
    val width: Int,
    val height: Int,
    val initialFocusX: Float,
    val initialFocusY: Float,
    val initialZoom: Float,
    val focusX: Float,
    val focusY: Float,
    val zoom: Float,
) {
    val hasChanges get() =
        focusX != initialFocusX ||
            focusY != initialFocusY ||
            zoom != initialZoom
}

@HiltViewModel
class BackgroundViewModel @Inject constructor(
    private val repository: BackgroundRepository,
    private val backgroundDao: BackgroundDao,
    private val navigationState: NavigationState,
    private val searchQueryState: SearchQueryState,
    private val messageHandler: MessageHandler,
) : ViewModel() {
    private val scope = viewModelScope + Dispatcher.Immediate

    private val mainImagesFlow = searchQueryState.flow(SearchScope.MainBackground)
        .flatMapLatestImages(BackgroundCollectionType.Main)
    private val cardImagesFlow = searchQueryState.flow(SearchScope.CardBackground)
        .flatMapLatestImages(BackgroundCollectionType.Card)

    val mainImages by mainImagesFlow.collectAsState(emptyList(), scope)
    val cardImages by cardImagesFlow.collectAsState(emptyList(), scope)

    private val defaultMainConfig = BackgroundCollectionConfig(
        collection = BackgroundCollectionType.Main,
        mode = BackgroundMode.Fixed,
        currentImageId = null,
    )
    private val defaultCardConfig = BackgroundCollectionConfig(
        collection = BackgroundCollectionType.Card,
        mode = BackgroundMode.Fixed,
        currentImageId = null,
    )
    val mainConfig by repository.configFlow(BackgroundCollectionType.Main)
        .collectAsState(defaultMainConfig, scope)
    val cardConfig by repository.configFlow(BackgroundCollectionType.Card)
        .collectAsState(defaultCardConfig, scope)

    private var mainPreviewId by mutableStateOf<Long?>(null)
    private var cardPreviewId by mutableStateOf<Long?>(null)

    var editorDraft by mutableStateOf<BackgroundEditorDraft?>(null)
        private set
    val showingDiscardDialog get() = navigationState.showingBackgroundDiscardDialog

    fun previewedImageId(collection: BackgroundCollectionType) = when (collection) {
        BackgroundCollectionType.Main -> mainPreviewId
        BackgroundCollectionType.Card -> cardPreviewId
    }

    fun currentConfig(collection: BackgroundCollectionType) = when (collection) {
        BackgroundCollectionType.Main -> mainConfig
        BackgroundCollectionType.Card -> cardConfig
    }

    fun images(collection: BackgroundCollectionType) = when (collection) {
        BackgroundCollectionType.Main -> mainImages
        BackgroundCollectionType.Card -> cardImages
    }

    fun previewRecord(collection: BackgroundCollectionType): BackgroundImageRecord? {
        val previewId = previewedImageId(collection)
        return images(collection).firstOrNull { it.id == previewId }
    }

    fun ensurePreviewSelection(collection: BackgroundCollectionType) {
        val items = images(collection)
        val previewId = previewedImageId(collection)
        if (previewId != null && items.any { it.id == previewId })
            return

        val currentId = currentConfig(collection).currentImageId
        val fallbackId = when {
            currentId != null && items.any { it.id == currentId } -> currentId
            else -> items.firstOrNull()?.id
        }
        setPreviewImage(collection, fallbackId)
    }

    fun setPreviewImage(collection: BackgroundCollectionType, imageId: Long?) {
        when (collection) {
            BackgroundCollectionType.Main -> mainPreviewId = imageId
            BackgroundCollectionType.Card -> cardPreviewId = imageId
        }
    }

    fun importImage(collection: BackgroundCollectionType, uri: Uri) {
        scope.launchIO {
            runCatching { repository.importImage(collection, uri) }
                .onSuccess { image ->
                    withContext(Dispatcher.Immediate) {
                        setPreviewImage(collection, image.id)
                    }
                }
                .onFailure {
                    messageHandler.postMessage(R.string.background_import_failure_message)
                }
        }
    }

    fun toggleImageSelection(image: BackgroundImageRecord) {
        scope.launchIO {
            repository.updateSelection(image.id, !image.isEnabled)
        }
    }

    fun savePreviewAsCurrent(collection: BackgroundCollectionType) {
        val previewId = previewedImageId(collection) ?: return
        scope.launchIO {
            repository.setCurrentImage(collection, previewId)
        }
    }

    fun removeImage(id: Long) {
        scope.launchIO {
            repository.deleteImage(id)
            withContext(Dispatcher.Immediate) {
                if (previewedImageId(BackgroundCollectionType.Main) == id)
                    ensurePreviewSelection(BackgroundCollectionType.Main)
                if (previewedImageId(BackgroundCollectionType.Card) == id)
                    ensurePreviewSelection(BackgroundCollectionType.Card)
            }
        }
    }

    fun renameState(
        image: BackgroundImageRecord,
        onFinished: () -> Unit,
    ) = ValidatedNamingState(
        validator = backgroundRenameValidator(
            dao = backgroundDao,
            collection = image.collection,
            imageId = image.id,
            oldName = image.name,
            coroutineScope = scope,
        ),
        coroutineScope = scope,
        onNameValidated = { name ->
            repository.renameImage(image.id, name)
            withContext(Dispatcher.Immediate) { onFinished() }
        },
    )

    fun reorder(collection: BackgroundCollectionType, idsInOrder: List<Long>) {
        scope.launchIO {
            repository.reorder(collection, idsInOrder)
        }
    }

    fun openEditor(image: BackgroundImageRecord) {
        navigationState.openBackgroundEditor(image.id)
        editorDraft = BackgroundEditorDraft(
            imageId = image.id,
            collection = image.collection,
            name = image.name,
            imagePath = image.originalPath,
            width = image.width,
            height = image.height,
            initialFocusX = image.focusX,
            initialFocusY = image.focusY,
            initialZoom = image.zoom,
            focusX = image.focusX,
            focusY = image.focusY,
            zoom = image.zoom,
        )
    }

    fun updateEditorTransform(
        focusX: Float,
        focusY: Float,
        zoom: Float,
    ) {
        val draft = editorDraft ?: return
        editorDraft = draft.copy(
            focusX = focusX,
            focusY = focusY,
            zoom = zoom,
        )
        navigationState.updateBackgroundEditorDirtyState(editorDraft?.hasChanges == true)
    }

    fun applyEditorChanges() {
        val draft = editorDraft ?: return
        scope.launchIO {
            repository.updateTransform(
                id = draft.imageId,
                focusX = draft.focusX,
                focusY = draft.focusY,
                zoom = draft.zoom,
            )
            withContext(Dispatcher.Immediate) {
                navigationState.closeBackgroundEditor()
                editorDraft = null
            }
        }
    }

    fun dismissDiscardDialog() {
        navigationState.dismissBackgroundDiscardDialog()
    }

    fun discardEditorChanges() {
        navigationState.dismissBackgroundDiscardDialog()
        navigationState.closeBackgroundEditor()
        editorDraft = null
    }

    private fun Flow<String?>.flatMapLatestImages(
        collection: BackgroundCollectionType,
    ) = flatMapLatest { query ->
        repository.imagesFlow(collection, query)
    }
}

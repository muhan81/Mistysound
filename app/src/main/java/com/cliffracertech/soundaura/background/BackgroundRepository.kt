package com.cliffracertech.soundaura.background

import android.net.Uri
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.model.database.BackgroundConfigEntity
import com.cliffracertech.soundaura.model.database.BackgroundDao
import com.cliffracertech.soundaura.model.database.BackgroundImageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundRepository @Inject constructor(
    private val dao: BackgroundDao,
    private val storage: BackgroundImageStorage,
) {
    fun imagesFlow(
        collection: BackgroundCollectionType,
        query: String?,
    ): Flow<List<BackgroundImageRecord>> {
        val flow = if (query.isNullOrBlank())
            dao.getImages(collection.dbValue)
        else dao.getImages(collection.dbValue, "%$query%")
        return flow.map { entities -> entities.map { entity -> entity.toRecord() } }
    }

    fun currentBackgroundFlow(collection: BackgroundCollectionType): Flow<BackgroundRenderInfo?> =
        dao.getCurrentImage(collection.dbValue).map { entity ->
            entity?.toRenderInfo()
        }

    fun configFlow(collection: BackgroundCollectionType): Flow<BackgroundCollectionConfig> =
        dao.getConfig(collection.dbValue).map { entity ->
            entity?.let(::toConfig) ?: BackgroundCollectionConfig(
                collection = collection,
                mode = BackgroundMode.Fixed,
                currentImageId = null,
            )
        }

    suspend fun importImage(
        collection: BackgroundCollectionType,
        uri: Uri,
    ): BackgroundImageRecord = withContext(Dispatcher.IO) {
        val imported = storage.importImage(collection, uri)
        val entity = BackgroundImageEntity(
            collection = collection.dbValue,
            name = uniqueName(collection, imported.suggestedName),
            originalFileName = imported.originalFileName,
            thumbnailFileName = imported.thumbnailFileName,
            width = imported.width,
            height = imported.height,
            sortOrder = dao.getMaxSortOrder(collection.dbValue) + 1,
        )
        val id = dao.insertImage(entity)
        requireNotNull(dao.getImage(id)) { "Imported background image was not found after insert" }
            .toRecord()
    }

    suspend fun getImage(id: Long): BackgroundImageRecord? = withContext(Dispatcher.IO) {
        dao.getImage(id)?.toRecord()
    }

    suspend fun getCurrentImageId(collection: BackgroundCollectionType) =
        withContext(Dispatcher.IO) { dao.getCurrentImageId(collection.dbValue) }

    suspend fun getFirstImageId(collection: BackgroundCollectionType) = withContext(Dispatcher.IO) {
        dao.getFirstImageId(collection.dbValue)
    }

    suspend fun renameImage(id: Long, newName: String) = withContext(Dispatcher.IO) {
        dao.renameImage(id, newName)
    }

    suspend fun updateSelection(id: Long, enabled: Boolean) = withContext(Dispatcher.IO) {
        val entity = dao.getImage(id) ?: return@withContext
        dao.setImageEnabled(id, enabled)
        normalizeCurrentImage(BackgroundCollectionType.fromDbValue(entity.collection))
    }

    suspend fun updateTransform(
        id: Long,
        focusX: Float,
        focusY: Float,
        zoom: Float,
    ) = withContext(Dispatcher.IO) {
        dao.updateTransform(id, focusX, focusY, zoom)
    }

    suspend fun setCurrentImage(
        collection: BackgroundCollectionType,
        imageId: Long?,
    ) = withContext(Dispatcher.IO) {
        if (imageId == null) {
            dao.setCurrentImageId(collection.dbValue, null)
            return@withContext
        }

        val entity = dao.getImage(imageId) ?: return@withContext
        if (entity.collection != collection.dbValue)
            return@withContext
        if (!entity.isEnabled)
            dao.setImageEnabled(imageId, true)
        dao.setCurrentImageId(collection.dbValue, imageId)
    }

    suspend fun deleteImage(id: Long) = withContext(Dispatcher.IO) {
        val entity = dao.getImage(id) ?: return@withContext
        val collection = BackgroundCollectionType.fromDbValue(entity.collection)
        dao.deleteImage(id)
        storage.deleteImageFiles(collection, entity.originalFileName, entity.thumbnailFileName)
        normalizeCurrentImage(collection)
    }

    suspend fun reorder(
        collection: BackgroundCollectionType,
        idsInOrder: List<Long>,
    ) = withContext(Dispatcher.IO) {
        dao.replaceSortOrders(idsInOrder)
        normalizeCurrentImage(collection)
    }

    private suspend fun uniqueName(
        collection: BackgroundCollectionType,
        preferredName: String,
    ): String {
        val baseName = preferredName.ifBlank { "Image" }
        if (!dao.imageNameExists(collection.dbValue, baseName))
            return baseName

        var suffix = 2
        while (dao.imageNameExists(collection.dbValue, "$baseName $suffix"))
            suffix++
        return "$baseName $suffix"
    }

    private suspend fun normalizeCurrentImage(collection: BackgroundCollectionType) {
        val currentId = dao.getCurrentImageId(collection.dbValue)
        if (currentId != null && dao.getImageEnabled(currentId) == true)
            return
        dao.setCurrentImageId(collection.dbValue, dao.getFirstEnabledImageId(collection.dbValue))
    }

    private fun BackgroundImageEntity.toRecord() = BackgroundImageRecord(
        id = id,
        collection = BackgroundCollectionType.fromDbValue(collection),
        name = name,
        originalPath = storage.originalPath(BackgroundCollectionType.fromDbValue(collection), originalFileName),
        thumbnailPath = storage.thumbnailPath(BackgroundCollectionType.fromDbValue(collection), thumbnailFileName),
        width = width,
        height = height,
        focusX = focusX,
        focusY = focusY,
        zoom = zoom,
        sortOrder = sortOrder,
        isEnabled = isEnabled,
    )

    private fun BackgroundImageEntity.toRenderInfo() = BackgroundRenderInfo(
        imageId = id,
        name = name,
        imagePath = storage.originalPath(BackgroundCollectionType.fromDbValue(collection), originalFileName),
        thumbnailPath = storage.thumbnailPath(BackgroundCollectionType.fromDbValue(collection), thumbnailFileName),
        width = width,
        height = height,
        focusX = focusX,
        focusY = focusY,
        zoom = zoom,
    )

    private fun toConfig(entity: BackgroundConfigEntity) = BackgroundCollectionConfig(
        collection = BackgroundCollectionType.fromDbValue(entity.collection),
        mode = runCatching { BackgroundMode.valueOf(entity.mode) }.getOrDefault(BackgroundMode.Fixed),
        currentImageId = entity.currentImageId,
    )
}

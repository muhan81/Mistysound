package com.cliffracertech.soundaura.model.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.background.BackgroundCollectionType
import com.cliffracertech.soundaura.background.BackgroundMode
import com.cliffracertech.soundaura.model.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "backgroundImage",
    indices = [
        Index(value = ["collection", "name"], unique = true),
        Index(value = ["collection", "sortOrder"]),
    ],
)
data class BackgroundImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val collection: String,
    val name: String,
    val originalFileName: String,
    val thumbnailFileName: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(defaultValue = "0.5")
    val focusX: Float = 0.5f,
    @ColumnInfo(defaultValue = "0.5")
    val focusY: Float = 0.5f,
    @ColumnInfo(defaultValue = "1.0")
    val zoom: Float = 1f,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
)

@Entity(
    tableName = "backgroundConfig",
    foreignKeys = [
        ForeignKey(
            entity = BackgroundImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentImageId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index(value = ["currentImageId"])],
)
data class BackgroundConfigEntity(
    @PrimaryKey
    val collection: String,
    val mode: String = BackgroundMode.Fixed.name,
    val currentImageId: Long? = null,
)

@Dao
abstract class BackgroundDao {
    @Query("SELECT * FROM backgroundImage WHERE collection = :collection ORDER BY sortOrder ASC, id ASC")
    abstract fun getImages(collection: String): Flow<List<BackgroundImageEntity>>

    @Query(
        "SELECT * FROM backgroundImage " +
            "WHERE collection = :collection AND name LIKE :filter " +
            "ORDER BY sortOrder ASC, id ASC"
    )
    abstract fun getImages(collection: String, filter: String): Flow<List<BackgroundImageEntity>>

    @Query("SELECT * FROM backgroundImage WHERE id = :id LIMIT 1")
    abstract suspend fun getImage(id: Long): BackgroundImageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertImage(image: BackgroundImageEntity): Long

    @Query("UPDATE backgroundImage SET name = :name WHERE id = :id")
    abstract suspend fun renameImage(id: Long, name: String)

    @Query("UPDATE backgroundImage SET isEnabled = :enabled WHERE id = :id")
    abstract suspend fun setImageEnabled(id: Long, enabled: Boolean)

    @Query(
        "UPDATE backgroundImage SET focusX = :focusX, focusY = :focusY, zoom = :zoom " +
            "WHERE id = :id"
    )
    abstract suspend fun updateTransform(id: Long, focusX: Float, focusY: Float, zoom: Float)

    @Query("UPDATE backgroundImage SET sortOrder = :sortOrder WHERE id = :id")
    abstract suspend fun setImageSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM backgroundImage WHERE id = :id")
    abstract suspend fun deleteImage(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM backgroundImage WHERE collection = :collection")
    abstract suspend fun getMaxSortOrder(collection: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM backgroundImage WHERE collection = :collection AND name = :name)")
    abstract suspend fun imageNameExists(collection: String, name: String): Boolean

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM backgroundImage " +
            "WHERE collection = :collection AND name = :name AND id != :excludedId)"
    )
    abstract suspend fun imageNameExists(collection: String, name: String, excludedId: Long): Boolean

    @Query("SELECT currentImageId FROM backgroundConfig WHERE collection = :collection LIMIT 1")
    abstract suspend fun getCurrentImageId(collection: String): Long?

    @Query("UPDATE backgroundConfig SET currentImageId = :imageId WHERE collection = :collection")
    abstract suspend fun setCurrentImageId(collection: String, imageId: Long?)

    @Query("SELECT mode FROM backgroundConfig WHERE collection = :collection LIMIT 1")
    abstract suspend fun getMode(collection: String): String?

    @Query("SELECT * FROM backgroundConfig WHERE collection = :collection LIMIT 1")
    abstract fun getConfig(collection: String): Flow<BackgroundConfigEntity?>

    @Query(
        "SELECT backgroundImage.* FROM backgroundConfig " +
            "JOIN backgroundImage ON backgroundConfig.currentImageId = backgroundImage.id " +
            "WHERE backgroundConfig.collection = :collection LIMIT 1"
    )
    abstract fun getCurrentImage(collection: String): Flow<BackgroundImageEntity?>

    @Query(
        "SELECT id FROM backgroundImage WHERE collection = :collection AND isEnabled = 1 " +
            "ORDER BY sortOrder ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getFirstEnabledImageId(collection: String): Long?

    @Query(
        "SELECT id FROM backgroundImage WHERE collection = :collection " +
            "ORDER BY sortOrder ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getFirstImageId(collection: String): Long?

    @Query("SELECT isEnabled FROM backgroundImage WHERE id = :id LIMIT 1")
    abstract suspend fun getImageEnabled(id: Long): Boolean?

    @Query("SELECT collection FROM backgroundImage WHERE id = :id LIMIT 1")
    abstract suspend fun getImageCollection(id: Long): String?

    @Transaction
    open suspend fun replaceSortOrders(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id ->
            setImageSortOrder(id, index)
        }
    }
}

fun newBackgroundNameValidator(
    dao: BackgroundDao,
    collection: BackgroundCollectionType,
    coroutineScope: CoroutineScope,
    initialName: String = "",
) = Validator(
    initialValue = initialName,
    coroutineScope = coroutineScope,
    messageFor = { name, hasBeenChanged -> when {
        name.isBlank() && hasBeenChanged ->
            Validator.Message.Error(com.cliffracertech.soundaura.R.string.name_dialog_blank_name_error_message)
        withContext(Dispatcher.IO) { dao.imageNameExists(collection.dbValue, name) } ->
            Validator.Message.Error(com.cliffracertech.soundaura.R.string.name_dialog_duplicate_name_error_message)
        else -> null
    }})

fun backgroundRenameValidator(
    dao: BackgroundDao,
    collection: BackgroundCollectionType,
    imageId: Long,
    oldName: String,
    coroutineScope: CoroutineScope,
) = Validator(
    initialValue = oldName,
    coroutineScope = coroutineScope,
    messageFor = { name, _ -> when {
        name == oldName -> null
        name.isBlank() ->
            Validator.Message.Error(com.cliffracertech.soundaura.R.string.name_dialog_blank_name_error_message)
        withContext(Dispatcher.IO) { dao.imageNameExists(collection.dbValue, name, imageId) } ->
            Validator.Message.Error(com.cliffracertech.soundaura.R.string.name_dialog_duplicate_name_error_message)
        else -> null
    }})

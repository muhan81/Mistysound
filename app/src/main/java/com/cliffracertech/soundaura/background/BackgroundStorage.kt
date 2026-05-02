package com.cliffracertech.soundaura.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class ImportedBackgroundFile(
    val suggestedName: String,
    val originalFileName: String,
    val thumbnailFileName: String,
    val width: Int,
    val height: Int,
)

@Singleton
class BackgroundImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val thumbnailEdgePx = 640
    }

    suspend fun importImage(
        collection: BackgroundCollectionType,
        uri: Uri,
    ): ImportedBackgroundFile = withContext(Dispatcher.IO) {
        val directory = collectionDirectory(collection)
        val sourceName = resolveDisplayName(uri)
        val extension = resolveExtension(uri)
        val baseName = UUID.randomUUID().toString()
        val originalFile = File(directory, "$baseName.$extension")
        val thumbnailFile = File(directory, "${baseName}_thumb.png")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                originalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read imported image")

            val (width, height) = readImageSize(originalFile)
                ?: error("Imported file is not a supported image")
            createThumbnail(originalFile, thumbnailFile)

            ImportedBackgroundFile(
                suggestedName = sourceName,
                originalFileName = originalFile.name,
                thumbnailFileName = thumbnailFile.name,
                width = width,
                height = height,
            )
        } catch (error: Throwable) {
            originalFile.delete()
            thumbnailFile.delete()
            throw error
        }
    }

    suspend fun deleteImageFiles(
        collection: BackgroundCollectionType,
        originalFileName: String,
        thumbnailFileName: String,
    ) = withContext(Dispatcher.IO) {
        originalFile(collection, originalFileName).delete()
        thumbnailFile(collection, thumbnailFileName).delete()
    }

    fun originalPath(collection: BackgroundCollectionType, fileName: String) =
        originalFile(collection, fileName).absolutePath

    fun thumbnailPath(collection: BackgroundCollectionType, fileName: String) =
        thumbnailFile(collection, fileName).absolutePath

    private fun originalFile(collection: BackgroundCollectionType, fileName: String) =
        File(collectionDirectory(collection), fileName)

    private fun thumbnailFile(collection: BackgroundCollectionType, fileName: String) =
        File(collectionDirectory(collection), fileName)

    private fun collectionDirectory(collection: BackgroundCollectionType) =
        File(context.filesDir, "backgrounds/${collection.directoryName}")
            .apply { mkdirs() }

    private fun resolveDisplayName(uri: Uri): String {
        val fallback = context.getString(R.string.background_default_image_name)
        val rawName = DocumentFile.fromSingleUri(context, uri)
            ?.name
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.trim()
            ?: uri.lastPathSegment
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.trim()
        return rawName?.takeIf(String::isNotBlank) ?: fallback
    }

    private fun resolveExtension(uri: Uri): String {
        val rawName = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
        return rawName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: "img"
    }

    private fun readImageSize(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    private fun createThumbnail(sourceFile: File, targetFile: File) {
        val bitmap = decodeSampledBitmap(
            file = sourceFile,
            reqWidth = thumbnailEdgePx,
            reqHeight = thumbnailEdgePx,
        ) ?: error("Unable to decode imported image")
        targetFile.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                error("Unable to save image thumbnail")
        }
    }

    fun decodeSampledBitmap(
        file: File,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var sampleSize = 1
        if (width <= reqWidth && height <= reqHeight)
            return sampleSize

        var halfWidth = width / 2
        var halfHeight = height / 2
        while ((halfWidth / sampleSize) >= reqWidth && (halfHeight / sampleSize) >= reqHeight)
            sampleSize *= 2
        return max(sampleSize, 1)
    }
}

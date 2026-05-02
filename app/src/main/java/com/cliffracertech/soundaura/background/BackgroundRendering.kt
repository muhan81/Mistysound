package com.cliffracertech.soundaura.background

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class VisibleWindow(
    val widthFraction: Float,
    val heightFraction: Float,
)

data class BackgroundTransform(
    val focusX: Float,
    val focusY: Float,
    val zoom: Float,
)

private fun calculateVisibleWindow(
    imageWidth: Int,
    imageHeight: Int,
    viewportAspectRatio: Float,
    zoom: Float,
): VisibleWindow {
    val safeZoom = zoom.coerceAtLeast(1f)
    val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
    val baseWidthFraction: Float
    val baseHeightFraction: Float
    if (imageAspectRatio > viewportAspectRatio) {
        baseHeightFraction = 1f
        baseWidthFraction = viewportAspectRatio / imageAspectRatio
    } else {
        baseWidthFraction = 1f
        baseHeightFraction = imageAspectRatio / viewportAspectRatio
    }
    return VisibleWindow(
        widthFraction = (baseWidthFraction / safeZoom).coerceAtMost(1f),
        heightFraction = (baseHeightFraction / safeZoom).coerceAtMost(1f),
    )
}

fun clampBackgroundTransform(
    imageWidth: Int,
    imageHeight: Int,
    viewportAspectRatio: Float,
    focusX: Float,
    focusY: Float,
    zoom: Float,
): BackgroundTransform {
    val safeZoom = zoom.coerceIn(1f, 6f)
    val visibleWindow = calculateVisibleWindow(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        viewportAspectRatio = viewportAspectRatio,
        zoom = safeZoom,
    )
    val clampedFocusX = clampFocusAxis(focusX, visibleWindow.widthFraction)
    val clampedFocusY = clampFocusAxis(focusY, visibleWindow.heightFraction)
    return BackgroundTransform(clampedFocusX, clampedFocusY, safeZoom)
}

private fun clampFocusAxis(focus: Float, visibleFraction: Float): Float {
    if (visibleFraction >= 1f)
        return 0.5f
    val half = visibleFraction / 2f
    return focus.coerceIn(half, 1f - half)
}

@Composable
fun rememberBackgroundBitmap(
    storage: BackgroundImageStorage,
    path: String?,
    requestSize: IntSize,
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, requestSize) {
        value = if (path.isNullOrEmpty() || requestSize.width <= 0 || requestSize.height <= 0) {
            null
        } else withContext(com.cliffracertech.soundaura.Dispatcher.IO) {
            storage.decodeSampledBitmap(
                file = java.io.File(path),
                reqWidth = requestSize.width,
                reqHeight = requestSize.height,
            )?.asImageBitmap()
        }
    }
    return bitmap
}

@Composable
fun BackgroundImageBox(
    background: BackgroundRenderInfo?,
    storage: BackgroundImageStorage,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    fallbackColor: Color = MaterialTheme.colors.surface,
    overlayColor: Color = Color.Transparent,
    preferThumbnail: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) = BoxWithConstraints(
    modifier = modifier
        .clip(shape)
        .background(fallbackColor),
) {
    val density = LocalDensity.current
    val requestSize = with(density) {
        IntSize(
            width = maxWidth.roundToPx().coerceAtLeast(1),
            height = maxHeight.roundToPx().coerceAtLeast(1),
        )
    }
    val bitmap = rememberBackgroundBitmap(
        storage = storage,
        path = when {
            background == null -> null
            preferThumbnail -> background.thumbnailPath
            else -> background.imagePath
        },
        requestSize = requestSize,
    )
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null && background != null) {
            CroppedBitmapCanvas(
                bitmap = bitmap,
                focusX = background.focusX,
                focusY = background.focusY,
                zoom = background.zoom,
                overlayColor = overlayColor,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(fallbackColor)
                if (overlayColor.alpha > 0f)
                    drawRect(overlayColor)
            }
        }
        content()
    }
}

@Composable
private fun CroppedBitmapCanvas(
    bitmap: ImageBitmap,
    focusX: Float,
    focusY: Float,
    zoom: Float,
    overlayColor: Color,
    modifier: Modifier = Modifier,
) = Canvas(modifier.fillMaxSize()) {
    val viewportAspect = size.width / size.height
    val transform = clampBackgroundTransform(
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        viewportAspectRatio = viewportAspect,
        focusX = focusX,
        focusY = focusY,
        zoom = zoom,
    )
    val visibleWindow = calculateVisibleWindow(
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        viewportAspectRatio = viewportAspect,
        zoom = transform.zoom,
    )
    val sourceWidth = (bitmap.width * visibleWindow.widthFraction)
        .roundToInt()
        .coerceIn(1, bitmap.width)
    val sourceHeight = (bitmap.height * visibleWindow.heightFraction)
        .roundToInt()
        .coerceIn(1, bitmap.height)
    val sourceLeft = ((transform.focusX - visibleWindow.widthFraction / 2f) * bitmap.width)
        .roundToInt()
        .coerceIn(0, bitmap.width - sourceWidth)
    val sourceTop = ((transform.focusY - visibleWindow.heightFraction / 2f) * bitmap.height)
        .roundToInt()
        .coerceIn(0, bitmap.height - sourceHeight)

    drawImage(
        image = bitmap,
        srcOffset = androidx.compose.ui.unit.IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
    if (overlayColor.alpha > 0f)
        drawRect(overlayColor)
}

@Composable
fun BackgroundSurface(
    background: BackgroundRenderInfo?,
    storage: BackgroundImageStorage,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    fallbackColor: Color = MaterialTheme.colors.surface,
    overlayColor: Color = MaterialTheme.colors.surface.copy(alpha = 0.72f),
    preferThumbnail: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) = Surface(
    modifier = modifier,
    shape = shape,
    color = Color.Transparent,
) {
    BackgroundImageBox(
        background = background,
        storage = storage,
        modifier = Modifier.fillMaxSize(),
        shape = shape,
        fallbackColor = fallbackColor,
        overlayColor = overlayColor,
        preferThumbnail = preferThumbnail,
        content = content,
    )
}

fun BackgroundImageRecord.toRenderInfo() = BackgroundRenderInfo(
    imageId = id,
    name = name,
    imagePath = originalPath,
    thumbnailPath = thumbnailPath,
    width = width,
    height = height,
    focusX = focusX,
    focusY = focusY,
    zoom = zoom,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundStorageEntryPoint {
    fun backgroundImageStorage(): BackgroundImageStorage
}

@Composable
fun rememberBackgroundStorage(): BackgroundImageStorage {
    val context = LocalContext.current.applicationContext
    return androidx.compose.runtime.remember(context) {
        EntryPointAccessors.fromApplication(
            context,
            BackgroundStorageEntryPoint::class.java,
        ).backgroundImageStorage()
    }
}

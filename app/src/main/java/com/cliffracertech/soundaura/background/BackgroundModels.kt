package com.cliffracertech.soundaura.background

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.cliffracertech.soundaura.R

enum class BackgroundCollectionType(
    val dbValue: String,
    val directoryName: String,
    @StringRes val settingsTitleResId: Int,
    @StringRes val pageTitleResId: Int,
    @StringRes val editorTitleResId: Int,
) {
    Main(
        dbValue = "main",
        directoryName = "main",
        settingsTitleResId = R.string.main_background_setting_title,
        pageTitleResId = R.string.main_background_page_title,
        editorTitleResId = R.string.main_background_editor_title,
    ),
    Card(
        dbValue = "card",
        directoryName = "card",
        settingsTitleResId = R.string.card_background_setting_title,
        pageTitleResId = R.string.card_background_page_title,
        editorTitleResId = R.string.card_background_editor_title,
    );

    companion object {
        fun fromDbValue(value: String) =
            entries.firstOrNull { it.dbValue == value } ?: Main
    }
}

enum class BackgroundMode { Fixed, Sequential, Random }

data class BackgroundImageRecord(
    val id: Long,
    val collection: BackgroundCollectionType,
    val name: String,
    val originalPath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val focusX: Float,
    val focusY: Float,
    val zoom: Float,
    val sortOrder: Int,
    val isEnabled: Boolean,
)

data class BackgroundRenderInfo(
    val imageId: Long,
    val name: String,
    val imagePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val focusX: Float,
    val focusY: Float,
    val zoom: Float,
)

data class BackgroundCollectionConfig(
    val collection: BackgroundCollectionType,
    val mode: BackgroundMode,
    val currentImageId: Long?,
)

val LocalMainBackground = staticCompositionLocalOf<BackgroundRenderInfo?> { null }
val LocalCardBackground = staticCompositionLocalOf<BackgroundRenderInfo?> { null }

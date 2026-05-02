package com.cliffracertech.soundaura.background

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.NamingDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.library.Folder
import com.cliffracertech.soundaura.library.FolderView
import com.cliffracertech.soundaura.library.Playlist
import com.cliffracertech.soundaura.library.PlaylistView
import com.cliffracertech.soundaura.library.rememberFolderViewCallback
import com.cliffracertech.soundaura.library.rememberPlaylistViewCallback
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.theme.LocalCardAppearance
import com.cliffracertech.soundaura.ui.theme.OverlayActionStyle
import com.cliffracertech.soundaura.ui.theme.rememberOverlayActionColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun BackgroundCollectionScreen(
    collection: BackgroundCollectionType,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val viewModel: BackgroundViewModel = viewModel()
    val storage = rememberBackgroundStorage()
    val images = viewModel.images(collection)
    val config = viewModel.currentConfig(collection)

    LaunchedEffect(collection, images, config.currentImageId) {
        viewModel.ensurePreviewSelection(collection)
    }

    var imageForRename by remember { mutableStateOf<BackgroundImageRecord?>(null) }
    var imageForRemoval by remember { mutableStateOf<BackgroundImageRecord?>(null) }

    val reorderableState = rememberReorderableLazyListState(state) { from, to ->
        val current = images.toMutableList()
        if (from.index !in current.indices || to.index !in current.indices)
            return@rememberReorderableLazyListState
        current.add(to.index, current.removeAt(from.index))
        viewModel.reorder(collection, current.map(BackgroundImageRecord::id))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BackgroundPreviewSection(
                collection = collection,
                preview = viewModel.previewRecord(collection),
                currentImageId = config.currentImageId,
                storage = storage,
            )
        }
        if (images.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.background_empty_state_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(images, key = BackgroundImageRecord::id) { image ->
                ReorderableItem(reorderableState, key = image.id) {
                    BackgroundImageListItem(
                        image = image,
                        isCurrent = config.currentImageId == image.id,
                        storage = storage,
                        modifier = Modifier.longPressDraggableHandle(),
                        onPreviewClick = { viewModel.setPreviewImage(collection, image.id) },
                        onToggleSelection = { viewModel.toggleImageSelection(image) },
                        onEditClick = { viewModel.openEditor(image) },
                        onRenameClick = { imageForRename = image },
                        onRemoveClick = { imageForRemoval = image },
                    )
                }
            }
        }
    }

    imageForRename?.let { image ->
        NamingDialog(
            onDismissRequest = { imageForRename = null },
            state = viewModel.renameState(image) { imageForRename = null },
            title = stringResource(R.string.background_rename_dialog_title),
        )
    }

    imageForRemoval?.let { image ->
        SoundAuraDialog(
            title = stringResource(R.string.background_remove_dialog_title),
            text = stringResource(R.string.background_remove_dialog_message, image.name),
            onDismissRequest = { imageForRemoval = null },
            onConfirm = {
                viewModel.removeImage(image.id)
                imageForRemoval = null
            },
        )
    }
}

@Composable
private fun BackgroundPreviewSection(
    collection: BackgroundCollectionType,
    preview: BackgroundImageRecord?,
    currentImageId: Long?,
    storage: BackgroundImageStorage,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.background_preview_title),
            style = MaterialTheme.typography.h6,
        )
        when (collection) {
            BackgroundCollectionType.Main -> MainBackgroundPreview(
                preview = preview,
                currentImageId = currentImageId,
                storage = storage,
            )
            BackgroundCollectionType.Card -> CardBackgroundPreview(
                preview = preview,
                currentImageId = currentImageId,
                storage = storage,
            )
        }
    }
}

@Composable
private fun MainBackgroundPreview(
    preview: BackgroundImageRecord?,
    currentImageId: Long?,
    storage: BackgroundImageStorage,
) = BackgroundSurface(
    background = preview?.toRenderInfo(),
    storage = storage,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(9f / 16f),
    overlayColor = MaterialTheme.colors.background.copy(alpha = 0.34f),
) {
    PreviewHeader(preview, currentImageId)
}

@Composable
private fun CardBackgroundPreview(
    preview: BackgroundImageRecord?,
    currentImageId: Long?,
    storage: BackgroundImageStorage,
) {
    val renderInfo = preview?.toRenderInfo()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewHeader(preview, currentImageId)
        CompositionLocalProvider(LocalCardBackground provides renderInfo) {
            FolderView(
                folder = Folder(
                    id = 0L,
                    name = stringResource(R.string.background_preview_folder_name),
                    isActive = false,
                    shuffle = false,
                    playlistCount = 2,
                ),
                callback = rememberFolderViewCallback(),
            )
            PlaylistView(
                playlist = Playlist(
                    id = 0L,
                    name = stringResource(R.string.background_preview_track_name),
                    isActive = false,
                    isSingleTrack = true,
                ),
                callback = rememberPlaylistViewCallback(),
            )
        }
    }
}

@Composable
private fun PreviewHeader(
    preview: BackgroundImageRecord?,
    currentImageId: Long?,
) {
    val badgeColors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = preview?.name ?: stringResource(R.string.background_no_preview_message),
            style = MaterialTheme.typography.subtitle1,
        )
        if (preview != null && preview.id == currentImageId) {
            Surface(
                color = badgeColors.containerColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.background_current_badge),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = badgeColors.contentColor,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
private fun BackgroundImageListItem(
    image: BackgroundImageRecord,
    isCurrent: Boolean,
    storage: BackgroundImageStorage,
    onPreviewClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onEditClick: () -> Unit,
    onRenameClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardAppearance = LocalCardAppearance.current
    val cardBorder = cardAppearance.border
    val secondaryActionColors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
    BackgroundSurface(
        background = image.toRenderInfo(),
        storage = storage,
        modifier = if (cardBorder != null)
            modifier.border(cardBorder, MaterialTheme.shapes.large)
        else modifier,
        preferThumbnail = true,
        overlayColor = cardAppearance.imageOverlayColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPreviewClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionBubble(
                selected = image.isEnabled,
                onClick = onToggleSelection,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = image.name,
                    style = MaterialTheme.typography.h6,
                )
                val currentBadge = stringResource(R.string.background_current_badge)
                val subtitle = buildString {
                    append("${image.width}x${image.height}")
                    if (isCurrent)
                        append("  -  $currentBadge")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.74f),
                )
            }
            BackgroundItemMenu(
                name = image.name,
                onEditClick = onEditClick,
                onRenameClick = onRenameClick,
                onRemoveClick = onRemoveClick,
                tint = secondaryActionColors.containerColor,
            )
        }
    }
}

@Composable
private fun SelectionBubble(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = rememberOverlayActionColors(
        if (selected) OverlayActionStyle.Primary
        else OverlayActionStyle.Neutral
    )
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        backgroundColor = colors.containerColor,
        contentColor = colors.contentColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
            contentDescription = null,
        )
    }
}

@Composable
private fun BackgroundItemMenu(
    name: String,
    onEditClick: () -> Unit,
    onRenameClick: () -> Unit,
    onRemoveClick: () -> Unit,
    tint: Color,
) {
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.item_options_button_description, name),
                tint = tint,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(onClick = {
                expanded = false
                onEditClick()
            }) { Text(stringResource(R.string.edit)) }
            DropdownMenuItem(onClick = {
                expanded = false
                onRenameClick()
            }) { Text(stringResource(R.string.rename)) }
            DropdownMenuItem(onClick = {
                expanded = false
                onRemoveClick()
            }) { Text(stringResource(R.string.remove)) }
        }
    }
}

@Composable
fun BackgroundImportButton(
    collection: BackgroundCollectionType,
    modifier: Modifier = Modifier,
) {
    val viewModel: BackgroundViewModel = viewModel()
    val colors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importImage(collection, it) }
    }
    FloatingActionButton(
        onClick = { launcher.launch("image/*") },
        modifier = modifier,
        backgroundColor = colors.containerColor,
        contentColor = colors.contentColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.background_import_button_description),
        )
    }
}

@Composable
fun BackgroundSaveButton(
    collection: BackgroundCollectionType,
    modifier: Modifier = Modifier,
) {
    val viewModel: BackgroundViewModel = viewModel()
    val colors = rememberOverlayActionColors(OverlayActionStyle.Primary)
    viewModel.previewedImageId(collection) ?: return
    ExtendedFloatingActionButton(
        onClick = { viewModel.savePreviewAsCurrent(collection) },
        modifier = modifier,
        backgroundColor = colors.containerColor,
        contentColor = colors.contentColor,
        icon = { Icon(Icons.Default.Check, contentDescription = null) },
        text = { Text(stringResource(R.string.background_save_effect_button)) },
    )
}

@Composable
fun BackgroundEditorScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    val viewModel: BackgroundViewModel = viewModel()
    val draft = viewModel.editorDraft
    val storage = rememberBackgroundStorage()

    if (draft == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.background_editor_loading_message))
        }
        return
    }

    if (viewModel.showingDiscardDialog) {
        SoundAuraDialog(
            title = stringResource(R.string.background_discard_changes_title),
            text = stringResource(R.string.background_discard_changes_message),
            onDismissRequest = viewModel::dismissDiscardDialog,
            onConfirm = viewModel::discardEditorChanges,
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    val confirmColors = rememberOverlayActionColors(OverlayActionStyle.Primary)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.background_editor_instruction),
                style = MaterialTheme.typography.body2,
            )
            BackgroundEditorViewport(
                draft = draft,
                storage = storage,
                modifier = Modifier.fillMaxWidth(),
                onTransformChange = viewModel::updateEditorTransform,
            )
            if (draft.collection == BackgroundCollectionType.Card) {
                CompositionLocalProvider(
                    LocalCardBackground provides BackgroundRenderInfo(
                        imageId = draft.imageId,
                        name = draft.name,
                        imagePath = draft.imagePath,
                        thumbnailPath = draft.imagePath,
                        width = draft.width,
                        height = draft.height,
                        focusX = draft.focusX,
                        focusY = draft.focusY,
                        zoom = draft.zoom,
                    )
                ) {
                    FolderView(
                        folder = Folder(
                            id = 0L,
                            name = stringResource(R.string.background_preview_folder_name),
                            isActive = false,
                            shuffle = false,
                            playlistCount = 2,
                        ),
                        callback = rememberFolderViewCallback(),
                    )
                    PlaylistView(
                        playlist = Playlist(
                            id = 0L,
                            name = stringResource(R.string.background_preview_track_name),
                            isActive = false,
                            isSingleTrack = true,
                        ),
                        callback = rememberPlaylistViewCallback(),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            OutlinedButton(
                onClick = viewModel::discardEditorChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
        ExtendedFloatingActionButton(
            onClick = viewModel::applyEditorChanges,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
            backgroundColor = confirmColors.containerColor,
            contentColor = confirmColors.contentColor,
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            text = { Text(stringResource(R.string.background_apply_crop_button)) },
        )
    }
}

@Composable
private fun BackgroundEditorViewport(
    draft: BackgroundEditorDraft,
    storage: BackgroundImageStorage,
    onTransformChange: (focusX: Float, focusY: Float, zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestDraft by rememberUpdatedState(draft)
    val latestTransformChange by rememberUpdatedState(onTransformChange)
    val selectionColors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
    val aspectRatio = when (draft.collection) {
        BackgroundCollectionType.Main -> 9f / 16f
        BackgroundCollectionType.Card -> 1.95f
    }
    val renderInfo = BackgroundRenderInfo(
        imageId = draft.imageId,
        name = draft.name,
        imagePath = draft.imagePath,
        thumbnailPath = draft.imagePath,
        width = draft.width,
        height = draft.height,
        focusX = draft.focusX,
        focusY = draft.focusY,
        zoom = draft.zoom,
    )
    Surface(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .pointerInput(draft.imageId) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val currentDraft = latestDraft
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val viewportAspect = width / height
                    val newZoom = (currentDraft.zoom * gestureZoom).coerceIn(1f, 6f)
                    val visibleFractions = currentVisibleFractions(
                        imageWidth = currentDraft.width,
                        imageHeight = currentDraft.height,
                        viewportAspectRatio = viewportAspect,
                        zoom = newZoom,
                    )
                    val proposedFocusX =
                        currentDraft.focusX - (pan.x / width) * visibleFractions.first
                    val proposedFocusY =
                        currentDraft.focusY - (pan.y / height) * visibleFractions.second
                    val clamped = clampBackgroundTransform(
                        imageWidth = currentDraft.width,
                        imageHeight = currentDraft.height,
                        viewportAspectRatio = viewportAspect,
                        focusX = proposedFocusX,
                        focusY = proposedFocusY,
                        zoom = newZoom,
                    )
                    latestTransformChange(clamped.focusX, clamped.focusY, clamped.zoom)
                }
            },
        shape = MaterialTheme.shapes.large,
    ) {
        BackgroundImageBox(
            background = renderInfo,
            storage = storage,
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            overlayColor = MaterialTheme.colors.background.copy(alpha = 0.12f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .border(
                    border = BorderStroke(2.dp, selectionColors.containerColor),
                    shape = MaterialTheme.shapes.large,
                )
        )
    }
}

private fun currentVisibleFractions(
    imageWidth: Int,
    imageHeight: Int,
    viewportAspectRatio: Float,
    zoom: Float,
): Pair<Float, Float> {
    val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
    val widthFraction: Float
    val heightFraction: Float
    if (imageAspectRatio > viewportAspectRatio) {
        heightFraction = 1f / zoom
        widthFraction = viewportAspectRatio / imageAspectRatio / zoom
    } else {
        widthFraction = 1f / zoom
        heightFraction = imageAspectRatio / viewportAspectRatio / zoom
    }
    return widthFraction.coerceAtMost(1f) to heightFraction.coerceAtMost(1f)
}

/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.AppCardSurface
import com.cliffracertech.soundaura.ui.theme.OverlayActionStyle
import com.cliffracertech.soundaura.ui.theme.rememberOverlayActionColors
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderPalette

data class Folder(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val shuffle: Boolean,
    val playlistCount: Int,
    val hasError: Boolean = false)

interface FolderViewCallback {
    fun onAddRemoveButtonClick(folder: Folder)
    fun onOpenFolder(folder: Folder)
    fun onRenameClick(folder: Folder)
    fun onAddAudioClick(folder: Folder)
    fun onShuffleClick(folder: Folder)
    fun onRemoveClick(folder: Folder)
}

@Composable fun rememberFolderViewCallback(
    onAddRemoveButtonClick: (Folder) -> Unit = {},
    onOpenFolder: (Folder) -> Unit = {},
    onRenameClick: (Folder) -> Unit = {},
    onAddAudioClick: (Folder) -> Unit = {},
    onShuffleClick: (Folder) -> Unit = {},
    onRemoveClick: (Folder) -> Unit = {},
) = remember { object: FolderViewCallback {
    override fun onAddRemoveButtonClick(folder: Folder) = onAddRemoveButtonClick(folder)
    override fun onOpenFolder(folder: Folder) = onOpenFolder(folder)
    override fun onRenameClick(folder: Folder) = onRenameClick(folder)
    override fun onAddAudioClick(folder: Folder) = onAddAudioClick(folder)
    override fun onShuffleClick(folder: Folder) = onShuffleClick(folder)
    override fun onRemoveClick(folder: Folder) = onRemoveClick(folder)
}}

@Composable fun FolderView(
    folder: Folder,
    callback: FolderViewCallback,
    modifier: Modifier = Modifier,
) = AppCardSurface(modifier, MaterialTheme.shapes.large) {
        val neutralActionColors = rememberOverlayActionColors(OverlayActionStyle.Neutral)
        val primaryActionColors = rememberOverlayActionColors(OverlayActionStyle.Primary)
        val secondaryActionColors = rememberOverlayActionColors(OverlayActionStyle.Secondary)
        val sliderPalette = rememberOverlaySliderPalette()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (folder.hasError)
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).padding(10.dp),
                    tint = MaterialTheme.colors.error)
            else AddRemoveButton(
                added = folder.isActive,
                contentDescription = stringResource(
                    if (folder.isActive) R.string.set_folder_inactive_description
                    else R.string.set_folder_active_description,
                    folder.name),
                backgroundColor = neutralActionColors.containerColor,
                tint = primaryActionColors.containerColor,
                onClick = { callback.onAddRemoveButtonClick(folder) })

            Row(
                modifier = Modifier
                    .weight(1f)
                    .minTouchTargetSize()
                    .clickable { callback.onOpenFolder(folder) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = sliderPalette.accentStart,
                    modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(folder.name, style = MaterialTheme.typography.h5)
                    Text(
                        text = stringResource(
                            R.string.folder_item_count_description,
                            folder.playlistCount),
                        style = MaterialTheme.typography.caption,
                        color = LocalContentColor.current.copy(alpha = 0.74f))
                }
            }

            if (folder.hasError) {
                IconButton({ callback.onRemoveClick(folder) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(
                            R.string.remove_item_description, folder.name),
                        tint = MaterialTheme.colors.error)
                }
            } else FolderMenu(folder, callback, secondaryActionColors.containerColor)
        }
}

@Composable private fun FolderMenu(
    folder: Folder,
    callback: FolderViewCallback,
    tint: Color,
) {
    var showingOptionsMenu by rememberMutableStateOf(false)

    IconButton({ showingOptionsMenu = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(
                R.string.item_options_button_description, folder.name),
            tint = tint)
    }

    DropdownMenu(
        expanded = showingOptionsMenu,
        onDismissRequest = { showingOptionsMenu = false }
    ) {
        DropdownMenuItem(onClick = {
            showingOptionsMenu = false
            callback.onRenameClick(folder)
        }) { Text(stringResource(R.string.rename)) }

        DropdownMenuItem(onClick = {
            showingOptionsMenu = false
            callback.onAddAudioClick(folder)
        }) { Text(stringResource(R.string.folder_add_audio)) }

        DropdownMenuItem(onClick = {
            showingOptionsMenu = false
            callback.onShuffleClick(folder)
        }) {
            Text(stringResource(
                if (folder.shuffle) R.string.folder_random_play
                else R.string.folder_sequential_play))
        }

        DropdownMenuItem(onClick = {
            showingOptionsMenu = false
            callback.onRemoveClick(folder)
        }) { Text(stringResource(R.string.remove)) }
    }
}

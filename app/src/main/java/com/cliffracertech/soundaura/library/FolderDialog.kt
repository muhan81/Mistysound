/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.addbutton.SystemFileChooser
import com.cliffracertech.soundaura.dialog.NamingDialog
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize

sealed class FolderDialog(
    val onDismissRequest: () -> Unit,
) {
    class SourceChoice(
        onDismissRequest: () -> Unit,
        val onLocalAudioClick: () -> Unit,
        val onCurrentAudioClick: () -> Unit,
    ): FolderDialog(onDismissRequest)

    class SelectingFiles(
        onDismissRequest: () -> Unit,
        val onFilesSelected: (List<Uri>) -> Unit,
    ): FolderDialog(onDismissRequest)

    class NameFolder(
        onDismissRequest: () -> Unit,
        namingState: ValidatedNamingState,
    ): FolderDialog(onDismissRequest),
       NamingState by namingState

    class Rename(
        onDismissRequest: () -> Unit,
        namingState: ValidatedNamingState,
    ): FolderDialog(onDismissRequest),
       NamingState by namingState

    class RequestStoragePermissionExplanation(
        permissionsUsed: Int,
        permissionsAllowed: Int,
        onDismissRequest: () -> Unit,
        val onOkClick: () -> Unit,
    ): FolderDialog(onDismissRequest) {
        val text = StringResource(
            string = null,
            stringResId = R.string.add_local_files_request_storage_permission_explanation_for_tracks,
            permissionsUsed,
            permissionsAllowed)
    }

    class RequestStoragePermission(
        onDismissRequest: () -> Unit,
        val onResult: (Boolean) -> Unit,
    ): FolderDialog(onDismissRequest)

    class Remove(
        val folder: Folder,
        onDismissRequest: () -> Unit,
        val onConfirmClick: () -> Unit,
    ): FolderDialog(onDismissRequest)
}

@Composable fun FolderDialogShower(
    dialogState: FolderDialog?,
    modifier: Modifier = Modifier,
) = when (dialogState) {
    null -> {}
    is FolderDialog.SourceChoice -> FolderSourceChoiceDialog(dialogState, modifier)
    is FolderDialog.SelectingFiles -> SystemFileChooser { uris ->
        if (uris.isEmpty()) dialogState.onDismissRequest()
        else dialogState.onFilesSelected(uris)
    }
    is FolderDialog.NameFolder -> NamingDialog(
        onDismissRequest = dialogState.onDismissRequest,
        modifier = modifier,
        title = stringResource(R.string.folder_name_dialog_title),
        state = dialogState)
    is FolderDialog.Rename -> NamingDialog(
        onDismissRequest = dialogState.onDismissRequest,
        modifier = modifier,
        title = stringResource(R.string.default_rename_dialog_title),
        state = dialogState)
    is FolderDialog.RequestStoragePermissionExplanation -> SoundAuraDialog(
        modifier = modifier,
        title = stringResource(R.string.add_local_files_request_storage_permission_title),
        text = dialogState.text.resolve(LocalContext.current),
        onDismissRequest = dialogState.onDismissRequest,
        onConfirm = dialogState.onOkClick)
    is FolderDialog.RequestStoragePermission ->
        AccessAudioFilesPermissionRequester(onPermissionGranted = dialogState.onResult)
    is FolderDialog.Remove -> SoundAuraDialog(
        modifier = modifier,
        title = stringResource(R.string.confirm_remove_title, dialogState.folder.name),
        text = stringResource(R.string.confirm_remove_folder_message),
        confirmText = stringResource(R.string.remove),
        onDismissRequest = dialogState.onDismissRequest,
        onConfirm = {
            dialogState.onConfirmClick()
            dialogState.onDismissRequest()
        })
}

@Composable private fun FolderSourceChoiceDialog(
    state: FolderDialog.SourceChoice,
    modifier: Modifier = Modifier,
) = SoundAuraDialog(
    modifier = modifier,
    title = stringResource(R.string.folder_source_dialog_title),
    onDismissRequest = state.onDismissRequest,
    buttons = {
        HorizontalDivider()
        TextButton(
            modifier = Modifier.minTouchTargetSize().fillMaxWidth(),
            shape = RectangleShape,
            textResId = R.string.folder_choose_local_audio,
            onClick = state.onLocalAudioClick)

        HorizontalDivider()
        TextButton(
            modifier = Modifier.minTouchTargetSize().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium.bottomShape(),
            textResId = R.string.folder_choose_current_audio,
            onClick = state.onCurrentAudioClick)
    })

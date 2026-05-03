/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.model.UpdateInfo
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomStartShape

@Composable fun UpdateAvailableDialog(
    update: UpdateInfo,
    showIgnoreOption: Boolean,
    onUpdateClick: () -> Unit,
    onDismissClick: (ignoreThisUpdate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ignoreThisUpdate by rememberSaveable(update.tagName) { mutableStateOf(false) }
    SoundAuraDialog(
        modifier = modifier,
        title = stringResource(R.string.update_available_title),
        onDismissRequest = { onDismissClick(false) },
        buttons = {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onDismissClick(if (showIgnoreOption) ignoreThisUpdate else false) },
                    shape = MaterialTheme.shapes.medium.bottomStartShape(),
                ) {
                    Text(
                        text = stringResource(
                            if (showIgnoreOption) R.string.update_later
                            else R.string.no))
                }
                VerticalDivider()
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUpdateClick,
                    shape = MaterialTheme.shapes.medium.bottomEndShape(),
                ) {
                    Text(
                        text = stringResource(
                            if (showIgnoreOption) R.string.confirm_update
                            else R.string.yes))
                }
            }
        },
    ) {
        Text(
            text = stringResource(
                R.string.update_available_message,
                update.versionName,
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.body1,
        )
        if (showIgnoreOption) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ignoreThisUpdate = !ignoreThisUpdate }
                    .padding(start = 8.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = ignoreThisUpdate,
                    onCheckedChange = { ignoreThisUpdate = it },
                )
                Text(
                    text = stringResource(R.string.ignore_this_update),
                    style = MaterialTheme.typography.body1,
                )
            }
        }
    }
}

/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.UpdateAvailableDialog
import com.cliffracertech.soundaura.dialog.DialogWidth
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.theme.rememberOverlaySliderColors

@Composable fun AppSettings(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { DisplaySettingsCategory() }
        item { PlaybackSettingsCategory() }
        item { AboutSettingsCategory() }
    }
}

@Composable private fun DisplaySettingsCategory() =
    SettingCategory(stringResource(R.string.display)) { paddingModifier ->
        val viewModel: SettingsViewModel = viewModel()

        EnumDialogSetting(
            title = stringResource(R.string.app_theme),
            modifier = paddingModifier,
            values = AppTheme.values(),
            valueNames = AppTheme.valueStrings(),
            currentValue = viewModel.appTheme,
            onValueClick = viewModel::onAppThemeClick)
        FrostedGlassOpacitySetting(viewModel, paddingModifier)
        HorizontalDivider(paddingModifier)
        EnumDialogSetting(
            title = stringResource(R.string.language_setting_title),
            modifier = paddingModifier,
            values = AppLanguage.values(),
            valueNames = AppLanguage.valueStrings(),
            currentValue = viewModel.appLanguage,
            onValueClick = viewModel::onAppLanguageClick)
        HorizontalDivider(paddingModifier)
        Setting(
            title = stringResource(R.string.main_background_setting_title),
            modifier = paddingModifier,
            subtitle = stringResource(R.string.main_background_setting_description),
            icon = { androidx.compose.material.Icon(Icons.Default.Image, null) },
            onClick = viewModel::onMainBackgroundClick,
        ) {
            androidx.compose.material.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.54f),
            )
        }
    }

@Composable private fun FrostedGlassOpacitySetting(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) = AnimatedVisibility(
    visible = viewModel.frostedGlassOpacitySettingVisible,
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
) {
    var sliderValue by remember(viewModel.frostedGlassOpacityPercent) {
        mutableFloatStateOf(viewModel.frostedGlassOpacityPercent.toFloat())
    }
    val sliderColors = rememberOverlaySliderColors()
    Column {
        HorizontalDivider(modifier)
        Setting(
            title = stringResource(R.string.frosted_glass_opacity_title),
            modifier = modifier,
            subtitle = stringResource(
                R.string.frosted_glass_opacity_description,
                sliderValue.toInt(),
            ),
        ) {
            androidx.compose.material.Text(
                text = "${sliderValue.toInt()}%",
                style = MaterialTheme.typography.subtitle1,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value
            },
            onValueChangeFinished = {
                viewModel.onFrostedGlassOpacityChange(sliderValue.toInt())
            },
            valueRange = 0f..100f,
            colors = sliderColors,
            modifier = modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable private fun PlayInBackgroundSetting(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onTileTutorialShowRequest: () -> Unit,
) = Setting(
    title = stringResource(R.string.play_in_background_setting_title),
    modifier = modifier,
    subtitle = stringResource(R.string.play_in_background_setting_description),
    onClick = viewModel::onPlayInBackgroundTitleClick
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Vertical Divider
        Box(Modifier.width((1.5).dp).height(40.dp)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))

        Spacer(Modifier.width(6.dp))
        Switch(checked = viewModel.playInBackground,
            onCheckedChange = remember {{ viewModel.onPlayInBackgroundSwitchClick() }})
    }
    if (viewModel.showingPlayInBackgroundExplanation)
        PlayInBackgroundExplanationDialog(
            onDismissRequest = viewModel::onPlayInBackgroundExplanationDismiss)
    if (viewModel.showingNotificationPermissionDialog) {
        val context = LocalContext.current
        val showExplanation =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                false
            else ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED
        NotificationPermissionDialog(
            showExplanationFirst = showExplanation,
            onShowTileTutorialClick = onTileTutorialShowRequest,
            onDismissRequest = viewModel::onNotificationPermissionDialogDismiss,
            onPermissionResult = viewModel::onNotificationPermissionDialogConfirm)
    }
}

@Composable private fun AutoPauseDuringCallSetting(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) = AnimatedVisibility(
    visible = viewModel.autoPauseDuringCallSettingVisible,
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
) {
    Column {
        HorizontalDivider(modifier)
        Setting(
            title = stringResource(R.string.auto_pause_during_calls_setting_title),
            modifier = modifier,
            subtitle = stringResource(R.string.auto_pause_during_calls_setting_subtitle),
            onClick = viewModel::onAutoPauseDuringCallClick
        ) {
            Switch(checked = viewModel.autoPauseDuringCall,
                onCheckedChange = remember {{ viewModel.onAutoPauseDuringCallClick() }})
        }
        if (viewModel.showingPhoneStatePermissionDialog) {
            val context = LocalContext.current
            val showExplanation = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_DENIED
            PhoneStatePermissionDialog(
                showExplanationFirst = showExplanation,
                onDismissRequest = viewModel::onPhoneStatePermissionDialogDismiss,
                onPermissionResult = viewModel::onPhoneStatePermissionDialogConfirm)
        }
    }
}

@Composable private fun PlaybackSettingsCategory() =
    SettingCategory(stringResource(R.string.playback)) { paddingModifier ->
        val viewModel: SettingsViewModel = viewModel()
        var showingTileTutorialDialog by rememberSaveable { mutableStateOf(false) }

        PlayInBackgroundSetting(
            viewModel = viewModel,
            modifier = paddingModifier,
            onTileTutorialShowRequest = { showingTileTutorialDialog = true })
        AutoPauseDuringCallSetting(viewModel, paddingModifier)

        HorizontalDivider(paddingModifier)
        EnumDialogSetting(
            title = stringResource(R.string.on_zero_volume_behavior_setting_title),
            modifier = paddingModifier,
            dialogWidth = DialogWidth.MatchToScreenSize(),
            description = stringResource(R.string.on_zero_volume_behavior_setting_description),
            values = enumValues(),
            valueNames = OnZeroVolumeAudioDeviceBehavior.valueStrings(),
            valueDescriptions = OnZeroVolumeAudioDeviceBehavior.valueDescriptions(),
            currentValue = viewModel.onZeroVolumeAudioDeviceBehavior,
            onValueClick = viewModel::onOnZeroVolumeAudioDeviceBehaviorClick)
        HorizontalDivider(paddingModifier)
        DialogSetting(
            title = stringResource(R.string.control_playback_using_tile_setting_title),
            modifier = paddingModifier,
            dialogVisible = showingTileTutorialDialog,
            onShowRequest = { showingTileTutorialDialog = true },
            onDismissRequest = { showingTileTutorialDialog = false },
            content = { TileTutorialDialog(onDismissRequest = it) })
        HorizontalDivider(paddingModifier)
        Setting(
            title = stringResource(R.string.stop_instead_of_pause_setting_title),
            modifier = paddingModifier,
            subtitle = stringResource(R.string.stop_instead_of_pause_setting_description),
            onClick = viewModel::onStopInsteadOfPauseClick
        ) {
            Switch(checked = viewModel.stopInsteadOfPause,
                onCheckedChange = { viewModel.onStopInsteadOfPauseClick() })
        }
    }

@Composable private fun AboutSettingsCategory() =
    SettingCategory(stringResource(R.string.about)) { paddingModifier ->
        val viewModel: SettingsViewModel = viewModel()
        val uriHandler = LocalUriHandler.current
        DialogSetting(stringResource(R.string.privacy_policy_setting_title), paddingModifier) {
            PrivacyPolicyDialog(onDismissRequest = it)
        }
        HorizontalDivider(paddingModifier)
        DialogSetting(stringResource(R.string.open_source_licenses), paddingModifier) {
            OpenSourceLibrariesUsedDialog(onDismissRequest = it)
        }
        HorizontalDivider(paddingModifier)
        Setting(
            title = stringResource(R.string.check_for_updates),
            modifier = paddingModifier,
            subtitle = if (viewModel.checkingForUpdates)
                stringResource(R.string.checking_for_updates)
            else null,
            onClick = viewModel::onCheckForUpdatesClick,
        ) {
            androidx.compose.material.Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.54f),
            )
        }
        viewModel.manualUpdate?.let { update ->
            UpdateAvailableDialog(
                update = update,
                showIgnoreOption = false,
                onUpdateClick = {
                    uriHandler.openUri(update.downloadUrl)
                    viewModel.onManualUpdateDialogDismiss()
                },
                onDismissClick = { viewModel.onManualUpdateDialogDismiss() },
            )
        }
        HorizontalDivider(paddingModifier)
        DialogSetting(stringResource(R.string.about_app_setting_title), paddingModifier) {
            AboutAppDialog(onDismissRequest = it)
        }
    }

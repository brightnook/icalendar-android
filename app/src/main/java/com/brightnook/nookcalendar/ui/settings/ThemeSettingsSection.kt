package com.brightnook.nookcalendar.ui.settings

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.theme.ThemeAssetFailure

@Composable
internal fun ColumnScope.ThemeSettingsSection(
    theme: ThemeSettings,
    settingsPresentation: SettingsSurfacePresentation,
    importError: ThemeAssetFailure?,
    photoPicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    onSelectTheme: (ThemeBackgroundMode) -> Unit,
    onRemoveThemeImage: () -> Unit,
    onSetApplyThemeToWidget: (Boolean) -> Unit,
) {
    ThemeChoice(
        label = stringResource(R.string.settings_theme_white),
        selected = theme.mode == ThemeBackgroundMode.WHITE,
        tag = "theme-white",
        settingsPresentation = settingsPresentation,
        onClick = { onSelectTheme(ThemeBackgroundMode.WHITE) },
    )
    ThemeChoice(
        label = stringResource(R.string.settings_theme_black),
        selected = theme.mode == ThemeBackgroundMode.BLACK,
        tag = "theme-black",
        settingsPresentation = settingsPresentation,
        onClick = { onSelectTheme(ThemeBackgroundMode.BLACK) },
    )
    ThemeChoice(
        label = stringResource(R.string.settings_theme_image),
        selected = theme.mode == ThemeBackgroundMode.IMAGE,
        tag = "theme-image",
        settingsPresentation = settingsPresentation,
        onClick = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
    if (theme.assetKey != null) {
        TextButton(
            onClick = onRemoveThemeImage,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("theme-remove-image"),
        ) {
            Text(stringResource(R.string.settings_theme_remove_image), Modifier.weight(1f))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_theme_apply_to_widget), Modifier.weight(1f))
        Switch(
            checked = theme.applyToWidget,
            onCheckedChange = onSetApplyThemeToWidget,
            modifier = Modifier.testTag("theme-apply-to-widget"),
            colors = SwitchDefaults.colors(
                checkedThumbColor = settingsPresentation.modalContainerColor,
                checkedTrackColor = settingsPresentation.actionContentColor,
                checkedBorderColor = settingsPresentation.actionContentColor,
                uncheckedThumbColor = settingsPresentation.secondaryContentColor,
                uncheckedTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                uncheckedBorderColor = settingsPresentation.outlineColor,
            ),
        )
    }
    importError?.let { failure ->
        Text(
            text = stringResource(failure.messageResource()),
            color = settingsPresentation.errorColor,
            modifier = Modifier.testTag("theme-import-error"),
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    tag: String,
    settingsPresentation: SettingsSurfacePresentation,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics {
                this[SettingsActionColorSemantics] = settingsPresentation.actionContentColor
            },
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = settingsPresentation.actionContentColor,
                unselectedColor = settingsPresentation.outlineColor,
            ),
        )
        Text(label, Modifier.weight(1f))
    }
}

private fun ThemeAssetFailure.messageResource(): Int = when (this) {
    ThemeAssetFailure.UNSUPPORTED -> R.string.settings_theme_error_unsupported
    ThemeAssetFailure.TOO_LARGE -> R.string.settings_theme_error_too_large
    ThemeAssetFailure.IO -> R.string.settings_theme_error_io
}

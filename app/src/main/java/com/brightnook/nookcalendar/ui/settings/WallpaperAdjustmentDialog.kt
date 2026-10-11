package com.brightnook.nookcalendar.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.brightnook.nookcalendar.R

internal const val WALLPAPER_ADJUSTMENT_TAG = "wallpaper-adjustment"

@Composable
internal fun WallpaperAdjustmentDialog(
    uri: Uri,
    onCancel: () -> Unit,
    onSave: (Float, Float, Float) -> Unit,
) {
    var focusX by remember(uri) { mutableFloatStateOf(0f) }
    var focusY by remember(uri) { mutableFloatStateOf(0f) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (viewport.width > 0f) {
            focusX = (focusX - panChange.x * 2f / viewport.width).coerceIn(-1f, 1f)
        }
        if (viewport.height > 0f) {
            focusY = (focusY - panChange.y * 2f / viewport.height).coerceIn(-1f, 1f)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag(WALLPAPER_ADJUSTMENT_TAG), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(focusX, focusY),
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .transformable(transformState)
                        .testTag("wallpaper-adjustment-image"),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.settings_wallpaper_adjust_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_wallpaper_adjust_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.settings_wallpaper_adjust_cancel), color = Color.White)
                    }
                    TextButton(
                        onClick = { onSave(focusX, focusY, scale) },
                        modifier = Modifier.testTag("wallpaper-adjustment-save"),
                    ) {
                        Text(stringResource(R.string.settings_wallpaper_adjust_save), color = Color.White)
                    }
                }
            }
        }
    }
}

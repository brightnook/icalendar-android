package com.victor2319.ioscalendar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import java.io.File

const val CALENDAR_BACKGROUND_TAG = "calendar-background"
const val CALENDAR_WALLPAPER_TAG = "calendar-wallpaper"

@Composable
fun CalendarBackground(
    presentation: CalendarThemePresentation,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(presentation.backgroundColor)
            .testTag(CALENDAR_BACKGROUND_TAG),
    ) {
        presentation.wallpaperPath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag(CALENDAR_WALLPAPER_TAG),
            )
        }
        content()
    }
}

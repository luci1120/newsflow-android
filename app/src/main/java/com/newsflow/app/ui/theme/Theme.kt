package com.newsflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun NewsFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Blue,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            surface = SurfaceDark,
            background = SurfaceDark,
        )
    } else {
        lightColorScheme(
            primary = Blue,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            surface = Surface,
            background = Surface,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

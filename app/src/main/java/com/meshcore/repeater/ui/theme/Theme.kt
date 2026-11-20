package com.meshcore.repeater.ui.theme

import androidx.compose.runtime.Composable
import com.meshcore.repeater.model.ThemeMode

@Composable
fun RepeaterConfiguratorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    BauhausTheme(themeMode = themeMode, content = content)
}

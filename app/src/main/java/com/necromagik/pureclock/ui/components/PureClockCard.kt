package com.necromagik.pureclock.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect

@Composable
fun PureClockCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val shape = RoundedCornerShape(themeConfig.cardCornerRadius)

    Box(
        modifier = modifier
            .pure3DEffect(
                shape = shape,
                accentColor = themeConfig.accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(16.dp),
        content = content
    )
}
package com.necromagik.pureclock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.AnalogStyle
import com.necromagik.pureclock.data.DigitalStyle
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig

// ============================================================================
// СЕКЦИЯ 1: ДИАЛОГ ВЫБОРА СТИЛЕЙ ЦИФЕРБЛАТОВ С ИНТЕРАКТИВНЫМ ПРЕВЬЮ
// ============================================================================
@Composable
fun ClockStylePickerDialog(
    currentAnalogStyle: AnalogStyle,
    currentDigitalStyle: DigitalStyle,
    onAnalogStyleSelected: (AnalogStyle) -> Unit,
    onDigitalStyleSelected: (DigitalStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var tempAnalogStyle by remember { mutableStateOf(currentAnalogStyle) }
    var tempDigitalStyle by remember { mutableStateOf(currentDigitalStyle) }

    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Стиль циферблата",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ИНТЕРАКТИВНОЕ ПРЕВЬЮ ЦИФЕРБЛАТА
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(themeConfig.cardCornerRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    SmoothAnalogClock(
                        analogStyle = tempAnalogStyle,
                        digitalStyle = tempDigitalStyle,
                        clockSize = 160.dp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = accentColor,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Аналоговый", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Цифровой", fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    if (selectedTab == 0) {
                        items(AnalogStyle.entries) { style ->
                            StyleCard(
                                title = style.title,
                                description = style.description,
                                isSelected = style == tempAnalogStyle,
                                accentColor = accentColor,
                                onClick = {
                                    tempAnalogStyle = style
                                    onAnalogStyleSelected(style)
                                }
                            )
                        }
                    } else {
                        items(DigitalStyle.entries) { style ->
                            StyleCard(
                                title = style.title,
                                description = style.description,
                                isSelected = style == tempDigitalStyle,
                                accentColor = accentColor,
                                onClick = {
                                    tempDigitalStyle = style
                                    onDigitalStyleSelected(style)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.bounceClick()) {
                Text("Готово", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(themeConfig.cardCornerRadius)
    )
}

// ============================================================================
// СЕКЦИЯ 2: КАРТОЧКА СТИЛЯ С RADIO BUTTON И BORDER
// ============================================================================
@Composable
private fun StyleCard(
    title: String,
    description: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(themeConfig.cardCornerRadius))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(themeConfig.cardCornerRadius))
            .bounceClick()
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Color.Gray, fontSize = 11.sp)
            }
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
            )
        }
    }
}
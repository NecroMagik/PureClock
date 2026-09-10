package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.WorldCity
import com.necromagik.pureclock.data.WorldClockRepository
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.SmoothAnalogClock
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(
    onOpenSettings: () -> Unit = {},
    externalShowAddDialog: Boolean = false,
    onDialogDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val is24Hour by settingsManager.is24HourFormatFlow.collectAsState()

    val repository = remember {
        val db = AppDatabase.getDatabase(context)
        WorldClockRepository(db.cityDao(), context)
    }

    var savedIds by remember { mutableStateOf(settingsManager.savedCityIds) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    val isDialogVisible = showAddCityDialog || externalShowAddDialog
    var currentShiftHours by remember { mutableIntStateOf(0) }
    var savedCities by remember { mutableStateOf<List<WorldCity>>(emptyList()) }

    LaunchedEffect(savedIds) {
        savedCities = repository.getSavedCities(savedIds)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            SmoothAnalogClock(
                analogStyle = settingsManager.selectedAnalogStyle,
                digitalStyle = settingsManager.selectedDigitalStyle,
                clockSize = 330.dp,
                onShiftHoursChanged = { newShift ->
                    currentShiftHours = newShift
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ОТСЛЕЖИВАЕМЫЕ ГОРОДА",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${savedCities.size} городов",
                    color = Color.Gray.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                itemsIndexed(savedCities, key = { _, city -> city.id }) { index, city ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { isVisible = true }

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = index * 40)) +
                                slideInVertically(
                                    initialOffsetY = { 30 },
                                    animationSpec = tween(durationMillis = 300, delayMillis = index * 40)
                                ),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        CityClockCard(
                            city = city,
                            is24Hour = is24Hour,
                            shiftHours = currentShiftHours,
                            onDelete = {
                                val newSet = savedIds - city.id
                                savedIds = newSet
                                settingsManager.savedCityIds = newSet
                            }
                        )
                    }
                }
            }
        }
    }

    if (isDialogVisible) {
        AddCityDialog(
            alreadySavedIds = savedIds,
            repository = repository,
            onCitySelected = { cityId ->
                val newSet = savedIds + cityId
                savedIds = newSet
                settingsManager.savedCityIds = newSet
                showAddCityDialog = false
                onDialogDismiss()
            },
            onDismiss = {
                showAddCityDialog = false
                onDialogDismiss()
            }
        )
    }
}

@Composable
private fun CityClockCard(
    city: WorldCity,
    is24Hour: Boolean,
    shiftHours: Int,
    onDelete: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }
    val isDay = city.isDaytime(shiftHours)
    val sunMoonColor = if (isDay) Color(0xFFFFB74D) else Color(0xFF90CAF9)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = cardShape,
                accentColor = accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sunMoonColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = sunMoonColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = city.cityName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${city.countryName} • ${city.getTimeDifferenceText(shiftHours)}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Увеличенный размер и жирность шрифта времени
                    Text(
                        text = city.getFormattedTime(is24Hour, shiftHours),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    // Акцентная подпись с количеством добавленных/убранных часов при ручном сдвиге
                    if (shiftHours != 0) {
                        val shiftText = if (shiftHours > 0) "+$shiftHours ч" else "$shiftHours ч"
                        Text(
                            text = shiftText,
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCityDialog(
    alreadySavedIds: Set<String>,
    repository: WorldClockRepository,
    onCitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<WorldCity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        searchResults = repository.searchCities(searchQuery)
            .filter { !alreadySavedIds.contains(it.id) }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = themeConfig.accentColor)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Добавить город", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск (Пекин, Мумбаи)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (searchResults.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Города не найдены", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(searchResults, key = { _, city -> city.id }) { _, city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .bounceClick { onCitySelected(city.id) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city.cityName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = city.countryName,
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = city.getTimeDifferenceText(0),
                                    color = themeConfig.accentColor,
                                    fontSize = 12.sp
                                )
                            }
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.bounceClick()) {
                Text("Отмена", color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
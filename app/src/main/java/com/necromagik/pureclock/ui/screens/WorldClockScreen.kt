package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ============================================================================
// СЕКЦИЯ 1: ЭКРАН МИРОВОГО ВРЕМЕНИ И СПИСОК ОТСЛЕЖИВАЕМЫХ ГОРОДОВ
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(
    onOpenSettings: () -> Unit = {},
    externalShowAddDialog: Boolean = false,
    onDialogDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }

    // Инициализируем репозиторий с передачей Room DAO
    val repository = remember {
        val db = AppDatabase.getDatabase(context)
        WorldClockRepository(db.cityDao(), context)
    }

    var savedIds by remember { mutableStateOf(settingsManager.savedCityIds) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    val isDialogVisible = showAddCityDialog || externalShowAddDialog

    var currentShiftHours by remember { mutableIntStateOf(0) }

    // Загружаем города асинхронно из базы Room
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SmoothAnalogClock(
                analogStyle = settingsManager.selectedAnalogStyle,
                digitalStyle = settingsManager.selectedDigitalStyle,
                clockSize = 350.dp,
                onShiftHoursChanged = { newShift ->
                    currentShiftHours = newShift
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Отслеживаемые города",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${savedCities.size} городов",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(savedCities, key = { _, city -> city.id }) { index, city ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { isVisible = true }

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = index * 50)) +
                                slideInVertically(
                                    initialOffsetY = { 30 },
                                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
                                ),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        CityClockCard(
                            city = city,
                            is24Hour = settingsManager.is24HourFormat,
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

// ============================================================================
// СЕКЦИЯ 2: ВЫЗОВ МОДАЛЬНОГО ОКНА ПОИСКА И ДОБАВЛЕНИЯ ГОРОДОВ
// ============================================================================
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

// ============================================================================
// СЕКЦИЯ 3: КАРТОЧКА ГОРОДА С ДНЁМ/НОЧЬЮ И ДЕЛЬТОЙ ЧАСОВ
// ============================================================================
@Composable
private fun CityClockCard(
    city: WorldCity,
    is24Hour: Boolean,
    shiftHours: Int,
    onDelete: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current

    Card(
        shape = RoundedCornerShape(themeConfig.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = city.cityName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (city.isDaytime(shiftHours)) Icons.Default.WbSunny else Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = if (city.isDaytime(shiftHours)) Color(0xFFFFB74D) else Color(0xFF90CAF9),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${city.countryName} • ${city.getTimeDifferenceText(shiftHours)}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = city.getFormattedTime(is24Hour, shiftHours),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

// ============================================================================
// СЕКЦИЯ 4: ДИАЛОГ ОФЛАЙН/ОНЛАЙН ПОИСКА ГОРОДОВ ЧЕРЕЗ ROOM
// ============================================================================
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

    // Делаем мгновенный реактивный поиск в Room БД при вводе
    LaunchedEffect(searchQuery) {
        isLoading = true
        searchResults = repository.searchCities(searchQuery)
            .filter { !alreadySavedIds.contains(it.id) }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить город", color = MaterialTheme.colorScheme.onSurface) },
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
                    shape = RoundedCornerShape(themeConfig.cardCornerRadius / 2),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

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
                        itemsIndexed(searchResults, key = { _, city -> city.id }) { index, city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick { onCitySelected(city.id) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city.cityName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = city.countryName,
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = city.getTimeDifferenceText(0),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
        shape = RoundedCornerShape(themeConfig.cardCornerRadius)
    )
}
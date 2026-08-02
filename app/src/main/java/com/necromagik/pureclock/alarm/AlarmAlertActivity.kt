package com.necromagik.pureclock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.theme.PureClockGreen
import com.necromagik.pureclock.ui.theme.PureClockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random

class AlarmAlertActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeListener: (() -> Unit)? = null
    private var lastShakeTime: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeUpDeviceAndShowOverLockscreen()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            PureClockTheme {
                AlarmAlertContent(
                    onDismiss = { stopAlarmService() },
                    onSnooze = { snoozeAlarm() },
                    registerShakeListener = { listener ->
                        shakeListener = listener
                        accelerometer?.let {
                            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                        }
                    }
                )
            }
        }
    }

    private fun snoozeAlarm() {
        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId != -1L) {
            val settingsManager = SettingsManager.getInstance(this)
            val snoozeMinutes = settingsManager.defaultSnoozeTimeMinutes

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    AlarmScheduler(applicationContext).snooze(alarm, snoozeMinutes)
                }
            }
        }
        stopAlarmService()
    }

    private fun wakeUpDeviceAndShowOverLockscreen() {
        // Запрашиваем отображение строго поверх экрана блокировки
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        // Держим экран включенным
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            if (gForce > 2.2) {
                val now = System.currentTimeMillis()
                if (lastShakeTime + 800 < now) {
                    lastShakeTime = now
                    shakeListener?.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java)
        stopService(intent)
        dismissAndExit()
    }

    private fun dismissAndExit() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(mainIntent)
        finish()
    }
}

@Composable
fun AlarmAlertContent(
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    registerShakeListener: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val dismissMethod = remember { settingsManager.dismissMethod }

    var currentTimeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            kotlinx.coroutines.delay(1000)
        }
    }

    val num1 = remember { Random.nextInt(12, 49) }
    val num2 = remember { Random.nextInt(4, 19) }
    val correctAnswer = remember { num1 + num2 }
    var mathInput by remember { mutableStateOf("") }

    var shakeCount by remember { mutableIntStateOf(5) }

    DisposableEffect(dismissMethod) {
        if (dismissMethod == "SHAKE") {
            registerShakeListener {
                if (shakeCount > 1) {
                    shakeCount--
                } else {
                    onDismiss()
                }
            }
        }
        onDispose {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager?.unregisterListener(context as? SensorEventListener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Будильник",
                color = Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentTimeText.ifEmpty { "07:00" },
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when (dismissMethod) {
            "MATH" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Решите пример для сброса:", color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$num1 + $num2 = ?", color = PureClockGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = mathInput,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                mathInput = input
                                if (input.toIntOrNull() == correctAnswer) {
                                    onDismiss()
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PureClockGreen, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.width(140.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSnooze,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Отложить (${settingsManager.defaultSnoozeTimeMinutes} мин)", color = Color.White)
                    }
                }
            }

            "SHAKE" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Потрясите телефон!", color = Color.Gray, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Осталось: $shakeCount", color = PureClockGreen, fontSize = 36.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onSnooze,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Отложить (${settingsManager.defaultSnoozeTimeMinutes} мин)", color = Color.White)
                    }
                }
            }

            else -> {
                SwipeToDismissBar(
                    onDismiss = onDismiss,
                    onSnooze = onSnooze,
                    snoozeMinutes = settingsManager.defaultSnoozeTimeMinutes
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissBar(
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    snoozeMinutes: Int
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onDismiss()
            SwipeToDismissBoxValue.EndToStart -> onSnooze()
            SwipeToDismissBoxValue.Settled -> {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = {
                val color = when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> PureClockGreen
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFF2C2C2E)
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, RoundedCornerShape(32.dp))
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                        Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Text(
                        text = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) "Сброс" else "Отложить",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            content = {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Свайп вправо: Сброс", color = PureClockGreen, fontSize = 13.sp)
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray)
                        Text("Свайп влево: Отложить", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Отсрочка сигналов на $snoozeMinutes мин",
            color = Color.DarkGray,
            fontSize = 12.sp
        )
    }
}
package com.example.sci

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sci.auth.AuthViewModel
import com.example.sci.auth.LoginScreen
import com.example.sci.auth.RegisterScreen
import com.example.sci.data.repository.DeviceRepository
import com.example.sci.mqtt.DeviceStateMessage
import com.example.sci.mqtt.PendingDeviceCommand
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UiTab(
    val id: String,
    val name: String,
    val order: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        MqttManager.init(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authViewModel = remember { AuthViewModel() }
    val authState by authViewModel.uiState.collectAsState()
    val deviceRepository = remember { DeviceRepository() }

    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val userId = firebaseUser?.uid
    val homeId = "default_home"

    val startDestination = if (authState.isLoggedIn) NavRoutes.HOME else NavRoutes.LOGIN

    val tabs = remember { mutableStateListOf<UiTab>() }
    val devices = remember { mutableStateListOf<Device>() }

    var isHomeDataLoading by remember { mutableStateOf(false) }

    var tabListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var deviceListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    // MQTT receive handler
    LaunchedEffect(Unit) {
        MqttManager.setOnMessageReceived { topic, payload ->
            scope.launch {
                try {
                    if (topic.endsWith("/state")) {
                        val stateMessage = DeviceStateMessage.fromJson(payload)

                        val index = devices.indexOfFirst { it.id == stateMessage.deviceId }
                        if (index != -1 && stateMessage.success) {
                            devices[index] = devices[index].copy(
                                isOn = stateMessage.power == "ON",
                                params = stateMessage.params
                            )
                        }

                        stateMessage.requestId?.let { requestId ->
                            MqttManager.removePendingCommand(requestId)
                        }
                    }

                    if (topic.endsWith("/online")) {
                        snackbarHostState.showSnackbar("Online topic: $payload")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("MQTT parse error: ${e.message}")
                }
            }
        }

        MqttManager.connect(
            onSuccess = {
                scope.launch {
                    snackbarHostState.showSnackbar("MQTT connected")
                }
            },
            onError = { error ->
                scope.launch {
                    snackbarHostState.showSnackbar("MQTT connect failed: $error")
                }
            }
        )
    }

    // Clean listeners when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            tabListener?.remove()
            deviceListener?.remove()
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(NavRoutes.LOGIN) { inclusive = true }
            }
        }
    }

    // Firestore realtime listeners
    LaunchedEffect(authState.isLoggedIn, userId) {
        if (authState.isLoggedIn && userId != null) {
            isHomeDataLoading = true

            try {
                deviceRepository.ensureDefaultHomeAndTabs(userId, homeId)

                tabListener?.remove()
                deviceListener?.remove()

                tabListener = deviceRepository.listenTabs(
                    userId = userId,
                    homeId = homeId,
                    onChanged = { remoteTabs ->
                        tabs.clear()
                        tabs.addAll(remoteTabs)
                    },
                    onError = { e ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Tabs listener error: ${e.message}")
                        }
                    }
                )

                deviceListener = deviceRepository.listenDevices(
                    userId = userId,
                    homeId = homeId,
                    onChanged = { remoteDevices ->
                        devices.clear()
                        devices.addAll(remoteDevices)

                        MqttManager.connect(
                            onSuccess = {
                                remoteDevices.forEach { device ->
                                    MqttManager.subscribe(device.mqttStateTopic)
                                    MqttManager.subscribe(device.mqttOnlineTopic)
                                }
                            }
                        )
                    },
                    onError = { e ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Devices listener error: ${e.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Load data failed: ${e.message}")
            } finally {
                isHomeDataLoading = false
            }
        } else {
            tabListener?.remove()
            deviceListener?.remove()
            tabListener = null
            deviceListener = null

            tabs.clear()
            devices.clear()
            isHomeDataLoading = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onGoToRegister = {
                    navController.navigate(NavRoutes.REGISTER)
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            RegisterScreen(
                authViewModel = authViewModel,
                onGoToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.HOME) {
            if (isHomeDataLoading) {
                HomeLoadingScreen()
            } else {
                TuyaHomeScreen(
                    tabs = tabs,
                    devices = devices,
                    snackbarHostState = snackbarHostState,
                    onDeviceClick = { device ->
                        navController.navigate(NavRoutes.detailRoute(Uri.encode(device.id)))
                    },
                    onQuickToggle = { device ->
                        if (userId == null) return@TuyaHomeScreen

                        val index = devices.indexOfFirst { it.id == device.id }
                        if (index != -1) {
                            val currentDevice = devices[index]
                            val newState = !currentDevice.isOn

                            devices[index] = currentDevice.copy(isOn = newState)

                            scope.launch {
                                try {
                                    deviceRepository.updateQuickToggle(
                                        userId = userId,
                                        homeId = homeId,
                                        deviceId = currentDevice.id,
                                        isOn = newState
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "Quick toggle DB update failed: ${e.message}"
                                    )
                                }
                            }

                            MqttManager.connect(
                                onSuccess = {
                                    val payload = when (currentDevice.type) {
                                        DeviceType.LIGHT -> {
                                            val distanceMm = getIntParam(currentDevice, "distanceMm")
                                            val timeS = getIntParam(currentDevice, "timeS")
                                            """{"power":"${if (newState) "ON" else "OFF"}","params":{"distanceMm":$distanceMm,"timeS":$timeS}}"""
                                        }

                                        DeviceType.AIR_CONDITIONER -> {
                                            val temperatureC = getIntParam(currentDevice, "temperatureC")
                                            val co2Percent = getIntParam(currentDevice, "co2Percent")
                                            """{"power":"${if (newState) "ON" else "OFF"}","params":{"temperatureC":$temperatureC,"co2Percent":$co2Percent}}"""
                                        }

                                        else -> {
                                            """{"power":"${if (newState) "ON" else "OFF"}"}"""
                                        }
                                    }

                                    MqttManager.publish(
                                        topic = currentDevice.mqttSetTopic,
                                        payload = payload,
                                        onSuccess = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Quick toggle sent to MQTT")
                                            }
                                        },
                                        onError = { error ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Quick toggle failed: $error")
                                            }
                                        }
                                    )
                                },
                                onError = { error ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("MQTT connect error: $error")
                                    }
                                }
                            )
                        }
                    },
                    onAddDevice = { mqttTopic, currentTabId ->
                        if (userId == null) return@TuyaHomeScreen

                        val newDevice = createDeviceFromTopic(
                            topic = mqttTopic,
                            currentTabId = currentTabId,
                            homeId = homeId
                        )

                        val duplicated = devices.any { it.id == newDevice.id }
                        if (duplicated) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Device already exists")
                            }
                        } else {
                            devices.add(newDevice)

                            scope.launch {
                                try {
                                    deviceRepository.addDevice(
                                        userId = userId,
                                        homeId = homeId,
                                        device = newDevice
                                    )

                                    MqttManager.connect(
                                        onSuccess = {
                                            MqttManager.subscribe(newDevice.mqttStateTopic)
                                            MqttManager.subscribe(newDevice.mqttOnlineTopic)
                                        }
                                    )

                                    snackbarHostState.showSnackbar("Device added to database")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Add device failed: ${e.message}")
                                }
                            }
                        }
                    },
                    onDeleteDevice = { device ->
                        if (userId == null) return@TuyaHomeScreen

                        devices.removeAll { it.id == device.id }

                        scope.launch {
                            try {
                                deviceRepository.deleteDevice(
                                    userId = userId,
                                    homeId = homeId,
                                    deviceId = device.id
                                )
                                snackbarHostState.showSnackbar("Device deleted")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Delete failed: ${e.message}")
                            }
                        }
                    },
                    onRenameTab = { tabId, newName ->
                        if (userId == null) return@TuyaHomeScreen

                        val trimmedName = newName.trim()

                        if (trimmedName.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Tab name cannot be empty")
                            }
                        } else if (tabs.any { it.name.equals(trimmedName, ignoreCase = true) && it.id != tabId }) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Tab name already exists")
                            }
                        } else {
                            val index = tabs.indexOfFirst { it.id == tabId }
                            if (index != -1) {
                                val oldTab = tabs[index]
                                tabs[index] = oldTab.copy(name = trimmedName)

                                scope.launch {
                                    try {
                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(userId)
                                            .collection("homes")
                                            .document(homeId)
                                            .collection("tabs")
                                            .document(tabId)
                                            .update("name", trimmedName)
                                            .await()

                                        snackbarHostState.showSnackbar("Renamed tab to $trimmedName")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Rename tab failed: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        composable(
            route = NavRoutes.DETAIL,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val device = devices.find { it.id == deviceId }

            if (device != null) {
                DeviceDetailScreen(
                    device = device,
                    tabName = resolveTabName(tabs, device.tabId),
                    onBack = { navController.popBackStack() },
                    onDelete = {
                        if (userId == null) return@DeviceDetailScreen

                        devices.removeAll { it.id == device.id }
                        navController.popBackStack()

                        scope.launch {
                            try {
                                deviceRepository.deleteDevice(
                                    userId = userId,
                                    homeId = homeId,
                                    deviceId = device.id
                                )
                                snackbarHostState.showSnackbar("Device deleted")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Delete failed: ${e.message}")
                            }
                        }
                    },
                    onSave = { newIsOn, param1, param2 ->
                        val requestId = "req_${System.currentTimeMillis()}"

                        val safeParam1 = param1.toIntOrNull() ?: 0
                        val safeParam2 = param2.toIntOrNull() ?: 0

                        val payload = when (device.type) {
                            DeviceType.LIGHT -> {
                                """{
                                    "requestId":"$requestId",
                                    "power":"${if (newIsOn) "ON" else "OFF"}",
                                    "params":{
                                        "distanceMm":$safeParam1,
                                        "timeS":$safeParam2
                                    }
                                }""".trimIndent()
                            }

                            DeviceType.AIR_CONDITIONER -> {
                                """{
                                    "requestId":"$requestId",
                                    "power":"${if (newIsOn) "ON" else "OFF"}",
                                    "params":{
                                        "temperatureC":$safeParam1,
                                        "co2Percent":$safeParam2
                                    }
                                }""".trimIndent()
                            }

                            else -> {
                                """{
                                    "requestId":"$requestId",
                                    "power":"${if (newIsOn) "ON" else "OFF"}"
                                }""".trimIndent()
                            }
                        }

                        MqttManager.addPendingCommand(
                            PendingDeviceCommand(
                                requestId = requestId,
                                deviceId = device.id
                            )
                        )

                        MqttManager.publish(
                            topic = device.mqttSetTopic,
                            payload = payload,
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Command sent. Waiting for device...")
                                }
                            },
                            onError = { error ->
                                MqttManager.removePendingCommand(requestId)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Publish failed: $error")
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

fun getIntParam(device: Device, key: String, defaultValue: Int = 0): Int {
    val value = device.params[key]
    return when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Float -> value.toInt()
        is String -> value.toIntOrNull() ?: defaultValue
        else -> defaultValue
    }
}

fun defaultParamsByType(type: DeviceType): Map<String, Any> {
    return when (type) {
        DeviceType.LIGHT -> mapOf(
            "distanceMm" to 100,
            "timeS" to 1
        )

        DeviceType.AIR_CONDITIONER -> mapOf(
            "temperatureC" to 26,
            "co2Percent" to 45
        )

        else -> emptyMap()
    }
}

fun detectDeviceTypeFromTopic(topic: String): DeviceType {
    val upperTopic = topic.uppercase()
    return when {
        upperTopic.contains("ABC") -> DeviceType.LIGHT
        upperTopic.contains("DEF") -> DeviceType.AIR_CONDITIONER
        else -> DeviceType.FAN
    }
}

fun displayNameByType(type: DeviceType): String {
    return when (type) {
        DeviceType.LIGHT -> "Incubator"
        DeviceType.AIR_CONDITIONER -> "Rat"
        DeviceType.FAN -> "Fan"
        DeviceType.THERMOSTAT -> "Thermostat"
        DeviceType.AIR_PURIFIER -> "Air purifier"
        DeviceType.SWITCH -> "Switch"
        DeviceType.CURTAIN -> "Curtain"
        DeviceType.SENSOR -> "Sensor"
    }
}

fun createDeviceFromTopic(topic: String, currentTabId: String, homeId: String): Device {
    val type = detectDeviceTypeFromTopic(topic)
    val deviceName = displayNameByType(type)

    val safeDeviceId = topic
        .lowercase()
        .replace("/", "_")
        .replace(" ", "_")
        .replace(Regex("[^a-z0-9_]"), "")
        .take(80)

    return Device(
        id = safeDeviceId,
        name = deviceName,
        tabId = currentTabId,
        isOn = false,
        type = type,
        mqttSetTopic = "homes/$homeId/devices/$safeDeviceId/set",
        mqttStateTopic = "homes/$homeId/devices/$safeDeviceId/state",
        mqttOnlineTopic = "homes/$homeId/devices/$safeDeviceId/online",
        params = defaultParamsByType(type)
    )
}

fun resolveTabName(tabs: List<UiTab>, tabId: String): String {
    return tabs.firstOrNull { it.id == tabId }?.name ?: tabId
}

@Composable
fun HomeLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF243746), Color(0xFF7D8A96))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6F8))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Loading your home...",
                    color = Color(0xFF1F2D3A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please wait a moment",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun TuyaHomeScreen(
    tabs: List<UiTab>,
    devices: List<Device>,
    snackbarHostState: SnackbarHostState,
    onDeviceClick: (Device) -> Unit,
    onQuickToggle: (Device) -> Unit,
    onAddDevice: (String, String) -> Unit,
    onDeleteDevice: (Device) -> Unit,
    onRenameTab: (String, String) -> Unit
) {
    var selectedTabId by remember { mutableStateOf("tab_favorites") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<Device?>(null) }
    var showRenameTabDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty() && tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.sortedBy { it.order }.first().id
        }
    }

    val filteredDevices = devices.filter { it.tabId == selectedTabId }
    val selectedTabName = resolveTabName(tabs, selectedTabId)

    if (showAddDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { topic ->
                onAddDevice(topic, selectedTabId)
                showAddDialog = false
            }
        )
    }

    showDeleteDialogFor?.let { device ->
        DeleteDeviceDialog(
            device = device,
            onDismiss = { showDeleteDialogFor = null },
            onDelete = {
                onDeleteDevice(device)
                showDeleteDialogFor = null
            }
        )
    }

    if (showRenameTabDialog) {
        RenameTabDialog(
            currentName = selectedTabName,
            onDismiss = { showRenameTabDialog = false },
            onRename = { newName ->
                onRenameTab(selectedTabId, newName)
                showRenameTabDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF243746), Color(0xFF7D8A96))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Supports More Devices",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Easily manage all your smart home",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
                )

                HomeContainer(
                    selectedTabId = selectedTabId,
                    selectedTabName = selectedTabName,
                    tabs = tabs,
                    devices = filteredDevices,
                    onTabSelected = { selectedTabId = it },
                    onDeviceClick = onDeviceClick,
                    onQuickToggle = onQuickToggle,
                    onAddClick = { showAddDialog = true },
                    onDeleteClick = { device -> showDeleteDialogFor = device },
                    onRenameTabClick = { showRenameTabDialog = true }
                )
            }
        }
    }
}

@Composable
fun HomeContainer(
    selectedTabId: String,
    selectedTabName: String,
    tabs: List<UiTab>,
    devices: List<Device>,
    onTabSelected: (String) -> Unit,
    onDeviceClick: (Device) -> Unit,
    onQuickToggle: (Device) -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: (Device) -> Unit,
    onRenameTabClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6F8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color(0xFF1F2D3A),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = selectedTabName,
                    color = Color(0xFF1F2D3A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onRenameTabClick) {
                    Text("Rename", color = Color(0xFF1F2D3A))
                }

                IconButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add device",
                        tint = Color(0xFF1F2D3A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                tabs.sortedBy { it.order }.forEach { tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabSelected(tab.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = tab.name,
                            color = if (tab.id == selectedTabId) Color(0xFF1F2D3A) else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = if (tab.id == selectedTabId) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (tab.id == selectedTabId) {
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F2D3A))
                            )
                        } else {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                itemsIndexed(devices) { _, device ->
                    DeviceCard(
                        device = device,
                        tabName = resolveTabName(tabs, device.tabId),
                        onClick = { onDeviceClick(device) },
                        onQuickToggle = { onQuickToggle(device) },
                        onDeleteClick = { onDeleteClick(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: Device,
    tabName: String,
    onClick: () -> Unit,
    onQuickToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val icon = when (device.type) {
        DeviceType.LIGHT -> Icons.Default.Lightbulb
        DeviceType.THERMOSTAT -> Icons.Default.DeviceThermostat
        DeviceType.AIR_PURIFIER -> Icons.Default.Air
        DeviceType.SWITCH -> Icons.Default.Tune
        DeviceType.AIR_CONDITIONER -> Icons.Default.AcUnit
        DeviceType.CURTAIN -> Icons.Default.Window
        DeviceType.FAN -> Icons.Default.Air
        DeviceType.SENSOR -> Icons.Default.Sensors
    }

    val firstLabel: String
    val firstValue: String
    val secondLabel: String
    val secondValue: String

    when (device.type) {
        DeviceType.LIGHT -> {
            firstLabel = "Distance"
            firstValue = "${getIntParam(device, "distanceMm")} mm"
            secondLabel = "Time"
            secondValue = "${getIntParam(device, "timeS")} s"
        }

        DeviceType.AIR_CONDITIONER -> {
            firstLabel = "Temp"
            firstValue = "${getIntParam(device, "temperatureC")} °C"
            secondLabel = "CO2"
            secondValue = "${getIntParam(device, "co2Percent")} %"
        }

        else -> {
            firstLabel = "-"
            firstValue = "-"
            secondLabel = "-"
            secondValue = "-"
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isOn) Color.White else Color(0xFFEEEEEE)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (device.isOn) Color(0xFFEAF2FF) else Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (device.isOn) Color(0xFF4A7DFF) else Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete device",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.clickable { onClick() }) {
                Text(
                    text = device.name,
                    color = Color(0xFF1F2D3A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )

                Text(
                    text = tabName,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = device.mqttSetTopic,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = firstLabel,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    Text(
                        text = firstValue,
                        color = Color(0xFF1F2D3A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = secondLabel,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    Text(
                        text = secondValue,
                        color = Color(0xFF1F2D3A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (device.isOn) "ON" else "OFF",
                    color = if (device.isOn) Color(0xFF24A148) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onQuickToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF24C36B),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFBDBDBD)
                    )
                )
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var topic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add device") },
        text = {
            Column {
                Text(
                    text = "Enter MQTT topic. Current mapping: ABC = Light, DEF = Air conditioner.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("MQTT topic") },
                    placeholder = { Text("Example: homes/default_home/raw/ABC_motor_01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = topic.trim()
                    if (trimmed.isNotEmpty()) onAdd(trimmed)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F2D3A)
                )
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF1F2D3A))
            }
        }
    )
}

@Composable
fun DeleteDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete device") },
        text = { Text("Delete ${device.name}?") },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F2D3A)
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF1F2D3A))
            }
        }
    )
}

@Composable
fun RenameTabDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename tab") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Tab name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) onRename(trimmed)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F2D3A)
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF1F2D3A))
            }
        }
    )
}

@Composable
fun DeviceDetailScreen(
    device: Device,
    tabName: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Boolean, String, String) -> Unit
) {
    var isOn by remember(device.id) { mutableStateOf(device.isOn) }

    val initialParam1 = when (device.type) {
        DeviceType.LIGHT -> getIntParam(device, "distanceMm").toString()
        DeviceType.AIR_CONDITIONER -> getIntParam(device, "temperatureC").toString()
        else -> ""
    }

    val initialParam2 = when (device.type) {
        DeviceType.LIGHT -> getIntParam(device, "timeS").toString()
        DeviceType.AIR_CONDITIONER -> getIntParam(device, "co2Percent").toString()
        else -> ""
    }

    var param1 by remember(device.id) { mutableStateOf(initialParam1) }
    var param2 by remember(device.id) { mutableStateOf(initialParam2) }

    val param1Label = when (device.type) {
        DeviceType.LIGHT -> "Distance (mm)"
        DeviceType.AIR_CONDITIONER -> "Temperature (°C)"
        else -> "Parameter 1"
    }

    val param1Placeholder = when (device.type) {
        DeviceType.LIGHT -> "Example: 120"
        DeviceType.AIR_CONDITIONER -> "Example: 26"
        else -> "Enter value"
    }

    val param2Label = when (device.type) {
        DeviceType.LIGHT -> "Time (s)"
        DeviceType.AIR_CONDITIONER -> "CO2 concentration (%)"
        else -> "Parameter 2"
    }

    val param2Placeholder = when (device.type) {
        DeviceType.LIGHT -> "Example: 3"
        DeviceType.AIR_CONDITIONER -> "Example: 45"
        else -> "Enter value"
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val deviceIcon = when (device.type) {
        DeviceType.LIGHT -> Icons.Default.Lightbulb
        DeviceType.THERMOSTAT -> Icons.Default.DeviceThermostat
        DeviceType.AIR_PURIFIER -> Icons.Default.Air
        DeviceType.SWITCH -> Icons.Default.Tune
        DeviceType.AIR_CONDITIONER -> Icons.Default.AcUnit
        DeviceType.CURTAIN -> Icons.Default.Window
        DeviceType.FAN -> Icons.Default.Air
        DeviceType.SENSOR -> Icons.Default.Sensors
    }

    LaunchedEffect(Unit) {
        MqttManager.connect(
            onSuccess = {},
            onError = { error ->
                scope.launch {
                    snackbarHostState.showSnackbar("MQTT error: $error")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF243746), Color(0xFF7D8A96))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = tabName,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 14.sp
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isOn) Color(0xFFEAF2FF) else Color(0xFFE8E8E8)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = deviceIcon,
                                            contentDescription = null,
                                            tint = if (isOn) Color(0xFF4A7DFF) else Color.DarkGray,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(30.dp))
                                            .background(
                                                if (isOn) Color(0xFFE8F8EE) else Color(0xFFF1F1F1)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isOn) "Online" else "Offline",
                                            color = if (isOn) Color(0xFF24A148) else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(90.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = device.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F2D3A),
                                        maxLines = 1
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "ID: ${device.id}",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = device.mqttSetTopic,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Power",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2D3A)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isOn) "Device is ON" else "Device is OFF",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1F2D3A)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Tap switch to change status",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Switch(
                                        checked = isOn,
                                        onCheckedChange = { isOn = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF24C36B),
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color(0xFFBDBDBD)
                                        )
                                    )
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = param1Label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2D3A)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = param1,
                                    onValueChange = { param1 = it },
                                    label = { Text(param1Label) },
                                    placeholder = { Text(param1Placeholder) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = param2Label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2D3A)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = param2,
                                    onValueChange = { param2 = it },
                                    label = { Text(param2Label) },
                                    placeholder = { Text(param2Placeholder) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSave(isOn, param1, param2)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
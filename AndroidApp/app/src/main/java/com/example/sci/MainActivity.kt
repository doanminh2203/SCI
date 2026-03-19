package com.example.sci

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TuyaHomeScreen()
                }
            }
        }
    }
}

@Composable
fun TuyaHomeScreen() {
    var selectedRoom by remember { mutableStateOf("Living room") }

    val allDevices = remember {
        mutableStateListOf(
            Device("Light", "Living room", true, DeviceType.LIGHT),
            Device("Thermostat", "Bedroom", false, DeviceType.THERMOSTAT),
            Device("Air purifier", "Living room", true, DeviceType.AIR_PURIFIER),
            Device("Switch", "Bedroom", true, DeviceType.SWITCH),
            Device("Air conditioner", "Living room", false, DeviceType.AIR_CONDITIONER),
            Device("Curtain", "Bedroom", true, DeviceType.CURTAIN),
            Device("Fan", "Living room", false, DeviceType.FAN),
            Device("Temp sensor", "Kitchen", true, DeviceType.SENSOR)
        )
    }

    val roomTabs = listOf("Favorites", "Living room", "Bedroom")

    val filteredDevices = when (selectedRoom) {
        "Favorites" -> allDevices.filter { it.isOn }
        else -> allDevices.filter { it.room == selectedRoom }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF243746),
                        Color(0xFF7D8A96)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                selectedRoom = selectedRoom,
                roomTabs = roomTabs,
                devices = filteredDevices,
                onTabSelected = { selectedRoom = it },
                onToggleDevice = { indexInFiltered ->
                    val deviceToChange = filteredDevices[indexInFiltered]
                    val realIndex = allDevices.indexOf(deviceToChange)
                    if (realIndex != -1) {
                        allDevices[realIndex] = allDevices[realIndex].copy(
                            isOn = !allDevices[realIndex].isOn
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun HomeContainer(
    selectedRoom: String,
    roomTabs: List<String>,
    devices: List<Device>,
    onTabSelected: (String) -> Unit,
    onToggleDevice: (Int) -> Unit
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
                    text = "My home",
                    color = Color(0xFF1F2D3A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A7DFF))
                )

                Text(
                    text = "+",
                    color = Color(0xFF1F2D3A),
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                roomTabs.forEach { tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onTabSelected(tab) }
                    ) {
                        Text(
                            text = tab,
                            color = if (tab == selectedRoom) Color(0xFF1F2D3A) else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = if (tab == selectedRoom) FontWeight.SemiBold else FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (tab == selectedRoom) {
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
                items(devices.size) { index ->
                    DeviceCard(
                        device = devices[index],
                        onToggle = { onToggleDevice(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onToggle: () -> Unit
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

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isOn) Color.White else Color(0xFFEEEEEE)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = device.name,
                color = Color(0xFF1F2D3A),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Text(
                text = device.room,
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onToggle() },
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
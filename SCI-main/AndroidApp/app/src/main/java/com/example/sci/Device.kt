package com.example.sci

data class Device(
    // id này phải trùng đúng document ID trong Firestore
    val id: String,

    val name: String,

    // tabId cũng nên trùng document ID của tab trong Firestore
    val tabId: String,

    val isOn: Boolean,
    val type: DeviceType,

    // Giữ tên field trong app giống Firestore để khỏi mapping lộn xộn
    val distanceMm: Int = 0,
    val timeS: Int = 0,

    val mqttSetTopic: String = "",
    val mqttStateTopic: String = "",
    val mqttOnlineTopic: String = ""
)

enum class DeviceType {
    LIGHT,
    THERMOSTAT,
    AIR_PURIFIER,
    SWITCH,
    AIR_CONDITIONER,
    CURTAIN,
    FAN,
    SENSOR
}
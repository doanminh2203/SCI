package com.example.sci

data class Device(
    val id: String,
    val name: String,
    val tabId: String,
    val isOn: Boolean,
    val type: DeviceType,
    val mqttSetTopic: String = "",
    val mqttStateTopic: String = "",
    val mqttOnlineTopic: String = "",
    val params: Map<String, Any> = emptyMap()
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
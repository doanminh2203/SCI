package com.example.sci
data class Device(
    val name: String,
    val room: String,
    val isOn: Boolean,
    val type: DeviceType
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
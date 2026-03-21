package com.example.sci.mqtt

data class PendingDeviceCommand(
    val requestId: String,
    val deviceId: String
)
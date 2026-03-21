package com.example.sci.mqtt

import org.json.JSONObject

data class DeviceStateMessage(
    val requestId: String?,
    val deviceId: String,
    val success: Boolean,
    val power: String,
    val params: Map<String, Any>,
    val source: String,
    val message: String
) {
    companion object {
        fun fromJson(json: String): DeviceStateMessage {
            val obj = JSONObject(json)

            val paramsObj = obj.optJSONObject("params")
            val paramsMap = mutableMapOf<String, Any>()

            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    paramsMap[key] = paramsObj.get(key)
                }
            }

            return DeviceStateMessage(
                requestId = if (obj.has("requestId")) obj.optString("requestId") else null,
                deviceId = obj.optString("deviceId"),
                success = obj.optBoolean("success", false),
                power = obj.optString("power"),
                params = paramsMap,
                source = obj.optString("source"),
                message = obj.optString("message")
            )
        }
    }
}
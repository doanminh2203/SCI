package com.example.sci.data.repository

import com.example.sci.Device
import com.example.sci.DeviceType
import com.example.sci.UiTab
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeviceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun addDevice(
        homeId: String,
        device: Device
    ) {
        val data = mapOf(
            "name" to device.name,
            "type" to device.type.name,
            "tabId" to device.tabId,
            "mqttSetTopic" to device.mqttSetTopic,
            "mqttStateTopic" to device.mqttStateTopic,
            "mqttOnlineTopic" to device.mqttOnlineTopic,
            "distanceMm" to device.distanceMm,
            "timeS" to device.timeS,
            "isOn" to device.isOn,
            "lastSeen" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("homes")
            .document(homeId)
            .collection("devices")
            .document(device.id)
            .set(data)
            .await()
    }

    suspend fun updateDeviceState(
        homeId: String,
        deviceId: String,
        isOn: Boolean,
        distanceMm: Int,
        timeS: Int
    ) {
        db.collection("homes")
            .document(homeId)
            .collection("devices")
            .document(deviceId)
            .update(
                mapOf(
                    "isOn" to isOn,
                    "distanceMm" to distanceMm,
                    "timeS" to timeS,
                    "lastSeen" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun updateQuickToggle(
        homeId: String,
        deviceId: String,
        isOn: Boolean
    ) {
        db.collection("homes")
            .document(homeId)
            .collection("devices")
            .document(deviceId)
            .update(
                mapOf(
                    "isOn" to isOn,
                    "lastSeen" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun deleteDevice(
        homeId: String,
        deviceId: String
    ) {
        db.collection("homes")
            .document(homeId)
            .collection("devices")
            .document(deviceId)
            .delete()
            .await()
    }

    // Đọc toàn bộ tabs từ Firestore
    suspend fun loadTabs(homeId: String): List<UiTab> {
        val snapshot = db.collection("homes")
            .document(homeId)
            .collection("tabs")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val orderLong = doc.getLong("order") ?: 0L

            UiTab(
                id = doc.id,
                name = name,
                order = orderLong.toInt()
            )
        }.sortedBy { it.order }
    }

    // Đọc toàn bộ devices từ Firestore
    suspend fun loadDevices(homeId: String): List<Device> {
        val snapshot = db.collection("homes")
            .document(homeId)
            .collection("devices")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val typeString = doc.getString("type") ?: return@mapNotNull null
            val tabId = doc.getString("tabId") ?: return@mapNotNull null

            val type = try {
                DeviceType.valueOf(typeString)
            } catch (_: Exception) {
                return@mapNotNull null
            }

            Device(
                id = doc.id,
                name = name,
                tabId = tabId,
                isOn = doc.getBoolean("isOn") ?: false,
                type = type,
                distanceMm = (doc.getLong("distanceMm") ?: 0L).toInt(),
                timeS = (doc.getLong("timeS") ?: 0L).toInt(),
                mqttSetTopic = doc.getString("mqttSetTopic") ?: "",
                mqttStateTopic = doc.getString("mqttStateTopic") ?: "",
                mqttOnlineTopic = doc.getString("mqttOnlineTopic") ?: ""
            )
        }
    }
}
package com.example.sci.data.repository

import com.example.sci.Device
import com.example.sci.DeviceType
import com.example.sci.UiTab
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeviceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun homeDoc(userId: String, homeId: String) =
        db.collection("users")
            .document(userId)
            .collection("homes")
            .document(homeId)

    suspend fun loadTabs(userId: String, homeId: String): List<UiTab> {
        val snapshot = homeDoc(userId, homeId)
            .collection("tabs")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val order = (doc.getLong("order") ?: 0L).toInt()

            UiTab(
                id = doc.id,
                name = name,
                order = order
            )
        }.sortedBy { it.order }
    }

    suspend fun loadDevices(userId: String, homeId: String): List<Device> {
        val snapshot = homeDoc(userId, homeId)
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

    suspend fun addDevice(userId: String, homeId: String, device: Device) {
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

        homeDoc(userId, homeId)
            .collection("devices")
            .document(device.id)
            .set(data)
            .await()
    }

    suspend fun updateDeviceState(
        userId: String,
        homeId: String,
        deviceId: String,
        isOn: Boolean,
        distanceMm: Int,
        timeS: Int
    ) {
        homeDoc(userId, homeId)
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
        userId: String,
        homeId: String,
        deviceId: String,
        isOn: Boolean
    ) {
        homeDoc(userId, homeId)
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
        userId: String,
        homeId: String,
        deviceId: String
    ) {
        homeDoc(userId, homeId)
            .collection("devices")
            .document(deviceId)
            .delete()
            .await()
    }

    suspend fun ensureDefaultHomeAndTabs(userId: String, homeId: String) {
        val homeRef = homeDoc(userId, homeId)
        val homeSnapshot = homeRef.get().await()

        if (!homeSnapshot.exists()) {
            homeRef.set(
                mapOf(
                    "name" to "My Home",
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        }

        val tabsSnapshot = homeRef.collection("tabs").get().await()
        if (tabsSnapshot.isEmpty) {
            val defaultTabs = listOf(
                UiTab("tab_favorites", "Favorites", 1),
                UiTab("tab_living_room", "Living room", 2),
                UiTab("tab_bedroom", "Bedroom", 3)
            )

            for (tab in defaultTabs) {
                homeRef.collection("tabs")
                    .document(tab.id)
                    .set(
                        mapOf(
                            "name" to tab.name,
                            "order" to tab.order
                        )
                    )
                    .await()
            }
        }
    }
}
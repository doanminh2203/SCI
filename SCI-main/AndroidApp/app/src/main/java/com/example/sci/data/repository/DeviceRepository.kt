package com.example.sci.data.repository

import com.example.sci.Device
import com.example.sci.DeviceType
import com.example.sci.UiTab
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
class DeviceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun homeDoc(userId: String, homeId: String) =
        db.collection("users")
            .document(userId)
            .collection("homes")
            .document(homeId)

    fun listenDevices(
        userId: String,
        homeId: String,
        onChanged: (List<Device>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return homeDoc(userId, homeId)
            .collection("devices")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onChanged(emptyList())
                    return@addSnapshotListener
                }

                val deviceList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val typeString = doc.getString("type") ?: return@mapNotNull null
                    val tabId = doc.getString("tabId") ?: return@mapNotNull null

                    val type = try {
                        DeviceType.valueOf(typeString)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }

                    val params = doc.get("params") as? Map<String, Any> ?: emptyMap()

                    Device(
                        id = doc.id,
                        name = name,
                        tabId = tabId,
                        isOn = doc.getBoolean("isOn") ?: false,
                        type = type,
                        mqttSetTopic = doc.getString("mqttSetTopic") ?: "",
                        mqttStateTopic = doc.getString("mqttStateTopic") ?: "",
                        mqttOnlineTopic = doc.getString("mqttOnlineTopic") ?: "",
                        params = params
                    )
                }

                onChanged(deviceList)
            }
    }
    fun listenTabs(
        userId: String,
        homeId: String,
        onChanged: (List<UiTab>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return homeDoc(userId, homeId)
            .collection("tabs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onChanged(emptyList())
                    return@addSnapshotListener
                }

                val tabList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val order = (doc.getLong("order") ?: 0L).toInt()

                    UiTab(
                        id = doc.id,
                        name = name,
                        order = order
                    )
                }.sortedBy { it.order }

                onChanged(tabList)
            }
    }
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

            val params = doc.get("params") as? Map<String, Any> ?: emptyMap()

            Device(
                id = doc.id,
                name = name,
                tabId = tabId,
                isOn = doc.getBoolean("isOn") ?: false,
                type = type,
                mqttSetTopic = doc.getString("mqttSetTopic") ?: "",
                mqttStateTopic = doc.getString("mqttStateTopic") ?: "",
                mqttOnlineTopic = doc.getString("mqttOnlineTopic") ?: "",
                params = params
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
            "isOn" to device.isOn,
            "params" to device.params,

            "ownerUserId" to userId,
            "homeId" to homeId,


            "lastSeen" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis()
        )

        homeDoc(userId, homeId)
            .collection("devices")
            .document(device.id)
            .set(data)
            .await()
    }

    suspend fun updateDevice(
        userId: String,
        homeId: String,
        deviceId: String,
        isOn: Boolean,
        params: Map<String, Any>
    ) {
        homeDoc(userId, homeId)
            .collection("devices")
            .document(deviceId)
            .update(
                mapOf(
                    "isOn" to isOn,
                    "params" to params,
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
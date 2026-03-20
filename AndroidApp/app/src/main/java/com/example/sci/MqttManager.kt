package com.example.sci
import android.util.Log
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

object MqttManager {

    private const val TAG = "MqttManager"

    // Đổi thông tin broker tại đây
    private const val SERVER_URI = "tcp://broker.hivemq.com:1883"
    private const val CLIENT_ID = "android_esp32_client_demo"

    private var client: MqttAsyncClient? = null

    fun connect(
        onConnected: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            if (client == null) {
                client = MqttAsyncClient(SERVER_URI, CLIENT_ID + System.currentTimeMillis())
            }

            if (client?.isConnected == true) {
                onConnected?.invoke()
                return
            }

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
            }

            client?.connect(options)?.waitForCompletion()
            onConnected?.invoke()
            Log.d(TAG, "Connected to MQTT broker")
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT connect error", e)
            onError?.invoke(e.message ?: "MQTT connection failed")
        }
    }

    fun publish(
        topic: String,
        payload: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            if (client?.isConnected != true) {
                onError?.invoke("MQTT is not connected")
                return
            }

            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                isRetained = false
            }

            client?.publish(topic, message)
            onSuccess?.invoke()
            Log.d(TAG, "Published: $payload to $topic")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT publish error", e)
            onError?.invoke(e.message ?: "MQTT publish failed")
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
    }
}
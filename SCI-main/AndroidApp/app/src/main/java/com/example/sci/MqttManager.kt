package com.example.sci

import android.content.Context
import android.util.Log

import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

import info.mqtt.android.service.MqttAndroidClient

object MqttManager {

    private const val TAG = "MqttManager"

    // TODO: Thay bằng thông tin broker thật của bạn
    private const val SERVER_URI = "ssl://yf0477b4.ala.us-east-1.emqxsl.com:8883"
    private const val USERNAME = "abc"
    private const val PASSWORD = "123"

    private var mqttClient: MqttAndroidClient? = null
    private var onMessageReceived: ((topic: String, payload: String) -> Unit)? = null

    fun init(context: Context) {
        if (mqttClient != null) return

        val clientId = "android-client-" + System.currentTimeMillis()
        mqttClient = MqttAndroidClient(context.applicationContext, SERVER_URI, clientId)

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "MQTT connected: $serverURI reconnect=$reconnect")
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e(TAG, "MQTT connection lost", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val safeTopic = topic ?: return
                val payload = message?.toString() ?: ""
                Log.d(TAG, "Message arrived: $safeTopic -> $payload")
                onMessageReceived?.invoke(safeTopic, payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d(TAG, "MQTT delivery complete")
            }
        })
    }

    fun setOnMessageReceived(listener: (topic: String, payload: String) -> Unit) {
        onMessageReceived = listener
    }

    fun connect(
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val client = mqttClient ?: run {
            onError?.invoke("MQTT client not initialized")
            return
        }

        if (client.isConnected) {
            onSuccess?.invoke()
            return
        }

        val options = MqttConnectOptions().apply {
            userName = USERNAME
            password = PASSWORD.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
        }

        try {
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT connect success")
                    onSuccess?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT connect failed", exception)
                    onError?.invoke(exception?.message ?: "MQTT connect failed")
                }
            })
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "MQTT connect exception")
        }
    }

    fun subscribe(
        topic: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val client = mqttClient ?: run {
            onError?.invoke("MQTT client not initialized")
            return
        }

        if (!client.isConnected) {
            onError?.invoke("MQTT is not connected")
            return
        }

        try {
            client.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed: $topic")
                    onSuccess?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Subscribe failed: $topic", exception)
                    onError?.invoke(exception?.message ?: "Subscribe failed")
                }
            })
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Subscribe exception")
        }
    }

    fun publish(
        topic: String,
        payload: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val client = mqttClient ?: run {
            onError?.invoke("MQTT client not initialized")
            return
        }

        if (!client.isConnected) {
            onError?.invoke("MQTT is not connected")
            return
        }

        try {
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                isRetained = false
            }

            client.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Publish success: $topic -> $payload")
                    onSuccess?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Publish failed", exception)
                    onError?.invoke(exception?.message ?: "Publish failed")
                }
            })
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Publish exception")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.unregisterResources()
            mqttClient?.close()
            mqttClient = null
        } catch (_: Exception) {
        }
    }
}
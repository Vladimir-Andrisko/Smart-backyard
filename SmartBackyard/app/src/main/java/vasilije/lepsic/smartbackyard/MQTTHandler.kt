package vasilije.lepsic.smartbackyard

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttTopic
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MQTTHandler {
    companion object MQTTHandler {

        fun mainLoop() {
            if (!isConnected())
                connect()

            //Keep alive signal
            val msg = MQTTFactory.createMessage("uuid:1::app", keepAliveQOS)
            msg.qos = keepAliveQOS
            publish("garden/app/alive", msg)
        }

        fun connect() : Boolean {
            if (isConnected())
                disconnect()

            return try {
                mqttClient = MqttClient("tcp://$ipAddress:1883", getClientId(), persistence)
                getOptions().isCleanSession = true
                mqttClient!!.connect(options)
                subscribe("garden/global/actuator/roof/status")
                subscribe("garden/global/sensor/reservoir")
                subscribe("garden/global/sensor/humidity")
                subscribe("garden/global/sensor/luminosity")
                for (i in 1..10) {
                    subscribe("garden/row${i}/sensor")
                    subscribe("garden/row${i}/actuator")
                }
                true
            } catch  (e : MqttException) {
                false
            }
        }

        fun grabSavedIp(context : Context) : String {
            val sharedPreferences = context.getSharedPreferences("SmartBackyardPrefs", Context.MODE_PRIVATE)
            return sharedPreferences.getString("ip_address", "vlada.local")!!
        }

        fun disconnect() {
            mqttClient?.disconnect()
            mqttClient = null
        }

        fun getOptions() : MqttConnectOptions {
            return options
        }

        fun getIpAddress() : String {
            return ipAddress
        }

        fun getClientId() : String {
            return clientId
        }

        fun setIpAddress(ipAddress : String) {
            this.ipAddress = ipAddress
        }

        fun setClientId(clientId : String) {
            this.clientId = clientId
        }

        fun setCallback(callback : MqttCallback) {
            if (isConnected())
                mqttClient?.setCallback(callback)
        }

        fun clearCallback() {
            if (mqttClient != null)
                mqttClient?.setCallback(null)
        }

        fun isConnected() : Boolean {
            return mqttClient != null && mqttClient!!.isConnected
        }

        fun publish(topic : String, message : MqttMessage) {
            if (isConnected())
                mqttClient?.publish(topic, message)
        }

        fun subscribe(topic : String) {
            mqttClient?.subscribe(topic)
        }

        fun unsubscribe(topic : String) {
            mqttClient?.unsubscribe(topic)
        }

        fun getTopicObject(topic : String) : MqttTopic? {
            return mqttClient?.getTopic(topic)
        }

        private var mqttClient : MqttClient? = null
        private var options = MqttConnectOptions()
        private var ipAddress = ""
        private var clientId = ""
        private val persistence = MemoryPersistence()

        const val keepAliveQOS = 0
        const val valveQOS = 2
        const val sensorQOS = 1
        const val globalSensorQOS = 0
        const val roofQOS = 1
    }
}
package vasilije.lepsic.smartbackyard

import android.content.Context
import android.util.Log
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

            publish("test/t1", "Test alive message")
        }

        fun connect() : Boolean {
            if (isConnected())
                disconnect()

            return try {
                mqttClient = MqttClient("tcp://$ipAddress:1883", getClientId(), persistence)
                getOptions().isCleanSession = true
                mqttClient!!.connect(options)
                true
            } catch  (e : MqttException) {
                Log.d("MQTT Handler", "MQTT handler exception: ${e.cause?.toString()}")
                false
            }
        }

        fun grabSavedIp(context : Context) : String {
            val sharedPreferences = context.getSharedPreferences("SmartBackyardPrefs", Context.MODE_PRIVATE)
            return sharedPreferences.getString("ip_address", "192.168.100.39")!!
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

        fun getQOS() : Int {
            return qos
        }

        fun setIpAddress(ipAddress : String) {
            this.ipAddress = ipAddress
        }

        fun setClientId(clientId : String) {
            this.clientId = clientId
        }

        fun setQOS(qos : Int) {
            this.qos = qos
        }

        fun isConnected() : Boolean {
            return mqttClient != null && mqttClient!!.isConnected
        }

        fun publish(topic : String, message : String) {
            mqttClient?.publish(topic, MqttMessage(message.encodeToByteArray()))
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
        private var qos = 2
        private val persistence = MemoryPersistence()
    }
}
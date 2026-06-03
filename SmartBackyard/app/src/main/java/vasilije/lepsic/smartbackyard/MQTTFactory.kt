package vasilije.lepsic.smartbackyard

import org.eclipse.paho.client.mqttv3.MqttMessage

class MQTTFactory {
    companion object {
        fun createMessage(message: String, qos: Int): MqttMessage {
            val msg = MqttMessage(message.encodeToByteArray())
            msg.qos = qos
            return msg
        }
    }
}
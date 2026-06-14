package vasilije.lepsic.smartbackyard

import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

object MQTTFactory {
    fun createMessage(message: String, qos: Int): MqttMessage {
        val msg = MqttMessage(message.encodeToByteArray())
        msg.qos = qos
        return msg
    }

    // SET komanda
    fun createSetMessage(
        uuid: String,
        group: String,
        parameter: String,
        value: String,
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "SET")
            put("uuid", uuid)
            put("group", group)
            put("Service", JSONObject().apply {
                put(parameter, value)
            })
        }
        return createMessage(json.toString(), qos)
    }



    // GET komanda
    fun createGetMessage(
        jsonType: String, // "state" ili "info"
        device: String,   // "*" ili devId
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "GET")
            put("json", jsonType)
            put("device", device)
        }
        return createMessage(json.toString(), qos)
    }

    // Parsiranje SET komande
    fun parseSetMessage(payload: String): SetCommand? {
        return try {
            val json = JSONObject(payload)
            if (json.optString("command_type") != "SET") return null
            SetCommand(
                group = json.getString("group"),
                device = json.getString("device"),
                service = json.getString("service"),
                parameter = json.getString("parameter"),
                value = json.getString("value")
            )
        } catch (_: org.json.JSONException) {
            null
        }
    }

    // Parsiranje GET komande
    fun parseGetMessage(payload: String): GetCommand? {
        return try {
            val json = JSONObject(payload)
            val service = json.getJSONObject("Service")
            if (json.optString("command_type") != "GET") return null
            GetCommand(
                uuid = json.getString("uuid"),
                group = json.getString("group"),
                service = service.keys().asSequence().associateWith { service.get(it) }
            )
        } catch (_: org.json.JSONException) {
            null
        }
    }

    // Generičko parsiranje - vrati command_type da klijent odluči dalje
    fun getCommandType(payload: String): String? {
        return try {
            JSONObject(payload).optString("command_type", "")
        } catch (_: org.json.JSONException) {
            null
        }
    }
}

data class SetCommand(
    val group: String,
    val device: String,
    val service: String,
    val parameter: String,
    val value: String
)

data class GetCommand(
    val uuid: String,
    val group: String,
    val service: Map<String, Any>
)
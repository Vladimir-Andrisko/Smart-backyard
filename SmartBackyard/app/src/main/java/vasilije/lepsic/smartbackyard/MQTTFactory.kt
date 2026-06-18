package vasilije.lepsic.smartbackyard

import android.util.Log
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

    fun createSetRowsMessage(
        rows: List<RowCommandEntry>
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "SET.garden_rows")
            put("control", JSONObject().apply {
                for (i in rows.indices) {
                    put("row${i + 1}", JSONObject().apply {
                        put("max_moisture", rows[i].max_moisture)
                        put("min_moisture", rows[i].min_moisture)
                        put("max_water", rows[i].max_water)
                        put("min_pause", rows[i].min_pause)
                    })
                }
            })
        }

        return createMessage(json.toString(), MQTTHandler.configQOS)
    }



    // GET komanda
    fun createGetMessage(
        uuid: String,   // "*" ili devId
        group: String,
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "GET")
            put("group", group)
            put("uuid", uuid)
        }
        return createMessage(json.toString(), qos)
    }

    fun createGetAllMessage(
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "GET.all")
        }
        return createMessage(json.toString(), qos)
    }

    fun createAliveMessage(
        uuid: String,
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("uuid", uuid)
        }

        return createMessage(json.toString(), qos)
    }

    fun createSetAutomaticMessage(
        enabled: Boolean,
        qos: Int
    ): MqttMessage {
        val json = JSONObject().apply {
            put("command_type", "SET.automatic")
            put("Control", if (enabled) "ON" else "OFF")
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
    fun parseGetMessage(payload: String): Any? {
        return try {
            val json = JSONObject(payload)
            val command_type = json.getString("command_type")
            if (command_type != "GET.all") {
                val service = json.getJSONObject("Service")
                GetCommand(
                    uuid = json.getString("uuid"),
                    group = json.getString("group"),
                    command_type = command_type,
                    service = service.keys().asSequence().associateWith { service.get(it) }
                )
            }

            val m : MutableMap<String, Any> = mutableMapOf()
            for (key in json.keys()) {
                if (key == "command_type")
                    continue

                val obj = json.getJSONObject(key)
                m[key] = obj
            }

            GetAllCommand(uuid = m)
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
    val command_type: String,
    val service: Map<String, Any>
)

data class GetAllCommand(
    val uuid: Map<String, Any>
)

data class RowCommandEntry(
    val max_moisture: Float,
    val min_moisture: Float,
    val max_water: Int,
    val min_pause: Int
)
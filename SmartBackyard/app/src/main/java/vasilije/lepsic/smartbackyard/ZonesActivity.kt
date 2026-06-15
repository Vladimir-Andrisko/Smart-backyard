package vasilije.lepsic.smartbackyard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage

class ZonesActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var containerRows: LinearLayout

    private lateinit var tvGlobalTemp: TextView
    private lateinit var tvGlobalHumidity: TextView
    private lateinit var pbWaterLevel: ProgressBar
    private lateinit var tvSoilType: TextView

    private lateinit var tvSunIntensity: TextView
    private lateinit var tvRoofStatus: TextView
    private lateinit var btnRoofAction: Button

    private lateinit var baza: AppDatabase
    private lateinit var redKontrola : LinearLayout
    private var isRoofOpened = false
    private lateinit var tvAirTemperature: TextView

    private val rowSensorRegex = Regex("""uuid:(10|[1-9])::row_sensor""")
    private val rowActuatorRegex = Regex("""uuid:(10|[1-9])::row_actuator""")
    private val rowRegex = Regex("""row(10|[1-9])""")

    private val getRequestDelay : Long = 15000

    /*private val rowRegexSensor = Regex("""garden/row(10|[1-9])/sensor""")
    private val rowRegexActuator = Regex("""garden/row(10|[1-9])/actuator""")*/

    fun azurirajUITemperatureVazduha(temp: Int) {
        tvAirTemperature.text = getString(R.string.degree_format, temp)
        tvAirTemperature.setTextColor(
            getColor(
                when {
                    temp > 45 -> R.color.dark_red
                    temp > 35 -> R.color.red
                    temp > 27 -> R.color.orange
                    temp > 20 -> R.color.yellow
                    temp > 10 -> R.color.green
                    temp > 0 -> R.color.light_green
                    else -> R.color.light_blue
                }
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            db.globalStatusDao().setRoofStatus("UNINITIALIZED")
        }
    }

    fun updateUI() {
        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            val roofStatus = db.globalStatusDao().getRoofStatus()
            if (roofStatus == "UNINITIALIZED")
                btnRoofAction.visibility = View.INVISIBLE

            withContext(Dispatchers.Main) {

                azurirajUIKrova(roofStatus == "OPEN")
                azurirajNivoVode(db.globalStatusDao().getWaterLevel())
                azurirajUIZaSunce(db.globalStatusDao().getLuminosity())
                azurirajUITemperatureVazduha(db.globalStatusDao().getAirTemperature())
                azurirajGlobalnaVlaznostVazduha(db.globalStatusDao().getHumidity())

                val redovi = db.backyardDao().getAllRedovi()
                val redoviStatus = db.backyardDao().getAllRedoviStatus()

                for (i in redovi.indices) {
                    val red = redovi[i]
                    val redStatus = redoviStatus[i]
                    azurirajStatusDugmetaNaKartici(red.redId, redStatus.open)
                    azurirajVlaguZemljistaNaKartici(red.redId, redStatus.soilMoisture)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zones)
        //ZonesActivityInstanceHolder.setZonesActivity(this)

        baza = AppDatabase.getInstance(this)
        tvAirTemperature = findViewById(R.id.airTemperature)

        btnBack = findViewById(R.id.btnBack)
        containerRows = findViewById(R.id.containerRows)
        tvGlobalTemp = findViewById(R.id.tvGlobalTemp)
        tvGlobalHumidity = findViewById(R.id.tvGlobalHumidity)
        pbWaterLevel = findViewById(R.id.pbWaterLevel)
        tvSoilType = findViewById(R.id.tvSoilType)
        tvSunIntensity = findViewById(R.id.tvSunIntensity)
        tvRoofStatus = findViewById(R.id.tvRoofStatus)
        btnRoofAction = findViewById(R.id.btnRoofAction)

        btnBack.setOnClickListener { finish() }

        // Nivo vode: default 0% dok ne stigne MQTT
        pbWaterLevel.progress = 0
        azurirajNivoVode(0)

        // Globalna vlažnost vazduha: default 0% dok ne stigne MQTT
        azurirajGlobalnaVlaznostVazduha(0)

        // Učitaj tip zemljišta iz SharedPreferences
        val prefs = getSharedPreferences("BastaPrefs", Context.MODE_PRIVATE)
        val tipZemljista = prefs.getString("TIP_ZEMLJISTA", null)
        if (tipZemljista != null) {
            tvGlobalTemp.text = "Tip zemljišta: $tipZemljista"
        }

        btnRoofAction.visibility = View.INVISIBLE

        btnRoofAction.setOnClickListener {
            posaljiKrovuMqttKomandu()
        }

        // Učitaj redove iz baze i napravi kartice
        ucitajIPrivkazujRedove()

        val db = AppDatabase.getInstance(this)

        MQTTHandler.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {

            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null)
                    return

                Log.d("MQTTtest", message.toString())

                // basta/global/senzor/kolicinaSvetlosti  → azurirajUIZaSunce(Int)
                // basta/global/aktuator/krov/status      → azurirajUIKrova(Boolean)
                // basta/global/senzor/rezervoar          → azurirajNivoVode(Int)
                // basta/global/senzor/vlaznostVazduha    → AzureWaveGlobalnaVlaznostVazduha(Int)
                // basta/red{id}/senzor/vlaga             → azurirajVlaguZemljistaNaKartici(redId, Int)
                // basta/red{id}/aktuator/ventil/status   → ažurira tvStatus na kartici

                val json = MQTTFactory.parseGetMessage(String(message.payload, Charsets.UTF_8))
                if (json == null) {
                    Log.d("MQTT", "GET callback failed")
                    return
                }

                /*if (json.uuid == "uuid:1::roof_actuator") {
                    val state = json.service["State"] as String
                    lifecycleScope.launch {
                        db.globalStatusDao().setRoofStatus(state)
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "KROV", state)
                    }

                    return
                }

                if (json.uuid == "uuid:1::light_sensor") {
                    val state = json.service["Luminosity"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setLuminosity(state)
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "JACINA SVETLOSTI", state.toString())
                    }

                    return
                }

                if (json.uuid == "uuid:1::temperature_sensor") {
                    val state = json.service["Temperature"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setAirTemperature(state)
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "TEMPERATURA VAZDUHA", state.toString())
                    }

                    return
                }

                // TODO
                if (json.uuid == "WATER_LEVEL") {
                    val state = json.service["WaterLevel"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setWaterLevel(state)
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "NIVO VODE", state.toString())
                    }
                }

                val sensorMatch = rowSensorRegex.matchEntire(json.uuid)
                if (sensorMatch != null) {
                    val rowMatch = rowRegex.matchEntire(json.group) ?: return
                    val state = json.service["Humidity"] as Int

                    lifecycleScope.launch {
                        db.backyardDao().setRedMoisture(rowMatch.groupValues[1].toInt(), state)
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "SENZOR", state.toString())
                    }

                    return
                }

                val actuatorMatch = rowActuatorRegex.matchEntire(json.uuid)
                if (actuatorMatch != null) {
                    val rowMatch = rowRegex.matchEntire(json.group) ?: return
                    val state = json.service["State"] as String

                    lifecycleScope.launch {
                        db.backyardDao().setRedStatus(rowMatch.groupValues[1].toInt(), state == "OPEN")
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "VENTIL", state)
                    }

                    return
                }*/

                val state = json.service["State"]
                if (state != "ON") {
                    Toast.makeText(this@ZonesActivity, "device ${json.uuid} is $state", Toast.LENGTH_SHORT).show()
                    return
                }

                if (json.uuid == "uuid:1::humidity_sensor") {
                    try {
                        val humidity = json.service["Humidity"] as Int
                        lifecycleScope.launch {
                            db.globalStatusDao().setHumidity(humidity)
                        }
                    }
                    catch(_ : java.lang.ClassCastException) {}
                }

                if (json.uuid == "uuid:1::temperature_sensor") {
                    try {
                        val temperature = json.service["Temperature"] as Int
                        lifecycleScope.launch {
                            db.globalStatusDao()
                                .setAirTemperature(temperature)
                        }
                    }
                    catch(_ : java.lang.ClassCastException) {}
                }

                if (json.uuid == "uuid:1::light_sensor") {
                    try {
                        val luminosity = json.service["Intensity"] as Int
                        lifecycleScope.launch {
                            db.globalStatusDao().setLuminosity(luminosity)
                        }
                    }
                    catch(_ : java.lang.ClassCastException) {}
                }

                if (json.uuid == "uuid:1::roof_actuator") {
                    try {
                        lifecycleScope.launch {
                            val str = json.service["Position"] as String
                            isRoofOpened = str == "OPEN"

                            db.globalStatusDao().setRoofStatus(str)

                            withContext(Dispatchers.Main) {
                                btnRoofAction.visibility = View.VISIBLE
                            }
                        }
                    }
                    catch(_ : java.lang.ClassCastException) {}
                }

                updateUI()
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })

        lifecycleScope.launch {
            while(true) {
                delay(getRequestDelay)
                if (isDestroyed || isFinishing)
                    break
                /*MQTTHandler.publish(MQTTHandler.publishTopic, MQTTFactory.createSetMessage(
                "uuid:${red.redId}::row_actuator", "row${red.redId}", "State", "OPEN", MQTTHandler.valveQOS))*/
                MQTTHandler.publish(
                    MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                        "uuid:1::roof_actuator", "global", MQTTHandler.roofQOS
                    )
                )
                MQTTHandler.publish(
                    MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                        "uuid:1::humidity_sensor", "global", MQTTHandler.globalSensorQOS
                    )
                )
                MQTTHandler.publish(
                    MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                        "uuid:1::temperature_sensor", "global", MQTTHandler.globalSensorQOS
                    )
                )
                MQTTHandler.publish(
                    MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                        "uuid:1::light_sensor", "global", MQTTHandler.globalSensorQOS
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // UČITAVANJE REDOVA IZ BAZE
    // -------------------------------------------------------------------------

    private fun ucitajIPrivkazujRedove() {
        lifecycleScope.launch(Dispatchers.IO) {
            val redovi = baza.backyardDao().getAllRedovi()
            val sveKulture = baza.backyardDao().getAllKulture()
            val kulturaMap = sveKulture.associateBy { it.kulturaId }

            withContext(Dispatchers.Main) {
                containerRows.removeAllViews()

                if (redovi.isEmpty()) {
                    val tvPrazno = TextView(this@ZonesActivity).apply {
                        text = "Nema konfigurisanih redova.\nIdi u Konfigurator da dodaš redove."
                        textSize = 14f
                        setTextColor(Color.parseColor("#7F8C8D"))
                        gravity = Gravity.CENTER
                        setPadding(0, 32, 0, 0)
                    }
                    containerRows.addView(tvPrazno)
                    return@withContext
                }

                for (red in redovi) {
                    val kultura = kulturaMap[red.kulturaIdRef]
                    val kartica = kreirajKarticu(red, kultura)
                    containerRows.addView(kartica)
                }
            }
        }
    }

    /**
     * Dinamički kreira karticu za jedan red bašte.
     * Sadrži: naziv reda, kulturu, status ventila i dugme za ručno zalivanje.
     */
    private fun kreirajKarticu(red: RedBasteEntity, kultura: KulturaEntity?): LinearLayout {
        val dp = resources.displayMetrics.density

        // Outer kartica
        val kartica = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            elevation = 4 * dp
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, (12 * dp).toInt())
            layoutParams = lp
        }

        // Naziv reda
        val tvNaziv = TextView(this).apply {
            text = red.nazivReda
            textSize = 16f
            setTextColor(Color.parseColor("#2C3E50"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Kultura
        val tvKultura = TextView(this).apply {
            text = if (kultura != null)
                "Kultura: ${kultura.naziv}  |  Vlaga: ${kultura.moistureMin.toInt()}% – ${kultura.moistureMax.toInt()}%"
            else
                "Kultura: nije dodeljena"
            textSize = 13f
            setTextColor(Color.parseColor("#7F8C8D"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
            layoutParams = lp
        }

        // Vlažnost zemljišta (default 0% dok ne stigne MQTT)
        val tvVlaga = TextView(this).apply {
            text = "Vlažnost zemljišta: 0%"
            textSize = 13f
            setTextColor(Color.parseColor("#3498DB"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, (2 * dp).toInt(), 0, (6 * dp).toInt())
            layoutParams = lp
            // Tag za kasniji MQTT update: pronađi view po red_id
            tag = "vlaga_${red.redId}"
        }

        // Separator
        val separator = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            lp.setMargins(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
            layoutParams = lp
        }

        // Red sa statusom ventila i dugmetom
        redKontrola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Status ventila (defaultno ZATVOREN dok ne stigne MQTT)
        val tvStatus = TextView(this).apply {
            text = "● ZATVOREN"
            textSize = 13f
            tag = "tvStatus_red${red.redId}"
            setTextColor(Color.parseColor("#E74C3C"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Dugme za ručno zalivanje - toggle OPEN/CLOSE
        var zalivanjAktivno = false

        val btnZalij = Button(this).apply {
            text = "ZALIJ"
            tag = "btnZalij_${red.redId}"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3498DB"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            setOnClickListener {
                zalivanjAktivno = !zalivanjAktivno

                if (zalivanjAktivno) {
                    MQTTHandler.publish(MQTTHandler.publishTopic, MQTTFactory.createSetMessage(
                        "uuid:${red.redId}::row_actuator", "row${red.redId}", "State", "OPEN", MQTTHandler.valveQOS))
                    lifecycleScope.launch {
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "VENTIL", "OPEN")
                    }
                    tvStatus.text = "● AKTIVNO"
                    tvStatus.setTextColor(Color.parseColor("#2ECC71"))
                    text = "PRESTANI"
                    setBackgroundColor(Color.parseColor("#E74C3C"))
                } else {
                    MQTTHandler.publish(MQTTHandler.publishTopic, MQTTFactory.createSetMessage(
                        "uuid:${red.redId}::row_actuator", "global", "State", "CLOSED", MQTTHandler.valveQOS))
                    lifecycleScope.launch {
                        addLog(AppDatabase.getInstance(this@ZonesActivity), "VENTIL", "CLOSED")
                    }
                    tvStatus.text = "● ZATVOREN"
                    tvStatus.setTextColor(Color.parseColor("#E74C3C"))
                    text = "ZALIJ"
                    setBackgroundColor(Color.parseColor("#3498DB"))
                }
            }
        }

        redKontrola.addView(tvStatus)
        redKontrola.addView(btnZalij)

        kartica.addView(tvNaziv)
        kartica.addView(tvKultura)
        kartica.addView(tvVlaga)
        kartica.addView(separator)
        kartica.addView(redKontrola)

        return kartica
    }

    // -------------------------------------------------------------------------
    // NIVO VODE U REZERVOARU
    // -------------------------------------------------------------------------

    /**
     * Poziva se kada stigne MQTT poruka sa topika rezervoara.
     * Za sada defaultno 0% dok MQTT nije implementiran.
     */
    fun azurirajNivoVode(procenat: Int) {
        pbWaterLevel.progress = procenat
        tvSoilType.text = "Rezervoar: $procenat%"
    }

    fun azurirajGlobalnaVlaznostVazduha(procenat: Int) {
        tvGlobalHumidity.text = "Vlažnost vazduha: $procenat%"
    }

    /**
     * Poziva se iz MQTT callback-a za topik basta/red{id}/senzor/vlaga
     * Pronalazi karticu po tagu i ažurira prikaz vlažnosti zemljišta.
     */
    fun azurirajVlaguZemljistaNaKartici(redId: Int, procenat: Int) {
        val tvVlaga = containerRows.findViewWithTag<TextView>("vlaga_$redId")
        tvVlaga?.text = "Vlažnost zemljišta: $procenat%"
    }

    fun azurirajStatusDugmetaNaKartici(redId: Int, zalivanjAktivno: Boolean) {
        val tvStatus = containerRows.findViewWithTag<TextView>("tvStatus_$redId")
        val btnZalij = containerRows.findViewWithTag<Button>("btnZalij_$redId")
        if (zalivanjAktivno) {
            tvStatus.text = "● AKTIVNO"
            tvStatus.setTextColor(Color.parseColor("#2ECC71"))
            btnZalij.text = "PRESTANI"
            btnZalij.setBackgroundColor(Color.parseColor("#E74C3C"))
        } else {
            tvStatus.text = "● ZATVOREN"
            tvStatus.setTextColor(Color.parseColor("#E74C3C"))
            btnZalij.text = "ZALIJ"
            btnZalij.setBackgroundColor(Color.parseColor("#3498DB"))
        }
    }

    // -------------------------------------------------------------------------
    // MQTT (stubovi - popuniti kada se implementira MqttHelper)
    // -------------------------------------------------------------------------

   // private fun pretplatiSeNaMqttSenzore() {
        // basta/global/senzor/kolicinaSvetlosti  → azurirajUIZaSunce(Int)
        // basta/global/aktuator/krov/status      → azurirajUIKrova(Boolean)
        // basta/global/senzor/rezervoar          → azurirajNivoVode(Int)
        // basta/global/senzor/vlaznostVazduha    → azurirajGlobalnaVlaznostVazduha(Int)
        // basta/red{id}/senzor/vlaga             → azurirajVlaguZemljistaNaKartici(redId, Int)
        // basta/red{id}/aktuator/ventil/status   → ažurira tvStatus na kartici
    //}

    private fun posaljiKrovuMqttKomandu() {
        /*val topik = "garden/global/actuator/roof"
        val payload = if (!isRoofOpened) "OPEN" else "CLOSED"
        MQTTHandler.publish(topik,
            MQTTFactory.createMessage(payload, MQTTHandler.roofQOS))
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            addLog(db,"ROOF", payload)
        }
        btnRoofAction.text = if (!isRoofOpened) "Otvaranje..." else "Zatvaranje..."
        btnRoofAction.isEnabled = false*/
        /*lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                btnRoofAction.setBackgroundColor(Color.parseColor("#D3D3D3"))
            }
            delay(5000)
            btnRoofAction.isEnabled = true
            isRoofOpened = !isRoofOpened
            withContext(Dispatchers.Main) {
                azurirajUIKrova(isRoofOpened)
            }
        }*/

        val value = if (!isRoofOpened) "OPEN" else "CLOSED"
        MQTTHandler.publish(MQTTHandler.publishTopic,
            MQTTFactory.createSetMessage("uuid:1::roof_actuator", "global", "Position", value, MQTTHandler.roofQOS))
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            addLog(db, "ROOF", value)
        }
        btnRoofAction.text = if (!isRoofOpened) "Otvaranje..." else "Zatvaranje..."
        btnRoofAction.isEnabled = false
        btnRoofAction.alpha = 0.5F
    }

    private fun azurirajUIZaSunce(procenat: Int) {
        val opis = when {
            procenat < 30 -> "Slabo sunce"
            procenat in 30..70 -> "Umereno sunce"
            else -> "Jako sunce"
        }
        tvSunIntensity.text = "$procenat% ($opis)"
    }

    private fun azurirajUIKrova(otvoren: Boolean) {
        isRoofOpened = otvoren
        btnRoofAction.isEnabled = true
        btnRoofAction.alpha = 1.0F
        if (isRoofOpened) {
            tvRoofStatus.text = "OTVOREN"
            tvRoofStatus.setTextColor(Color.parseColor("#2ECC71"))
            btnRoofAction.text = "ZATVORI KROV"
        } else {
            tvRoofStatus.text = "ZATVOREN"
            tvRoofStatus.setTextColor(Color.parseColor("#E74C3C"))
            btnRoofAction.text = "OTVORI KROV"
        }
        btnRoofAction.setBackgroundColor(Color.parseColor("#3498DB"))
    }
}
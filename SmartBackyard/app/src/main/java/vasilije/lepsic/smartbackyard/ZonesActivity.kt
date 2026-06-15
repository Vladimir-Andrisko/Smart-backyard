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
    private var isRoofOpened = false
    private lateinit var tvAirTemperature: TextView

    private val getRequestDelay : Long = 4000
    private val rowSensorRegex = Regex("""uuid:(10|[1-9])::row_sensor""")
    private val rowActuatorRegex = Regex("""uuid:(10|[1-9])::row_actuator""")

    @Volatile private var isUpdatingUI = false

    // -------------------------------------------------------------------------
    // Mapa kartica po redId-u, da ne moramo da rušimo i pravimo nove view-ove
    // svaki put kada se osvežava UI.
    // -------------------------------------------------------------------------
    data class RedKarticaViews(
        val root: LinearLayout,
        val tvVlaga: TextView,
        val tvStatus: TextView,
        val btnZalij: Button
    )

    private val karticeMap = mutableMapOf<Int, RedKarticaViews>()

    private fun setRowButtonEnabled(redId: Int, enabled: Boolean) {
        karticeMap[redId]?.btnZalij?.apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
        }
    }

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
        if (isUpdatingUI) return
        isUpdatingUI = true

        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            try {
                ucitajIPrikazujRedove()

                val roofStatus = db.globalStatusDao().getRoofStatus()

                if (roofStatus == "UNINITIALIZED") {
                    withContext(Dispatchers.Main) {
                        btnRoofAction.visibility = View.INVISIBLE
                    }
                }

                withContext(Dispatchers.Main) {

                    azurirajUIKrova(roofStatus == "OPEN")
                    //azurirajNivoVode(db.globalStatusDao().getWaterLevel())
                    azurirajUIZaSunce(db.globalStatusDao().getLuminosity())
                    azurirajUITemperatureVazduha(db.globalStatusDao().getAirTemperature())
                    azurirajGlobalnaVlaznostVazduha(db.globalStatusDao().getHumidity())

                    val redovi = db.backyardDao().getAllRedovi()
                    val redoviStatus = db.backyardDao().getAllRedoviStatus()

                    if (redovi.isNotEmpty() && redoviStatus.isNotEmpty()) {
                        for (i in redovi.indices) {
                            val red = redovi[i]
                            val redStatus = redoviStatus[i]

                            azurirajStatusDugmetaNaKartici(red.redId, redStatus.open)
                            azurirajVlaguZemljistaNaKartici(red.redId, redStatus.soilMoisture)
                        }
                    }
                }

            } finally {
                isUpdatingUI = false
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
        //ucitajIPrikazujRedove()

        val db = AppDatabase.getInstance(this)

        MQTTHandler.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {

            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null)
                    return

                val json = MQTTFactory.parseGetMessage(String(message.payload, Charsets.UTF_8))
                if (json == null) {
                    Log.d("MQTT", "GET callback failed")
                    return
                }

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
                                updateUI()
                            }
                        }
                    }
                    catch(_ : java.lang.ClassCastException) {}
                }

                val match_sensor = rowSensorRegex.matchEntire(json.uuid)
                if (match_sensor != null) {
                    try {
                        val row = match_sensor.groupValues[1].toInt()
                        val humidity = json.service["Humidity"] as Int

                        lifecycleScope.launch {
                            db.backyardDao().setRedMoisture(row, humidity)
                            addLog(
                                AppDatabase.getInstance(this@ZonesActivity),
                                "SENZOR",
                                state.toString()
                            )
                        }
                    } catch(e : Exception) {
                        Log.d("Test", e.toString())
                    }
                }

                val match_actuator = rowActuatorRegex.matchEntire(json.uuid)
                if (match_actuator != null) {
                    val row = match_actuator.groupValues[1].toInt()
                    val position = json.service["Position"]

                    lifecycleScope.launch {
                        db.backyardDao().setRedStatus(row, position == "OPEN")

                        withContext(Dispatchers.Main) {
                            setRowButtonEnabled(row, true)
                        }
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })

        lifecycleScope.launch {
            while(true) {
                delay(getRequestDelay)
                if (isDestroyed || isFinishing)
                    break

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
                val db = AppDatabase.getInstance(this@ZonesActivity)
                for (i in 0 until db.backyardDao().getAllRedovi().size) {
                    MQTTHandler.publish(
                        MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                            "uuid:${i + 1}::row_sensor", "row${i + 1}", MQTTHandler.valveQOS
                        )
                    )
                }

                for (i in 0 until db.backyardDao().getAllRedovi().size) {
                    MQTTHandler.publish(
                        MQTTHandler.publishTopic, MQTTFactory.createGetMessage(
                            "uuid:${i + 1}::row_actuator", "row${i + 1}", MQTTHandler.valveQOS
                        )
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // UČITAVANJE / SINHRONIZACIJA REDOVA IZ BAZE
    // -------------------------------------------------------------------------

    /**
     * Sinhronizuje containerRows sa stanjem u bazi:
     * - ne briše postojeće kartice osim ako se redovi stvarno promene
     * - dodaje kartice za nove redove
     * - uklanja kartice za obrisane redove
     * - ažurira status/vlagu postojećih kartica iz baze
     */
    private fun ucitajIPrikazujRedove() {
        lifecycleScope.launch(Dispatchers.IO) {
            val redovi = baza.backyardDao().getAllRedovi()
            val sveKulture = baza.backyardDao().getAllKulture()
            val kulturaMap = sveKulture.associateBy { it.kulturaId }
            val statusi = baza.backyardDao().getAllRedoviStatus().associateBy { it.redIDRef }

            withContext(Dispatchers.Main) {
                if (redovi.isEmpty()) {
                    if (karticeMap.isEmpty()) {
                        if (containerRows.childCount == 0) {
                            val tvPrazno = TextView(this@ZonesActivity).apply {
                                text = "Nema konfigurisanih redova.\nIdi u Konfigurator da dodaš redove."
                                textSize = 14f
                                setTextColor(Color.parseColor("#7F8C8D"))
                                gravity = Gravity.CENTER
                                setPadding(0, 32, 0, 0)
                            }
                            containerRows.addView(tvPrazno)
                        }
                    }
                    return@withContext
                }

                // ako je do sada prikazana samo "Nema konfigurisanih redova" poruka, ukloni je
                if (karticeMap.isEmpty() && containerRows.childCount > 0) {
                    containerRows.removeAllViews()
                }

                val currentIds = redovi.map { it.redId }.toSet()

                // ukloni kartice za redove koji više ne postoje
                val zaUklanjanje = karticeMap.keys - currentIds
                for (redId in zaUklanjanje) {
                    karticeMap[redId]?.root?.let { containerRows.removeView(it) }
                    karticeMap.remove(redId)
                }

                // dodaj nove ili ažuriraj postojeće kartice
                for (red in redovi) {
                    val kultura = kulturaMap[red.kulturaIdRef]
                    val open = statusi[red.redId]?.open ?: false
                    val vlaga = statusi[red.redId]?.soilMoisture ?: 0

                    val existing = karticeMap[red.redId]
                    if (existing == null) {
                        val kartica = kreirajKarticu(red, kultura, open, vlaga)
                        containerRows.addView(kartica)
                    } else {
                        primeniStatusVentila(red.redId, open)
                        azurirajVlaguZemljistaNaKartici(red.redId, vlaga)
                    }
                }
            }
        }
    }

    /**
     * Kreira karticu za jedan red bašte i registruje njene view-ove u karticeMap,
     * kako bi se kasnije moglo ažurirati bez findViewWithTag pretrage.
     */
    private fun kreirajKarticu(
        red: RedBasteEntity,
        kultura: KulturaEntity?,
        open: Boolean,
        vlaga: Int
    ): LinearLayout {
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

        // Vlažnost zemljišta (popunjava se iz baze, default 0% ako nema podatka)
        val tvVlaga = TextView(this).apply {
            text = "Vlažnost zemljišta: $vlaga%"
            textSize = 13f
            setTextColor(Color.parseColor("#3498DB"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, (2 * dp).toInt(), 0, (6 * dp).toInt())
            layoutParams = lp
        }

        // Separator
        val separator = View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            lp.setMargins(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
            layoutParams = lp
        }

        // Red sa statusom ventila i dugmetom
        val redKontrola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Status ventila (popunjava se odmah ispod, prema stanju iz baze)
        val tvStatus = TextView(this).apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Dugme za ručno zalivanje - toggle OPEN/CLOSE
        val btnZalij = Button(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            /*setOnClickListener {
                val trenutno = karticeMap[red.redId] ?: return@setOnClickListener
                // novo stanje je suprotno od trenutno prikazanog
                val novoZalivanjeAktivno = trenutno.btnZalij.text == "ZALIJ"
                val pozicija = if (novoZalivanjeAktivno) "OPEN" else "CLOSED"

                MQTTHandler.publish(MQTTHandler.publishTopic, MQTTFactory.createSetMessage(
                    "uuid:${red.redId}::row_actuator", "row${red.redId}", "Position", pozicija, MQTTHandler.valveQOS))

                primeniStatusVentila(red.redId, novoZalivanjeAktivno)
            }*/
            setOnClickListener {
                val trenutno = karticeMap[red.redId] ?: return@setOnClickListener

                val novoZalivanjeAktivno = trenutno.btnZalij.text == "ZALIJ"
                val pozicija = if (novoZalivanjeAktivno) "OPEN" else "CLOSED"

                setRowButtonEnabled(red.redId, false)

                MQTTHandler.publish(
                    MQTTHandler.publishTopic,
                    MQTTFactory.createSetMessage(
                        "uuid:${red.redId}::row_actuator",
                        "row${red.redId}",
                        "Position",
                        pozicija,
                        MQTTHandler.valveQOS
                    )
                )

                // Optional optimistic UI update
                primeniStatusVentila(red.redId, novoZalivanjeAktivno)
            }
        }

        redKontrola.addView(tvStatus)
        redKontrola.addView(btnZalij)

        kartica.addView(tvNaziv)
        kartica.addView(tvKultura)
        kartica.addView(tvVlaga)
        kartica.addView(separator)
        kartica.addView(redKontrola)

        karticeMap[red.redId] = RedKarticaViews(
            root = kartica,
            tvVlaga = tvVlaga,
            tvStatus = tvStatus,
            btnZalij = btnZalij
        )

        // postavi inicijalni status ventila prema bazi
        primeniStatusVentila(red.redId, open)

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
     * Pronalazi karticu po redId-u (kroz karticeMap) i ažurira prikaz vlažnosti zemljišta.
     */
    fun azurirajVlaguZemljistaNaKartici(redId: Int, procenat: Int) {
        karticeMap[redId]?.tvVlaga?.text = "Vlažnost zemljišta: $procenat%"
    }

    /**
     * Ažurira prikaz statusa ventila na kartici prema stanju iz baze.
     * Ne upisuje ništa u bazu - to se radi na mestu gde se status zapravo menja
     * (klik na dugme, ili MQTT callback koji ažurira red status).
     */
    fun azurirajStatusDugmetaNaKartici(redId: Int, zalivanjAktivno: Boolean) {
        primeniStatusVentila(redId, zalivanjAktivno)
    }

    /**
     * Postavlja izgled (tekst i boje) statusa ventila i dugmeta za dati red,
     * bez ikakvog pristupa bazi.
     */
    private fun primeniStatusVentila(redId: Int, zalivanjAktivno: Boolean) {
        val kartica = karticeMap[redId] ?: return
        if (zalivanjAktivno) {
            kartica.tvStatus.text = "● AKTIVNO"
            kartica.tvStatus.setTextColor(Color.parseColor("#2ECC71"))
            kartica.btnZalij.text = "PRESTANI"
            kartica.btnZalij.setBackgroundColor(Color.parseColor("#E74C3C"))
        } else {
            kartica.tvStatus.text = "● ZATVOREN"
            kartica.tvStatus.setTextColor(Color.parseColor("#E74C3C"))
            kartica.btnZalij.text = "ZALIJ"
            kartica.btnZalij.setBackgroundColor(Color.parseColor("#3498DB"))
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
        //isRoofOpened = otvoren
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
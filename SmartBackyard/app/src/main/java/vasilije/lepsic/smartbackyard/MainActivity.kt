package vasilije.lepsic.smartbackyard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenuToggle: ImageView
    private lateinit var tvConnectionStatus: TextView

    // Dashboard Kartice i Alarmi
    private lateinit var btnCardOverview: LinearLayout
    private lateinit var btnCardCatalog: LinearLayout
    private lateinit var btnCardAnalytics: LinearLayout
    private lateinit var bannerAlert: LinearLayout

    // Stavke menija
    //private lateinit var menuItemHome: TextView
    private lateinit var menuItemConfigurator: TextView
    private lateinit var menuItemLogs: TextView // PROMENJENO: Umesto drone
    private lateinit var menuItemSettings: TextView

    private val rowSensorRegex = Regex("""uuid:(10|[1-9])::row_sensor""")
    private val rowActuatorRegex = Regex("""uuid:(10|[1-9])::row_actuator""")
    private val rowRegex = Regex("""row(10|[1-9])""")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicijalizacija navigacije
        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenuToggle = findViewById(R.id.btnMenuToggle)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        // Inicijalizacija Dashboard elemenata
        btnCardOverview = findViewById(R.id.btnCardOverview)
        btnCardCatalog = findViewById(R.id.btnCardCatalog)
        btnCardAnalytics = findViewById(R.id.btnCardAnalytics)
        bannerAlert = findViewById(R.id.bannerAlert)

        // Inicijalizacija stavki menija
        //menuItemHome = findViewById(R.id.menuItemHome)
        menuItemConfigurator = findViewById(R.id.menuItemConfigurator)
        menuItemLogs = findViewById(R.id.menuItemLogs) // PROMENJENO
        menuItemSettings = findViewById(R.id.menuItemSettings)

        val baza = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            DatabaseSeeder.popuniAkoJePrazno(baza)
        }


        // OTVARANJE BOČNE FIOKE
        btnMenuToggle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // AKCIJE ZA KARTICE NA DASHBOARD-U
        btnCardOverview.setOnClickListener {
            val intent = Intent(this, ZonesActivity::class.java)
            startActivity(intent)
        }

        btnCardCatalog.setOnClickListener {
            val intent = Intent(this, CatalogActivity::class.java)
            startActivity(intent)
        }

        btnCardAnalytics.setOnClickListener {
            val intent = Intent(this, AnalyticsActivity::class.java)
            startActivity(intent)
        }

        // KLIK NA BANER ZA UPOZORENJE
        bannerAlert.setOnClickListener {
            Toast.makeText(this, "Ruter vodi na problematični red!", Toast.LENGTH_LONG).show()
        }

        // AKCIJE ZA STAVKE U BOČNOM MENIJU
        /*menuItemHome.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }*/

        menuItemConfigurator.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val intent = Intent(this, ConfiguratorActivity::class.java)
            startActivity(intent)
        }

        // PROMENJENO: Otvaranje ekrana sa logovima najnovijih dešavanja sa kontrolera
        menuItemLogs.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val intent = Intent(this, LogsActivity::class.java)
            startActivity(intent)
        }

        // AŽURIRANO: Pokretanje ekrana za podešavanja preko bočne trake
        menuItemSettings.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START)
                else
                    finish()
            }
        }

        onBackPressedDispatcher.addCallback(callback)
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            if (!db.globalStatusDao().hasData())
                db.globalStatusDao().initializeColumn()
        }
        MQTTHandler.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {

            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null)
                    return

                // basta/global/senzor/kolicinaSvetlosti  → azurirajUIZaSunce(Int)
                // basta/global/aktuator/krov/status      → azurirajUIKrova(Boolean)
                // basta/global/senzor/rezervoar          → azurirajNivoVode(Int)
                // basta/global/senzor/vlaznostVazduha    → azurirajGlobalnaVlaznostVazduha(Int)
                // basta/red{id}/senzor/vlaga             → azurirajVlaguZemljistaNaKartici(redId, Int)
                // basta/red{id}/aktuator/ventil/status   → ažurira tvStatus na kartici

                val json = MQTTFactory.parseGetMessage(message.payload.contentToString())
                if (json == null) {
                    Log.d("MQTT", "GET callback failed")
                    return
                }

                if (json.uuid == "uuid:1::roof_actuator") {
                    val state = json.service["State"] as String
                    lifecycleScope.launch {
                        db.globalStatusDao().setRoofStatus(state)
                        addLog(AppDatabase.getInstance(this@MainActivity), "KROV", state)
                    }

                    return
                }

                if (json.uuid == "uuid:1::light_sensor") {
                    val state = json.service["Luminosity"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setLuminosity(state)
                        addLog(AppDatabase.getInstance(this@MainActivity), "JACINA SVETLOSTI", state.toString())
                    }

                    return
                }

                if (json.uuid == "uuid:1::temperature_sensor") {
                    val state = json.service["Temperature"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setAirTemperature(state)
                        addLog(AppDatabase.getInstance(this@MainActivity), "TEMPERATURA VAZDUHA", state.toString())
                    }

                    return
                }

                // TODO
                if (json.uuid == "WATER_LEVEL") {
                    val state = json.service["WaterLevel"] as Int
                    lifecycleScope.launch {
                        db.globalStatusDao().setWaterLevel(state)
                        addLog(AppDatabase.getInstance(this@MainActivity), "NIVO VODE", state.toString())
                    }
                }

                val sensorMatch = rowSensorRegex.matchEntire(json.uuid)
                if (sensorMatch != null) {
                    val rowMatch = rowRegex.matchEntire(json.group) ?: return
                    val state = json.service["Humidity"] as Int

                    lifecycleScope.launch {
                        db.backyardDao().setRedMoisture(rowMatch.groupValues[1].toInt(), state)
                        addLog(AppDatabase.getInstance(this@MainActivity), "SENZOR", state.toString())
                    }

                    return
                }

                val actuatorMatch = rowActuatorRegex.matchEntire(json.uuid)
                if (actuatorMatch != null) {
                    val rowMatch = rowRegex.matchEntire(json.group) ?: return
                    val state = json.service["State"] as String

                    lifecycleScope.launch {
                        db.backyardDao().setRedStatus(rowMatch.groupValues[1].toInt(), state == "OPEN")
                        addLog(AppDatabase.getInstance(this@MainActivity), "VENTIL", state)
                    }

                    return
                }

                ZonesActivityInstanceHolder.getZonesActivity()?.updateUI()
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })
        mqttConnectionTest()
    }

    private fun mqttConnectionTest() {
        try {
            MQTTHandler.setClientId("SampleClient")
            var lastAddress = MQTTHandler.grabSavedIp(this)
            lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    val addr = MQTTHandler.grabSavedIp(this@MainActivity)
                    if (addr != lastAddress) {
                        MQTTHandler.disconnect()
                        lastAddress = addr
                        continue
                    }
                    MQTTHandler.setIpAddress(lastAddress)
                    withContext(Dispatchers.Main) {
                        tvConnectionStatus.text = getString(if (MQTTHandler.isConnected())
                            R.string.status_local_active else R.string.status_local_inactive)
                        tvConnectionStatus.setTextColor(getColor(if (MQTTHandler.isConnected())
                            R.color.connection_green else R.color.connection_red))
                    }
                    if (!MQTTHandler.isConnected()) {
                        MQTTHandler.connect()
                        continue
                    }
                    MQTTHandler.mainLoop()
                    delay(10000)
                }
            }
        } catch (_: MqttException) {
        }
    }
}
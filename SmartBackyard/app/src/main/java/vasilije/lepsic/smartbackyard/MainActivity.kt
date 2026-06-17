package vasilije.lepsic.smartbackyard

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttException

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_NO
        )
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

            db.globalStatusDao().setRoofStatus("UNINITIALIZED")
        }
        mqttConnectionTest()
    }

    private fun mqttConnectionTest() {
        MQTTHandler.setClientId("SampleClient")
        var lastAddress = MQTTHandler.grabSavedIp(this)
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val addr = MQTTHandler.grabSavedIp(this@MainActivity)
                    if (addr != lastAddress) {
                        MQTTHandler.disconnect()
                        lastAddress = addr
                        continue
                    }
                    MQTTHandler.setIpAddress(lastAddress)
                    withContext(Dispatchers.Main) {
                        tvConnectionStatus.text = getString(
                            if (MQTTHandler.isConnected())
                                R.string.status_local_active else R.string.status_local_inactive
                        )
                        tvConnectionStatus.setTextColor(
                            getColor(
                                if (MQTTHandler.isConnected())
                                    R.color.connection_green else R.color.connection_red
                            )
                        )
                    }
                    if (!MQTTHandler.isConnected()) {
                        MQTTHandler.connect()
                        continue
                    }
                    MQTTHandler.mainLoop()
                } catch (_ : MqttException) {}
                delay(2000)
            }
        }
    }
}
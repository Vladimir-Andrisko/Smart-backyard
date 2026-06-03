package vasilije.lepsic.smartbackyard

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsActivity : AppCompatActivity() {

    private lateinit var btnLogsBack: Button
    private lateinit var btnClearLogs: Button
    private lateinit var listViewLogs: ListView

    // Dinamička lista koja drži stringove spremne za prikaz na ekranu
    private val listaLogovaPrikaz = ArrayList<String>()
    private lateinit var adapterLogova: ArrayAdapter<String>

    // Instanca Room baze podataka
    private lateinit var baza: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        // Inicijalizacija Room baze podataka preko Singleton-a
        baza = AppDatabase.getInstance(this)

        btnLogsBack = findViewById(R.id.btnLogsBack)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        listViewLogs = findViewById(R.id.listViewLogs)

        btnLogsBack.setOnClickListener { finish() }

        // Povezivanje adaptera sa vizuelnom listom
        adapterLogova = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            listaLogovaPrikaz
        )
        listViewLogs.adapter = adapterLogova

        // 1. Asinhrono učitavanje podataka iz prave Room/SQLite baze
        osveziLogoveIzBaze()

        // 2. Logika za brisanje kompletne istorije iz baze podataka i sa ekrana
        btnClearLogs.setOnClickListener {
            lifecycleScope.launch {
                // Brisanje podataka radimo na pozadinskoj (IO) niti da ne blokiramo UI
                withContext(Dispatchers.IO) {
                    baza.logDao().obrisiSveLogove()
                }

                // Nakon uspešnog brisanja iz baze, praznimo listu na ekranu i osvežavamo prikaz
                listaLogovaPrikaz.clear()
                adapterLogova.notifyDataSetChanged()

                Toast.makeText(this@LogsActivity, getString(R.string.toast_logs_cleared), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Povlači sve logove iz lokalne Room baze i mapira ih u string format za prikaz.
     */
    private fun osveziLogoveIzBaze() {
        lifecycleScope.launch {
            // Čitanje iz baze se izvršava asinhrono na pozadinskoj niti
            val entitetiIzBaze = withContext(Dispatchers.IO) {
                var trenutniLogovi = baza.logDao().dohvatiSveLogove()

                // Ako je baza skroz prazna (npr. prvo pokretanje), napuni je tvojim inicijalnim logovima
                if (trenutniLogovi.isEmpty()) {
                    nahraniBazuInicijalnimPodacima()
                    // Ponovo povlačimo podatke nakon uspešnog upisa
                    trenutniLogovi = baza.logDao().dohvatiSveLogove()
                }
                trenutniLogovi
            }

            // Čistimo listu prikaza i punimo je formatiranim stringovima iz baze
            listaLogovaPrikaz.clear()
            for (log in entitetiIzBaze) {
                // Sastavljanje stringa iz kolona baze podataka u traženi format
                listaLogovaPrikaz.add("[${log.timestamp}] ${log.komponenta}: ${log.poruka}")
            }

            // Obaveštavamo adapter na glavnoj niti da ponovo iscrta listu sa novim podacima
            adapterLogova.notifyDataSetChanged()
        }
    }

    /**
     * Pomoćna suspend funkcija koja inicijalno puni SQLite tabelu tvojim predefinisanim IoT logovima.
     */
    private suspend fun nahraniBazuInicijalnimPodacima() {
        withContext(Dispatchers.IO) {
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 11:45:12", komponenta = "KONTROLER", poruka = "Primljena naredba za deblokadu Zone 1."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 11:30:00", komponenta = "KROV", poruka = "Elektro-motor aktiviran. Pokrenuto zatvaranje krova."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 11:29:45", komponenta = "SENZOR", poruka = "Detektovana prevelika jačina sunčevog zračenja: 85%."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 10:44:02", komponenta = "ALARM", poruka = "Aktiviran 'Watering Failsafe' na Zoni 1 - Nivo vlage ne raste!"))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 10:43:00", komponenta = "VENTIL", poruka = "Otvoren elektroventil na Zoni 1 (Trajanje: 60s)."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 08:15:34", komponenta = "TELEMETRIJA", poruka = "Senzor vlage Zona 1 poslao vrednost: 42%."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 06:00:15", komponenta = "KROV", poruka = "Automatsko otvaranje krova na početku dnevnog ciklusa."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 04:22:11", komponenta = "ALARM", poruka = "Detektovan 'Leak Detected'! Pad nivoa vode u rezervoaru tokom noći."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "30.05.2026. 00:01:05", komponenta = "SISTEM", poruka = "Uspešna sinhronizacija lokalne SQLite baze sa cloud backend-om."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "29.05.2026. 22:40:00", komponenta = "ALARM", poruka = "'Sensor Offline' - Zona 2 nije poslala podatke 2 ciklusa."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "29.05.2026. 19:15:32", komponenta = "VENTIL", poruka = "Zatvoren ventil na Zoni 2 nakon uspešnog zalivanja."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "29.05.2026. 19:00:01", komponenta = "KONTROLER", poruka = "Automatsko pokretanje ciklusa zalivanja za: Paradajz."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "29.05.2026. 14:22:10", komponenta = "SENZOR", poruka = "Jačina sunčevog zračenja u opadanju: 45%."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "28.05.2026. 23:58:12", komponenta = "SISTEM", poruka = "Kreiran automatski lokalni backup baze podataka."))
            baza.logDao().ubaciLog(LogEntity(timestamp = "28.05.2026. 18:45:50", komponenta = "KONTROLER", poruka = "Registrovana promena mrežnog režima rada (Prebačeno na lokalni rad)."))
        }
    }
}
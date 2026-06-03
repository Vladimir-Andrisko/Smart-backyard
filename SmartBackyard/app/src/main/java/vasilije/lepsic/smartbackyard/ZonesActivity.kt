package vasilije.lepsic.smartbackyard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zones)

        baza = AppDatabase.getInstance(this)

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

        pretplatiSeNaMqttSenzore()

        btnRoofAction.setOnClickListener {
            posaljiKrovuMqttKomandu()
        }

        // Učitaj redove iz baze i napravi kartice
        ucitajIPrivkazujRedove()
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
        val redKontrola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Status ventila (defaultno ZATVOREN dok ne stigne MQTT)
        val tvStatus = TextView(this).apply {
            text = "● ZATVOREN"
            textSize = 13f
            setTextColor(Color.parseColor("#E74C3C"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Dugme za ručno zalivanje - toggle OPEN/CLOSE
        var zalivanjAktivno = false
        val topik = "basta/red${red.redId}/aktuator/ventil"

        val btnZalij = Button(this).apply {
            text = "ZALIJ"
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
                    // mqttManager.publish(topik, "OPEN", qos = 1)
                    Toast.makeText(this@ZonesActivity, "MQTT: $topik → OPEN (QoS 1)", Toast.LENGTH_SHORT).show()
                    tvStatus.text = "● AKTIVNO"
                    tvStatus.setTextColor(Color.parseColor("#2ECC71"))
                    text = "PRESTANI"
                    setBackgroundColor(Color.parseColor("#E74C3C"))
                } else {
                    // mqttManager.publish(topik, "CLOSE", qos = 1)
                    Toast.makeText(this@ZonesActivity, "MQTT: $topik → CLOSE (QoS 1)", Toast.LENGTH_SHORT).show()
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

    // -------------------------------------------------------------------------
    // MQTT (stubovi - popuniti kada se implementira MqttHelper)
    // -------------------------------------------------------------------------

    private fun pretplatiSeNaMqttSenzore() {
        // basta/global/senzor/kolicinaSvetlosti  → azurirajUIZaSunce(Int)
        // basta/global/aktuator/krov/status      → azurirajUIKrova(Boolean)
        // basta/global/senzor/rezervoar          → azurirajNivoVode(Int)
        // basta/global/senzor/vlaznostVazduha    → azurirajGlobalnaVlaznostVazduha(Int)
        // basta/red{id}/senzor/vlaga             → azurirajVlaguZemljistaNaKartici(redId, Int)
        // basta/red{id}/aktuator/ventil/status   → ažurira tvStatus na kartici
    }

    private fun posaljiKrovuMqttKomandu() {
        val topik = "basta/global/aktuator/krov"
        val payload = if (!isRoofOpened) "OPEN" else "CLOSE"
        // mqttManager.publish(topik, payload, qos = 1)
        Toast.makeText(this, "MQTT [$payload] → $topik (QoS 1)", Toast.LENGTH_SHORT).show()
        btnRoofAction.text = if (!isRoofOpened) "Otvaranje..." else "Zatvaranje..."
        btnRoofAction.isEnabled = false
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
        if (isRoofOpened) {
            tvRoofStatus.text = "OTVOREN"
            tvRoofStatus.setTextColor(Color.parseColor("#2ECC71"))
            btnRoofAction.text = "ZATVORI KROV"
            btnRoofAction.setBackgroundColor(Color.parseColor("#E74C3C"))
        } else {
            tvRoofStatus.text = "ZATVOREN"
            tvRoofStatus.setTextColor(Color.parseColor("#E74C3C"))
            btnRoofAction.text = "OTVORI KROV"
            btnRoofAction.setBackgroundColor(Color.parseColor("#3498DB"))
        }
    }
}
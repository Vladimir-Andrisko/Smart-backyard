package vasilije.lepsic.smartbackyard

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var baza: AppDatabase
    // Čuvamo listu redova da možemo da mapiramo poziciju spinnera na red_id
    private val listaRedova = mutableListOf<RedBasteEntity>()

    private lateinit var btnAnalyticsBack: Button
    private lateinit var spinnerZone: Spinner

    private lateinit var btnPeriod24h: Button
    private lateinit var btnPeriod7d: Button
    private lateinit var btnPeriod30d: Button
    private lateinit var btnPeriodCustom: Button

    private lateinit var panelCustomDates: LinearLayout
    private lateinit var tvDateFrom: TextView
    private lateinit var tvDateTo: TextView

    private lateinit var chartPlaceholder: LinearLayout

    private lateinit var panelNoDataState: LinearLayout
    private lateinit var tvNoDataDesc: TextView

    private var trenutnoIzabraniPeriod = "24h"
    private var datumOd = ""
    private var datumDo = ""

    // Klasa koja čuva meta-podatke o istorijskim događajima ventila
    data class IstorijaZalivanja(val indeksTacke: Int, val trajanjeSekunde: Int, val pokretac: String)

    private fun getPeriodRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()

        return when (trenutnoIzabraniPeriod) {
            "24h" -> Pair(now - 60L * 1000, now)

            "7d" -> Pair(now - 7L * 60 * 1000, now)

            "30d" -> Pair(now - 30L * 60 * 1000, now)

            "custom" -> {
                try {
                    val sdf = SimpleDateFormat("d.M.yyyy.", Locale.getDefault())

                    val from = sdf.parse(datumOd)?.time ?: 0L
                    val to = sdf.parse(datumDo)?.time ?: now

                    Pair(from, to + 24L * 60 * 60 * 1000)
                } catch (_: Exception) {
                    Pair(0L, now)
                }
            }

            else -> Pair(0L, now)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        btnAnalyticsBack = findViewById(R.id.btnAnalyticsBack)
        spinnerZone = findViewById(R.id.spinnerZone)
        btnPeriod24h = findViewById(R.id.btnPeriod24h)
        btnPeriod7d = findViewById(R.id.btnPeriod7d)
        btnPeriod30d = findViewById(R.id.btnPeriod30d)
        btnPeriodCustom = findViewById(R.id.btnPeriodCustom)
        panelCustomDates = findViewById(R.id.panelCustomDates)
        tvDateFrom = findViewById(R.id.tvDateFrom)
        tvDateTo = findViewById(R.id.tvDateTo)
        chartPlaceholder = findViewById(R.id.chartPlaceholder)
        panelNoDataState = findViewById(R.id.panelNoDataState)
        tvNoDataDesc = findViewById(R.id.tvNoDataDesc)

        baza = AppDatabase.getInstance(this)

        btnAnalyticsBack.setOnClickListener { finish() }

        val adapterZona = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mutableListOf())
        adapterZona.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerZone.adapter = adapterZona

        spinnerZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                osveziGrafikonZaPeriodX()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Učitaj redove iz baze i napuni spinner
        ucitajRedoveUSpinner(adapterZona)

        btnPeriod24h.setOnClickListener {
            azurirajAktivniTaster(btnPeriod24h, "24h")
            panelCustomDates.visibility = View.GONE
            osveziGrafikonZaPeriodX()
        }

        btnPeriod7d.setOnClickListener {
            azurirajAktivniTaster(btnPeriod7d, "7d")
            panelCustomDates.visibility = View.GONE
            osveziGrafikonZaPeriodX()
        }

        btnPeriod30d.setOnClickListener {
            azurirajAktivniTaster(btnPeriod30d, "30d")
            panelCustomDates.visibility = View.GONE
            osveziGrafikonZaPeriodX()
        }

        btnPeriodCustom.setOnClickListener {
            azurirajAktivniTaster(btnPeriodCustom, "custom")
            otvoriKalendarskiBiracOpsega()
        }
    }

    private fun azurirajAktivniTaster(aktivniButt: Button, periodKljuc: String) {
        trenutnoIzabraniPeriod = periodKljuc
        val tasteri = listOf(btnPeriod24h, btnPeriod7d, btnPeriod30d, btnPeriodCustom)
        for (b in tasteri) {
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7F8C8D"))
        }
        aktivniButt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E4053"))
    }

    private fun otvoriKalendarskiBiracOpsega() {
        val kalendar = Calendar.getInstance()
        val godina = kalendar.get(Calendar.YEAR)
        val mesec = kalendar.get(Calendar.MONTH)
        val dan = kalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, gOd, mOd, dOd ->
            datumOd = "$dOd.${mOd + 1}.$gOd."
            tvDateFrom.text = getString(R.string.label_date_from, datumOd)

            DatePickerDialog(this, { _, gDo, mDo, dDo ->
                datumDo = "$dDo.${mDo + 1}.$gDo."
                tvDateTo.text = getString(R.string.label_date_to, datumDo)

                panelCustomDates.visibility = View.VISIBLE
                osveziGrafikonZaPeriodX()

            }, godina, mesec, dan).apply {
                setTitle("Izaberi datum DO")
                show()
            }
        }, godina, mesec, dan).apply {
            setTitle("Izaberi datum OD")
            show()
        }
    }

    private fun ucitajRedoveUSpinner(adapterZona: ArrayAdapter<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val redoviIzBaze = baza.backyardDao().getAllRedovi()
            val kulture = baza.backyardDao().getAllKulture()
            val mapaKultura = kulture.associateBy { it.kulturaId }

            withContext(Dispatchers.Main) {
                listaRedova.clear()
                listaRedova.addAll(redoviIzBaze)

                adapterZona.clear()
                adapterZona.addAll(redoviIzBaze.map { red ->
                    val nazivKulture = mapaKultura[red.kulturaIdRef]?.naziv ?: "—"
                    "${red.nazivReda} ($nazivKulture)"
                })
                adapterZona.notifyDataSetChanged()

                if (redoviIzBaze.isNotEmpty()) {
                    osveziGrafikonZaPeriodX()
                }
            }
        }
    }

    private fun osveziGrafikonZaPeriodX() {

        chartPlaceholder.removeAllViews()

        if (listaRedova.isEmpty()) return
        if (spinnerZone.selectedItemPosition < 0) return

        val selektovaniRed = listaRedova[spinnerZone.selectedItemPosition]

        lifecycleScope.launch(Dispatchers.IO) {

            val kulture = baza.backyardDao().getAllKulture()
            val mapaKultura = kulture.associateBy { it.kulturaId }

            val kultura = mapaKultura[selektovaniRed.kulturaIdRef]

            val moistureMin = kultura?.moistureMin ?: 45f
            val moistureMax = kultura?.moistureMax ?: 75f

            val (startTime, endTime) = getPeriodRange()

            val vlagaOcitavanja =
                baza.backyardDao().getOcitavanjaZaPeriodX(
                    selektovaniRed.redId,
                    "humidity",
                    startTime,
                    endTime
                )

            val listaVlage =
                vlagaOcitavanja.map { it.vrednost }

            withContext(Dispatchers.Main) {

                panelNoDataState.visibility =
                    if (listaVlage.isEmpty()) View.VISIBLE
                    else View.GONE

                if (listaVlage.isEmpty()) {
                    tvNoDataDesc.text =
                        getString(
                            R.string.no_data_state_desc,
                            when (trenutnoIzabraniPeriod) {
                                "24h" -> "24 sata"
                                "7d" -> "7 dana"
                                "30d" -> "30 dana"
                                else -> "izabrani period"
                            }
                        )
                }

                prikaziOciscenGrafikon(
                    moistureMin = moistureMin,
                    moistureMax = moistureMax,
                    listaVlage = listaVlage,
                    listaTemp = emptyList(),
                    dogadjaji = emptyList()
                )
            }
        }
    }

    // Očišćena metoda koja prima direktne podatke i prosleđuje ih Canvas-u
    private fun prikaziOciscenGrafikon(
        moistureMin: Float,
        moistureMax: Float,
        listaVlage: List<Float>,
        listaTemp: List<Float>,
        dogadjaji: List<IstorijaZalivanja>
    ) {
        val kombinovaniGrafikon = CustomLineChart(
            this,
            listaVlage,
            listaTemp,
            false,
            moistureMin,
            moistureMax,
            dogadjaji
        )
        chartPlaceholder.addView(kombinovaniGrafikon)
    }

    private class CustomLineChart(
        context: Context,
        private val podaciVlage: List<Float>,
        private val podaciTemp: List<Float>,
        private val prikaziTemperaturu: Boolean,
        private val moistureMin: Float,
        private val moistureMax: Float,
        private val dogadjajiZalivanja: List<IstorijaZalivanja>
    ) : View(context) {

        private data class KapljicaKlikBoks(val x: Float, val y: Float, val podaci: IstorijaZalivanja)
        private val listaIscrtanihKapljica = mutableListOf<KapljicaKlikBoks>()

        private val paintVlaga = Paint().apply {
            color = Color.parseColor("#3498DB")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val paintTemp = Paint().apply {
            color = Color.parseColor("#95A5A6")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val paintKapljica = Paint().apply {
            color = Color.parseColor("#2980B9")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val paintMinLinija = Paint().apply {
            color = Color.parseColor("#E74C3C")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }

        private val paintMaxLinija = Paint().apply {
            color = Color.parseColor("#2ECC71")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }

        private val paintMreza = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        private val paintTekst = Paint().apply {
            color = Color.parseColor("#7F8C8D")
            textSize = 24f
            isAntiAlias = true
        }

        private val paintTekstOkvira = Paint().apply {
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val sirina = width.toFloat()
            val visina = height.toFloat()
            val padding = 75f

            // Crta mrežu bez obzira da li ima podataka, da grafikon ne bude skroz prazan
            val brojLinijaMreze = 4
            for (i in 0..brojLinijaMreze) {
                val y = padding + (visina - 2 * padding) * i / brojLinijaMreze
                canvas.drawLine(padding, y, sirina - padding, y, paintMreza)

                val procenatVlage = 100 - (i * 25)
                canvas.drawText("$procenatVlage%", 10f, y + 8f, paintTekst)

                if (prikaziTemperaturu) {
                    val vrednostTemp = 50 - (i * 12.5).toInt()
                    canvas.drawText("$vrednostTemp°C", sirina - padding + 12f, y + 8f, paintTekst)
                }
            }

            val yMin = visina - padding - ((moistureMin / 100f) * (visina - 2 * padding))
            val yMax = visina - padding - ((moistureMax / 100f) * (visina - 2 * padding))

            canvas.drawLine(padding, yMin, sirina - padding, yMin, paintMinLinija)
            paintTekstOkvira.color = Color.parseColor("#E74C3C")
            canvas.drawText("MIN (${moistureMin.toInt()}%)", padding + 10f, yMin - 8f, paintTekstOkvira)

            canvas.drawLine(padding, yMax, sirina - padding, yMax, paintMaxLinija)
            paintTekstOkvira.color = Color.parseColor("#2ECC71")
            canvas.drawText("MAX (${moistureMax.toInt()}%)", padding + 10f, yMax - 8f, paintTekstOkvira)

            // Ako su liste prazne, prekidamo crtanje samih krivih linija i kapljica
            if (podaciVlage.isEmpty() /*|| podaciTemp.isEmpty()*/) return

            listaIscrtanihKapljica.clear()

            /*if (prikaziTemperaturu) {
                nacrtajLinijuSerije(canvas, podaciTemp, 50f, sirina, visina, padding, paintTemp, logujKapljice = false)
            }*/

            nacrtajLinijuSerije(canvas, podaciVlage, 100f, sirina, visina, padding, paintVlaga, logujKapljice = true)
        }

        private fun nacrtajLinijuSerije(
            canvas: Canvas,
            serija: List<Float>,
            maxVrednostSkale: Float,
            sirina: Float,
            visina: Float,
            padding: Float,
            paintLinije: Paint,
            logujKapljice: Boolean
        ) {
            val korakX = (sirina - 2 * padding) / (serija.size - 1).coerceAtLeast(1)
            val putanja = Path()

            val xKoordinate = FloatArray(serija.size)
            val yKoordinate = FloatArray(serija.size)

            for ((indeks, vrednost) in serija.withIndex()) {
                val x = padding + (indeks * korakX)
                val y = visina - padding - ((vrednost / maxVrednostSkale) * (visina - 2 * padding))

                xKoordinate[indeks] = x
                yKoordinate[indeks] = y

                if (indeks == 0) {
                    putanja.moveTo(x, y)
                } else {
                    putanja.lineTo(x, y)
                }
            }
            canvas.drawPath(putanja, paintLinije)

            for (indeks in serija.indices) {
                val cx = xKoordinate[indeks]
                val cy = yKoordinate[indeks]
                canvas.drawCircle(cx, cy, 5f, Paint().apply { color = paintLinije.color; isAntiAlias = true })

                if (logujKapljice) {
                    val dogadjaj = dogadjajiZalivanja.find { it.indeksTacke == indeks }
                    if (dogadjaj != null) {
                        canvas.drawCircle(cx, cy - 20f, 10f, paintKapljica)
                        val putanjaKapljice = Path().apply {
                            moveTo(cx - 10f, cy - 20f)
                            lineTo(cx, cy - 35f)
                            lineTo(cx + 10f, cy - 20f)
                            close()
                        }
                        canvas.drawPath(putanjaKapljice, paintKapljica)

                        listaIscrtanihKapljica.add(KapljicaKlikBoks(cx, cy - 20f, dogadjaj))
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val xKlik = event.x
                val yKlik = event.y

                for (kapljica in listaIscrtanihKapljica) {
                    val rastojanje = sqrt((xKlik - kapljica.x).toDouble().pow(2.0) + (yKlik - kapljica.y).toDouble().pow(2.0))
                    if (rastojanje <= 35.0) {
                        prikaziTooltipProzorcic(kapljica.podaci)
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        private fun prikaziTooltipProzorcic(podaci: IstorijaZalivanja) {
            AlertDialog.Builder(context).apply {
                setTitle(context.getString(R.string.tooltip_title))
                setMessage(
                    context.getString(R.string.tooltip_duration, podaci.trajanjeSekunde) + "\n" +
                            context.getString(R.string.tooltip_trigger, podaci.pokretac)
                )
                setPositiveButton("U redu", null)
                show()
            }
        }
    }
}
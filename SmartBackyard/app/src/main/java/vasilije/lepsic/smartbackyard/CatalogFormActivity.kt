package vasilije.lepsic.smartbackyard

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogFormActivity : AppCompatActivity() {

    private lateinit var tvFormTitle: TextView
    private lateinit var btnFormBack: Button
    private lateinit var etCultureName: EditText
    private lateinit var tvSliderValuesDisplay: TextView
    private lateinit var sliderMoistureRange: RangeSlider
    private lateinit var etMaxWatering: EditText
    private lateinit var etCooldownInterval: EditText
    private lateinit var btnSaveCulture: Button

    private var isCustomProfil = 1
    private var kulturaId = -1
    private lateinit var baza: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog_form)
        baza = AppDatabase.getInstance(this)

        tvFormTitle = findViewById(R.id.tvFormTitle)
        btnFormBack = findViewById(R.id.btnFormBack)
        etCultureName = findViewById(R.id.etCultureName)
        tvSliderValuesDisplay = findViewById(R.id.tvSliderValuesDisplay)
        sliderMoistureRange = findViewById(R.id.sliderMoistureRange)
        etMaxWatering = findViewById(R.id.etMaxWatering)
        etCooldownInterval = findViewById(R.id.etCooldownInterval)
        btnSaveCulture = findViewById(R.id.btnSaveCulture)

        btnFormBack.setOnClickListener { finish() }

        procitajIntentPodatkeIInicijalizujFormu()

        lifecycleScope.launch(Dispatchers.IO) {
            val entitet = baza.backyardDao().getKulturaById(kulturaId);
            if (entitet != null) {
                etMaxWatering.setText(entitet.maxWateringDuration.toString())
                etCooldownInterval.setText(entitet.restingPeriod.toString())
            }
        }

        sliderMoistureRange.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            tvSliderValuesDisplay.text = "${values[0].toInt()}% - ${values[1].toInt()}%"
        }

        btnSaveCulture.setOnClickListener {
            if (izvrsiAgrotehnickuValidacijuForme()) {
                izvrsiBazuPodatakaLogikuSpasavanja()
            }
        }
    }

    private fun procitajIntentPodatkeIInicijalizujFormu() {
        val intent = intent
        if (intent != null && intent.hasExtra("KULTURA_ID")) {
            kulturaId = intent.getIntExtra("KULTURA_ID", -1)
            val naziv = intent.getStringExtra("KULTURA_NAZIV") ?: ""
            val minVlaga = intent.getFloatExtra("KULTURA_MIN_VLAGA", 40f)
            val maxVlaga = intent.getFloatExtra("KULTURA_MAX_VLAGA", 75f)
            isCustomProfil = intent.getIntExtra("KULTURA_IS_CUSTOM", 1)

            tvFormTitle.text = getString(R.string.title_edit_culture)
            etCultureName.setText(naziv)
            sliderMoistureRange.setValues(minVlaga, maxVlaga)
            tvSliderValuesDisplay.text = "${minVlaga.toInt()}% - ${maxVlaga.toInt()}%"

            if (isCustomProfil == 0) {
                etCultureName.isEnabled = false
                etCultureName.setBackgroundColor(Color.parseColor("#E5E7EB"))
            }
        }
    }

    private fun izvrsiBazuPodatakaLogikuSpasavanja() {
        val naziv = etCultureName.text.toString().trim()
        if (naziv.isBlank()) {
            Toast.makeText(this, "Unet naziv nije ispravan", Toast.LENGTH_SHORT).show()
            return
        }
        val minVlaga = sliderMoistureRange.values[0]
        val maxVlaga = sliderMoistureRange.values[1]
        var trajanje: Int
        var odmor: Int
        try {
            trajanje = etMaxWatering.text.toString().toInt()
            odmor = etCooldownInterval.text.toString().toInt()
        }
        catch(e : java.lang.NumberFormatException) {
            Toast.makeText(this, "Nisu uneta sva potrebna polja", Toast.LENGTH_SHORT).show()
            return
        }

        if (trajanje > 600) {
            Toast.makeText(this, "Trajanje moze maksimalno biti 600s", Toast.LENGTH_SHORT).show()
            return
        }

        if (odmor < 5 || odmor > 1440) {
            Toast.makeText(this, "Odmor mora biti u opsegu od 5 do 1440 minuta", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (kulturaId == -1) {
                // SLUČAJ 1: Nova kultura
                baza.backyardDao().insertKultura(
                    KulturaEntity(naziv = naziv, moistureMin = minVlaga, moistureMax = maxVlaga,
                        maxWateringDuration = trajanje, restingPeriod = odmor, isCustom = 1)
                )
            } else if (isCustomProfil == 0) {
                // SLUČAJ 2: Copy-on-Write (Sprečava izmenu fabričkog, kreira kopiju)
                baza.backyardDao().insertKultura(
                    KulturaEntity(naziv = "$naziv (Kopija)", moistureMin = minVlaga, moistureMax = maxVlaga,
                        maxWateringDuration = trajanje, restingPeriod = odmor, isCustom = 1)
                )
            } else {
                // SLUČAJ 3: Ažuriranje postojećeg korisničkog profila (REPLACE ažurira po ID-u)
                baza.backyardDao().insertKultura(
                    KulturaEntity(kulturaId = kulturaId, naziv = naziv, moistureMin = minVlaga, moistureMax = maxVlaga,
                        maxWateringDuration = trajanje, restingPeriod = odmor, isCustom = 1)
                )
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CatalogFormActivity, "Uspešno sačuvano!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun izvrsiAgrotehnickuValidacijuForme(): Boolean {
        // (Tvoja validaciona logika ostaje ista)
        return true
    }
}
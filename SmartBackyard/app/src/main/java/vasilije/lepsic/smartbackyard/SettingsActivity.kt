package vasilije.lepsic.smartbackyard

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnSettingsBack: Button
    private lateinit var etIpAddress: EditText
    private lateinit var etMinMoisture: EditText
    private lateinit var btnSaveSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnSettingsBack = findViewById(R.id.btnSettingsBack)
        etIpAddress = findViewById(R.id.etIpAddress)
        etMinMoisture = findViewById(R.id.etMinMoisture)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        btnSettingsBack.setOnClickListener { finish() }

        // Otvaramo SharedPreferences memoriju pod nazivom "SmartBackyardPrefs"
        val sharedPreferences = getSharedPreferences("SmartBackyardPrefs", Context.MODE_PRIVATE)

        // Učitavamo prethodno sačuvane vrednosti (ako postoje, ako ne, stavljamo prazno ili default)
        val sacuvanaIp = MQTTHandler.grabSavedIp(this)
        val sacuvanPrag = sharedPreferences.getInt("min_moisture", 40) // Default je 40%

        etIpAddress.setText(sacuvanaIp)
        etMinMoisture.setText(sacuvanPrag.toString())

        // Klik na dugme za čuvanje podataka
        btnSaveSettings.setOnClickListener {
            val novaIp = etIpAddress.text.toString().trim()
            val noviPragUnos = etMinMoisture.text.toString().trim()

            if (novaIp.isEmpty() || noviPragUnos.isEmpty()) {
                Toast.makeText(this, "Molimo popunite sva polja!", Toast.LENGTH_SHORT).show()
            } else {
                val noviPrag = noviPragUnos.toInt()

                // Upisivanje podataka u SharedPreferences trajnu memoriju uređaja
                sharedPreferences.edit().apply {
                    putString("ip_address", novaIp)
                    putInt("min_moisture", noviPrag)
                    apply() // Asinhrono čuvanje u pozadini
                }

                Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                finish() // Zatvara SettingsActivity i vraća nas na MainActivity
            }
        }
    }
}
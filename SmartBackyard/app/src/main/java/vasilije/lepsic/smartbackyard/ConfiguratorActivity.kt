package vasilije.lepsic.smartbackyard

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfiguratorActivity : AppCompatActivity() {

    private lateinit var baza: AppDatabase
    private val tempRedovi = mutableListOf<RedBasteEntity>()
    private lateinit var adapter: RedBasteAdapter
    private var mapaKultura: Map<Int, KulturaEntity> = emptyMap()

    private lateinit var spinnerZemljiste: Spinner
    private lateinit var btnDodajRed: Button
    private lateinit var btnPotvrdi: Button
    private val maxRows = 10

    fun updateRowButtonAvailability(rowCount : Int) {
        btnDodajRed.isEnabled = rowCount < maxRows
    }

    fun updateRowNames() {
        for (i in 0 until tempRedovi.size)
            tempRedovi[i].nazivReda = "Red ${i + 1}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configurator)
        baza = AppDatabase.getInstance(this)

        val prefs = getSharedPreferences("BastaPrefs", Context.MODE_PRIVATE)
        val zemljisteSaved = prefs.getString("TIP_ZEMLJISTA", null)

        spinnerZemljiste = findViewById(R.id.spinnerZemljiste)
        btnDodajRed = findViewById(R.id.btnDodajRed)
        btnPotvrdi = findViewById(R.id.btnPotvrdi)
        val rvRedovi = findViewById<RecyclerView>(R.id.rvRedovi)

        if (zemljisteSaved != null) spinnerZemljiste.visibility = View.GONE

        adapter = RedBasteAdapter(tempRedovi, mapaKultura) { red ->
            tempRedovi.remove(red)
            adapter.notifyDataSetChanged()
        }
        rvRedovi.layoutManager = LinearLayoutManager(this)
        rvRedovi.adapter = adapter

        // Seeder pa ucitavanje — samo jednom, sekvencijalno
        lifecycleScope.launch {
            DatabaseSeeder.popuniAkoJePrazno(baza)
            ucitajRedoveIzBaze()
        }

        btnDodajRed.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val listaKultura = baza.backyardDao().getAllKulture()

                withContext(Dispatchers.Main) {
                    if (listaKultura.isEmpty()) {
                        Toast.makeText(this@ConfiguratorActivity, "Prvo dodaj kulture u katalog!", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val nazivi = listaKultura.map { it.naziv }.toTypedArray()

                    androidx.appcompat.app.AlertDialog.Builder(this@ConfiguratorActivity)
                        .setTitle("Izaberite kulturu za novi red")
                        .setItems(nazivi) { _, which ->
                            val odabrana = listaKultura[which]
                            tempRedovi.add(RedBasteEntity(
                                tempRedovi.size + 1,
                                nazivReda = "Red ${tempRedovi.size + 1}",
                                kulturaIdRef = odabrana.kulturaId
                            ))
                            adapter.notifyDataSetChanged()
                            updateRowButtonAvailability(adapter.itemCount)
                        }
                        .show()
                }
            }
        }

        btnPotvrdi.setOnClickListener {
            if (!MQTTHandler.isConnected()) {
                Toast.makeText(this, "Niste povezani, pokusajte ponovo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val odabranoZemljiste = if (spinnerZemljiste.visibility == View.VISIBLE) {
                spinnerZemljiste.selectedItem?.toString()
            } else {
                zemljisteSaved
            }

            if (odabranoZemljiste != null) {
                prefs.edit().putString("TIP_ZEMLJISTA", odabranoZemljiste).apply()
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val lst : MutableList<RowCommandEntry> = mutableListOf()
                baza.backyardDao().deleteAllRedoviStatus()
                baza.backyardDao().deleteAllRedovi()
                tempRedovi.forEach {
                    if (it.kulturaIdRef != null) {
                        val kultura = baza.backyardDao().getKulturaById(it.kulturaIdRef)
                        if (kultura != null) {
                            baza.backyardDao().insertRedBaste(it)
                            val id = it.redId
                            baza.backyardDao().insertRedStatus(RedBasteStatusEntity(0, id, false, 5))
                            lst.add(
                                RowCommandEntry(
                                    kultura.moistureMax,
                                    kultura.moistureMin,
                                    kultura.maxWateringDuration,
                                    kultura.restingPeriod
                                )
                            )
                        }
                    }
                }

                MQTTHandler.publish(MQTTHandler.publishTopic, MQTTFactory.createSetRowsMessage(lst))

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConfiguratorActivity, "Redovi sačuvani!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private suspend fun ucitajRedoveIzBaze() {
        val redoviIzBaze = baza.backyardDao().getAllRedovi()
        val kulture = baza.backyardDao().getAllKulture()

        withContext(Dispatchers.Main) {
            mapaKultura = kulture.associateBy { it.kulturaId }
            tempRedovi.clear()
            tempRedovi.addAll(redoviIzBaze)
            adapter = RedBasteAdapter(tempRedovi, mapaKultura) { red ->
                tempRedovi.remove(red)
                adapter.notifyDataSetChanged()
            }
            findViewById<RecyclerView>(R.id.rvRedovi).adapter = adapter
            updateRowButtonAvailability(adapter.itemCount)
        }
    }
}
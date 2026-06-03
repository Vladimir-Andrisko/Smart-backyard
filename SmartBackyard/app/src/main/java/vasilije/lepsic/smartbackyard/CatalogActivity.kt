package vasilije.lepsic.smartbackyard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogActivity : AppCompatActivity() {

    private lateinit var btnCatalogBack: Button
    private lateinit var listViewCatalog: ListView
    private lateinit var btnAddNewCulture: Button

    private val lokalnaListaKultura = mutableListOf<KulturaEntity>()
    private lateinit var adapterKataloga: CatalogAdapter

    private lateinit var baza: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        baza = AppDatabase.getInstance(this)

        btnCatalogBack = findViewById(R.id.btnCatalogBack)
        listViewCatalog = findViewById(R.id.listViewCatalog)
        btnAddNewCulture = findViewById(R.id.btnAddNewCulture)

        btnCatalogBack.setOnClickListener { finish() }

        adapterKataloga = CatalogAdapter(this, lokalnaListaKultura)
        listViewCatalog.adapter = adapterKataloga

        // Seeder pa ucitavanje — sekvencijalno
        lifecycleScope.launch {
            DatabaseSeeder.popuniAkoJePrazno(baza)
            osveziKatalogIzBaze()
        }

        btnAddNewCulture.setOnClickListener {
            val namera = android.content.Intent(this, CatalogFormActivity::class.java)
            startActivity(namera)
        }
    }

    override fun onResume() {
        super.onResume()
        osveziKatalogIzBaze()
    }

    private fun osveziKatalogIzBaze() {
        lifecycleScope.launch {
            val kultureIzBaze = withContext(Dispatchers.IO) {
                baza.backyardDao().getAllKulture()
            }

            lokalnaListaKultura.clear()
            lokalnaListaKultura.addAll(kultureIzBaze)
            adapterKataloga.notifyDataSetChanged()
        }
    }

    fun izvrsiBezbednoBrisanjeKulture(kultura: KulturaEntity) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.dialog_delete_title))
            setMessage(getString(R.string.dialog_delete_msg))
            setPositiveButton("Obriši") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        baza.backyardDao().deleteKultura(kultura)
                    }
                    lokalnaListaKultura.remove(kultura)
                    adapterKataloga.notifyDataSetChanged()
                    Toast.makeText(this@CatalogActivity, getString(R.string.toast_deleted), Toast.LENGTH_LONG).show()
                }
            }
            setNegativeButton("Otkaži", null)
            show()
        }
    }

    inner class CatalogAdapter(
        private val prosledjeniKontekst: Context,
        private val kulture: List<KulturaEntity>
    ) : ArrayAdapter<KulturaEntity>(prosledjeniKontekst, 0, kulture) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val stavkaView = convertView ?: LayoutInflater.from(prosledjeniKontekst).inflate(
                R.layout.item_catalog_culture, parent, false
            )

            val kultura = kulture[position]

            val tvCultureName = stavkaView.findViewById<TextView>(R.id.tvCultureName)
            val tvCultureParams = stavkaView.findViewById<TextView>(R.id.tvCultureParams)
            val tvProfileTag = stavkaView.findViewById<TextView>(R.id.tvProfileTag)
            val tvStatusIcon = stavkaView.findViewById<TextView>(R.id.tvStatusIcon)
            val btnDeleteCulture = stavkaView.findViewById<Button>(R.id.btnDeleteCulture)

            tvCultureName.text = kultura.naziv
            tvCultureParams.text = prosledjeniKontekst.getString(R.string.label_moisture_range, kultura.moistureMin, kultura.moistureMax)

            if (kultura.isCustom == 0) {
                tvProfileTag.text = prosledjeniKontekst.getString(R.string.tag_system_profile)
                tvProfileTag.setBackgroundColor(Color.parseColor("#2ECC71"))
                tvStatusIcon.text = "🔒"
                btnDeleteCulture.visibility = View.GONE
            } else {
                tvProfileTag.text = prosledjeniKontekst.getString(R.string.tag_custom_profile)
                tvProfileTag.setBackgroundColor(Color.parseColor("#E67E22"))
                tvStatusIcon.text = "✏️"
                btnDeleteCulture.visibility = View.VISIBLE

                btnDeleteCulture.setOnClickListener {
                    this@CatalogActivity.izvrsiBezbednoBrisanjeKulture(kultura)
                }
            }

            stavkaView.setOnClickListener {
                val namera = android.content.Intent(prosledjeniKontekst, CatalogFormActivity::class.java).apply {
                    putExtra("KULTURA_ID", kultura.kulturaId)
                    putExtra("KULTURA_NAZIV", kultura.naziv)
                    putExtra("KULTURA_MIN_VLAGA", kultura.moistureMin)
                    putExtra("KULTURA_MAX_VLAGA", kultura.moistureMax)
                    putExtra("KULTURA_IS_CUSTOM", kultura.isCustom)
                }
                prosledjeniKontekst.startActivity(namera)
            }

            return stavkaView
        }
    }
}
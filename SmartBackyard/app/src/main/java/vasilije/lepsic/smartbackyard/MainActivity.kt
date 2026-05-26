package vasilije.lepsic.smartbackyard

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener {
            osveziStatusDvorista()
        }
    }

    private fun osveziStatusDvorista() {
        tvConnectionStatus.text = "Sistem na mreži online"
        tvConnectionStatus.setTextColor(Color.parseColor("#2ECC71"))
        Toast.makeText(this, "Podaci uspešno ažurirani!", Toast.LENGTH_SHORT).show()
    }
}
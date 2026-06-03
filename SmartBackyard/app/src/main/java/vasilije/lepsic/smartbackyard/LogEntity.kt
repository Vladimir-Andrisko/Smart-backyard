package vasilije.lepsic.smartbackyard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tablica_logova")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String, // Npr. "30.05.2026. 11:45:12"
    val komponenta: String, // Npr. "KONTROLER", "ALARM", "VENTIL"
    val poruka: String // Npr. "Primljena naredba za deblokadu Zone 1."
)
package vasilije.lepsic.smartbackyard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperatura_ocitavanja")
data class TemperaturaOcitavanjeEntity(
    @PrimaryKey(autoGenerate = true)
    val temperaturaId: Int = 0,

    val timestamp: Long,

    val temperatura: Float
)
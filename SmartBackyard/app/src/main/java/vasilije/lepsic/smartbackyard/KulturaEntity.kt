package vasilije.lepsic.smartbackyard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kulture")
data class KulturaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "kultura_id")
    val kulturaId: Int = 0,

    @ColumnInfo(name = "naziv")
    val naziv: String,

    @ColumnInfo(name = "moisture_min")
    val moistureMin: Float,

    @ColumnInfo(name = "moisture_max")
    val moistureMax: Float,

    @ColumnInfo(name = "max_watering_duration")
    val maxWateringDuration: Int,

    @ColumnInfo(name = "resting_period")
    val restingPeriod: Int,

    @ColumnInfo(name = "is_custom")
    val isCustom: Int // 0 za fabrički, 1 za korisnički profil
)
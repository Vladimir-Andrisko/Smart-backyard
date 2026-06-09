package vasilije.lepsic.smartbackyard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "redovi_baste_status",
    foreignKeys = [
        ForeignKey(
            entity = RedBasteEntity::class,
            parentColumns = ["red_id"],
            childColumns = ["red_id_ref"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RedBasteStatusEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "red_baste_status_id")
    val redBasteStatusId : Int = 0,

    @ColumnInfo(name = "red_id_ref")
    val redIDRef : Int,

    @ColumnInfo(name = "open")
    val open : Boolean,

    @ColumnInfo(name = "soil_moisture")
    var soilMoisture : Int = 0
)
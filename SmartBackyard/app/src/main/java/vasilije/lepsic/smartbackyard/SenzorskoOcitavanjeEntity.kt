package vasilije.lepsic.smartbackyard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "senzorska_ocitavanja",
    foreignKeys = [
        ForeignKey(
            entity = RedBasteEntity::class,
            parentColumns = ["red_id"], // Sada ovo postoji u RedBasteEntity
            childColumns = ["red_id"],  // Ovo se odnosi na polje redId u ovoj klasi
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SenzorskoOcitavanjeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ocitavanje_id")
    val ocitavanjeId: Int = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "red_id") // Ovo polje mora da se poklapa sa childColumns
    val redId: Int?,

    @ColumnInfo(name = "tip_metrike")
    val tipMetrike: String,

    @ColumnInfo(name = "vrednost")
    val vrednost: Float
)
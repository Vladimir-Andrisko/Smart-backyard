package vasilije.lepsic.smartbackyard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "redovi_baste",
    foreignKeys = [
        ForeignKey(
            entity = KulturaEntity::class,
            parentColumns = ["kultura_id"],
            childColumns = ["kultura_id_ref"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RedBasteEntity(
    @PrimaryKey
    @ColumnInfo(name = "red_id")
    var redId: Int,   // Assign manually (1-10)

    @ColumnInfo(name = "naziv_reda")
    var nazivReda: String,

    @ColumnInfo(name = "kultura_id_ref")
    val kulturaIdRef: Int?
)
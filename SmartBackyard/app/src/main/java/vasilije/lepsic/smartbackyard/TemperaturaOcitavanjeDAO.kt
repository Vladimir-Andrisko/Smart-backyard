package vasilije.lepsic.smartbackyard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TemperaturaOcitavanjeDAO {
    @Insert
    suspend fun insertTemperatura(
        temperatura: TemperaturaOcitavanjeEntity
    ): Long


    @Query("""
    SELECT * FROM temperatura_ocitavanja
    WHERE timestamp BETWEEN :startTime AND :endTime
    ORDER BY timestamp ASC
""")
    suspend fun getTemperaturaZaPeriod(
        startTime: Long,
        endTime: Long
    ): List<TemperaturaOcitavanjeEntity>
}
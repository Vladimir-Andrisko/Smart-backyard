package vasilije.lepsic.smartbackyard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {

    // Vraća sve logove sortirane tako da najnoviji budu na vrhu
    @Query("SELECT * FROM tablica_logova ORDER BY id DESC")
    suspend fun dohvatiSveLogove(): List<LogEntity>

    // Dodavanje novog loga u bazu
    @Insert
    suspend fun ubaciLog(log: LogEntity)

    // Brisanje cele istorije (Za tvoje crveno dugme)
    @Query("DELETE FROM tablica_logova")
    suspend fun obrisiSveLogove()
}
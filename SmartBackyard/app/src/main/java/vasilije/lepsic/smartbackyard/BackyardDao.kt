package vasilije.lepsic.smartbackyard

import androidx.room.*

@Dao
interface BackyardDao {

    // --- UPITI ZA KATALOG BILJAKA (KULTURE) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKultura(kultura: KulturaEntity): Long

    @Query("""
    SELECT * FROM kulture 
    ORDER BY is_custom ASC, naziv ASC
""")
    suspend fun getAllKulture(): List<KulturaEntity>

    @Query("SELECT * FROM kulture WHERE kultura_id = :id")
    suspend fun getKulturaById(id : Int) : KulturaEntity?

    @Delete
    suspend fun deleteKultura(kultura: KulturaEntity)


    // --- UPITI ZA KONFIGURACIJU BAŠTE (REDOVI) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedBaste(red: RedBasteEntity): Long

    @Query("SELECT * FROM redovi_baste")
    suspend fun getAllRedovi(): List<RedBasteEntity>

    @Query("DELETE FROM redovi_baste WHERE red_id = :id")
    suspend fun deleteRedById(id: Int)

    @Query("DELETE FROM redovi_baste")
    suspend fun deleteAllRedovi()

    @Query("SELECT * FROM redovi_baste_status")
    suspend fun getAllRedoviStatus() : List<RedBasteStatusEntity>

    @Query("SELECT * FROM redovi_baste_status WHERE red_id_ref = :id")
    suspend fun getRedStatusByRedId(id : Int) : RedBasteStatusEntity?

    @Query("DELETE FROM redovi_baste_status")
    suspend fun deleteAllRedoviStatus()

    @Query("SELECT EXISTS(SELECT 1 FROM redovi_baste WHERE red_id = :id)")
    suspend fun redExists(id: Int): Boolean

    @Query("UPDATE redovi_baste_status SET `open` = :isOpen WHERE red_id_ref = :id")
    suspend fun setRedStatus(id: Int, isOpen : Boolean)

    @Query("UPDATE redovi_baste_status SET `soil_moisture` = :moisture WHERE red_id_ref = :id")
    suspend fun setRedMoisture(id: Int, moisture : Int)

    @Insert
    suspend fun insertRedStatus(status : RedBasteStatusEntity) : Long


    // --- UPITI ZA VREMENSKE SERIJE (MONITORING ZA PERIOD X) ---
    @Insert
    suspend fun insertOcitavanje(ocitavanje: SenzorskoOcitavanjeEntity): Long

    // Ključni upit za istorijski monitoring: Izvlači merenja za određeni red, određenu metriku u opsegu Od-Do (Period X)
    @Query("""
        SELECT * FROM senzorska_ocitavanja 
        WHERE red_id = :redId 
        AND tip_metrike = :tipMetrike 
        AND timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp ASC
    """)
    suspend fun getOcitavanjaZaPeriodX(
        redId: Int?,
        tipMetrike: String,
        startTime: Long,
        endTime: Long
    ): List<SenzorskoOcitavanjeEntity>

    // --- UPIT ZA SENSOR SUNCA (GLOBALNI PANEL) ---
    @Query("""
        SELECT * FROM senzorska_ocitavanja 
        WHERE tip_metrike = 'jacina_sunca' AND red_id IS NULL 
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun dohvatiNajnovijeOcitavanjeSunca(): SenzorskoOcitavanjeEntity?
}
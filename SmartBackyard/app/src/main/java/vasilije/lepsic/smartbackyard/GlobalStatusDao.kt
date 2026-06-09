package vasilije.lepsic.smartbackyard

import androidx.room.Dao
import androidx.room.Query

@Dao
interface GlobalStatusDao {
    @Query("SELECT roof_open FROM global_status")
    suspend fun getRoofStatus() : String

    @Query("UPDATE global_status SET roof_open = :status")
    suspend fun setRoofStatus(status: String)

    @Query("SELECT water_level FROM global_status")
    suspend fun getWaterLevel() : Int

    @Query("UPDATE global_status SET water_level = :level")
    suspend fun setWaterLevel(level: Int)

    @Query("SELECT humidity FROM global_status")
    suspend fun getHumidity() : Int

    @Query("UPDATE global_status SET humidity = :humidity")
    suspend fun setHumidity(humidity: Int)

    @Query("SELECT luminosity FROM global_status")
    suspend fun getLuminosity() : Int

    @Query("UPDATE global_status SET luminosity = :luminosity")
    suspend fun setLuminosity(luminosity: Int)

    @Query("SELECT air_temperature FROM global_status")
    suspend fun getAirTemperature() : Int

    @Query("UPDATE global_status SET air_temperature = :airTemperature")
    suspend fun setAirTemperature(airTemperature: Int)

    @Query("""INSERT INTO global_status (roof_open, water_level, humidity, luminosity, air_temperature) VALUES ('CLOSED', 0, 0, 0, 0)""")
    suspend fun initializeColumn()

    @Query("SELECT EXISTS(SELECT 1 FROM global_status LIMIT 1)")
    suspend fun hasData(): Boolean
}
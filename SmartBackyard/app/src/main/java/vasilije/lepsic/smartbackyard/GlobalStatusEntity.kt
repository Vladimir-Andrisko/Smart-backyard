package vasilije.lepsic.smartbackyard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_status")
class GlobalStatusEntity {
    @PrimaryKey(autoGenerate = true)
    private var id = 0

    @ColumnInfo(name = "roof_open")
    private var roofOpen = "CLOSED"

    @ColumnInfo(name = "water_level")
    private var waterLevel = 0

    @ColumnInfo(name = "humidity")
    private var humidity = 0

    @ColumnInfo(name = "luminosity")
    private var luminosity = 0

    @ColumnInfo(name = "air_temperature")
    private var airTemperature = 0

    fun setId(id : Int) {
        this.id = id
    }

    fun setRoofOpen(roofOpen : String) {
        this.roofOpen = roofOpen
    }

    fun setWaterLevel(waterLevel : Int) {
        this.waterLevel = waterLevel
    }

    fun setHumidity(humidity : Int) {
        this.humidity = humidity
    }

    fun setLuminosity(luminosity : Int) {
        this.luminosity = luminosity
    }

    fun setAirTemperature(airTemperature : Int) {
        this.airTemperature = airTemperature
    }

    fun getId() : Int {
        return id
    }

    fun getRoofOpen() : String {
        return roofOpen
    }

    fun getWaterLevel() : Int {
        return waterLevel
    }

    fun getHumidity() : Int {
        return humidity
    }

    fun getLuminosity() : Int {
        return luminosity
    }

    fun getAirTemperature() : Int {
        return airTemperature
    }
}
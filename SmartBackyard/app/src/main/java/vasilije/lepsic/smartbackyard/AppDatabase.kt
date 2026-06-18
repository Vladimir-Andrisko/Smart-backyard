package vasilije.lepsic.smartbackyard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LogEntity::class,
        KulturaEntity::class,
        RedBasteEntity::class,
        SenzorskoOcitavanjeEntity::class,
        RedBasteStatusEntity::class,
        GlobalStatusEntity::class,
        TemperaturaOcitavanjeEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao
    abstract fun backyardDao(): BackyardDao
    abstract fun globalStatusDao(): GlobalStatusDao

    abstract fun temperatureDao(): TemperaturaOcitavanjeDAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_backyard_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
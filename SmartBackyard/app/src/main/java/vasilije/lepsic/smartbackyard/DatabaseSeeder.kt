package vasilije.lepsic.smartbackyard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseSeeder {

    suspend fun popuniAkoJePrazno(baza: AppDatabase) {
        withContext(Dispatchers.IO) {
            val kulture = baza.backyardDao().getAllKulture()
            if (kulture.isNotEmpty()) return@withContext

            val fabrickiKatalog = listOf(
                KulturaEntity(naziv = "Paradajz",      moistureMin = 60f, moistureMax = 80f, maxWateringDuration = 15, restingPeriod = 120, isCustom = 0),
                KulturaEntity(naziv = "Paprika",       moistureMin = 65f, moistureMax = 85f, maxWateringDuration = 20, restingPeriod = 90,  isCustom = 0),
                KulturaEntity(naziv = "Krastavac",     moistureMin = 70f, moistureMax = 90f, maxWateringDuration = 25, restingPeriod = 60,  isCustom = 0),
                KulturaEntity(naziv = "Zelena Salata", moistureMin = 65f, moistureMax = 80f, maxWateringDuration = 10, restingPeriod = 180, isCustom = 0),
                KulturaEntity(naziv = "Kupus",         moistureMin = 70f, moistureMax = 85f, maxWateringDuration = 30, restingPeriod = 120, isCustom = 0),
                KulturaEntity(naziv = "Šargarepa",     moistureMin = 55f, moistureMax = 75f, maxWateringDuration = 12, restingPeriod = 240, isCustom = 0),
                KulturaEntity(naziv = "Crni Luk",      moistureMin = 50f, moistureMax = 70f, maxWateringDuration = 8,  restingPeriod = 300, isCustom = 0),
                KulturaEntity(naziv = "Beli Luk",      moistureMin = 50f, moistureMax = 65f, maxWateringDuration = 8,  restingPeriod = 360, isCustom = 0),
                KulturaEntity(naziv = "Krompir",       moistureMin = 55f, moistureMax = 75f, maxWateringDuration = 20, restingPeriod = 180, isCustom = 0),
                KulturaEntity(naziv = "Spanać",        moistureMin = 65f, moistureMax = 85f, maxWateringDuration = 10, restingPeriod = 120, isCustom = 0),
                KulturaEntity(naziv = "Patlidžan",    moistureMin = 65f, moistureMax = 85f, maxWateringDuration = 25, restingPeriod = 90,  isCustom = 0),
                KulturaEntity(naziv = "Tikvica",      moistureMin = 70f, moistureMax = 90f, maxWateringDuration = 30, restingPeriod = 60,  isCustom = 0),
                KulturaEntity(naziv = "Pasulj",       moistureMin = 50f, moistureMax = 70f, maxWateringDuration = 15, restingPeriod = 240, isCustom = 0),
                KulturaEntity(naziv = "Grašak",       moistureMin = 55f, moistureMax = 75f, maxWateringDuration = 15, restingPeriod = 240, isCustom = 0),
                KulturaEntity(naziv = "Jagoda",       moistureMin = 60f, moistureMax = 80f, maxWateringDuration = 12, restingPeriod = 120, isCustom = 0),
                KulturaEntity(naziv = "Blitva",       moistureMin = 65f, moistureMax = 85f, maxWateringDuration = 15, restingPeriod = 120, isCustom = 0),
                KulturaEntity(naziv = "Rotkvica",     moistureMin = 60f, moistureMax = 80f, maxWateringDuration = 8,  restingPeriod = 180, isCustom = 0),
                KulturaEntity(naziv = "Celer",        moistureMin = 70f, moistureMax = 90f, maxWateringDuration = 20, restingPeriod = 90,  isCustom = 0)
            )
            fabrickiKatalog.forEach { baza.backyardDao().insertKultura(it) }
        }
    }
}
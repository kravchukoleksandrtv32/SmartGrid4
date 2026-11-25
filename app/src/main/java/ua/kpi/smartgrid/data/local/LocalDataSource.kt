package ua.kpi.smartgrid.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Локальне джерело даних (Data Source): працює лише з базою Room.
 * Репозиторій не знає про DAO — він викликає методи цього класу.
 */
class LocalDataSource(database: AppDatabase) {

    private val objectDao = database.gridObjectDao()
    private val measurementDao = database.measurementDao()

    // ---------- Енергооб'єкти (кеш довідника з сервера) ----------

    val objects: Flow<List<GridObjectEntity>> = objectDao.getAll()

    fun objectById(id: Int): Flow<GridObjectEntity?> = objectDao.getById(id)

    suspend fun saveObjects(objects: List<GridObjectEntity>) = objectDao.upsertAll(objects)

    suspend fun objectsCount(): Int = objectDao.count()

    suspend fun lastSyncTime(): Long = objectDao.lastSyncTime() ?: 0L

    // ---------- Вимірювання ----------

    fun searchMeasurements(query: String, sortByPower: Boolean): Flow<List<MeasurementWithObject>> =
        measurementDao.search(query, sortByPower)

    fun measurementsForObject(objectId: Int): Flow<List<MeasurementEntity>> = measurementDao.getForObject(objectId)

    suspend fun getMeasurement(id: Int): MeasurementEntity? = measurementDao.getById(id)

    suspend fun insertMeasurement(m: MeasurementEntity) = measurementDao.insert(m)

    suspend fun updateMeasurement(m: MeasurementEntity) = measurementDao.update(m)

    suspend fun deleteMeasurement(m: MeasurementEntity) = measurementDao.delete(m)
}

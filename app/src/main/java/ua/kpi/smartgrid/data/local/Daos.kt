package ua.kpi.smartgrid.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY role, name")
    suspend fun getAll(): List<UserEntity>
}

@Dao
interface GridObjectDao {
    // Upsert оновлює наявні рядки без видалення, тому вимірювання (ForeignKey CASCADE) не зникають
    @Upsert
    suspend fun upsertAll(objects: List<GridObjectEntity>)

    @Query("SELECT * FROM grid_objects ORDER BY name")
    fun getAll(): Flow<List<GridObjectEntity>>

    @Query("SELECT COUNT(*) FROM grid_objects")
    suspend fun count(): Int

    @Query("SELECT * FROM grid_objects WHERE id = :id")
    fun getById(id: Int): Flow<GridObjectEntity?>

    // Час останньої синхронізації кешу з сервером
    @Query("SELECT MAX(syncedAt) FROM grid_objects")
    suspend fun lastSyncTime(): Long?
}

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insert(measurement: MeasurementEntity)

    @Update
    suspend fun update(measurement: MeasurementEntity)

    @Delete
    suspend fun delete(measurement: MeasurementEntity)

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Int): MeasurementEntity?

    // Вимірювання одного об'єкта (екран деталей)
    @Query("SELECT * FROM measurements WHERE objectId = :objectId ORDER BY createdAt DESC")
    fun getForObject(objectId: Int): Flow<List<MeasurementEntity>>

    // Пошук за назвою об'єкта і сортування за датою або потужністю
    @Transaction
    @Query(
        """
        SELECT measurements.*, grid_objects.name AS objectName, grid_objects.capacity_kw AS capacityKw
        FROM measurements
        INNER JOIN grid_objects ON measurements.objectId = grid_objects.id
        WHERE grid_objects.name LIKE '%' || :query || '%'
        ORDER BY CASE WHEN :sortByPower THEN measurements.power ELSE measurements.createdAt END DESC
        """
    )
    fun search(query: String, sortByPower: Boolean): Flow<List<MeasurementWithObject>>
}

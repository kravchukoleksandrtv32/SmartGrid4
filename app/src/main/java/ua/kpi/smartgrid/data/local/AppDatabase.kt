package ua.kpi.smartgrid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Версія 2: додано роль користувача і довідник енергооб'єктів замість таблиці substations
@Database(
    entities = [UserEntity::class, GridObjectEntity::class, MeasurementEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun gridObjectDao(): GridObjectDao
    abstract fun measurementDao(): MeasurementDao
}

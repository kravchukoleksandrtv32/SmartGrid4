package ua.kpi.smartgrid.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Обліковий запис. Таблицю users використовує сервер-імітація (MockServerInterceptor),
 * застосунок напряму до неї не звертається — лише через REST API.
 */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val password: String,
    val role: String = "OPERATOR" // OPERATOR або ADMIN
)

/**
 * Енергооб'єкт мережі (підстанція, ТП, СЕС, накопичувач).
 * Довідник приходить із сервера і кешується в Room для роботи офлайн.
 */
@Entity(tableName = "grid_objects")
data class GridObjectEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,                                   // SUBSTATION, TRANSFORMER, SOLAR, STORAGE
    @ColumnInfo(name = "capacity_kw") val capacityKw: Double,
    val district: String,
    val lat: Double,
    val lon: Double,
    val description: String,
    val status: String,                                 // ONLINE, MAINTENANCE, OFFLINE
    @ColumnInfo(name = "load_kw") val loadKw: Double,   // сума навантажень фідерів
    val feeders: String,                                // фідери одним рядком, напр. «Ф-1: 210 кВт; Ф-2: 180 кВт»
    val updatedAt: Long,                                // час показників на сервері
    val syncedAt: Long                                  // коли запис збережено в кеш
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(
        entity = GridObjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["objectId"],
        onDelete = ForeignKey.CASCADE // видалення об'єкта видаляє його вимірювання
    )],
    indices = [Index("objectId")]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val objectId: Int,
    val voltage: Double,     // напруга, кВ
    val current: Double,     // струм, А
    val cosPhi: Double,      // коефіцієнт потужності
    val resistance: Double,  // опір фази лінії, Ом
    val power: Double,       // активна потужність, кВт (обчислюється)
    val losses: Double,      // втрати, кВт (обчислюються)
    val createdAt: Long      // час запису, мс
)

/** Вимірювання разом із назвою та потужністю об'єкта (результат JOIN). */
data class MeasurementWithObject(
    @Embedded val measurement: MeasurementEntity,
    val objectName: String,
    val capacityKw: Double
)

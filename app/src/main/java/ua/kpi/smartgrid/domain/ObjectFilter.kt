package ua.kpi.smartgrid.domain

import ua.kpi.smartgrid.data.local.GridObjectEntity
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Координати користувача (з GPS). */
data class UserLocation(val lat: Double, val lon: Double)

/** Критерії сортування списку об'єктів. */
enum class SortOption(val title: String) {
    NAME("Назва"),
    TYPE("Тип"),
    CAPACITY("Потужність"),
    LOAD("Завантаження"),
    UPDATED("Оновлення"),
    DISTANCE("Відстань")
}

/** Параметри фільтрації та сортування, які обрав користувач. */
data class ObjectFilters(
    val types: Set<String> = emptySet(), // порожня множина — усі типи
    val state: String? = null,           // null — будь-який стан
    val minCapacity: Float = 0f,         // мінімальна номінальна потужність, кВт
    val sort: SortOption = SortOption.NAME,
    val ascending: Boolean = true
) {
    val isDefault: Boolean get() = this == ObjectFilters()
}

/** Елемент списку: об'єкт + обчислені параметри для відображення. */
data class ObjectItem(
    val obj: GridObjectEntity,
    val percent: Double,       // завантаження, %
    val state: String,         // Норма / Перевантаження / Відключено ...
    val distanceKm: Double?    // відстань від користувача (null — позиція невідома)
)

/**
 * Багаторівнева фільтрація і сортування: пошук за назвою → тип → стан → потужність → сортування.
 * Чиста функція без Android-залежностей, тому її легко перевірити unit-тестом.
 */
object ObjectFilter {

    val states = listOf("Норма", "Високе навантаження", "Перевантаження", "Обслуговування", "Відключено")

    fun apply(
        list: List<GridObjectEntity>,
        query: String,
        filters: ObjectFilters,
        location: UserLocation?
    ): List<ObjectItem> {
        val items = list
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }   // 1. пошук
            .filter { filters.types.isEmpty() || it.type in filters.types }                    // 2. тип
            .filter { it.capacityKw >= filters.minCapacity }                                   // 3. потужність
            .map { obj ->
                ObjectItem(
                    obj = obj,
                    percent = GridLabels.loadPercent(obj),
                    state = GridLabels.state(obj),
                    distanceKm = location?.let { distanceKm(it.lat, it.lon, obj.lat, obj.lon) }
                )
            }
            .filter { filters.state == null || it.state == filters.state }                     // 4. стан

        // 5. сортування за обраним критерієм; відстань без GPS — у кінці списку
        val sorted = when (filters.sort) {
            SortOption.NAME -> items.sortedBy { it.obj.name }
            SortOption.TYPE -> items.sortedWith(compareBy<ObjectItem> { it.obj.type }.thenBy { it.obj.name })
            SortOption.CAPACITY -> items.sortedBy { it.obj.capacityKw }
            SortOption.LOAD -> items.sortedBy { it.percent }
            SortOption.UPDATED -> items.sortedBy { it.obj.updatedAt }
            SortOption.DISTANCE -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }
        return if (filters.ascending) sorted else sorted.reversed()
    }

    // Відстань між двома точками на поверхні Землі (формула гаверсинусів), км
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    private const val EARTH_RADIUS_KM = 6371.0
}

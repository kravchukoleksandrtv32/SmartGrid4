package ua.kpi.smartgrid.domain

import ua.kpi.smartgrid.data.local.GridObjectEntity

/** Українські назви типів і станів енергооб'єктів. */
object GridLabels {

    val types = mapOf(
        "SUBSTATION" to "Підстанція",
        "TRANSFORMER" to "ТП",
        "SOLAR" to "СЕС",
        "STORAGE" to "Накопичувач"
    )

    fun type(type: String): String = types[type] ?: type

    fun status(status: String): String = when (status) {
        "ONLINE" -> "Працює"
        "MAINTENANCE" -> "Обслуговування"
        "OFFLINE" -> "Відключено"
        else -> status
    }

    // Стан для користувача: якщо об'єкт працює — оцінюємо завантаження, інакше показуємо статус
    fun state(obj: GridObjectEntity): String =
        if (obj.status == "ONLINE") Calculator.getLoadStatus(loadPercent(obj)) else status(obj.status)

    fun loadPercent(obj: GridObjectEntity): Double = Calculator.calculateLoadPercent(obj.loadKw, obj.capacityKw)
}

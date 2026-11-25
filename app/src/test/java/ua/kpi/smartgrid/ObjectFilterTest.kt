package ua.kpi.smartgrid

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.kpi.smartgrid.data.local.GridObjectEntity
import ua.kpi.smartgrid.domain.ObjectFilter
import ua.kpi.smartgrid.domain.ObjectFilters
import ua.kpi.smartgrid.domain.SortOption

/** Перевірка багаторівневої фільтрації, сортування і розрахунку відстані. */
class ObjectFilterTest {

    private fun obj(id: Int, name: String, type: String, capacity: Double, load: Double, status: String = "ONLINE") =
        GridObjectEntity(id, name, type, capacity, "Район", 50.45, 30.52, "", status, load, "", 0L, 0L)

    private val list = listOf(
        obj(1, "ПС «Північна»", "SUBSTATION", 1000.0, 1100.0),  // перевантаження
        obj(2, "ТП-115", "TRANSFORMER", 400.0, 100.0),          // норма
        obj(3, "СЕС «Троєщина»", "SOLAR", 750.0, 0.0),
        obj(4, "ТП-507", "TRANSFORMER", 400.0, 0.0, status = "OFFLINE")
    )

    @Test
    fun search_and_type() {
        val result = ObjectFilter.apply(list, "тп", ObjectFilters(types = setOf("TRANSFORMER")), null)
        assertEquals(listOf(2, 4), result.map { it.obj.id })
    }

    @Test
    fun state_filter() {
        val result = ObjectFilter.apply(list, "", ObjectFilters(state = "Перевантаження"), null)
        assertEquals(listOf(1), result.map { it.obj.id })
    }

    @Test
    fun sort_by_load_descending() {
        val result = ObjectFilter.apply(list, "", ObjectFilters(sort = SortOption.LOAD, ascending = false), null)
        assertEquals(1, result.first().obj.id)
    }

    @Test
    fun distance_kyiv_lviv() {
        // Київ — Львів ≈ 470 км
        val km = ObjectFilter.distanceKm(50.45, 30.52, 49.84, 24.03)
        assertEquals(470.0, km, 10.0)
    }
}

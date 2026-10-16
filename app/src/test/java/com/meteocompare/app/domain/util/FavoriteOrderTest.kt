package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteOrderTest {
    private fun city(id: String) = City(id, id, country = "X", latitude = 0.0, longitude = 0.0)
    private val a = city("a")
    private val b = city("b")
    private val c = city("c")

    @Test
    fun `l'ordre demande est applique`() {
        assertEquals(listOf(c, a, b), reorderFavorites(listOf(a, b, c), listOf("c", "a", "b")))
    }

    @Test
    fun `une ville absente de l'ordre reste a la fin et rien n'est perdu`() {
        assertEquals(listOf(b, a, c), reorderFavorites(listOf(a, b, c), listOf("b", "a")))
    }

    @Test
    fun `les identifiants inconnus ou en double sont ignores`() {
        assertEquals(listOf(b, a), reorderFavorites(listOf(a, b), listOf("zz", "b", "b", "a")))
    }
}

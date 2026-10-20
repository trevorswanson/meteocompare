package com.meteocompare.app.ui.radar

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarCellMotion
import com.meteocompare.app.domain.radar.RadarEvolution
import com.meteocompare.app.domain.radar.extractRainCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class RadarNowcastFeaturesTest {
    private val city = City(
        id = "paris", name = "Paris", country = "France",
        latitude = 48.8566, longitude = 2.3522, timezone = "Europe/Paris"
    )

    /** Cellule carrée de 30 px au centre de l'image, qui file vers l'est. */
    private fun eastboundCell() = extractRainCells(square(241, 241, 30), minPixels = 1).first().copy(
        motion = RadarCellMotion(
            vx = 1.0, vy = 0.0, confidence = .8, history = 3, spanMinutes = 20.0,
            method = "centroid", centroidVx = 1.0, centroidVy = 0.0, advection = null,
            evolution = RadarEvolution(0.0, 0.0, 0.0, .8)
        )
    )

    @Test
    fun `no cells give an empty layer`() {
        assertEquals(0, nowcastFeatures(emptyList(), city, 30).features()!!.size)
    }

    @Test
    fun `a moving cell yields contour, envelope, trajectory, arrow and horizon label`() {
        val features = nowcastFeatures(listOf(eastboundCell()), city, 30).features()!!
        val kinds = features.map { it.getStringProperty("kind") }.toSet()
        listOf(NowcastKind.ENVELOPE, NowcastKind.CONTOUR, NowcastKind.TRAJECTORY, NowcastKind.ARROW, NowcastKind.HORIZON_LABEL)
            .forEach { assertTrue("${it.id} missing from $kinds", it.id in kinds) }
        assertEquals("+30", features.first { it.getStringProperty("kind") == NowcastKind.HORIZON_LABEL.id }.getStringProperty("label"))

        // Vers l'est : cap proche de 90°, et la flèche à l'est de la ville.
        val arrow = features.first { it.getStringProperty("kind") == NowcastKind.ARROW.id }
        assertEquals(90.0, arrow.getNumberProperty("bearing").toDouble(), 10.0)
        assertTrue((arrow.geometry() as Point).longitude() > city.longitude)
        assertEquals(city.latitude, (arrow.geometry() as Point).latitude(), .2)
    }

    private fun square(cx: Int, cy: Int, size: Int): ByteArray {
        val mask = ByteArray(512 * 512)
        for (y in cy - size / 2 until cy + size / 2) for (x in cx - size / 2 until cx + size / 2) mask[y * 512 + x] = 1
        return mask
    }
}

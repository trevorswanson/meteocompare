package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RainOnsetTest {

    private val hours = overviewHours(6)

    private fun trace(group: ForecastConsensus.Group, vararg values: Double?) =
        FamilyTrace(group = group, models = listOf(WeatherModel.GFS), values = values.toList())

    @Test
    fun `aucune pluie prevue donne une fenetre nulle`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0, 0.05, 0.0, 0.0, 0.0, 0.0)
        )

        assertNull(rainOnsetWindow(hours, traces))
    }

    @Test
    fun `une seule lignee ne permet pas de mesurer un desaccord`() {
        val traces = listOf(trace(ForecastConsensus.Group.NOAA_GFS, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0))

        assertNull(rainOnsetWindow(hours, traces))
    }

    @Test
    fun `la fenetre encadre les debuts prevus et retient la mediane`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 0.0, 0.5, 1.0, 0.0, 0.0, 0.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0, 0.0, 0.3, 0.8, 0.0, 0.0),
            trace(ForecastConsensus.Group.UKMO_GLOBAL, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0)
        )

        val window = rainOnsetWindow(hours, traces)!!

        assertEquals(hours[1], window.earliest)
        assertEquals(hours[2], window.median)
        assertEquals(hours[4], window.latest)
        assertEquals(3, window.familiesWithRain)
        assertEquals(3, window.familiesWithData)
        assertTrue(window.hasTimingSpread)
    }

    @Test
    fun `avec un nombre pair de lignees pluvieuses la mediane est la plus precoce`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0, 0.0, 0.0, 0.0, 0.6, 0.0),
            trace(ForecastConsensus.Group.UKMO_GLOBAL, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )

        val window = rainOnsetWindow(hours, traces)!!

        assertEquals(hours[1], window.median)
        assertEquals(2, window.familiesWithRain)
        assertEquals(3, window.familiesWithData)
    }

    @Test
    fun `une lignee sans donnee n'est pas comptee`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0, 0.4, 0.0, 0.0, 0.0, 0.0),
            trace(ForecastConsensus.Group.UKMO_GLOBAL, null, null, null, null, null, null)
        )

        val window = rainOnsetWindow(hours, traces)!!

        assertEquals(2, window.familiesWithData)
        assertFalse(window.hasTimingSpread)
    }

    @Test
    fun `la part humide ne compte que les lignees ayant une valeur`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 1.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0),
            trace(ForecastConsensus.Group.UKMO_GLOBAL, null)
        )

        assertEquals(FamilyRainShare(wet = 1, total = 2), familyRainShare(traces, 0))
        assertEquals(FamilyRainShare(wet = 0, total = 0), familyRainShare(traces, 5))
    }
}

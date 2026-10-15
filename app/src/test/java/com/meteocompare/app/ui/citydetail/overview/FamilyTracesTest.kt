package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyTracesTest {

    @Test
    fun `IFS et AIFS forment une seule lignee ECMWF distincte de GFS`() {
        val forecast = overviewForecast(
            WeatherModel.ECMWF to overviewHourly(listOf(10.0, 11.0)),
            WeatherModel.ECMWF_AIFS to overviewHourly(listOf(12.0, 13.0)),
            WeatherModel.GFS to overviewHourly(listOf(15.0, 16.0))
        )

        val traces = buildFamilyTemperatureTraces(forecast, overviewHours(2))

        assertEquals(
            listOf(ForecastConsensus.Group.ECMWF_GLOBAL, ForecastConsensus.Group.NOAA_GFS),
            traces.map { it.group }
        )
        val ecmwf = traces.first()
        assertEquals(listOf(WeatherModel.ECMWF, WeatherModel.ECMWF_AIFS), ecmwf.models)
        // Médiane pondérée de deux variantes à poids égal : moyenne des deux.
        assertEquals(11.0, ecmwf.values[0]!!, 1e-9)
        assertEquals(12.0, ecmwf.values[1]!!, 1e-9)
        assertEquals(listOf(15.0, 16.0), traces[1].values)
    }

    @Test
    fun `une heure absente ou hors horizon reste nulle sans extrapolation`() {
        val forecast = overviewForecast(
            WeatherModel.HRRR_CONUS to overviewHourly(listOf(10.0, null)),
            WeatherModel.GFS to overviewHourly(listOf(9.0, 9.5, 10.0))
        )

        val traces = buildFamilyTemperatureTraces(forecast, overviewHours(3))
        val hrrr = traces.single { it.group == ForecastConsensus.Group.NOAA_HRRR }

        assertEquals(listOf(10.0, null, null), hrrr.values)
    }

    @Test
    fun `une valeur physiquement impossible est ignoree`() {
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(listOf(999.0, 12.0))
        )

        val trace = buildFamilyTemperatureTraces(forecast, overviewHours(2)).single()

        assertNull(trace.values[0])
        assertEquals(12.0, trace.values[1]!!, 1e-9)
    }

    @Test
    fun `une lignee sans aucune donnee n'est pas tracee`() {
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(listOf(12.0, 13.0)),
            WeatherModel.UKMO_GLOBAL to overviewHourly(listOf(null, null))
        )

        val traces = buildFamilyTemperatureTraces(forecast, overviewHours(2))

        assertEquals(listOf(ForecastConsensus.Group.NOAA_GFS), traces.map { it.group })
    }

    @Test
    fun `le plafond garde les lignees les plus completes dans un ordre stable`() {
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(listOf(1.0, null, null)),
            WeatherModel.ECMWF to overviewHourly(listOf(1.0, 2.0, 3.0)),
            WeatherModel.UKMO_GLOBAL to overviewHourly(listOf(1.0, 2.0, null))
        )

        val traces = buildFamilyTemperatureTraces(forecast, overviewHours(3), maxFamilies = 2)

        assertEquals(
            listOf(ForecastConsensus.Group.ECMWF_GLOBAL, ForecastConsensus.Group.UKMO_GLOBAL),
            traces.map { it.group }
        )
    }

    @Test
    fun `une grille vide ne produit aucune courbe`() {
        val forecast = overviewForecast(WeatherModel.GFS to overviewHourly(listOf(12.0)))

        assertEquals(emptyList<FamilyTrace>(), buildFamilyTemperatureTraces(forecast, emptyList()))
    }
}

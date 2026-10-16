package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewModelTest {

    private val dry = List(24) { 0.0 }

    // Trois lignées indépendantes, sans probabilité native : la pluie ne
    // repose que sur des votes déterministes, avec des débuts décalés.
    private val forecast = overviewForecast(
        WeatherModel.GFS to overviewHourly(
            temperatures = List(24) { 12.0 + it * 0.2 },
            precipitations = dry.toMutableList().also { it[3] = 1.2; it[4] = 3.0 }
        ),
        WeatherModel.ECMWF to overviewHourly(
            temperatures = List(24) { 11.5 + it * 0.2 },
            precipitations = dry.toMutableList().also { it[5] = 0.8 }
        ),
        WeatherModel.UKMO_GLOBAL to overviewHourly(
            temperatures = List(24) { 13.0 + it * 0.2 },
            precipitations = dry
        )
    )

    private val hourly = buildSimplifiedTimeline(
        forecast = forecast,
        mode = DisplayMode.HOURLY,
        now = OVERVIEW_TEST_START,
        hourlyHorizonHours = 24
    )

    @Test
    fun `assemble une heure de pluie par point horaire avec une courbe par lignee`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), OVERVIEW_TEST_START)

        assertEquals(hourly.size, model.precipitation.size)
        assertEquals(model.timestamps, model.precipitation.map { it.instant })
        assertEquals(3, model.temperatureTraces.size)
        assertTrue(model.temperatureTraces.all { it.values.size == hourly.size })
    }

    @Test
    fun `sans probabilite native la pluie est formulee en part de lignees`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), OVERVIEW_TEST_START)

        val wetHour = model.precipitation[3]
        assertEquals(RainLikelihoodWording.FAMILY_SHARE, wetHour.wording)
        assertEquals(FamilyRainShare(wet = 1, total = 3), wetHour.familyShare)
        assertTrue(model.hasRain)
    }

    @Test
    fun `les tranches couvrent toute la fenetre dans l'ordre`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), OVERVIEW_TEST_START)

        assertEquals(
            listOf(DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.OVERNIGHT, DayPeriod.MORNING),
            model.periods.map { it.period }
        )
        assertEquals(hourly.size, model.periods.sumOf { it.hourCount })
    }
}

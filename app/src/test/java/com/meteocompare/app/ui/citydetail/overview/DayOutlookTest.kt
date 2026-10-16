package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOutlookTest {

    private val date = LocalDate.of(2026, 10, 17)
    private val midnight = Instant.parse("2026-10-17T00:00:00Z")

    private fun daily(min: Double, max: Double) = DailyForecast(
        dates = listOf(date),
        tempMax = listOf(max),
        tempMin = listOf(min),
        precipitationSum = listOf(0.0),
        windSpeedMax = listOf(10.0)
    )

    @Test
    fun `l'etendue d'un jour va de la plus basse minimale a la plus haute maximale entre lignees`() {
        val hourly = overviewHourly(List(2) { 10.0 })
        val forecast = overviewForecastWithDaily(
            listOf(
                Triple(WeatherModel.ECMWF, hourly, daily(min = 6.0, max = 14.0)),
                Triple(WeatherModel.ECMWF_AIFS, hourly, daily(min = 8.0, max = 16.0)),
                Triple(WeatherModel.GFS, hourly, daily(min = 4.0, max = 13.0))
            )
        )

        val range = buildDailyFamilyRanges(forecast).getValue(date)

        // ECMWF = médiane IFS/AIFS (7 / 15) ; GFS = 4 / 13 → enveloppe 4 → 15.
        assertEquals(4.0, range.low, 1e-9)
        assertEquals(15.0, range.high, 1e-9)
    }

    @Test
    fun `une journee incoherente (max sous min) est ignoree`() {
        val forecast = overviewForecastWithDaily(
            listOf(Triple(WeatherModel.GFS, overviewHourly(listOf(10.0)), daily(min = 12.0, max = 8.0)))
        )

        assertNull(buildDailyFamilyRanges(forecast)[date])
    }

    @Test
    fun `une journee complete est decrite sans aujourd'hui ni demain`() {
        val hours = List(24) { midnight.plusSeconds(it * 3_600L) }
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(
                temperatures = List(24) { 8.0 + it * 0.3 },
                precipitations = List(24) { if (it in 0..2) 1.5 else 0.0 },
                timestamps = hours
            ),
            WeatherModel.ECMWF to overviewHourly(
                temperatures = List(24) { 9.0 + it * 0.3 },
                precipitations = List(24) { if (it in 0..3) 2.0 else 0.0 },
                timestamps = hours
            )
        )
        val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, midnight, hourlyHorizonHours = 24)

        val outlook = buildDayOutlook(forecast, date, hourly)

        assertEquals(24, outlook.hourly.size)
        assertEquals(
            listOf(DayPeriod.OVERNIGHT, DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING),
            outlook.periods.map { it.period }
        )
        assertTrue(outlook.periods.drop(1).all { it.phrase.name.startsWith("IN_THE_") })
        val sky = outlook.narrative.filterIsInstance<NarrativeSentence.Sky>().single()
        assertEquals(OutlookScope.SINGLE_DAY, sky.scope)
        // La pluie dès minuit est annoncée pour une journée entière.
        assertEquals(midnight, outlook.narrative.filterIsInstance<NarrativeSentence.RainStart>().single().at)
        assertTrue(outlook.narrative.none { it is NarrativeSentence.TomorrowTrend })
    }

    @Test
    fun `seules les heures de la journee demandee sont gardees`() {
        val hours = List(30) { midnight.minusSeconds(3 * 3_600L).plusSeconds(it * 3_600L) }
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(temperatures = List(30) { 10.0 }, timestamps = hours)
        )
        val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, hours.first(), hourlyHorizonHours = 30)

        val outlook = buildDayOutlook(forecast, date, hourly)

        assertEquals(midnight, outlook.hourly.first().instant)
        assertEquals(24, outlook.hourly.size)
    }
}

package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineNightTest {

    private val start = Instant.parse("2026-10-15T12:00:00Z")
    private val end = start.plusSeconds(24 * 3_600L)
    private fun at(hours: Long) = start.plusSeconds(hours * 3_600L)

    @Test
    fun `nuit du coucher au lever suivant`() {
        val events = listOf(SolarEvent(sunset = true, at = at(6)), SolarEvent(sunset = false, at = at(18)))
        assertEquals(listOf(at(6) to at(18)), nightRanges(start, end, startsAtNight = false, events = events))
    }

    @Test
    fun `fenetre commencee la nuit et finie la nuit`() {
        val events = listOf(SolarEvent(sunset = false, at = at(2)), SolarEvent(sunset = true, at = at(14)))
        assertEquals(
            listOf(start to at(2), at(14) to end),
            nightRanges(start, end, startsAtNight = true, events = events)
        )
    }

    @Test
    fun `sans evenement la fenetre est entierement de jour ou de nuit`() {
        assertEquals(emptyList<Pair<Instant, Instant>>(), nightRanges(start, end, false, emptyList()))
        assertEquals(listOf(start to end), nightRanges(start, end, true, emptyList()))
    }

    @Test
    fun `humidite et point de rosee medians par heure`() {
        val forecast = overviewForecast(
            WeatherModel.GFS to overviewHourly(List(24) { 20.0 }, humidity = List(24) { 50 }),
            WeatherModel.ECMWF to overviewHourly(List(24) { 20.0 }, humidity = List(24) { 60 }),
            WeatherModel.UKMO_GLOBAL to overviewHourly(List(24) { 20.0 }, humidity = List(24) { 70 })
        )
        val first = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, start, hourlyHorizonHours = 24).first()
        assertEquals(60, first.relativeHumidityPercent)
        assertEquals(12.0, first.dewPointC!!, 0.3)
    }

    @Test
    fun `sans humidite fournie, pas de valeur inventee`() {
        val forecast = overviewForecast(WeatherModel.GFS to overviewHourly(List(24) { 20.0 }))
        val first = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, start, hourlyHorizonHours = 24).first()
        assertNull(first.relativeHumidityPercent)
        assertNull(first.dewPointC)
    }
}

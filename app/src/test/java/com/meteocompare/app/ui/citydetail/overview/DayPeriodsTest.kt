package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayPeriodsTest {

    private val zone = ZoneId.of("America/New_York")

    private fun points(startUtc: String, conditions: List<WeatherCondition?>): List<SimplifiedTimelinePoint> {
        val start = Instant.parse(startUtc)
        return conditions.mapIndexed { index, condition ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(index * 3_600L), condition = condition)
        }
    }

    @Test
    fun `la nuit apres minuit est rattachee a la soiree et le lendemain matin est nomme`() {
        // 20 h à New York (UTC−4) : soirée, nuit, puis demain matin.
        val hourly = points("2026-10-16T00:00:00Z", List(14) { WeatherCondition.CLEAR })

        val periods = buildPeriodSummaries(hourly, emptyList(), zone)

        assertEquals(
            listOf(PeriodPhrase.THIS_EVENING, PeriodPhrase.OVERNIGHT, PeriodPhrase.TOMORROW_MORNING),
            periods.map { it.phrase }
        )
        assertEquals(listOf(4, 6, 4), periods.map { it.hourCount })
    }

    @Test
    fun `une precipitation domine des qu'elle couvre un tiers des heures`() {
        val kinds = listOf(SkyKind.CLOUDY, SkyKind.CLOUDY, SkyKind.CLOUDY, SkyKind.CLOUDY, SkyKind.RAIN, SkyKind.RAIN)
        assertEquals(SkyKind.RAIN, dominantSky(kinds))
    }

    @Test
    fun `une averse isolee ne colore pas toute la tranche`() {
        val kinds = listOf(SkyKind.CLEAR, SkyKind.CLEAR, SkyKind.CLEAR, SkyKind.CLEAR, SkyKind.CLEAR, SkyKind.SHOWERS)
        assertEquals(SkyKind.CLEAR, dominantSky(kinds))
    }

    @Test
    fun `a egalite la categorie la plus genante l'emporte`() {
        assertEquals(SkyKind.CLOUDY, dominantSky(listOf(SkyKind.CLEAR, SkyKind.CLOUDY)))
        assertEquals(SkyKind.SNOW, dominantSky(listOf(SkyKind.RAIN, SkyKind.SNOW)))
        assertNull(dominantSky(listOf(null, null)))
    }

    @Test
    fun `chaque condition WMO a une categorie sauf l'inconnue`() {
        WeatherCondition.entries.forEach { condition ->
            if (condition == WeatherCondition.UNKNOWN) assertNull(SkyKind.of(condition))
            else assertEquals(condition.name, true, SkyKind.of(condition) != null)
        }
    }

    @Test
    fun `le pic de pluie d'une tranche est l'heure la plus probable`() {
        val hourly = points("2026-10-15T16:00:00Z", List(3) { WeatherCondition.RAIN })
        fun hour(index: Int, percent: Int) = OverviewPrecipHour(
            instant = hourly[index].instant!!,
            likelihoodPercent = percent,
            wording = RainLikelihoodWording.CHANCE,
            familyShare = FamilyRainShare(0, 0),
            amountIfRainMm = 1.0,
            intensity = RainIntensity.LIGHT,
            isNight = false
        )
        val rain = listOf(hour(0, 20), hour(1, 70), hour(2, 40))

        val period = buildPeriodSummaries(hourly, rain, zone).single()

        assertEquals(70, period.peakRain?.likelihoodPercent)
        assertEquals(PeriodPhrase.THIS_AFTERNOON, period.phrase)
    }
}

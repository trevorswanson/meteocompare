package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewNarrativeTest {

    private val start = Instant.parse("2026-10-15T12:00:00Z")
    private val today = LocalDate.of(2026, 10, 15)

    private fun period(phrase: PeriodPhrase, sky: SkyKind?, hours: Int = 6) = PeriodSummary(
        period = DayPeriod.AFTERNOON,
        phrase = phrase,
        startIndex = 0,
        endIndex = hours - 1,
        sky = sky,
        peakRain = null
    )

    private fun hour(index: Int, percent: Int?, wording: RainLikelihoodWording?, wet: Boolean) = OverviewPrecipHour(
        instant = start.plusSeconds(index * 3_600L),
        likelihoodPercent = percent,
        wording = wording,
        familyShare = FamilyRainShare(if (wet) 1 else 0, 3),
        amountIfRainMm = if (wet) 1.0 else 0.0,
        intensity = if (wet) RainIntensity.LIGHT else RainIntensity.NONE,
        isNight = false
    )

    private fun narrative(
        periods: List<PeriodSummary> = emptyList(),
        precipitation: List<OverviewPrecipHour> = emptyList(),
        daily: List<SimplifiedTimelinePoint> = emptyList()
    ) = buildOverviewNarrative(periods, precipitation, daily, today)

    @Test
    fun `les tranches identiques fusionnent et seul le premier changement est decrit`() {
        val sky = narrative(
            periods = listOf(
                period(PeriodPhrase.THIS_AFTERNOON, SkyKind.CLEAR),
                period(PeriodPhrase.THIS_EVENING, SkyKind.PARTLY_CLOUDY),
                period(PeriodPhrase.OVERNIGHT, SkyKind.PARTLY_CLOUDY),
                period(PeriodPhrase.TOMORROW_MORNING, SkyKind.RAIN)
            )
        ).single() as NarrativeSentence.Sky

        assertEquals(SkyKind.CLEAR, sky.first)
        assertEquals(PeriodPhrase.THIS_AFTERNOON, sky.firstWhen)
        assertEquals(SkyKind.PARTLY_CLOUDY, sky.then)
        assertEquals(PeriodPhrase.THIS_EVENING, sky.thenWhen)
    }

    @Test
    fun `une premiere tranche d'une heure est ignoree`() {
        val sky = narrative(
            periods = listOf(
                period(PeriodPhrase.THIS_AFTERNOON, SkyKind.CLEAR, hours = 1),
                period(PeriodPhrase.THIS_EVENING, SkyKind.CLOUDY)
            )
        ).single() as NarrativeSentence.Sky

        assertEquals(SkyKind.CLOUDY, sky.first)
        assertTrue(sky.throughout)
    }

    @Test
    fun `un debut de pluie avec probabilite native est qualifie par son pic`() {
        val rain = narrative(
            precipitation = listOf(
                hour(0, 5, RainLikelihoodWording.CHANCE, wet = false),
                hour(1, 40, RainLikelihoodWording.CHANCE, wet = true),
                hour(2, 75, RainLikelihoodWording.CHANCE, wet = true)
            )
        ).single() as NarrativeSentence.RainStart

        assertEquals(start.plusSeconds(3_600L), rain.at)
        assertEquals(RainStartLikelihood.LIKELY, rain.likelihood)
    }

    @Test
    fun `un vote deterministe n'est jamais qualifie de probable`() {
        val rain = narrative(
            precipitation = listOf(
                hour(0, 0, RainLikelihoodWording.FAMILY_SHARE, wet = false),
                hour(1, 90, RainLikelihoodWording.FAMILY_SHARE, wet = true)
            )
        ).single() as NarrativeSentence.RainStart

        assertEquals(RainStartLikelihood.SOME_FORECASTS, rain.likelihood)
    }

    @Test
    fun `pas de phrase de pluie pour une pluie deja en cours ou peu probable`() {
        assertTrue(narrative(precipitation = listOf(hour(0, 80, RainLikelihoodWording.CHANCE, wet = true))).isEmpty())
        assertTrue(
            narrative(
                precipitation = listOf(
                    hour(0, 0, RainLikelihoodWording.CHANCE, wet = false),
                    hour(1, 10, RainLikelihoodWording.CHANCE, wet = true)
                )
            ).isEmpty()
        )
    }

    @Test
    fun `demain n'est mentionne qu'en cas d'ecart notable de maximale`() {
        fun day(offset: Long, high: Double) = SimplifiedTimelinePoint(date = today.plusDays(offset), tempMaxC = high)

        assertEquals(
            NarrativeSentence.TomorrowTrend(warmer = true, strong = false),
            narrative(daily = listOf(day(0, 15.0), day(1, 19.0))).single()
        )
        assertEquals(
            NarrativeSentence.TomorrowTrend(warmer = false, strong = true),
            narrative(daily = listOf(day(0, 22.0), day(1, 14.0))).single()
        )
        assertTrue(narrative(daily = listOf(day(0, 15.0), day(1, 16.5))).isEmpty())
    }

    @Test
    fun `l'ordre des phrases est ciel, pluie, tendance`() {
        val result = narrative(
            periods = listOf(period(PeriodPhrase.THIS_AFTERNOON, SkyKind.CLOUDY)),
            precipitation = listOf(
                hour(0, 0, RainLikelihoodWording.CHANCE, wet = false),
                hour(1, 65, RainLikelihoodWording.CHANCE, wet = true)
            )
        )

        assertTrue(result[0] is NarrativeSentence.Sky)
        assertTrue(result[1] is NarrativeSentence.RainStart)
        assertNull(result.getOrNull(2))
    }
}

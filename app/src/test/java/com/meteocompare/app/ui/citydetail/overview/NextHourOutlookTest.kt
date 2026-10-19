package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextHourOutlookTest {

    private val quarter = Instant.parse("2026-10-19T11:45:00Z")
    private val now = quarter.plusSeconds(4 * 60L) // 11:49

    /** Quarts d'heure se terminant à 11:45, 12:00, 12:15… avec ces cumuls. */
    private fun precipitation(vararg mm: Double, snowCm: Double = 0.0) = NextHourPrecipitation(
        fetchedAt = now,
        steps = mm.mapIndexed { index, value ->
            NextHourPrecipitation.Step(quarter.plusSeconds(index * 15 * 60L), value, if (value > 0) snowCm else 0.0)
        }
    )

    @Test
    fun `rien ne tombe dans l'heure, pas de section`() {
        assertNull(buildNextHourOutlook(precipitation(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), now))
    }

    @Test
    fun `donnees qui ne couvrent pas l'heure, pas de section`() {
        assertNull(buildNextHourOutlook(precipitation(0.0, 0.3, 0.3), now))
        assertNull(buildNextHourOutlook(null, now))
    }

    @Test
    fun `pluie qui commence puis continue`() {
        // Sec jusqu'à 12:15, pluie ensuite : début dans 26 minutes.
        val outlook = buildNextHourOutlook(precipitation(0.0, 0.0, 0.0, 0.3, 0.3, 0.3, 0.3), now)!!
        assertEquals(NextHourEvent.STARTING, outlook.event)
        assertEquals(26, outlook.minutes)
        assertEquals(RainIntensity.LIGHT, outlook.intensity)
        assertEquals(NEXT_HOUR_MINUTES + 1, outlook.ratesMmPerHour.size)
    }

    @Test
    fun `averse qui commence et s'arrete dans l'heure`() {
        val outlook = buildNextHourOutlook(precipitation(0.0, 0.0, 0.8, 0.0, 0.0, 0.0, 0.0), now)!!
        assertEquals(NextHourEvent.STARTING_AND_STOPPING, outlook.event)
        assertEquals(11, outlook.minutes)
        assertEquals(15, outlook.durationMinutes)
        assertEquals(RainIntensity.MODERATE, outlook.intensity)
    }

    @Test
    fun `pluie en cours qui s'arrete puis reprend`() {
        val outlook = buildNextHourOutlook(precipitation(0.2, 0.2, 0.0, 0.2, 0.2, 0.2, 0.2), now)!!
        assertEquals(NextHourEvent.STOPPING_BRIEFLY, outlook.event)
        assertEquals(11, outlook.minutes)
    }

    @Test
    fun `pluie en cours qui s'arrete`() {
        val outlook = buildNextHourOutlook(precipitation(0.2, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0), now)!!
        assertEquals(NextHourEvent.STOPPING, outlook.event)
        assertEquals(26, outlook.minutes)
    }

    @Test
    fun `pluie forte toute l'heure`() {
        val outlook = buildNextHourOutlook(precipitation(3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0), now)!!
        assertEquals(NextHourEvent.CONTINUING, outlook.event)
        assertEquals(RainIntensity.HEAVY, outlook.intensity)
    }

    @Test
    fun `neige quand l'essentiel de l'eau tombe en neige`() {
        val outlook = buildNextHourOutlook(precipitation(0.0, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4, snowCm = 0.28), now)!!
        assertTrue(outlook.isSnow)
    }

    private fun steps(vararg mm: Double) = precipitation(*mm).steps

    private val wetHour = doubleArrayOf(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2)

    @Test
    fun `sans modele natif, le best match seul, sans bande ni badge`() {
        val outlook = buildNextHourOutlook(precipitation(*wetHour), now)!!
        assertNull(outlook.band)
        assertNull(outlook.agreement)
    }

    @Test
    fun `deux sources d'une meme lignee ne font pas une dispersion`() {
        val members = mapOf(
            WeatherModel.AROME_FRANCE_HD to steps(*wetHour),
            WeatherModel.AROME_FRANCE to steps(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )
        val outlook = buildNextHourOutlook(precipitation(*wetHour).copy(members = members), now)!!
        assertNull(outlook.band)
        assertNull(outlook.agreement)
    }

    @Test
    fun `un modele qui ne couvre pas l'heure est ecarte`() {
        val members = mapOf(
            WeatherModel.AROME_FRANCE_HD to steps(*wetHour),
            WeatherModel.ICON_D2 to steps(0.2, 0.2)
        )
        assertNull(buildNextHourOutlook(precipitation(*wetHour).copy(members = members), now)!!.band)
    }

    @Test
    fun `modeles natifs concordants`() {
        val members = mapOf(
            WeatherModel.AROME_FRANCE_HD to steps(*wetHour),
            WeatherModel.ICON_D2 to steps(0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3)
        )
        val outlook = buildNextHourOutlook(precipitation(*wetHour).copy(members = members), now)!!
        assertEquals(AgreementWording.AGREE, outlook.agreement)
        assertNotNull(outlook.band)
        val band = outlook.band!!
        assertEquals(NEXT_HOUR_MINUTES + 1, band.size)
        // La prévision affichée reste dans l'enveloppe.
        outlook.ratesMmPerHour.forEachIndexed { minute, rate -> assertTrue(rate in band[minute]) }
        assertEquals(1.2, band[30].endInclusive, 1e-9)
    }

    @Test
    fun `modeles natifs en desaccord`() {
        val members = mapOf(
            WeatherModel.AROME_FRANCE_HD to steps(*wetHour),
            WeatherModel.ICON_D2 to steps(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )
        val outlook = buildNextHourOutlook(precipitation(*wetHour).copy(members = members), now)!!
        assertEquals(AgreementWording.DISAGREE, outlook.agreement)
        assertEquals(0.0, outlook.band!![30].start, 1e-9)
    }

    @Test
    fun `desaccord partiel sur la moitie de l'heure`() {
        val members = mapOf(
            WeatherModel.AROME_FRANCE_HD to steps(*wetHour),
            WeatherModel.ICON_D2 to steps(0.2, 0.2, 0.2, 0.0, 0.0, 0.2, 0.2)
        )
        val outlook = buildNextHourOutlook(precipitation(*wetHour).copy(members = members), now)!!
        assertEquals(AgreementWording.SOME_DISAGREEMENT, outlook.agreement)
    }

    @Test
    fun `reperes d'intensite au tiers, aux deux tiers et en haut`() {
        assertEquals(0f, nextHourRateFraction(0.0), 0.001f)
        assertEquals(1f / 3, nextHourRateFraction(1.0), 0.001f)
        assertEquals(2f / 3, nextHourRateFraction(4.0), 0.001f)
        assertEquals(1f, nextHourRateFraction(10.0), 0.001f)
        assertEquals(1f, nextHourRateFraction(50.0), 0.001f)
    }
}

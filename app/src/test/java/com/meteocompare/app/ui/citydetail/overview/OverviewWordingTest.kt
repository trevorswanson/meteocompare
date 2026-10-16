package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.ui.citydetail.ModelConsensusLevel
import com.meteocompare.app.ui.citydetail.PrecipitationSignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverviewWordingTest {

    @Test
    fun `un pourcentage n'est formule comme un risque que s'il vient de probabilites natives`() {
        PrecipitationSignalSource.entries.forEach { source ->
            val expected = if (source.usesProbability) {
                RainLikelihoodWording.CHANCE
            } else {
                RainLikelihoodWording.FAMILY_SHARE
            }
            assertEquals(source.name, expected, rainLikelihoodWording(source))
        }
    }

    @Test
    fun `un vote deterministe n'est jamais presente comme un risque`() {
        assertEquals(
            RainLikelihoodWording.FAMILY_SHARE,
            rainLikelihoodWording(PrecipitationSignalSource.MODEL_AGREEMENT)
        )
        assertNull(rainLikelihoodWording(null))
    }

    @Test
    fun `le niveau d'accord se traduit sans promesse de justesse`() {
        assertEquals(AgreementWording.AGREE, agreementWording(ModelConsensusLevel.HIGH))
        assertEquals(AgreementWording.SOME_DISAGREEMENT, agreementWording(ModelConsensusLevel.MEDIUM))
        assertEquals(AgreementWording.DISAGREE, agreementWording(ModelConsensusLevel.LOW))
        assertNull(agreementWording(null))
    }

    @Test
    fun `classes d'intensite de pluie horaire`() {
        val threshold = 0.1
        assertEquals(RainIntensity.NONE, rainIntensity(null, threshold))
        assertEquals(RainIntensity.NONE, rainIntensity(0.1, threshold))
        assertEquals(RainIntensity.NONE, rainIntensity(Double.NaN, threshold))
        assertEquals(RainIntensity.LIGHT, rainIntensity(0.2, threshold))
        assertEquals(RainIntensity.MODERATE, rainIntensity(2.5, threshold))
        assertEquals(RainIntensity.HEAVY, rainIntensity(7.6, threshold))
    }

    @Test
    fun `l'opacite suit la probabilite native ou a defaut la part de lignees humides`() {
        val start = OVERVIEW_TEST_START
        fun hour(wording: RainLikelihoodWording?, percent: Int?, share: FamilyRainShare) = OverviewPrecipHour(
            instant = start,
            likelihoodPercent = percent,
            wording = wording,
            familyShare = share,
            amountIfRainMm = 1.0,
            intensity = RainIntensity.LIGHT,
            isNight = false
        )

        assertEquals(0.7f, likelihoodFraction(hour(RainLikelihoodWording.CHANCE, 70, FamilyRainShare(1, 4))), 1e-6f)
        assertEquals(0.25f, likelihoodFraction(hour(RainLikelihoodWording.FAMILY_SHARE, 70, FamilyRainShare(1, 4))), 1e-6f)
        assertEquals(0.4f, likelihoodFraction(hour(null, 40, FamilyRainShare(0, 0))), 1e-6f)
        assertEquals(1f, likelihoodFraction(hour(RainLikelihoodWording.CHANCE, 150, FamilyRainShare(0, 0))), 1e-6f)
    }
}

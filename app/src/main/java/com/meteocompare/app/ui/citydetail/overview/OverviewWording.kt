package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.ui.citydetail.ModelConsensusLevel
import com.meteocompare.app.ui.citydetail.PrecipitationSignalSource

/**
 * Règles de formulation de l'aperçu simple.
 *
 * Elles garantissent qu'un accord entre modèles déterministes n'est jamais
 * présenté comme une probabilité, et qu'un accord élevé n'est jamais présenté
 * comme une garantie de justesse. Les composables traduisent ces choix en
 * ressources texte ; la règle elle-même reste testable sur la JVM.
 */
internal enum class RainLikelihoodWording {
    /**
     * « 70 % de risque de pluie » : uniquement si au moins une partie du signal
     * provient de probabilités natives des modèles. Accompagné d'une mention
     * « moyenne des modèles ».
     */
    CHANCE,

    /**
     * « 3 lignées sur 4 prévoient de la pluie » : le pourcentage n'est qu'une
     * part de votes déterministes et ne doit pas être affiché comme un risque.
     */
    FAMILY_SHARE
}

internal fun rainLikelihoodWording(source: PrecipitationSignalSource?): RainLikelihoodWording? =
    when (source) {
        PrecipitationSignalSource.MODEL_PROBABILITY,
        PrecipitationSignalSource.MIXED -> RainLikelihoodWording.CHANCE
        PrecipitationSignalSource.MODEL_AGREEMENT -> RainLikelihoodWording.FAMILY_SHARE
        null -> null
    }

/** Libellé d'accord : décrit la convergence, jamais la justesse. */
internal enum class AgreementWording {
    AGREE,
    SOME_DISAGREEMENT,
    DISAGREE
}

internal fun agreementWording(level: ModelConsensusLevel?): AgreementWording? = when (level) {
    ModelConsensusLevel.HIGH -> AgreementWording.AGREE
    ModelConsensusLevel.MEDIUM -> AgreementWording.SOME_DISAGREEMENT
    ModelConsensusLevel.LOW -> AgreementWording.DISAGREE
    null -> null
}

/**
 * Classes d'intensité de pluie horaire, d'après les seuils usuels de taux de
 * pluie (faible < 2,5 mm/h, modérée < 7,6 mm/h, forte au-delà). Ce sont des
 * repères de présentation ajustables, pas des seuils de vigilance.
 */
internal enum class RainIntensity { NONE, LIGHT, MODERATE, HEAVY }

internal const val LIGHT_RAIN_MAX_MM_PER_HOUR = 2.5
internal const val MODERATE_RAIN_MAX_MM_PER_HOUR = 7.6

internal fun rainIntensity(mmPerHour: Double?, thresholdMm: Double): RainIntensity = when {
    mmPerHour == null || !mmPerHour.isFinite() || mmPerHour <= thresholdMm -> RainIntensity.NONE
    mmPerHour < LIGHT_RAIN_MAX_MM_PER_HOUR -> RainIntensity.LIGHT
    mmPerHour < MODERATE_RAIN_MAX_MM_PER_HOUR -> RainIntensity.MODERATE
    else -> RainIntensity.HEAVY
}

/**
 * Part du signal pluie utilisée pour l'opacité : probabilité moyenne quand
 * elle existe, sinon part des lignées humides.
 */
internal fun likelihoodFraction(hour: OverviewPrecipHour): Float = when (hour.wording) {
    RainLikelihoodWording.CHANCE -> (hour.likelihoodPercent ?: 0) / 100f
    RainLikelihoodWording.FAMILY_SHARE, null -> {
        val share = hour.familyShare
        if (share.total > 0) share.wet.toFloat() / share.total else (hour.likelihoodPercent ?: 0) / 100f
    }
}.coerceIn(0f, 1f)

internal fun intensityHeight(intensity: RainIntensity): Float = when (intensity) {
    RainIntensity.NONE -> 0f
    RainIntensity.LIGHT -> 0.35f
    RainIntensity.MODERATE -> 0.65f
    RainIntensity.HEAVY -> 1f
}

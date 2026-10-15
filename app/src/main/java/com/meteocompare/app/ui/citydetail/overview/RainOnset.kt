package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.PrecipitationThresholds
import java.time.Instant

/**
 * Fenêtre de début de pluie selon les lignées indépendantes.
 *
 * [median] est l'heure de début médiane parmi les lignées qui prévoient de la
 * pluie ; [earliest] et [latest] encadrent les débuts prévus. Ce n'est pas un
 * intervalle de probabilité : seulement l'étendue des scénarios déterministes.
 */
internal data class RainOnsetWindow(
    val earliest: Instant,
    val median: Instant,
    val latest: Instant,
    val familiesWithRain: Int,
    val familiesWithData: Int
) {
    /** Vrai lorsque les lignées divergent sur l'heure de début. */
    val hasTimingSpread: Boolean get() = earliest != latest
}

/**
 * Calcule la fenêtre de début de pluie sur la grille [timestamps].
 *
 * Retourne `null` si moins de deux lignées ont des données (aucun désaccord
 * mesurable) ou si aucune lignée ne prévoit de pluie au-dessus de [thresholdMm].
 * Pour un nombre pair de lignées pluvieuses, la médiane retenue est la plus
 * précoce des deux valeurs centrales : le résultat reste un horodatage réel de
 * la grille et ne retarde jamais artificiellement l'annonce.
 */
internal fun rainOnsetWindow(
    timestamps: List<Instant>,
    traces: List<FamilyTrace>,
    thresholdMm: Double = PrecipitationThresholds.HOURLY_OCCURRENCE_MM
): RainOnsetWindow? {
    val withData = traces.filter(FamilyTrace::hasData)
    if (withData.size < 2) return null

    val onsets = withData.mapNotNull { trace ->
        val index = trace.values.indexOfFirst { value -> value != null && value > thresholdMm }
        timestamps.getOrNull(index)
    }.sorted()
    if (onsets.isEmpty()) return null

    return RainOnsetWindow(
        earliest = onsets.first(),
        median = onsets[(onsets.size - 1) / 2],
        latest = onsets.last(),
        familiesWithRain = onsets.size,
        familiesWithData = withData.size
    )
}

/**
 * Nombre de lignées humides / disposant de données à l'échéance [index].
 * Sert au libellé « 3 lignées sur 4 prévoient de la pluie » quand aucune
 * probabilité native n'est disponible.
 */
internal data class FamilyRainShare(val wet: Int, val total: Int)

internal fun familyRainShare(
    traces: List<FamilyTrace>,
    index: Int,
    thresholdMm: Double = PrecipitationThresholds.HOURLY_OCCURRENCE_MM
): FamilyRainShare {
    val values = traces.mapNotNull { it.values.getOrNull(index) }
    return FamilyRainShare(wet = values.count { it > thresholdMm }, total = values.size)
}

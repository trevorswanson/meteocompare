package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.PrecipitationThresholds

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

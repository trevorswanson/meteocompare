package com.meteocompare.app.domain.model

import java.time.Instant

/**
 * Précipitations au pas de 15 minutes pour la prochaine heure (produit
 * « best match » d'Open-Meteo, indépendant de la comparaison des modèles).
 */
data class NextHourPrecipitation(
    val fetchedAt: Instant,
    val steps: List<Step>
) {
    /** Cumul sur le quart d'heure qui se termine à [endsAt] (convention Open-Meteo). */
    data class Step(
        val endsAt: Instant,
        val precipitationMm: Double,
        val snowfallCm: Double = 0.0
    )
}

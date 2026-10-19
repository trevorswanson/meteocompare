package com.meteocompare.app.domain.model

import java.time.Instant

/**
 * Précipitations au pas de 15 minutes pour la prochaine heure.
 *
 * [steps] vient du produit « best match » d'Open-Meteo et porte la prévision
 * affichée. [members] ne contient que des modèles qui calculent réellement au
 * pas de 15 minutes ([NATIVE_15_MINUTE_MODELS]) et qui ont renvoyé des
 * valeurs pour la position ; ils servent à la dispersion, jamais au texte.
 */
data class NextHourPrecipitation(
    val fetchedAt: Instant,
    val steps: List<Step>,
    val members: Map<WeatherModel, List<Step>> = emptyMap()
) {
    /** Cumul sur le quart d'heure qui se termine à [endsAt] (convention Open-Meteo). */
    data class Step(
        val endsAt: Instant,
        val precipitationMm: Double,
        val snowfallCm: Double = 0.0
    )
}

/**
 * Modèles dont Open-Meteo documente une sortie native au pas de 15 minutes.
 * Pour tous les autres, la série `minutely_15` n'est qu'une interpolation des
 * données horaires et ne doit pas servir à mesurer un accord à court terme.
 * Hors de leur domaine, Open-Meteo renvoie des valeurs nulles : la couverture
 * est vérifiée à l'usage. Ordre = préférence au sein d'une même lignée.
 */
val NATIVE_15_MINUTE_MODELS: List<WeatherModel> = listOf(
    WeatherModel.AROME_FRANCE_HD,
    WeatherModel.AROME_FRANCE,
    WeatherModel.ICON_D2,
    WeatherModel.HRRR_CONUS
)

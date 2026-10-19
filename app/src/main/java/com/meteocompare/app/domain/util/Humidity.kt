package com.meteocompare.app.domain.util

import kotlin.math.ln

/**
 * Point de rosée (°C) à partir de la température et de l'humidité relative,
 * formule de Magnus (coefficients Alduchov & Eskridge 1996). Écart typique
 * inférieur à 0,2 °C entre −40 °C et 50 °C ; null hors domaine utile.
 */
fun dewPointC(temperatureC: Double?, relativeHumidityPercent: Int?): Double? {
    if (temperatureC == null || !temperatureC.isFinite() || relativeHumidityPercent == null) return null
    if (relativeHumidityPercent !in 1..100) return null
    val gamma = ln(relativeHumidityPercent / 100.0) + MAGNUS_B * temperatureC / (MAGNUS_C + temperatureC)
    return MAGNUS_C * gamma / (MAGNUS_B - gamma)
}

private const val MAGNUS_B = 17.625
private const val MAGNUS_C = 243.04

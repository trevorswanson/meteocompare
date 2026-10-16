package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

/**
 * Prévision horaire d'une lignée numérique indépendante (ex. ECMWF = IFS + AIFS),
 * alignée sur une grille d'horodatages commune.
 *
 * Ces courbes servent uniquement à montrer, derrière la prévision principale,
 * les autres scénarios déterministes disponibles. Ce ne sont PAS des membres
 * d'ensemble : leur dispersion décrit un désaccord entre lignées, pas une
 * probabilité calibrée.
 *
 * @property values Une valeur par horodatage de la grille ; `null` lorsque la
 *           lignée ne fournit rien à cette échéance (fin d'horizon, donnée
 *           absente). Aucune valeur n'est extrapolée.
 */
internal data class FamilyTrace(
    val group: ForecastConsensus.Group,
    val models: List<WeatherModel>,
    val values: List<Double?>
) {
    val hasData: Boolean get() = values.any { it != null }
}

/** Nombre maximal de courbes secondaires affichées pour rester lisible. */
internal const val MAX_OVERVIEW_FAMILY_TRACES = 8

/** Température 2 m par lignée, filtrée par les bornes physiques du projet. */
internal fun buildFamilyTemperatureTraces(
    forecast: CityForecast,
    timestamps: List<Instant>,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): List<FamilyTrace> = buildFamilyTraces(
    forecast = forecast,
    timestamps = timestamps,
    maxFamilies = maxFamilies,
    selector = { hourly, index -> ForecastPhysicalLimits.temperature(hourly.temperature2m.getOrNull(index)) }
)

/** Cumul horaire de précipitations par lignée (mm), mêmes règles. */
internal fun buildFamilyPrecipitationTraces(
    forecast: CityForecast,
    timestamps: List<Instant>,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): List<FamilyTrace> = buildFamilyTraces(
    forecast = forecast,
    timestamps = timestamps,
    maxFamilies = maxFamilies,
    selector = { hourly, index -> ForecastPhysicalLimits.precipitation(hourly.precipitation.getOrNull(index)) }
)

/**
 * Construit une courbe par lignée [ForecastConsensus.Group].
 *
 * À chaque échéance, la valeur de la lignée est la médiane pondérée de ses
 * variantes, avec les mêmes poids que le consensus robuste
 * ([ForecastConsensus.familyBalancedWeights]). Les lignées sont ordonnées de
 * façon déterministe (ordre de déclaration de [ForecastConsensus.Group]) ; si
 * elles dépassent [maxFamilies], on garde celles qui couvrent le plus
 * d'échéances.
 */
internal fun buildFamilyTraces(
    forecast: CityForecast,
    timestamps: List<Instant>,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES,
    selector: (HourlyForecast, Int) -> Double?
): List<FamilyTrace> {
    if (timestamps.isEmpty() || maxFamilies <= 0) return emptyList()

    val indexByModel: Map<WeatherModel, Map<Instant, Int>> = forecast.seriesByModel
        .mapValues { (_, series) ->
            buildMap {
                series.hourly.timestamps.forEachIndexed { index, instant -> putIfAbsent(instant, index) }
            }
        }

    val traces = forecast.seriesByModel.keys
        .groupBy(ForecastConsensus::groupFor)
        .map { (group, models) ->
            val sortedModels = models.sortedBy { it.ordinal }
            val values = timestamps.map { instant ->
                val entries = sortedModels.mapNotNull { model ->
                    val series = forecast.seriesByModel[model] ?: return@mapNotNull null
                    val index = indexByModel[model]?.get(instant) ?: return@mapNotNull null
                    selector(series.hourly, index)?.let { ForecastConsensus.Entry(model, it) }
                }
                familyValue(entries)
            }
            FamilyTrace(group = group, models = sortedModels, values = values)
        }
        .filter(FamilyTrace::hasData)
        .sortedBy { it.group.ordinal }

    if (traces.size <= maxFamilies) return traces
    return traces
        .sortedWith(
            compareByDescending<FamilyTrace> { trace -> trace.values.count { it != null } }
                .thenBy { it.group.ordinal }
        )
        .take(maxFamilies)
        .sortedBy { it.group.ordinal }
}

private fun familyValue(entries: List<ForecastConsensus.Entry<Double>>): Double? {
    if (entries.isEmpty()) return null
    if (entries.size == 1) return entries.first().value
    val weights = ForecastConsensus.familyBalancedWeights(entries.map { it.model })
    return ForecastConsensus.weightedMedian(
        entries.mapNotNull { entry ->
            weights[entry.model]?.let { ForecastConsensus.WeightedEntry(entry.model, entry.value, it) }
        }
    )
}

/** Écart maximal entre lignées à une même échéance (au moins deux valeurs). */
internal fun maxFamilySpread(traces: List<FamilyTrace>): Double? {
    val length = traces.maxOfOrNull { it.values.size } ?: return null
    var best: Double? = null
    for (index in 0 until length) {
        val values = traces.mapNotNull { it.values.getOrNull(index)?.takeIf(Double::isFinite) }
        if (values.size < 2) continue
        val spread = values.max() - values.min()
        best = max(best ?: spread, spread)
    }
    return best
}

/** Étendue des températures d'une journée entre lignées : plus basse minimale → plus haute maximale. */
internal data class DailyFamilyRange(val low: Double, val high: Double)

/**
 * Pour chaque jour, minimale et maximale de chaque lignée (médiane de ses
 * variantes, comme les courbes horaires), puis l'enveloppe entre lignées.
 * Une seule lignée suffit à donner une étendue (égale à sa propre valeur).
 */
internal fun buildDailyFamilyRanges(forecast: CityForecast): Map<LocalDate, DailyFamilyRange> {
    data class DayValues(val lows: MutableList<ForecastConsensus.Entry<Double>>, val highs: MutableList<ForecastConsensus.Entry<Double>>)

    val byGroupAndDate = mutableMapOf<ForecastConsensus.Group, MutableMap<LocalDate, DayValues>>()
    forecast.seriesByModel.forEach { (model, series) ->
        val group = ForecastConsensus.groupFor(model)
        series.daily.dates.forEachIndexed { index, date ->
            val (max, min) = ForecastPhysicalLimits.dailyTemperaturePair(
                series.daily.tempMax.getOrNull(index),
                series.daily.tempMin.getOrNull(index)
            )
            val values = byGroupAndDate.getOrPut(group) { mutableMapOf() }
                .getOrPut(date) { DayValues(mutableListOf(), mutableListOf()) }
            min?.let { values.lows += ForecastConsensus.Entry(model, it) }
            max?.let { values.highs += ForecastConsensus.Entry(model, it) }
        }
    }

    val result = mutableMapOf<LocalDate, DailyFamilyRange>()
    byGroupAndDate.values.forEach { dates ->
        dates.forEach { (date, values) ->
            val low = familyValue(values.lows)
            val high = familyValue(values.highs)
            val current = result[date]
            val newLow = listOfNotNull(current?.low, low, high).minOrNull() ?: return@forEach
            val newHigh = listOfNotNull(current?.high, high, low).maxOrNull() ?: return@forEach
            result[date] = DailyFamilyRange(newLow, newHigh)
        }
    }
    return result
}

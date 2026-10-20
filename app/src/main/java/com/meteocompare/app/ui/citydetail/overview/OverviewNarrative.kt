package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.util.forecastSolarWindow
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Phrases de la synthèse « Prochaines 24 heures », sous forme structurée.
 * Le rendu en texte localisé est fait par l'interface ; ce découpage reste
 * testable sur la JVM et ne formule jamais un vote déterministe comme un risque.
 */
internal sealed interface NarrativeSentence {
    /** « Ciel dégagé cet après-midi, puis nuageux ce soir. » */
    data class Sky(
        val first: SkyKind,
        val firstWhen: PeriodPhrase,
        val then: SkyKind? = null,
        val thenWhen: PeriodPhrase? = null,
        val scope: OutlookScope = OutlookScope.NEXT_24_HOURS
    ) : NarrativeSentence {
        /** Une seule catégorie sur toute la fenêtre. */
        val throughout: Boolean get() = then == null
    }

    /** Début de pluie ; [likelihood] dépend de l'origine du signal. */
    data class RainStart(
        val at: Instant,
        val likelihood: RainStartLikelihood,
        val snow: Boolean = false
    ) : NarrativeSentence

    /** Écart notable entre la maximale de demain et celle d'aujourd'hui. */
    data class TomorrowTrend(val warmer: Boolean, val strong: Boolean) : NarrativeSentence

}

internal enum class RainStartLikelihood {
    /** Probabilité native ≥ 60 %. */
    LIKELY,

    /** Probabilité native entre 30 et 60 %. */
    POSSIBLE,

    /** Aucune probabilité native : seulement une partie des modèles. */
    SOME_FORECASTS
}

internal data class SolarEvent(val sunset: Boolean, val at: Instant)

internal const val RAIN_LIKELY_PERCENT = 60
internal const val RAIN_POSSIBLE_PERCENT = 30
internal const val NOTABLE_TREND_C = 3.0
internal const val STRONG_TREND_C = 6.0

internal fun buildOverviewNarrative(
    periods: List<PeriodSummary>,
    precipitation: List<OverviewPrecipHour>,
    daily: List<SimplifiedTimelinePoint>,
    today: LocalDate,
    scope: OutlookScope = OutlookScope.NEXT_24_HOURS
): List<NarrativeSentence> = listOfNotNull(
    skySentence(periods, scope),
    rainStartSentence(precipitation, scope),
    // « Demain » n'a de sens que pour les 24 prochaines heures.
    if (scope == OutlookScope.NEXT_24_HOURS) tomorrowTrend(daily, today) else null
)

private fun skySentence(periods: List<PeriodSummary>, scope: OutlookScope): NarrativeSentence.Sky? {
    // Une première tranche d'une seule heure décrit « maintenant », déjà
    // affiché au-dessus : on commence à la tranche suivante quand elle existe.
    val usable = periods
        .filter { it.sky != null }
        .let { list ->
            if (scope == OutlookScope.NEXT_24_HOURS && list.size > 1 && list.first().hourCount < 2) list.drop(1) else list
        }
    if (usable.isEmpty()) return null

    val segments = mutableListOf<PeriodSummary>()
    usable.forEach { period ->
        if (segments.lastOrNull()?.sky != period.sky) segments += period
    }
    val first = segments.first()
    val second = segments.getOrNull(1)
    return NarrativeSentence.Sky(
        first = requireNotNull(first.sky),
        firstWhen = first.phrase,
        then = second?.sky,
        thenWhen = second?.phrase,
        scope = scope
    )
}

private fun rainStartSentence(hours: List<OverviewPrecipHour>, scope: OutlookScope): NarrativeSentence.RainStart? {
    val firstWet = hours.indexOfFirst(OverviewPrecipHour::isWet)
    if (firstWet < 0) return null
    // Pluie dès maintenant : la phrase de ciel et « Maintenant » la disent déjà.
    if (firstWet == 0 && scope == OutlookScope.NEXT_24_HOURS) return null
    val hour = hours[firstWet]
    val likelihood = when (hour.wording) {
        RainLikelihoodWording.CHANCE -> {
            // Le pic des trois heures suivantes évite qu'une première heure
            // marginale masque un épisode bien plus probable.
            val peak = hours.subList(firstWet, minOf(hours.size, firstWet + 3))
                .maxOf { it.likelihoodPercent ?: 0 }
            when {
                peak >= RAIN_LIKELY_PERCENT -> RainStartLikelihood.LIKELY
                peak >= RAIN_POSSIBLE_PERCENT -> RainStartLikelihood.POSSIBLE
                else -> return null
            }
        }
        RainLikelihoodWording.FAMILY_SHARE, null -> RainStartLikelihood.SOME_FORECASTS
    }
    return NarrativeSentence.RainStart(hour.instant, likelihood, snow = hour.isSnow)
}

private fun tomorrowTrend(
    daily: List<SimplifiedTimelinePoint>,
    today: LocalDate
): NarrativeSentence.TomorrowTrend? {
    val todayHigh = daily.firstOrNull { it.date == today }?.tempMaxC ?: return null
    val tomorrowHigh = daily.firstOrNull { it.date == today.plusDays(1) }?.tempMaxC ?: return null
    val delta = tomorrowHigh - todayHigh
    if (!delta.isFinite() || abs(delta) < NOTABLE_TREND_C) return null
    return NarrativeSentence.TomorrowTrend(warmer = delta > 0, strong = abs(delta) >= STRONG_TREND_C)
}

/** Levers et couchers entre [start] et [end] inclus, dans l'ordre. */
internal fun solarEventsBetween(forecast: CityForecast, start: Instant, end: Instant, zone: ZoneId): List<SolarEvent> {
    val firstDay = start.atZone(zone).toLocalDate().minusDays(1)
    val lastDay = end.atZone(zone).toLocalDate().plusDays(1)
    return generateSequence(firstDay) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .map { forecastSolarWindow(forecast, it) }
        .flatMap { window ->
            listOfNotNull(
                window.sunrise?.let { SolarEvent(sunset = false, at = it) },
                window.sunset?.let { SolarEvent(sunset = true, at = it) }
            )
        }
        .filter { !it.at.isBefore(start) && !it.at.isAfter(end) }
        .distinctBy { it.at }
        .sortedBy { it.at }
        .toList()
}

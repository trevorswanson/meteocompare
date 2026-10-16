package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Tranches de la journée affichées au-dessus de la courbe horaire. */
internal enum class DayPeriod(val hours: IntRange) {
    OVERNIGHT(0..5),
    MORNING(6..11),
    AFTERNOON(12..17),
    EVENING(18..23);

    companion object {
        fun ofHour(hour: Int): DayPeriod = entries.first { hour in it.hours }
    }
}

/**
 * Catégorie de ciel simplifiée pour le texte de synthèse. L'ordre de
 * déclaration est la gravité croissante : à égalité, la plus gênante l'emporte.
 */
internal enum class SkyKind(val isPrecipitation: Boolean) {
    CLEAR(false),
    PARTLY_CLOUDY(false),
    CLOUDY(false),
    FOG(false),
    DRIZZLE(true),
    SHOWERS(true),
    RAIN(true),
    SNOW(true),
    FREEZING_RAIN(true),
    THUNDERSTORM(true);

    companion object {
        fun of(condition: WeatherCondition?): SkyKind? = when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> CLEAR
            WeatherCondition.PARTLY_CLOUDY -> PARTLY_CLOUDY
            WeatherCondition.OVERCAST -> CLOUDY
            WeatherCondition.FOG -> FOG
            WeatherCondition.DRIZZLE -> DRIZZLE
            WeatherCondition.RAIN_SHOWERS -> SHOWERS
            WeatherCondition.RAIN -> RAIN
            WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> SNOW
            WeatherCondition.FREEZING_RAIN -> FREEZING_RAIN
            WeatherCondition.THUNDERSTORM -> THUNDERSTORM
            WeatherCondition.UNKNOWN, null -> null
        }
    }
}

/** Formulation temporelle d'une tranche, relative au jour courant de la ville. */
internal enum class PeriodPhrase {
    THIS_MORNING,
    THIS_AFTERNOON,
    THIS_EVENING,
    OVERNIGHT,
    TOMORROW_MORNING,
    TOMORROW_AFTERNOON,
    TOMORROW_EVENING
}

/**
 * Une tranche de la fenêtre horaire.
 *
 * @property startIndex / [endIndex] indices inclusifs dans les points horaires.
 * @property sky catégorie dominante (null si aucune condition connue).
 * @property peakRain heure la plus pluvieuse de la tranche, pour l'en-tête.
 */
internal data class PeriodSummary(
    val period: DayPeriod,
    val phrase: PeriodPhrase,
    val startIndex: Int,
    val endIndex: Int,
    val sky: SkyKind?,
    val peakRain: OverviewPrecipHour?
) {
    val hourCount: Int get() = endIndex - startIndex + 1
}

/**
 * Découpe les points horaires en tranches Nuit / Matin / Après-midi / Soirée
 * dans le fuseau de la ville. Les heures 0–5 appartiennent à la nuit qui suit
 * la soirée précédente.
 */
internal fun buildPeriodSummaries(
    points: List<SimplifiedTimelinePoint>,
    precipitation: List<OverviewPrecipHour>,
    zone: ZoneId
): List<PeriodSummary> {
    val instants = points.map { it.instant ?: return emptyList() }
    if (instants.isEmpty()) return emptyList()
    val today = instants.first().atZone(zone).toLocalDate()

    data class Key(val period: DayPeriod, val date: LocalDate)

    val keys = instants.map { instant ->
        val local = instant.atZone(zone)
        val period = DayPeriod.ofHour(local.hour)
        // La nuit 0–5 h est rattachée à la soirée de la veille.
        val anchor = if (period == DayPeriod.OVERNIGHT) local.toLocalDate().minusDays(1) else local.toLocalDate()
        Key(period, anchor)
    }

    val result = mutableListOf<PeriodSummary>()
    var start = 0
    for (index in 1..keys.size) {
        if (index < keys.size && keys[index] == keys[start]) continue
        val key = keys[start]
        val range = start until index
        result += PeriodSummary(
            period = key.period,
            phrase = phraseFor(key.period, ChronoUnit.DAYS.between(today, key.date)),
            startIndex = range.first,
            endIndex = range.last,
            sky = dominantSky(range.map { SkyKind.of(points[it].condition) }),
            peakRain = range.mapNotNull { precipitation.getOrNull(it) }.maxByOrNull(::likelihoodFraction)
        )
        start = index
    }
    return result
}

private fun phraseFor(period: DayPeriod, dayOffset: Long): PeriodPhrase = when (period) {
    DayPeriod.OVERNIGHT -> PeriodPhrase.OVERNIGHT
    DayPeriod.MORNING -> if (dayOffset <= 0) PeriodPhrase.THIS_MORNING else PeriodPhrase.TOMORROW_MORNING
    DayPeriod.AFTERNOON -> if (dayOffset <= 0) PeriodPhrase.THIS_AFTERNOON else PeriodPhrase.TOMORROW_AFTERNOON
    DayPeriod.EVENING -> if (dayOffset <= 0) PeriodPhrase.THIS_EVENING else PeriodPhrase.TOMORROW_EVENING
}

/**
 * Catégorie dominante : une précipitation l'emporte dès qu'elle couvre au
 * moins un tiers des heures connues (au moins une heure), sinon la catégorie
 * la plus fréquente ; à égalité, la plus gênante.
 */
internal fun dominantSky(kinds: List<SkyKind?>): SkyKind? {
    val known = kinds.filterNotNull()
    if (known.isEmpty()) return null
    val counts = known.groupingBy { it }.eachCount()
    val minimumWetHours = maxOf(1, (known.size + 2) / 3)
    val wet = counts.filterKeys(SkyKind::isPrecipitation)
    val pool = if (wet.values.sum() >= minimumWetHours) wet else counts.filterKeys { !it.isPrecipitation }
    return pool.entries
        .maxWithOrNull(compareBy<Map.Entry<SkyKind, Int>> { it.value }.thenBy { it.key.ordinal })
        ?.key
}

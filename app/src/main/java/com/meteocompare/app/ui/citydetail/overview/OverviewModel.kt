package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Une heure de la frise pluie de l'aperçu simple. */
internal data class OverviewPrecipHour(
    val instant: Instant,
    /** Pourcentage du consensus existant ; sa formulation dépend de [wording]. */
    val likelihoodPercent: Int?,
    val wording: RainLikelihoodWording?,
    val familyShare: FamilyRainShare,
    /** Quantité si pluie (médiane des seuls scénarios humides). */
    val amountIfRainMm: Double?,
    val intensity: RainIntensity,
    val isNight: Boolean?,
    /** Neige plutôt que pluie à cette heure (condition consensuelle). */
    val isSnow: Boolean = false
) {
    val isWet: Boolean get() = intensity != RainIntensity.NONE && (likelihoodPercent ?: 0) > 0
}

/**
 * Données de l'aperçu simple (page principale).
 *
 * Tout provient de sorties déjà calculées (chronologie synthétique, moteur
 * sélectionné, accord brut) ; seules les courbes par lignée, les tranches de
 * la journée et la synthèse en texte sont dérivées ici, sans nouvelle logique
 * de prévision.
 */
internal data class OverviewModel(
    val hourly: List<SimplifiedTimelinePoint>,
    val daily: List<SimplifiedTimelinePoint>,
    val agreement: AgreementWording?,
    val temperatureTraces: List<FamilyTrace>,
    val precipitation: List<OverviewPrecipHour>,
    val periods: List<PeriodSummary>,
    val narrative: List<NarrativeSentence>,
    val solarEvent: SolarEvent?,
    /** Étendue min → max des lignées pour chaque jour (barres « autres modèles »). */
    val dailyRanges: Map<LocalDate, DailyFamilyRange> = emptyMap(),
    /** Levers et couchers dans la fenêtre horaire (repères et nuit sur la courbe). */
    val timelineSolarEvents: List<SolarEvent> = emptyList()
) {
    val today: SimplifiedTimelinePoint? get() = daily.firstOrNull()
    val hasRain: Boolean get() = precipitation.any(OverviewPrecipHour::isWet)
    val timestamps: List<Instant> get() = hourly.mapNotNull(SimplifiedTimelinePoint::instant)
}

/** Version courte des « Prochaines 24 heures » pour une journée, de minuit à minuit. */
internal data class DayOutlook(
    val date: LocalDate,
    val hourly: List<SimplifiedTimelinePoint>,
    val temperatureTraces: List<FamilyTrace>,
    val periods: List<PeriodSummary>,
    val narrative: List<NarrativeSentence>,
    val solarEvents: List<SolarEvent> = emptyList()
)

internal fun buildOverviewModel(
    forecast: CityForecast,
    hourly: List<SimplifiedTimelinePoint>,
    daily: List<SimplifiedTimelinePoint>,
    now: Instant,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): OverviewModel {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val core = hourlyCore(forecast, hourly, zone, OutlookScope.NEXT_24_HOURS, maxFamilies)
    val dailyPoints = daily.filter { it.date != null }
    val solarEvent = nextSolarEvent(forecast, now, zone)

    return OverviewModel(
        hourly = core.hourly,
        daily = dailyPoints,
        agreement = agreementWording(
            dailyPoints.firstOrNull()?.consensusLevel ?: core.hourly.firstOrNull()?.consensusLevel
        ),
        temperatureTraces = core.traces,
        precipitation = core.precipitation,
        periods = core.periods,
        narrative = buildOverviewNarrative(
            periods = core.periods,
            precipitation = core.precipitation,
            daily = dailyPoints,
            today = now.atZone(zone).toLocalDate(),
            solarEvent = solarEvent
        ),
        solarEvent = solarEvent,
        dailyRanges = buildDailyFamilyRanges(forecast),
        timelineSolarEvents = core.solarEvents
    )
}

/**
 * Journée [date] de minuit à minuit, à partir des points horaires de cette
 * journée (même chronologie synthétique que les 24 prochaines heures).
 */
internal fun buildDayOutlook(
    forecast: CityForecast,
    date: LocalDate,
    hourly: List<SimplifiedTimelinePoint>,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): DayOutlook {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val dayHourly = hourly.filter { it.instant?.atZone(zone)?.toLocalDate() == date }
    val core = hourlyCore(forecast, dayHourly, zone, OutlookScope.SINGLE_DAY, maxFamilies)
    return DayOutlook(
        date = date,
        hourly = core.hourly,
        temperatureTraces = core.traces,
        periods = core.periods,
        narrative = buildOverviewNarrative(
            periods = core.periods,
            precipitation = core.precipitation,
            daily = emptyList(),
            today = date,
            // Lever et coucher sont déjà sur la courbe du jour : pas de phrase.
            solarEvent = null,
            scope = OutlookScope.SINGLE_DAY
        ),
        solarEvents = core.solarEvents
    )
}

private class HourlyCore(
    val hourly: List<SimplifiedTimelinePoint>,
    val traces: List<FamilyTrace>,
    val precipitation: List<OverviewPrecipHour>,
    val periods: List<PeriodSummary>,
    val solarEvents: List<SolarEvent>
)

private fun hourlyCore(
    forecast: CityForecast,
    hourly: List<SimplifiedTimelinePoint>,
    zone: ZoneId,
    scope: OutlookScope,
    maxFamilies: Int
): HourlyCore {
    val hourlyPoints = hourly.filter { it.instant != null }
    val timestamps = hourlyPoints.mapNotNull(SimplifiedTimelinePoint::instant)
    val temperatureTraces = buildFamilyTemperatureTraces(forecast, timestamps, maxFamilies)
    // Les lignées pluie ne sont pas plafonnées : la part humide doit refléter
    // toutes les lignées disponibles, même si toutes ne sont pas dessinées.
    val precipitationTraces = buildFamilyPrecipitationTraces(forecast, timestamps, Int.MAX_VALUE)
    val threshold = PrecipitationThresholds.HOURLY_OCCURRENCE_MM

    val precipitation = hourlyPoints.mapIndexed { index, point ->
        val amountIfRain = point.precipitationConditionalMm ?: point.precipitationMm
        OverviewPrecipHour(
            instant = requireNotNull(point.instant),
            likelihoodPercent = point.precipitationPercent,
            wording = rainLikelihoodWording(point.precipitationSource),
            familyShare = familyRainShare(precipitationTraces, index, threshold),
            amountIfRainMm = amountIfRain,
            intensity = rainIntensity(amountIfRain, threshold),
            isNight = point.isNight,
            isSnow = SkyKind.of(point.condition) == SkyKind.SNOW
        )
    }
    return HourlyCore(
        hourly = hourlyPoints,
        traces = temperatureTraces,
        precipitation = precipitation,
        periods = buildPeriodSummaries(hourlyPoints, precipitation, zone, scope),
        solarEvents = if (timestamps.isEmpty()) emptyList() else {
            solarEventsBetween(forecast, timestamps.first(), timestamps.last(), zone)
        }
    )
}

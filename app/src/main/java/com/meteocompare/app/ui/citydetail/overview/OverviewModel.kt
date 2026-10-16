package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant

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
    val solarEvent: SolarEvent?
) {
    val today: SimplifiedTimelinePoint? get() = daily.firstOrNull()
    val hasRain: Boolean get() = precipitation.any(OverviewPrecipHour::isWet)
    val timestamps: List<Instant> get() = hourly.mapNotNull(SimplifiedTimelinePoint::instant)
}

internal fun buildOverviewModel(
    forecast: CityForecast,
    hourly: List<SimplifiedTimelinePoint>,
    daily: List<SimplifiedTimelinePoint>,
    now: Instant,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): OverviewModel {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
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
    val dailyPoints = daily.filter { it.date != null }
    val periods = buildPeriodSummaries(hourlyPoints, precipitation, zone)
    val solarEvent = nextSolarEvent(forecast, now, zone)

    return OverviewModel(
        hourly = hourlyPoints,
        daily = dailyPoints,
        agreement = agreementWording(
            dailyPoints.firstOrNull()?.consensusLevel ?: hourlyPoints.firstOrNull()?.consensusLevel
        ),
        temperatureTraces = temperatureTraces,
        precipitation = precipitation,
        periods = periods,
        narrative = buildOverviewNarrative(
            periods = periods,
            precipitation = precipitation,
            daily = dailyPoints,
            today = now.atZone(zone).toLocalDate(),
            solarEvent = solarEvent
        ),
        solarEvent = solarEvent
    )
}

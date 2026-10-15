package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.ui.citydetail.ForecastInsight
import com.meteocompare.app.ui.citydetail.ForecastInsightKind
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.selectKeyPointInsights
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
    val isNight: Boolean?
) {
    val isWet: Boolean get() = intensity != RainIntensity.NONE && (likelihoodPercent ?: 0) > 0
}

/**
 * Données de l'aperçu simple de la fiche ville.
 *
 * Tout provient de sorties déjà calculées par l'écran (chronologie
 * synthétique, insights, scénarios) ; seules les courbes par lignée sont
 * dérivées ici des séries brutes, sans nouvelle logique de prévision.
 */
internal data class OverviewModel(
    val hourly: List<SimplifiedTimelinePoint>,
    val daily: List<SimplifiedTimelinePoint>,
    /** Premier message « À retenir » utile, hors simple constat d'accord. */
    val headline: ForecastInsight?,
    val agreement: AgreementWording?,
    val temperatureTraces: List<FamilyTrace>,
    val precipitation: List<OverviewPrecipHour>,
    val rainOnset: RainOnsetWindow?,
    /** Scénarios alternatifs, uniquement lorsqu'il en existe au moins deux. */
    val scenarios: List<WeatherScenario>
) {
    val today: SimplifiedTimelinePoint? get() = daily.firstOrNull()
    val hasRain: Boolean get() = precipitation.any(OverviewPrecipHour::isWet)
    val timestamps: List<Instant> get() = hourly.mapNotNull(SimplifiedTimelinePoint::instant)
}

internal fun buildOverviewModel(
    forecast: CityForecast,
    hourly: List<SimplifiedTimelinePoint>,
    daily: List<SimplifiedTimelinePoint>,
    insights: List<ForecastInsight>,
    scenarios: List<WeatherScenario>,
    maxFamilies: Int = MAX_OVERVIEW_FAMILY_TRACES
): OverviewModel {
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
            isNight = point.isNight
        )
    }

    // Même sélection éditoriale que « À retenir », puis le message le plus
    // important (niveau, priorité) ; à égalité, le plus proche dans le temps.
    val headline = selectKeyPointInsights(insights, hasEvolutionHighlight = false)
        .filter { it.kind !in AGREEMENT_ONLY_INSIGHTS }
        .minWithOrNull(
            compareBy<ForecastInsight> { it.level.ordinal }
                .thenByDescending(ForecastInsight::priority)
        )

    return OverviewModel(
        hourly = hourlyPoints,
        daily = daily.filter { it.date != null },
        headline = headline,
        agreement = agreementWording(
            daily.firstOrNull()?.consensusLevel ?: hourlyPoints.firstOrNull()?.consensusLevel
        ),
        temperatureTraces = temperatureTraces,
        precipitation = precipitation,
        rainOnset = rainOnsetWindow(timestamps, precipitationTraces, threshold),
        scenarios = scenarios.takeIf { it.size >= 2 }.orEmpty()
    )
}

/** L'accord est déjà porté par la puce dédiée : inutile de le répéter en titre. */
private val AGREEMENT_ONLY_INSIGHTS = setOf(
    ForecastInsightKind.HIGH_AGREEMENT,
    ForecastInsightKind.DISAGREEMENT
)

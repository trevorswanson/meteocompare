package com.meteocompare.app.ui.citydetail.overview

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.NATIVE_15_MINUTE_MODELS
import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import java.time.Duration
import java.time.Instant
import kotlin.math.sqrt

/** Ce que fait la précipitation pendant l'heure qui vient. */
internal enum class NextHourEvent { STARTING, STARTING_AND_STOPPING, STOPPING, STOPPING_BRIEFLY, CONTINUING }

/**
 * Résumé « Prochaine heure » : une phrase et une courbe d'intensité minute par
 * minute (interpolée depuis le pas de 15 minutes d'Open-Meteo).
 */
internal data class NextHourOutlook(
    val event: NextHourEvent,
    val intensity: RainIntensity,
    val isSnow: Boolean,
    /** Minutes avant le début (STARTING*) ou l'arrêt (STOPPING*). */
    val minutes: Int,
    /** Durée de l'épisode pour STARTING_AND_STOPPING. */
    val durationMinutes: Int?,
    /** Intensité en mm/h pour chaque minute de 0 à [NEXT_HOUR_MINUTES] inclus. */
    val ratesMmPerHour: List<Double>,
    /**
     * Enveloppe min → max des modèles natifs 15 minutes (et du best match),
     * minute par minute ; null quand moins de deux lignées valides couvrent l'heure.
     */
    val band: List<ClosedFloatingPointRange<Double>>? = null,
    /** Convergence de ces mêmes modèles ; null dans les mêmes cas que [band]. */
    val agreement: AgreementWording? = null
)

internal const val NEXT_HOUR_MINUTES = 60
private const val STEP_MINUTES = 15L
private const val STEPS_PER_HOUR = 60.0 / STEP_MINUTES

/** Seuil de pluie au quart d'heure : Open-Meteo arrondit à 0,1 mm. */
private const val WET_STEP_MM = 0.05

/**
 * Construit la prochaine heure à partir de [precipitation], ou null s'il ne
 * tombe rien d'ici une heure (la section reste alors masquée) ou si les
 * données ne couvrent pas l'heure à venir.
 */
internal fun buildNextHourOutlook(precipitation: NextHourPrecipitation?, now: Instant): NextHourOutlook? {
    val perMinute = precipitation?.steps?.let { windowSteps(it, now) } ?: return null
    val wet = perMinute.map { it.precipitationMm > WET_STEP_MM }
    if (wet.none { it }) return null

    val window = perMinute.distinct()
    val wetSteps = window.filter { it.precipitationMm > WET_STEP_MM }
    val peakRate = wetSteps.maxOf { it.precipitationMm } * STEPS_PER_HOUR
    // Neige si au moins la moitié de l'eau tombe sous forme de neige.
    val snowWaterMm = wetSteps.sumOf { it.snowfallCm } / SNOW_CM_PER_WATER_MM
    val isSnow = snowWaterMm >= wetSteps.sumOf { it.precipitationMm } / 2
    val rates = smoothRates(window, now)
    val members = validMembers(precipitation.members, now)

    val (event, minutes, duration) = if (wet.first()) {
        val stop = wet.indexOfFirst { !it }
        when {
            stop < 0 -> Triple(NextHourEvent.CONTINUING, 0, null)
            wet.drop(stop).any { it } -> Triple(NextHourEvent.STOPPING_BRIEFLY, stop, null)
            else -> Triple(NextHourEvent.STOPPING, stop, null)
        }
    } else {
        val start = wet.indexOfFirst { it }
        val stop = wet.drop(start).indexOfFirst { !it }
        if (stop < 0) Triple(NextHourEvent.STARTING, start, null)
        else Triple(NextHourEvent.STARTING_AND_STOPPING, start, stop)
    }
    return NextHourOutlook(
        event = event,
        intensity = rainIntensity(peakRate, 0.0),
        isSnow = isSnow,
        minutes = minutes,
        durationMinutes = duration,
        ratesMmPerHour = rates,
        band = members?.let { memberWindows ->
            val memberRates = memberWindows.map { smoothRates(it.distinct(), now) } + listOf(rates)
            rates.indices.map { minute ->
                val values = memberRates.map { it[minute] }
                values.min()..values.max()
            }
        },
        agreement = members?.let(::memberAgreement)
    )
}

/**
 * Quarts d'heure couvrant chaque minute de la prochaine heure, ou null si la
 * série ne couvre pas toute l'heure (hors domaine, fin de run, données absentes).
 */
private fun windowSteps(steps: List<NextHourPrecipitation.Step>, now: Instant): List<NextHourPrecipitation.Step>? {
    val sorted = steps.sortedBy { it.endsAt }
    // Chaque valeur couvre le quart d'heure qui se termine à endsAt.
    val perMinute = (0 until NEXT_HOUR_MINUTES).map { minute ->
        val at = now.plusSeconds(minute * 60L)
        sorted.firstOrNull { !at.isBefore(it.endsAt.minusSeconds(STEP_MINUTES * 60)) && at.isBefore(it.endsAt) }
    }
    return if (perMinute.any { it == null }) null else perMinute.filterNotNull()
}

/**
 * Modèles natifs 15 minutes qui couvrent toute l'heure, une seule source par
 * lignée (AROME HD et AROME ne sont pas deux avis indépendants). Null s'il en
 * reste moins de deux : pas de dispersion mesurable.
 */
private fun validMembers(
    members: Map<WeatherModel, List<NextHourPrecipitation.Step>>,
    now: Instant
): List<List<NextHourPrecipitation.Step>>? = NATIVE_15_MINUTE_MODELS
    .mapNotNull { model -> members[model]?.let { windowSteps(it, now) }?.let { model to it } }
    .distinctBy { (model, _) -> ForecastConsensus.groupFor(model) }
    .map { it.second }
    .takeIf { it.size >= 2 }

/**
 * Part moyenne, minute par minute, des modèles qui tombent dans la classe
 * d'intensité majoritaire (sec, faible, modérée, forte).
 */
private fun memberAgreement(members: List<List<NextHourPrecipitation.Step>>): AgreementWording {
    val share = (0 until NEXT_HOUR_MINUTES).map { minute ->
        val classes = members.map { rainIntensity(it[minute].precipitationMm * STEPS_PER_HOUR, WET_STEP_MM * STEPS_PER_HOUR) }
        classes.groupingBy { it }.eachCount().values.max().toDouble() / classes.size
    }.average()
    return when {
        share >= AGREE_SHARE -> AgreementWording.AGREE
        share >= SOME_AGREEMENT_SHARE -> AgreementWording.SOME_DISAGREEMENT
        else -> AgreementWording.DISAGREE
    }
}

/** Au plus un quart d'heure de désaccord entre deux modèles reste « d'accord ». */
private const val AGREE_SHARE = 0.85
private const val SOME_AGREEMENT_SHARE = 0.6

/** 7 cm de neige pour 10 mm d'eau (rapport utilisé par Open-Meteo). */
private const val SNOW_CM_PER_WATER_MM = 0.7

/**
 * Courbe minute par minute : interpolation linéaire entre les milieux des
 * quarts d'heure, pour éviter des marches d'escalier sur le graphique.
 */
private fun smoothRates(window: List<NextHourPrecipitation.Step>, now: Instant): List<Double> {
    val anchors = window.map { step ->
        val mid = step.endsAt.minusSeconds(STEP_MINUTES * 30)
        Duration.between(now, mid).seconds / 60.0 to step.precipitationMm * STEPS_PER_HOUR
    }
    return (0..NEXT_HOUR_MINUTES).map { minute ->
        val m = minute.toDouble()
        val after = anchors.indexOfFirst { it.first >= m }
        when {
            after < 0 -> anchors.last().second
            after == 0 -> anchors.first().second
            else -> {
                val (x0, y0) = anchors[after - 1]
                val (x1, y1) = anchors[after]
                y0 + (y1 - y0) * ((m - x0) / (x1 - x0))
            }
        }
    }
}

/**
 * Position verticale (0 → 1) d'une intensité : les repères « Faible »,
 * « Modérée » et « Forte » sont au tiers, aux deux tiers et en haut.
 */
internal fun nextHourRateFraction(mmPerHour: Double): Float = when {
    !mmPerHour.isFinite() || mmPerHour <= 0.0 -> 0f
    mmPerHour <= LIGHT_MARK -> (sqrt(mmPerHour / LIGHT_MARK) / 3).toFloat()
    mmPerHour <= MODERATE_MARK -> (1f / 3) + ((mmPerHour - LIGHT_MARK) / (MODERATE_MARK - LIGHT_MARK) / 3).toFloat()
    else -> ((2f / 3) + ((mmPerHour - MODERATE_MARK) / (HEAVY_MARK - MODERATE_MARK) / 3).toFloat()).coerceAtMost(1f)
}

private const val LIGHT_MARK = 1.0
private const val MODERATE_MARK = 4.0
private const val HEAVY_MARK = 10.0

@StringRes
private fun nextHourSubjectRes(outlook: NextHourOutlook): Int = when (outlook.intensity) {
    RainIntensity.NONE, RainIntensity.LIGHT ->
        if (outlook.isSnow) R.string.overview_next_hour_snow_light else R.string.overview_next_hour_rain_light
    RainIntensity.MODERATE ->
        if (outlook.isSnow) R.string.overview_next_hour_snow_moderate else R.string.overview_next_hour_rain_moderate
    RainIntensity.HEAVY ->
        if (outlook.isSnow) R.string.overview_next_hour_snow_heavy else R.string.overview_next_hour_rain_heavy
}

@Composable
internal fun nextHourSummary(outlook: NextHourOutlook): String {
    val subject = stringResource(nextHourSubjectRes(outlook))
    return when (outlook.event) {
        NextHourEvent.STARTING -> stringResource(R.string.overview_next_hour_starting, subject, outlook.minutes)
        NextHourEvent.STARTING_AND_STOPPING -> stringResource(
            R.string.overview_next_hour_starting_stopping,
            subject,
            outlook.minutes,
            outlook.durationMinutes ?: 0
        )
        NextHourEvent.STOPPING -> stringResource(R.string.overview_next_hour_stopping, subject, outlook.minutes)
        NextHourEvent.STOPPING_BRIEFLY ->
            stringResource(R.string.overview_next_hour_stopping_briefly, subject, outlook.minutes)
        NextHourEvent.CONTINUING -> stringResource(R.string.overview_next_hour_continuing, subject)
    }
}

/** Section « Prochaine heure », affichée seulement quand il tombe quelque chose. */
@Composable
internal fun NextHourSection(outlook: NextHourOutlook, modifier: Modifier = Modifier) {
    val summary = nextHourSummary(outlook)
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_OVERVIEW_NEXT_HOUR),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverviewSectionTitle(
            text = stringResource(R.string.overview_next_hour_title),
            agreement = outlook.agreement,
            agreementTag = TAG_OVERVIEW_NEXT_HOUR_AGREEMENT
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // La phrase ci-dessus décrit déjà la courbe aux lecteurs d'écran.
        NextHourChart(
            rates = outlook.ratesMmPerHour,
            band = outlook.band,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
private fun NextHourChart(
    rates: List<Double>,
    band: List<ClosedFloatingPointRange<Double>>?,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val levelLabels = listOf(
        stringResource(R.string.overview_next_hour_level_light),
        stringResource(R.string.overview_next_hour_level_moderate),
        stringResource(R.string.overview_next_hour_level_heavy)
    )
    val tickLabels = (1..5).map { stringResource(R.string.overview_next_hour_tick, it * 10) }
    val fractions = remember(rates) { rates.map(::nextHourRateFraction) }
    val bandFractions = remember(band) {
        band?.map { nextHourRateFraction(it.start) to nextHourRateFraction(it.endInclusive) }
    }
    val accent = precipitationMetricAccent()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.fillMaxWidth().height(148.dp).testTag(TAG_OVERVIEW_NEXT_HOUR_CHART)) {
        val axisLabelHeight = 24.dp.toPx()
        val top = 22.dp.toPx()
        val baseline = size.height - axisLabelHeight
        val plotHeight = baseline - top
        fun x(minute: Int) = size.width * minute / NEXT_HOUR_MINUTES
        fun y(fraction: Float) = baseline - plotHeight * fraction

        // Repères d'intensité en pointillés, libellés au-dessus de chaque ligne.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        levelLabels.forEachIndexed { index, label ->
            val lineY = y((index + 1) / 3f)
            drawLine(gridColor, Offset(0f, lineY), Offset(size.width, lineY), 1.dp.toPx(), pathEffect = dash)
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(4.dp.toPx(), lineY - layout.size.height - 2.dp.toPx()))
        }

        val curve = Path().apply {
            moveTo(x(0), y(fractions.first()))
            fractions.forEachIndexed { minute, fraction -> if (minute > 0) lineTo(x(minute), y(fraction)) }
        }
        val area = Path().apply {
            moveTo(x(0), baseline)
            fractions.forEachIndexed { minute, fraction -> lineTo(x(minute), y(fraction)) }
            lineTo(x(NEXT_HOUR_MINUTES), baseline)
            close()
        }
        // Enveloppe des modèles natifs 15 minutes : bas puis haut, en un seul contour.
        bandFractions?.let { bounds ->
            val envelope = Path().apply {
                moveTo(x(0), y(bounds.first().second))
                bounds.forEachIndexed { minute, (_, high) -> lineTo(x(minute), y(high)) }
                for (minute in bounds.indices.reversed()) lineTo(x(minute), y(bounds[minute].first))
                close()
            }
            drawPath(envelope, accent.copy(alpha = 0.18f))
        }
        drawPath(area, accent.copy(alpha = if (bandFractions != null) 0.3f else 0.35f))
        drawPath(
            curve,
            accent,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), 1.5.dp.toPx())

        tickLabels.forEachIndexed { index, label ->
            val tickX = x((index + 1) * 10)
            drawLine(axisColor, Offset(tickX, baseline), Offset(tickX, baseline + 4.dp.toPx()), 1.dp.toPx())
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(tickX - layout.size.width / 2f, baseline + 6.dp.toPx()))
        }
    }
}

internal const val TAG_OVERVIEW_NEXT_HOUR = "overview_next_hour"
internal const val TAG_OVERVIEW_NEXT_HOUR_CHART = "overview_next_hour_chart"
internal const val TAG_OVERVIEW_NEXT_HOUR_AGREEMENT = "overview_next_hour_agreement"

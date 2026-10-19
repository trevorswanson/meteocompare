package com.meteocompare.app.ui.citydetail.overview

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.charts.canonicalChartRange
import com.meteocompare.app.core.charts.metricPlotValue
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.WindArrow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Courbe des prochaines heures, ajustée à la largeur de l'écran (sans défilement).
 *
 * - En haut : lever et coucher du soleil ; la nuit est légèrement assombrie.
 * - Trait foncé : prévision retenue (centrale du moteur sélectionné), avec un
 *   point, une icône et la température à intervalle régulier.
 * - Traits pâles : une ligne par lignée de modèles indépendante. Un faisceau
 *   serré signale une prévision solide ; ce ne sont pas des probabilités.
 * - Repère « Maintenant » en pointillés.
 * - Toucher une heure affiche une infobulle (condition, température, pluie,
 *   humidité, point de rosée, vent) qui reste jusqu'au prochain toucher
 *   ailleurs ; glisser la fait suivre le doigt et la ferme au relâchement.
 */
@Composable
internal fun HourlyOutlookChart(
    points: List<SimplifiedTimelinePoint>,
    traces: List<FamilyTrace>,
    timezone: String?,
    now: Instant?,
    modifier: Modifier = Modifier,
    solarEvents: List<SolarEvent> = emptyList(),
    /** Repère « Maintenant » ; inutile quand la courbe commence maintenant. */
    showNow: Boolean = true,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val main = points.map { metricPlotValue(it.temperatureC) }
    if (points.any { it.instant == null } || main.count { it != null } < 2) {
        Box(modifier = modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chart_not_enough_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val locale = LocalConfiguration.current.locales[0]
    val clock = LocalClockFormatPreference.current
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale, clock) { WeatherTimeFormat.hour(locale, clock) }
    val clockFormatter = remember(locale, clock) { WeatherTimeFormat.clock(locale, clock) }
    val resources = LocalResources.current
    val description = remember(points, traces, units, zone, hourFormatter, resources, locale) {
        hourlyOutlookDescription(resources, units, points, traces, zone, hourFormatter, locale)
    }
    val range = remember(points, traces) {
        canonicalChartRange(
            values = main + traces.flatMap(FamilyTrace::values),
            minimumSpan = 4.0,
            paddingFraction = 0.06,
            minimumPadding = 0.5
        )
    }
    val first = requireNotNull(points.first().instant)
    val last = requireNotNull(points.last().instant)
    val lastIndex = (points.size - 1).toFloat()
    fun hoursFromStart(instant: Instant): Float = Duration.between(first, instant).seconds / 3600f
    // Le repère « Maintenant » n'apparaît que si l'instant présent est dans la fenêtre.
    val nowOffsetHours = now
        ?.takeIf { showNow && !it.isBefore(first) && !it.isAfter(last) }
        ?.let { hoursFromStart(it).coerceIn(0f, lastIndex) }
    val visibleSolarEvents = remember(solarEvents, first, last) {
        solarEvents.filter { !it.at.isBefore(first) && !it.at.isAfter(last) }
    }
    val nights = remember(first, last, points, solarEvents) {
        nightRanges(first, last, points.first().isNight == true, solarEvents)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    // Trait un peu en retrait du texte (plus clair en thème clair, plus
    // sombre en thème sombre) pour que les températures restent lisibles
    // quand elles chevauchent la courbe.
    val lineColor = lerp(textColor, surfaceColor, LINE_FADE)
    val familyColor = textColor.copy(alpha = OTHER_MODELS_ALPHA)
    val nowColor = MaterialTheme.colorScheme.primary
    val darkTheme = surfaceColor.luminance() < 0.5f
    val nightColor = if (darkTheme) Color.Black.copy(alpha = 0.28f) else textColor.copy(alpha = 0.06f)
    val sunriseColor = MaterialTheme.colorScheme.primary
    val sunsetColor = MaterialTheme.colorScheme.tertiary
    // Halo couleur fond autour des températures posées sur la courbe.
    val halo = Shadow(color = surfaceColor, offset = Offset.Zero, blurRadius = 8f)

    val host = LocalTimelineTooltipHost.current ?: remember { TimelineTooltipHost() }
    val tooltipKey = remember { Any() }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.takeIf { host.activeKey === tooltipKey && it in points.indices }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_OVERVIEW_HOURLY_CHART)
            .semantics { contentDescription = description }
    ) {
        val chartWidth = maxWidth
        val hourWidth = (chartWidth - SidePadding * 2) / lastIndex.coerceAtLeast(1f)
        // Intervalle des étiquettes : le plus court qui laisse la place à une étiquette.
        val labelStep = LabelSteps.firstOrNull { hourWidth * it >= LabelWidth } ?: LabelSteps.last()
        val marked = remember(points, zone, labelStep) {
            points.indices.filter { index -> points[index].instant!!.atZone(zone).hour % labelStep == 0 }
        }
        fun xAt(index: Float): Dp = SidePadding + hourWidth * index
        fun yAt(value: Double): Dp = PlotTop + PlotHeight * (1f - ((value - range.min) / range.span).toFloat())
        val density = LocalDensity.current
        val sidePx = with(density) { SidePadding.toPx() }
        val hourPx = with(density) { hourWidth.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .clearAndSetSemantics { }
                .pointerInput(points.size, sidePx, hourPx) {
                    fun indexAt(x: Float) = ((x - sidePx) / hourPx).roundToInt().coerceIn(0, points.size - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    host.activeKey = null
                                } else if (!cancelled) {
                                    // Toucher : l'infobulle reste jusqu'au prochain toucher ailleurs.
                                    selectedIndex = indexAt(change.position.x)
                                    host.activeKey = tooltipKey
                                }
                                break
                            }
                            val delta = change.position - down.position
                            if (!dragging && !cancelled) {
                                if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) {
                                    dragging = true
                                } else if (abs(delta.y) > viewConfiguration.touchSlop) {
                                    // Défilement vertical de la page : on s'efface.
                                    cancelled = true
                                }
                            }
                            if (dragging) {
                                change.consume()
                                selectedIndex = indexAt(change.position.x)
                                host.activeKey = tooltipKey
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun x(index: Float) = xAt(index).toPx()
                fun y(value: Double) = yAt(value).toPx()
                val bottom = (PlotTop + PlotHeight).toPx()

                // Nuit : bande légèrement plus sombre entre coucher et lever.
                nights.forEach { (from, to) ->
                    val left = x(hoursFromStart(from).coerceIn(0f, lastIndex)).coerceAtLeast(0f)
                    val right = x(hoursFromStart(to).coerceIn(0f, lastIndex))
                    val clampedLeft = if (from == first) 0f else left
                    val clampedRight = if (to == last) size.width else right
                    if (clampedRight > clampedLeft) {
                        drawRect(
                            color = nightColor,
                            topLeft = Offset(clampedLeft, 0f),
                            size = androidx.compose.ui.geometry.Size(clampedRight - clampedLeft, bottom)
                        )
                    }
                }

                // Repère « Maintenant ».
                if (nowOffsetHours != null) {
                    val nowX = x(nowOffsetHours)
                    drawLine(
                        color = nowColor,
                        start = Offset(nowX, SolarBand.toPx()),
                        end = Offset(nowX, bottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    )
                }

                // Autres modèles : traits pâles et épais, lissés.
                val familyStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                traces.forEach { trace ->
                    drawSmoothSeries(trace.values.map(::metricPlotValue), ::x, ::y, familyColor, familyStroke)
                }

                // Prévision retenue.
                drawSmoothSeries(
                    main,
                    ::x,
                    ::y,
                    lineColor,
                    Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                marked.forEach { index ->
                    val value = main[index] ?: return@forEach
                    val center = Offset(x(index.toFloat()), y(value))
                    drawCircle(surfaceColor, radius = 6.5.dp.toPx(), center = center)
                    drawCircle(lineColor, radius = 5.dp.toPx(), center = center)
                }

                // Heure sélectionnée.
                if (selected != null) {
                    val selectedX = x(selected.toFloat())
                    drawLine(nowColor, Offset(selectedX, SolarBand.toPx()), Offset(selectedX, bottom), 2.dp.toPx())
                    main[selected]?.let { value ->
                        val center = Offset(selectedX, y(value))
                        drawCircle(surfaceColor, radius = 7.5.dp.toPx(), center = center)
                        drawCircle(nowColor, radius = 6.dp.toPx(), center = center)
                    }
                }
            }

            // Lever et coucher du soleil, en haut de la courbe (mêmes icônes
            // et couleurs que la fiche détaillée), heure centrée sous l'icône.
            visibleSolarEvents.forEach { event ->
                val eventX = xAt(hoursFromStart(event.at))
                Column(
                    modifier = Modifier
                        .offset(x = eventX - SunLabelWidth / 2)
                        .width(SunLabelWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (event.sunset) Icons.Filled.Bedtime else Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = if (event.sunset) sunsetColor else sunriseColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = event.at.atZone(zone).format(clockFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Icône et température à intervalle régulier, au-dessus du point.
            marked.forEach { index ->
                val value = main[index] ?: return@forEach
                val point = points[index]
                Column(
                    modifier = Modifier
                        .offset(x = xAt(index.toFloat()) - LabelWidth / 2, y = yAt(value) - LabelBand)
                        .width(LabelWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedWeatherIcon(
                        condition = point.condition,
                        size = 24.dp,
                        animated = false,
                        animateConditionChanges = false,
                        isNight = point.isNight == true
                    )
                    Text(
                        text = units.degrees(value, locale),
                        style = MaterialTheme.typography.titleSmall.copy(shadow = halo),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Axe horaire et « Maintenant ».
            if (nowOffsetHours != null) {
                Text(
                    text = stringResource(R.string.timeline_now),
                    style = MaterialTheme.typography.labelMedium,
                    color = nowColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = xAt(nowOffsetHours) - NowLabelWidth / 2, y = PlotTop + PlotHeight + 4.dp)
                        .width(NowLabelWidth)
                )
            }
            marked.forEach { index ->
                Text(
                    text = points[index].instant!!.atZone(zone).format(hourFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = xAt(index.toFloat()) - LabelWidth / 2, y = PlotTop + PlotHeight + 22.dp)
                        .width(LabelWidth)
                )
            }

            if (selected != null) {
                // À côté de l'heure choisie, du côté où il y a le plus de place.
                val selectedX = xAt(selected.toFloat())
                val tooltipX = (if (selectedX > chartWidth / 2) selectedX - TooltipWidth - 10.dp else selectedX + 10.dp)
                    .coerceIn(0.dp, (chartWidth - TooltipWidth).coerceAtLeast(0.dp))
                HourTooltip(
                    point = points[selected],
                    time = points[selected].instant!!.atZone(zone).format(clockFormatter),
                    units = units,
                    locale = locale,
                    modifier = Modifier
                        .offset(x = tooltipX)
                        .width(TooltipWidth)
                        .testTag(TAG_OVERVIEW_HOURLY_TOOLTIP)
                )
            }
        }
    }
}

/** Infobulle d'une heure : condition, température, pluie, humidité, point de rosée, vent. */
@Composable
private fun HourTooltip(
    point: SimplifiedTimelinePoint,
    time: String,
    units: WeatherUnits,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    val dash = "—"
    val precipitation = buildList {
        point.precipitationMm?.takeIf { it > 0.0 }?.let { add(units.rain(it, locale)) }
        rainSignalText(precipitationHourOf(point))?.let { add(it) }
    }.joinToString(" · ").ifEmpty { dash }
    val rows = listOf(
        stringResource(R.string.overview_detail_precipitation) to precipitation,
        stringResource(R.string.overview_detail_humidity) to
            (point.relativeHumidityPercent?.let { stringResource(R.string.overview_percent, it) } ?: dash),
        stringResource(R.string.overview_detail_dew_point) to units.degrees(point.dewPointC, locale)
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedWeatherIcon(
                    condition = point.condition,
                    size = 22.dp,
                    animated = false,
                    animateConditionChanges = false,
                    isNight = point.isNight == true
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = units.degrees(point.temperatureC, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            point.condition?.let {
                Text(
                    text = stringResource(weatherConditionLabelRes(it)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            rows.forEach { (label, value) -> TooltipRow(label, value) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.overview_detail_wind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                point.windDirectionDeg?.let { direction ->
                    WindArrow(directionDegrees = direction, size = 12.dp)
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text = point.windKmh?.let { units.speed(it, locale) } ?: dash,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TooltipRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Signal pluie d'une heure, avec les mêmes règles de formulation que l'aperçu. */
private fun precipitationHourOf(point: SimplifiedTimelinePoint): OverviewPrecipHour? {
    val instant = point.instant ?: return null
    if ((point.precipitationPercent ?: 0) <= 0) return null
    return OverviewPrecipHour(
        instant = instant,
        likelihoodPercent = point.precipitationPercent,
        wording = rainLikelihoodWording(point.precipitationSource),
        familyShare = FamilyRainShare(wet = point.wetModelCount, total = point.precipitationModelCount),
        amountIfRainMm = point.precipitationConditionalMm,
        intensity = RainIntensity.NONE,
        isNight = point.isNight
    )
}

/**
 * Intervalles de nuit dans [start, end] : du coucher au lever suivant. Si la
 * fenêtre commence la nuit, le premier intervalle part de [start] ; si elle
 * finit la nuit, le dernier s'arrête à [end].
 */
internal fun nightRanges(
    start: Instant,
    end: Instant,
    startsAtNight: Boolean,
    events: List<SolarEvent>
): List<Pair<Instant, Instant>> {
    val ranges = mutableListOf<Pair<Instant, Instant>>()
    var nightStart: Instant? = if (startsAtNight) start else null
    events.filter { it.at.isAfter(start) && it.at.isBefore(end) }.sortedBy { it.at }.forEach { event ->
        if (event.sunset) {
            if (nightStart == null) nightStart = event.at
        } else {
            ranges += (nightStart ?: start) to event.at
            nightStart = null
        }
    }
    nightStart?.let { ranges += it to end }
    return ranges
}

/** Part du fond mêlée à la couleur du texte pour le trait principal. */
private const val LINE_FADE = 0.3f

private val SidePadding = 20.dp
private val SolarBand = 34.dp
private val LabelBand = 50.dp
private val LabelWidth = 40.dp
private val NowLabelWidth = 72.dp
private val SunLabelWidth = 72.dp
private val TooltipWidth = 184.dp
private val PlotTop = SolarBand + LabelBand
private val PlotHeight = 104.dp
private val ChartHeight = PlotTop + PlotHeight + 44.dp
private val LabelSteps = listOf(1, 2, 3, 4, 6)

internal const val TAG_OVERVIEW_HOURLY_TOOLTIP = "overview_hourly_tooltip"

/**
 * Trace une série lissée (Catmull-Rom converti en Bézier cubique), coupée
 * sur les valeurs absentes. Aucune valeur n'est extrapolée.
 */
private fun DrawScope.drawSmoothSeries(
    values: List<Double?>,
    x: (Float) -> Float,
    y: (Double) -> Float,
    color: Color,
    stroke: Stroke
) {
    val segments = mutableListOf<MutableList<Offset>>()
    var current: MutableList<Offset>? = null
    values.forEachIndexed { index, value ->
        if (value == null) {
            current = null
        } else {
            val point = Offset(x(index.toFloat()), y(value))
            val segment = current ?: mutableListOf<Offset>().also { segments += it; current = it }
            segment += point
        }
    }
    segments.filter { it.size >= 2 }.forEach { segment ->
        val path = Path().apply {
            moveTo(segment[0].x, segment[0].y)
            for (i in 0 until segment.lastIndex) {
                val p0 = segment.getOrElse(i - 1) { segment[i] }
                val p1 = segment[i]
                val p2 = segment[i + 1]
                val p3 = segment.getOrElse(i + 2) { p2 }
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y
                )
            }
        }
        drawPath(path = path, color = color, style = stroke)
    }
}

/**
 * Description TalkBack : extrêmes de la prévision retenue et écart maximal
 * entre lignées, sans vocabulaire probabiliste.
 */
internal fun hourlyOutlookDescription(
    resources: Resources,
    units: WeatherUnits,
    points: List<SimplifiedTimelinePoint>,
    traces: List<FamilyTrace>,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    locale: Locale
): String {
    val valued = points.mapNotNull { point ->
        val value = metricPlotValue(point.temperatureC) ?: return@mapNotNull null
        val instant = point.instant ?: return@mapNotNull null
        instant to value
    }
    if (valued.isEmpty()) return resources.getString(R.string.chart_not_enough_data)
    val high = valued.maxBy { it.second }
    val low = valued.minBy { it.second }
    fun hour(instant: Instant) = instant.atZone(zone).format(hourFormatter)
    val base = resources.getString(
        R.string.overview_hourly_a11y,
        units.temp(high.second, locale = locale),
        hour(high.first),
        units.temp(low.second, locale = locale),
        hour(low.first)
    )
    val spread = maxFamilySpread(traces) ?: return base
    return base + " " + resources.getString(
        R.string.overview_hourly_spread_a11y,
        units.format(spread, WeatherUnit.TEMPERATURE_COMPACT, 0, locale, delta = true)
    )
}


internal const val TAG_OVERVIEW_HOURLY_CHART = "overview_hourly_chart"

package com.meteocompare.app.ui.citydetail.overview

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.charts.canonicalChartRange
import com.meteocompare.app.core.charts.metricPlotValue
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Courbe des prochaines heures, défilable horizontalement.
 *
 * - En-tête : Matin / Après-midi / Soirée / Nuit et signal pluie de chaque tranche.
 * - Trait foncé : prévision retenue (centrale du moteur sélectionné), avec un
 *   point, une icône et la température toutes les trois heures.
 * - Traits pâles : une ligne par lignée de modèles indépendante. Un faisceau
 *   serré signale une prévision solide ; ce ne sont pas des probabilités.
 * - Repère « Maintenant » en pointillés.
 */
@Composable
internal fun HourlyOutlookChart(
    points: List<SimplifiedTimelinePoint>,
    traces: List<FamilyTrace>,
    periods: List<PeriodSummary>,
    timezone: String?,
    now: Instant?,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
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
    val marked = remember(points, zone) {
        points.indices.filter { index -> points[index].instant!!.atZone(zone).hour % 3 == 0 }
    }
    val last = requireNotNull(points.last().instant)
    // Le repère « Maintenant » n'apparaît que si l'instant présent est dans la fenêtre.
    val nowOffsetHours = now
        ?.takeIf { !it.isBefore(first) && !it.isAfter(last.plusSeconds(3_599)) }
        ?.let { (Duration.between(first, it).toMinutes() / 60f).coerceIn(0f, (points.size - 1).toFloat()) }

    val contentWidth = SidePadding * 2 + HourWidth * (points.size - 1)
    fun xAt(index: Float): Dp = SidePadding + HourWidth * index
    fun yAt(value: Double): Dp = PlotTop + PlotHeight * (1f - ((value - range.min) / range.span).toFloat())

    val lineColor = MaterialTheme.colorScheme.onSurface
    val familyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = OTHER_MODELS_ALPHA)
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val nowColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag(TAG_OVERVIEW_HOURLY_CHART)
                .semantics { contentDescription = description }
        ) {
            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .height(ChartHeight)
                    .clearAndSetSemantics { }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun x(index: Float) = xAt(index).toPx()
                    fun y(value: Double) = yAt(value).toPx()

                    // Séparateurs de tranches.
                    periods.drop(1).forEach { period ->
                        val px = x(period.startIndex - 0.5f)
                        drawLine(separatorColor, Offset(px, 0f), Offset(px, (PlotTop + PlotHeight).toPx()), 1.dp.toPx())
                    }

                    // Repère « Maintenant ».
                    if (nowOffsetHours != null) {
                        val nowX = x(nowOffsetHours)
                        drawLine(
                            color = nowColor,
                            start = Offset(nowX, HeaderHeight.toPx()),
                            end = Offset(nowX, (PlotTop + PlotHeight).toPx()),
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
                }

                // En-têtes de tranches : nom et signal pluie.
                periods.forEach { period ->
                    val start = xAt(period.startIndex - 0.5f).coerceAtLeast(0.dp)
                    val end = xAt(period.endIndex + 0.5f).coerceAtMost(contentWidth)
                    PeriodHeader(
                        period = period,
                        modifier = Modifier
                            .offset(x = start)
                            .width((end - start).coerceAtLeast(0.dp))
                            .height(HeaderHeight)
                    )
                }

                // Icône et température toutes les trois heures, au-dessus du point.
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
                            size = 26.dp,
                            animated = false,
                            animateConditionChanges = false,
                            isNight = point.isNight == true
                        )
                        Text(
                            text = units.temp(value, locale = locale),
                            style = MaterialTheme.typography.titleSmall,
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
                            .offset(x = xAt(nowOffsetHours) - LabelWidth / 2, y = PlotTop + PlotHeight + 4.dp)
                            .width(LabelWidth)
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
            }
        }
        if (showLegend && traces.size >= 2) {
            ChartLegend(lineColor = lineColor, familyColor = familyColor)
        }
    }
}

@Composable
private fun PeriodHeader(period: PeriodSummary, modifier: Modifier) {
    val rain = rainSignalText(period.peakRain)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(periodNameRes(period.period)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (rain != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = rain,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ChartLegend(lineColor: Color, familyColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(width = 28.dp, height = 10.dp)) {
                val y = size.height / 2f
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width / 2f, y))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.overview_legend_forecast), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(18.dp))
            Canvas(Modifier.size(width = 28.dp, height = 10.dp)) {
                val y = size.height / 2f
                drawLine(familyColor, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.overview_legend_other_models),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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

private val HourWidth = 17.dp
private val SidePadding = 22.dp
private val HeaderHeight = 44.dp
private val LabelBand = 52.dp
private val LabelWidth = 48.dp
private val PlotTop = HeaderHeight + LabelBand
private val PlotHeight = 104.dp
private val ChartHeight = PlotTop + PlotHeight + 44.dp

internal const val TAG_OVERVIEW_HOURLY_CHART = "overview_hourly_chart"

package com.meteocompare.app.ui.citydetail.overview

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Courbe de température des prochaines heures : un trait plein pour la
 * prévision retenue (centrale du moteur sélectionné) et un trait pâle et
 * pointillé par lignée de modèles indépendante. Un faisceau serré signale des
 * prévisions concordantes, un faisceau large des scénarios divergents.
 *
 * Les lignes pâles sont des prévisions déterministes, pas des membres
 * d'ensemble : la légende et la description TalkBack ne parlent jamais de
 * probabilité.
 */
@Composable
internal fun HourlyOutlookChart(
    points: List<SimplifiedTimelinePoint>,
    traces: List<FamilyTrace>,
    timezone: String?,
    modifier: Modifier = Modifier,
    units: WeatherUnits = LocalWeatherUnits.current,
    onClick: (() -> Unit)? = null
) {
    val instants = points.map(SimplifiedTimelinePoint::instant)
    val main = points.map { metricPlotValue(it.temperatureC) }
    if (instants.any { it == null } || main.count { it != null } < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(ChartHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.chart_not_enough_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val locale = LocalConfiguration.current.locales[0]
    val clockPreference = LocalClockFormatPreference.current
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale, clockPreference) { WeatherTimeFormat.hour(locale, clockPreference) }
    val resources = LocalResources.current
    val description = remember(points, traces, units, zone, hourFormatter, resources, locale) {
        hourlyOutlookDescription(resources, units, points, traces, zone, hourFormatter, locale)
    }

    val range = remember(points, traces) {
        canonicalChartRange(
            values = main + traces.flatMap(FamilyTrace::values),
            minimumSpan = 4.0,
            paddingFraction = 0.08,
            minimumPadding = 0.5
        )
    }

    val mainColor = MaterialTheme.colorScheme.primary
    val familyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val nightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val valueStyle = TextStyle(color = valueColor, fontSize = 11.sp)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .testTag(TAG_OVERVIEW_HOURLY_CHART)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier
                )
                .semantics { contentDescription = description }
        ) {
            val leftPad = 30.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 16.dp.toPx()
            val bottomPad = 20.dp.toPx()
            val chartLeft = leftPad
            val chartRight = size.width - rightPad
            val chartTop = topPad
            val chartBottom = size.height - bottomPad
            val chartW = (chartRight - chartLeft).coerceAtLeast(1f)
            val chartH = (chartBottom - chartTop).coerceAtLeast(1f)
            val lastIndex = (points.size - 1).coerceAtLeast(1)
            val step = chartW / lastIndex

            fun xAt(index: Int): Float = chartLeft + index * step
            fun yAt(value: Double): Float =
                chartBottom - ((value - range.min) / range.span).toFloat() * chartH

            // Nuit : léger voile, sans dépendre de la couleur pour la lecture.
            points.forEachIndexed { index, point ->
                if (point.isNight == true) {
                    val left = (xAt(index) - step / 2f).coerceAtLeast(chartLeft)
                    val right = (xAt(index) + step / 2f).coerceAtMost(chartRight)
                    drawRect(
                        color = nightColor,
                        topLeft = Offset(left, chartTop),
                        size = Size(right - left, chartH)
                    )
                }
            }

            // Grille horizontale + valeurs d'axe.
            val ticks = range.ticks(3)
            val tickStep = range.span / 2.0
            ticks.forEach { tick ->
                val y = yAt(tick)
                drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1f)
                val label = units.axisValue(tick, WeatherUnit.TEMPERATURE_COMPACT, tickStep, locale = locale) +
                    units.suffix(WeatherUnit.TEMPERATURE_COMPACT)
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(chartLeft - measured.size.width - 4.dp.toPx(), y - measured.size.height / 2f)
                )
            }

            // Repères horaires toutes les 3 h (6 h si l'écran est étroit).
            val labelEvery = if (step * 3 < 34.dp.toPx()) 6 else 3
            points.forEachIndexed { index, point ->
                if (index % labelEvery != 0) return@forEachIndexed
                val instant = point.instant ?: return@forEachIndexed
                val label = instant.atZone(zone).format(hourFormatter)
                val measured = textMeasurer.measure(label, labelStyle)
                val x = (xAt(index) - measured.size.width / 2f)
                    .clampLabel(chartLeft, chartRight - measured.size.width)
                drawText(measured, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
            }

            // Lignées secondaires : traits fins et pointillés.
            val familyStroke = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
            traces.forEach { trace ->
                drawSeries(trace.values, ::xAt, ::yAt, familyColor, familyStroke)
            }

            // Prévision retenue.
            drawSeries(
                main,
                ::xAt,
                ::yAt,
                mainColor,
                Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Étiquettes du maximum et du minimum de la prévision retenue.
            val maxIndex = main.indices.filter { main[it] != null }.maxByOrNull { main[it]!! }
            val minIndex = main.indices.filter { main[it] != null }.minByOrNull { main[it]!! }
            listOfNotNull(maxIndex, minIndex).distinct().forEach { index ->
                val value = main[index] ?: return@forEach
                val label = units.temp(value, locale = locale)
                val measured = textMeasurer.measure(label, valueStyle)
                val above = index == maxIndex
                val y = yAt(value) + if (above) -measured.size.height - 3.dp.toPx() else 3.dp.toPx()
                val x = (xAt(index) - measured.size.width / 2f)
                    .clampLabel(chartLeft, chartRight - measured.size.width)
                drawCircle(mainColor, radius = 3.dp.toPx(), center = Offset(xAt(index), yAt(value)))
                drawText(
                    measured,
                    topLeft = Offset(x, y.clampLabel(0f, size.height - measured.size.height))
                )
            }
        }

        if (traces.size >= 2) {
            Text(
                text = stringResource(R.string.overview_hourly_legend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Trace une série en coupant le trait sur les valeurs absentes. */
private fun DrawScope.drawSeries(
    values: List<Double?>,
    xAt: (Int) -> Float,
    yAt: (Double) -> Float,
    color: Color,
    stroke: Stroke
) {
    val path = Path()
    var drawing = false
    var segments = 0
    values.forEachIndexed { index, value ->
        val plotted = metricPlotValue(value)
        if (plotted == null) {
            drawing = false
            return@forEachIndexed
        }
        val x = xAt(index)
        val y = yAt(plotted)
        if (drawing) {
            path.lineTo(x, y)
            segments++
        } else {
            path.moveTo(x, y)
            drawing = true
        }
    }
    if (segments > 0) drawPath(path = path, color = color, style = stroke)
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

/** Comme coerceIn, sans exception si le libellé est plus large que la zone. */
internal fun Float.clampLabel(min: Float, max: Float): Float = coerceAtMost(max).coerceAtLeast(min)

private val ChartHeight = 168.dp

internal const val TAG_OVERVIEW_HOURLY_CHART = "overview_hourly_chart"

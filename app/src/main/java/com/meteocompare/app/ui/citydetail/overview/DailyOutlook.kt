package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import java.time.format.DateTimeFormatter

/**
 * Liste compacte des prochains jours : condition, minimale → maximale de la
 * prévision retenue, repères pâles de l'étendue des maximales entre modèles,
 * signal pluie et vent maximal.
 */
@Composable
internal fun DailyOutlook(
    days: List<SimplifiedTimelinePoint>,
    modifier: Modifier = Modifier,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val rows = days.filter { it.date != null && (it.tempMinC != null || it.tempMaxC != null) }
    if (rows.isEmpty()) return

    // Échelle commune à toutes les lignes pour que les barres soient comparables.
    val scaleMin = rows.mapNotNull { listOfNotNull(it.tempMinC, it.temperatureMinAcrossModels).minOrNull() }.minOrNull()
    val scaleMax = rows.mapNotNull { listOfNotNull(it.tempMaxC, it.temperatureMaxAcrossModels).maxOrNull() }.maxOrNull()
    if (scaleMin == null || scaleMax == null) return
    val span = (scaleMax - scaleMin).coerceAtLeast(1.0)

    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_OVERVIEW_DAILY),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.overview_daily_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        rows.forEachIndexed { index, day ->
            DailyOutlookRow(
                day = day,
                isToday = index == 0,
                scaleMin = scaleMin,
                span = span,
                units = units
            )
        }
    }
}

@Composable
private fun DailyOutlookRow(
    day: SimplifiedTimelinePoint,
    isToday: Boolean,
    scaleMin: Double,
    span: Double,
    units: WeatherUnits
) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val date = day.date ?: return
    val dayLabel = if (isToday) {
        stringResource(R.string.timeline_today)
    } else {
        date.format(dayFormatter).replace(".", "").replaceFirstChar { it.uppercase() }
    }
    val conditionLabel = day.condition?.let { stringResource(weatherConditionLabelRes(it)) }
    val low = day.tempMinC
    val high = day.tempMaxC
    val rainText = dailyRainText(day)
    val rainA11y = dailyRainA11y(day)
    val windText = day.windKmh?.let { units.speed(it, locale) }
    val highRange = if (day.temperatureMinAcrossModels != null && day.temperatureMaxAcrossModels != null &&
        !units.sameDisplayedValue(day.temperatureMinAcrossModels, day.temperatureMaxAcrossModels, WeatherUnit.TEMPERATURE_COMPACT)
    ) {
        stringResource(
            R.string.overview_daily_high_range_a11y,
            units.temp(day.temperatureMinAcrossModels, locale = locale),
            units.temp(day.temperatureMaxAcrossModels, locale = locale)
        )
    } else null
    val description = listOfNotNull(
        dayLabel,
        conditionLabel,
        stringResource(R.string.overview_high_low, units.temp(high, locale = locale), units.temp(low, locale = locale)),
        highRange,
        rainA11y,
        windText?.let { stringResource(R.string.overview_wind, it) }
    ).joinToString(". ")

    val lowColor = low?.let(::temperatureHeatmapColor) ?: MaterialTheme.colorScheme.primary
    val highColor = high?.let(::temperatureHeatmapColor) ?: MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val rangeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(72.dp),
            maxLines = 1
        )
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            AnimatedWeatherIcon(
                condition = day.condition,
                size = 28.dp,
                animated = false,
                animateConditionChanges = false
            )
        }
        Text(
            text = rainText ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
            maxLines = 1
        )
        Text(
            text = units.temp(low, locale = locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
            maxLines = 1
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clearAndSetSemantics { }
        ) {
            val barH = 6.dp.toPx()
            val y = (size.height - barH) / 2f
            fun xAt(value: Double): Float =
                (((value - scaleMin) / span).toFloat() * size.width).coerceIn(0f, size.width)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, y),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(barH / 2f)
            )
            if (low != null && high != null && high >= low) {
                val start = xAt(low)
                val end = xAt(high).coerceAtLeast(start + barH)
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(lowColor, highColor), startX = start, endX = end),
                    topLeft = Offset(start, y),
                    size = Size(end - start, barH),
                    cornerRadius = CornerRadius(barH / 2f)
                )
            }
            // Étendue des maximales entre modèles : deux repères pâles.
            val rangeLow = day.temperatureMinAcrossModels
            val rangeHigh = day.temperatureMaxAcrossModels
            if (rangeLow != null && rangeHigh != null && rangeHigh > rangeLow) {
                listOf(rangeLow, rangeHigh).forEach { value ->
                    val x = xAt(value)
                    drawLine(
                        color = rangeColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
        Text(
            text = units.temp(high, locale = locale),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
            maxLines = 1
        )
        Text(
            text = windText ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
            maxLines = 1
        )
    }
}

/** Libellé court de pluie : risque si probabilités natives, sinon part de modèles. */
@Composable
private fun dailyRainText(day: SimplifiedTimelinePoint): String? {
    val percent = day.precipitationPercent ?: return null
    if (percent < DAILY_RAIN_DISPLAY_MIN_PERCENT) return null
    return when (rainLikelihoodWording(day.precipitationSource)) {
        RainLikelihoodWording.CHANCE -> stringResource(R.string.overview_percent, percent)
        RainLikelihoodWording.FAMILY_SHARE, null -> day.precipitationModelCount.takeIf { it > 0 }?.let { total ->
            stringResource(R.string.overview_daily_rain_models, day.wetModelCount, total)
        }
    }
}

@Composable
private fun dailyRainA11y(day: SimplifiedTimelinePoint): String? {
    val percent = day.precipitationPercent ?: return null
    if (percent < DAILY_RAIN_DISPLAY_MIN_PERCENT) return null
    return when (rainLikelihoodWording(day.precipitationSource)) {
        RainLikelihoodWording.CHANCE -> stringResource(R.string.overview_rain_chance_peak, percent)
        RainLikelihoodWording.FAMILY_SHARE, null -> day.precipitationModelCount.takeIf { it > 0 }?.let { total ->
            stringResource(R.string.overview_daily_rain_models_a11y, day.wetModelCount, total)
        }
    }
}

/** En dessous, le signal pluie n'est pas affiché pour ne pas surcharger la liste. */
private const val DAILY_RAIN_DISPLAY_MIN_PERCENT = 20

internal const val TAG_OVERVIEW_DAILY = "overview_daily"

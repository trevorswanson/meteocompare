package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import java.time.format.DateTimeFormatter

/**
 * Prochains jours : nom du jour et signal pluie, icône, minimale, barre
 * min → max sur une échelle commune, maximale. Une ligne se déplie pour le
 * vent, la pluie et l'étendue des maximales entre modèles.
 */
@Composable
internal fun DailyOutlook(
    days: List<SimplifiedTimelinePoint>,
    modifier: Modifier = Modifier,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val rows = days.filter { it.date != null && it.tempMinC != null && it.tempMaxC != null }
    if (rows.isEmpty()) return
    val scaleMin = rows.minOf { requireNotNull(it.tempMinC) }
    val scaleMax = rows.maxOf { requireNotNull(it.tempMaxC) }
    val span = (scaleMax - scaleMin).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_OVERVIEW_DAILY)) {
        rows.forEachIndexed { index, day ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
            DailyOutlookRow(day = day, isToday = index == 0, scaleMin = scaleMin, span = span, units = units)
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
    var expanded by rememberSaveable(day.date?.toString()) { mutableStateOf(false) }
    val date = requireNotNull(day.date)
    val low = requireNotNull(day.tempMinC)
    val high = requireNotNull(day.tempMaxC)
    val dayLabel = if (isToday) {
        stringResource(R.string.timeline_today)
    } else {
        date.format(dayFormatter).replace(".", "").replaceFirstChar { it.uppercase() }
    }
    val conditionLabel = day.condition?.let { stringResource(weatherConditionLabelRes(it)) }
    val rainText = dailyRainText(day)
    val rainA11y = dailyRainA11y(day)
    val highLow = stringResource(R.string.overview_high_low, units.temp(high, locale = locale), units.temp(low, locale = locale))
    val description = listOfNotNull(dayLabel, conditionLabel, highLow, rainA11y).joinToString(". ")
    val stateLabel = stringResource(if (expanded) R.string.section_state_expanded else R.string.section_state_collapsed)
    val pillColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = stateLabel
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(72.dp)) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (rainText != null) {
                    Text(
                        text = rainText,
                        style = MaterialTheme.typography.labelMedium,
                        color = precipitationMetricAccent(),
                        maxLines = 1
                    )
                }
            }
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                AnimatedWeatherIcon(
                    condition = day.condition,
                    size = 34.dp,
                    animated = false,
                    animateConditionChanges = false
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .padding(horizontal = 4.dp)
                    .clearAndSetSemantics { }
            ) {
                val track = (maxWidth - LabelSlot * 2).coerceAtLeast(24.dp)
                val start = LabelSlot + track * ((low - scaleMin) / span).toFloat()
                val end = (LabelSlot + track * ((high - scaleMin) / span).toFloat()).coerceAtLeast(start + PillHeight)
                Text(
                    text = units.temp(low, locale = locale),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = start - LabelSlot)
                        .width(LabelSlot - 4.dp)
                )
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = start)
                        .width(end - start)
                        .height(PillHeight)
                ) {
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
                Text(
                    text = units.temp(high, locale = locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = end + 4.dp)
                        .width(LabelSlot - 4.dp)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            DailyDetails(day = day, conditionLabel = conditionLabel, units = units)
        }
    }
}

@Composable
private fun DailyDetails(day: SimplifiedTimelinePoint, conditionLabel: String?, units: WeatherUnits) {
    val locale = LocalConfiguration.current.locales[0]
    val lines = buildList {
        conditionLabel?.let { add(it) }
        day.windKmh?.let { wind ->
            val gust = day.windGustKmh?.takeIf { it > wind }
            add(
                if (gust != null) {
                    stringResource(
                        R.string.overview_daily_wind_gusts,
                        units.speed(wind, locale),
                        units.speed(gust, locale)
                    )
                } else {
                    stringResource(R.string.overview_wind, units.speed(wind, locale))
                }
            )
        }
        (day.precipitationExpectedMm ?: day.precipitationMm)?.takeIf { it > 0.0 }?.let {
            add(stringResource(R.string.overview_daily_rain_amount, units.rain(it, locale)))
        }
        val rangeLow = day.temperatureMinAcrossModels
        val rangeHigh = day.temperatureMaxAcrossModels
        if (rangeLow != null && rangeHigh != null &&
            !units.sameDisplayedValue(rangeLow, rangeHigh, WeatherUnit.TEMPERATURE_COMPACT, locale = locale)
        ) {
            add(
                stringResource(
                    R.string.overview_daily_high_range_a11y,
                    units.temp(rangeLow, locale = locale),
                    units.temp(rangeHigh, locale = locale)
                )
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        RainLikelihoodWording.CHANCE -> stringResource(R.string.overview_rain_chance_a11y, percent)
        RainLikelihoodWording.FAMILY_SHARE, null -> day.precipitationModelCount.takeIf { it > 0 }?.let { total ->
            stringResource(R.string.overview_daily_rain_models_a11y, day.wetModelCount, total)
        }
    }
}

/** En dessous, le signal pluie n'est pas affiché pour ne pas surcharger la liste. */
private const val DAILY_RAIN_DISPLAY_MIN_PERCENT = 20

private val LabelSlot = 44.dp
private val PillHeight = 12.dp

internal const val TAG_OVERVIEW_DAILY = "overview_daily"

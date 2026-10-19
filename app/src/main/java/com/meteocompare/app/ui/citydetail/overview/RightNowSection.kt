package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Thermostat
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ForecastSolarWindow
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.theme.SectionHeadlineStyle
import com.meteocompare.app.ui.theme.confidenceColor
import java.util.Locale

/**
 * « Maintenant » : grande icône, température actuelle, condition et
 * max/min du jour. « Plus » déplie vent, UV, soleil et accord des modèles.
 */
@Composable
internal fun RightNowSection(
    model: OverviewModel,
    timezone: String?,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentIsNight: Boolean,
    uvIndex: Double?,
    solarWindow: ForecastSolarWindow?,
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalConfiguration.current.locales[0]
    var showMore by rememberSaveable { mutableStateOf(false) }
    val today = model.today
    val conditionLabel = currentCondition?.let { stringResource(weatherConditionLabelRes(it)) }
    val highLow = if (today?.tempMaxC != null || today?.tempMinC != null) {
        stringResource(
            R.string.overview_high_low,
            units.temp(today?.tempMaxC, locale = locale),
            units.temp(today?.tempMinC, locale = locale)
        )
    } else null
    val nowLabel = currentTemp?.let { stringResource(R.string.overview_now_a11y, units.temp(it, locale = locale)) }

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_OVERVIEW_RIGHT_NOW)) {
        OverviewSectionTitle(stringResource(R.string.overview_right_now_title))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(nowLabel, conditionLabel, highLow).joinToString(". ")
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                if (currentCondition != null) {
                    AnimatedWeatherIcon(condition = currentCondition, size = 104.dp, isNight = currentIsNight)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTemp?.let { units.temp(it, locale = locale) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light
                )
                conditionLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                highLow?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoreToggle(expanded = showMore, onToggle = { showMore = !showMore })
            }
        }
        AnimatedVisibility(visible = showMore) {
            RightNowDetails(
                model = model,
                timezone = timezone,
                uvIndex = uvIndex,
                solarWindow = solarWindow,
                onWhyClick = onWhyClick,
                units = units,
                locale = locale
            )
        }
    }
}

@Composable
private fun MoreToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag(TAG_OVERVIEW_MORE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (expanded) R.string.overview_less else R.string.overview_more),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RightNowDetails(
    model: OverviewModel,
    timezone: String?,
    uvIndex: Double?,
    solarWindow: ForecastSolarWindow?,
    onWhyClick: () -> Unit,
    units: WeatherUnits,
    locale: Locale
) {
    val clock = LocalClockFormatPreference.current
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val clockFormatter = remember(locale, clock) { WeatherTimeFormat.clock(locale, clock) }
    val current = model.hourly.firstOrNull()
    val rows = buildList {
        current?.windKmh?.let { wind ->
            val gust = current.windGustKmh?.takeIf { it > wind }
            add(
                stringResource(R.string.overview_detail_wind) to if (gust != null) {
                    stringResource(R.string.overview_detail_wind_gusts, units.speed(wind, locale), units.speed(gust, locale))
                } else {
                    units.speed(wind, locale)
                }
            )
        }
        current?.cloudCoverPercent?.let {
            add(stringResource(R.string.overview_detail_clouds) to stringResource(R.string.overview_percent, it))
        }
        uvIndex?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            add(stringResource(R.string.overview_detail_uv) to stringResource(R.string.uv_index_value, it))
        }
        solarWindow?.sunrise?.let {
            add(stringResource(R.string.overview_detail_sunrise) to it.atZone(zone).format(clockFormatter))
        }
        solarWindow?.sunset?.let {
            add(stringResource(R.string.overview_detail_sunset) to it.atZone(zone).format(clockFormatter))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        model.agreement?.let { AgreementRow(it, onWhyClick) }
    }
}

/** Accord entre modèles, sans promesse de justesse ; « Pourquoi ? » ouvre l'explication. */
@Composable
private fun AgreementRow(agreement: AgreementWording, onClick: () -> Unit) {
    val (labelRes, tone) = when (agreement) {
        AgreementWording.AGREE -> R.string.overview_agreement_agree to confidenceColor(80)
        AgreementWording.SOME_DISAGREEMENT -> R.string.overview_agreement_some to confidenceColor(50)
        AgreementWording.DISAGREE -> R.string.overview_agreement_disagree to confidenceColor(0)
    }
    val label = stringResource(labelRes)
    val a11y = stringResource(R.string.overview_agreement_a11y, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = a11y }
            .testTag(TAG_OVERVIEW_AGREEMENT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(tone, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.overview_why),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Titre de section appuyé (style « emphasized » de Material 3 Expressive). */
@Composable
internal fun OverviewSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SectionHeadlineStyle,
        modifier = modifier.semantics { androidx.compose.ui.semantics.heading() }
    )
}

internal const val TAG_OVERVIEW_RIGHT_NOW = "overview_right_now"
internal const val TAG_OVERVIEW_MORE = "overview_more"
internal const val TAG_OVERVIEW_AGREEMENT = "overview_agreement"

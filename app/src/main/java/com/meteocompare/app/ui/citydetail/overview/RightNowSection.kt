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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.temperatureMinMetricAccent
import java.util.Locale

/**
 * « Maintenant » : grande icône, température actuelle, condition et
 * max/min du jour, et la pastille de convergence des modèles (qui ouvre
 * l’explication). La flèche en bas à droite déplie vent, nuages, humidité,
 * point de rosée, UV et soleil.
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
            units.temp(today.tempMaxC, locale = locale),
            units.temp(today.tempMinC, locale = locale)
        )
    } else null
    val nowLabel = currentTemp?.let { stringResource(R.string.overview_now_a11y, units.temp(it, locale = locale)) }

    OverviewPane(modifier = modifier.testTag(TAG_OVERVIEW_RIGHT_NOW)) {
        OverviewSectionTitle(
            text = stringResource(R.string.overview_right_now_title),
            agreement = model.agreement,
            onAgreementClick = onWhyClick,
            agreementTag = TAG_OVERVIEW_AGREEMENT
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(nowLabel, conditionLabel, highLow).joinToString(". ")
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                if (currentCondition != null) {
                    AnimatedWeatherIcon(condition = currentCondition, size = 88.dp, isNight = currentIsNight)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Température actuelle, puis max ↑ et min ↓ du jour sur deux lignes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = units.degrees(currentTemp, locale),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light
                    )
                    if (today?.tempMaxC != null || today?.tempMinC != null) {
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            HighLowLine(
                                icon = Icons.Filled.ArrowUpward,
                                tint = temperatureMetricAccent(),
                                text = units.degrees(today.tempMaxC, locale)
                            )
                            HighLowLine(
                                icon = Icons.Filled.ArrowDownward,
                                tint = temperatureMinMetricAccent(),
                                text = units.degrees(today.tempMinC, locale)
                            )
                        }
                    }
                }
                conditionLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        }
        AnimatedVisibility(visible = showMore) {
            RightNowDetails(
                model = model,
                timezone = timezone,
                uvIndex = uvIndex,
                solarWindow = solarWindow,
                units = units,
                locale = locale
            )
        }
        // Flèche seule, en bas à droite du panneau : déplie vent, nuages, UV…
        MoreToggle(
            expanded = showMore,
            onToggle = { showMore = !showMore },
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun HighLowLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(2.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun MoreToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.testTag(TAG_OVERVIEW_MORE)) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            // Le libellé reste pour TalkBack : « Plus » / « Moins ».
            contentDescription = stringResource(if (expanded) R.string.overview_less else R.string.overview_more),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RightNowDetails(
    model: OverviewModel,
    timezone: String?,
    uvIndex: Double?,
    solarWindow: ForecastSolarWindow?,
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
        current?.relativeHumidityPercent?.let {
            add(stringResource(R.string.overview_detail_humidity) to stringResource(R.string.overview_percent, it))
        }
        current?.dewPointC?.let {
            add(stringResource(R.string.overview_detail_dew_point) to units.degrees(it, locale))
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
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
    }
}

/**
 * Titre de section appuyé (style « emphasized » de Material 3 Expressive), avec
 * à droite le badge de convergence des modèles quand il est connu.
 */
@Composable
internal fun OverviewSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    agreement: AgreementWording? = null,
    onAgreementClick: (() -> Unit)? = null,
    agreementTag: String? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = SectionHeadlineStyle,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (agreement != null) {
            Spacer(Modifier.width(12.dp))
            ConvergenceBadge(
                agreement = agreement,
                onClick = onAgreementClick,
                modifier = if (agreementTag != null) Modifier.testTag(agreementTag) else Modifier
            )
        }
    }
}

/**
 * Pastille de convergence des modèles : décrit leur accord, jamais la justesse.
 * Cliquable quand une explication détaillée existe.
 */
@Composable
internal fun ConvergenceBadge(
    agreement: AgreementWording,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (labelRes, tone) = when (agreement) {
        AgreementWording.AGREE -> R.string.overview_convergence_high to confidenceColor(80)
        AgreementWording.SOME_DISAGREEMENT -> R.string.overview_convergence_medium to confidenceColor(50)
        AgreementWording.DISAGREE -> R.string.overview_convergence_low to confidenceColor(0)
    }
    val label = stringResource(labelRes)
    val description = if (onClick != null) stringResource(R.string.overview_agreement_a11y, label) else label
    Surface(
        shape = CircleShape,
        color = tone.copy(alpha = 0.14f),
        modifier = modifier
            .then(if (onClick != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(tone, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

internal const val TAG_OVERVIEW_RIGHT_NOW = "overview_right_now"
internal const val TAG_OVERVIEW_MORE = "overview_more"
internal const val TAG_OVERVIEW_AGREEMENT = "overview_agreement"

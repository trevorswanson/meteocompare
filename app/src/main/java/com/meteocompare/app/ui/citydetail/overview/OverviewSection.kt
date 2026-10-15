package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.forecastInsightPresentation
import com.meteocompare.app.ui.citydetail.insightVisual
import com.meteocompare.app.ui.citylist.HomeWeatherScenarioRow
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.theme.confidenceColor

/**
 * Aperçu simple de la fiche ville : l'essentiel lisible en quelques
 * secondes, sans connaître les noms des modèles.
 *
 * Il ne calcule rien de nouveau : la prévision retenue vient du moteur
 * sélectionné, l'accord des sorties brutes des modèles, et les lignes pâles des
 * lignées indépendantes. « Pourquoi ? » ouvre l'explication existante.
 */
@Composable
internal fun OverviewSection(
    model: OverviewModel,
    timezone: String?,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentIsNight: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCompareModelsClick: (() -> Unit)? = null,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_OVERVIEW_SECTION)
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.overview_title),
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) }
            )
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OverviewNowRow(
                        model = model,
                        currentTemp = currentTemp,
                        currentCondition = currentCondition,
                        currentIsNight = currentIsNight,
                        units = units
                    )
                    OverviewHeadline(model = model, timezone = timezone)
                    model.agreement?.let { agreement ->
                        AgreementChip(agreement = agreement, onClick = onWhyClick)
                    }

                    OverviewDivider()
                    OverviewSubtitle(stringResource(R.string.overview_hourly_title))
                    HourlyOutlookChart(
                        points = model.hourly,
                        traces = model.temperatureTraces,
                        timezone = timezone,
                        units = units,
                        onClick = onWhyClick
                    )

                    OverviewDivider()
                    PrecipOutlook(
                        hours = model.precipitation,
                        onset = model.rainOnset,
                        timezone = timezone
                    )

                    if (model.scenarios.isNotEmpty()) {
                        OverviewDivider()
                        Column(modifier = Modifier.testTag(TAG_OVERVIEW_SCENARIOS)) {
                            OverviewSubtitle(stringResource(R.string.overview_scenarios_title))
                            model.scenarios.forEachIndexed { rank, scenario ->
                                HomeWeatherScenarioRow(
                                    scenario = scenario,
                                    rank = rank,
                                    isNight = currentIsNight
                                )
                            }
                        }
                    }

                    if (model.daily.isNotEmpty()) {
                        OverviewDivider()
                        DailyOutlook(days = model.daily, units = units)
                    }

                    if (onCompareModelsClick != null) {
                        TextButton(
                            onClick = onCompareModelsClick,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag(TAG_OVERVIEW_COMPARE_MODELS)
                        ) {
                            Text(stringResource(R.string.overview_compare_models))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewNowRow(
    model: OverviewModel,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentIsNight: Boolean,
    units: WeatherUnits
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = model.today
    val conditionLabel = currentCondition?.let { stringResource(weatherConditionLabelRes(it)) }
    val highLow = if (today?.tempMaxC != null || today?.tempMinC != null) {
        stringResource(
            R.string.overview_high_low,
            units.temp(today?.tempMaxC, locale = locale),
            units.temp(today?.tempMinC, locale = locale)
        )
    } else null
    val nowLabel = currentTemp?.let {
        stringResource(R.string.overview_now_a11y, units.temp(it, locale = locale))
    }
    val description = listOfNotNull(nowLabel, conditionLabel, highLow).joinToString(". ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            if (currentCondition != null) {
                AnimatedWeatherIcon(
                    condition = currentCondition,
                    size = 56.dp,
                    isNight = currentIsNight
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentTemp?.let { units.temp(it, locale = locale) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
            conditionLabel?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
            highLow?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverviewHeadline(model: OverviewModel, timezone: String?) {
    val insight = model.headline ?: return
    val visual = insightVisual(insight)
    val presentation = forecastInsightPresentation(insight, timezone, model.hourly.firstOrNull())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_OVERVIEW_HEADLINE)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            presentation.timeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgreementChip(agreement: AgreementWording, onClick: () -> Unit) {
    val (labelRes, toneColor) = when (agreement) {
        AgreementWording.AGREE -> R.string.overview_agreement_agree to confidenceColor(80)
        AgreementWording.SOME_DISAGREEMENT -> R.string.overview_agreement_some to confidenceColor(50)
        AgreementWording.DISAGREE -> R.string.overview_agreement_disagree to confidenceColor(0)
    }
    val label = stringResource(labelRes)
    val a11y = stringResource(R.string.overview_agreement_a11y, label)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .testTag(TAG_OVERVIEW_AGREEMENT)
            .semantics {
                contentDescription = a11y
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(toneColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.overview_why),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OverviewSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun OverviewDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * En-tête du groupe « Comparaison des modèles » qui regroupe, sous l'aperçu,
 * toutes les sections détaillées existantes.
 */
@Composable
internal fun ModelComparisonGroupHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_MODEL_COMPARISON_GROUP)
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.overview_model_comparison_title),
                subtitle = stringResource(R.string.overview_model_comparison_subtitle),
                expanded = expanded,
                onToggle = onToggle
            )
        }
    }
}

internal const val TAG_OVERVIEW_SECTION = "overview_section"
internal const val TAG_OVERVIEW_HEADLINE = "overview_headline"
internal const val TAG_OVERVIEW_AGREEMENT = "overview_agreement"
internal const val TAG_OVERVIEW_SCENARIOS = "overview_scenarios"
internal const val TAG_OVERVIEW_COMPARE_MODELS = "overview_compare_models"
internal const val TAG_MODEL_COMPARISON_GROUP = "model_comparison_group"

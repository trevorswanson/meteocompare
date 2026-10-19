package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ForecastSolarWindow
import java.time.Instant
import java.time.LocalDate

/**
 * Aperçu simple d'une ville : « Maintenant », « Prochaine heure » (s'il
 * tombe quelque chose d'ici une heure), « Prochaines 24 heures »,
 * « Prochains jours », puis l'accès aux détails des modèles.
 *
 * Contenu sans défilement propre : l'écran hôte le place dans sa liste.
 */
@Composable
internal fun OverviewContent(
    model: OverviewModel,
    timezone: String?,
    now: Instant,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentIsNight: Boolean,
    uvIndex: Double?,
    solarWindow: ForecastSolarWindow?,
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
    onModelDetailsClick: (() -> Unit)? = null,
    dayOutlookFor: (LocalDate) -> DayOutlook? = { null },
    nextHour: NextHourOutlook? = null,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    // Une seule infobulle horaire à la fois ; tout toucher ailleurs la ferme.
    val tooltipHost = remember { TimelineTooltipHost() }
    CompositionLocalProvider(LocalTimelineTooltipHost provides tooltipHost) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .dismissTimelineTooltipOnTouch(tooltipHost)
                .testTag(TAG_OVERVIEW_SECTION),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            RightNowSection(
                model = model,
                timezone = timezone,
                currentTemp = currentTemp,
                currentCondition = currentCondition,
                currentIsNight = currentIsNight,
                uvIndex = uvIndex,
                solarWindow = solarWindow,
                onWhyClick = onWhyClick,
                units = units
            )

            nextHour?.let { NextHourSection(it) }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewSectionTitle(stringResource(R.string.overview_next_24h_title))
                val summary = narrativeText(model.narrative, timezone)
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TAG_OVERVIEW_NARRATIVE)
                    )
                }
                HourlyOutlookChart(
                    points = model.hourly,
                    traces = model.temperatureTraces,
                    timezone = timezone,
                    now = now,
                    solarEvents = model.timelineSolarEvents,
                    units = units
                )
            }

            if (model.daily.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewSectionTitle(stringResource(R.string.overview_next_days_title))
                    DailyOutlook(
                        days = model.daily,
                        ranges = model.dailyRanges,
                        timezone = timezone,
                        now = now,
                        dayOutlookFor = dayOutlookFor,
                        units = units
                    )
                }
            }

            if (onModelDetailsClick != null) {
                ModelDetailsLink(onClick = onModelDetailsClick)
            }
        }
    }
}

/** Unique accès vers la comparaison complète des modèles (fiche détaillée historique). */
@Composable
private fun ModelDetailsLink(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_OVERVIEW_MODEL_DETAILS)
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.overview_model_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.overview_model_details_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal const val TAG_OVERVIEW_SECTION = "overview_section"
internal const val TAG_OVERVIEW_NARRATIVE = "overview_narrative"
internal const val TAG_OVERVIEW_MODEL_DETAILS = "overview_model_details"

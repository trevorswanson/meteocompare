package com.meteocompare.app.ui.citydetail.overview

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import java.time.Instant

/**
 * Frise pluie des prochaines heures.
 *
 * Hauteur d'une barre = intensité si la pluie se produit ; opacité = part du
 * signal de pluie. Le texte au-dessus résume le début de la pluie et sa
 * vraisemblance, avec une formulation qui dépend de l'origine du pourcentage
 * (probabilités natives ou simple part de lignées, cf. [RainLikelihoodWording]).
 */
@Composable
internal fun PrecipOutlook(
    hours: List<OverviewPrecipHour>,
    onset: RainOnsetWindow?,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val clockPreference = LocalClockFormatPreference.current
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale, clockPreference) { WeatherTimeFormat.hour(locale, clockPreference) }
    fun hour(instant: Instant): String = instant.atZone(zone).format(hourFormatter)

    val wetHours = hours.filter(OverviewPrecipHour::isWet)
    val headline = when {
        wetHours.isEmpty() -> stringResource(R.string.overview_precip_none)
        onset != null && onset.median == hours.firstOrNull()?.instant ->
            stringResource(R.string.overview_rain_now)
        onset != null && onset.hasTimingSpread -> stringResource(
            R.string.overview_rain_onset_range,
            hour(onset.median),
            hour(onset.earliest),
            hour(onset.latest)
        )
        onset != null -> stringResource(R.string.overview_rain_onset, hour(onset.median))
        wetHours.first().instant == hours.firstOrNull()?.instant ->
            stringResource(R.string.overview_rain_now)
        else -> stringResource(R.string.overview_rain_onset, hour(wetHours.first().instant))
    }

    val peak = wetHours.maxByOrNull { likelihoodFraction(it) }
    val likelihoodText = peak?.let { hourPeak ->
        when (hourPeak.wording) {
            RainLikelihoodWording.CHANCE -> hourPeak.likelihoodPercent?.let { percent ->
                stringResource(R.string.overview_rain_chance_peak, percent)
            }
            RainLikelihoodWording.FAMILY_SHARE, null -> hourPeak.familyShare
                .takeIf { it.total > 0 }
                ?.let { share -> stringResource(R.string.overview_rain_family_share, share.wet, share.total) }
        }
    }
    val noteText = when (peak?.wording) {
        RainLikelihoodWording.CHANCE -> stringResource(R.string.overview_rain_chance_note)
        RainLikelihoodWording.FAMILY_SHARE -> stringResource(R.string.overview_rain_family_share_note)
        null -> null
    }
    val intensityText = peak?.intensity?.let { intensity ->
        when (intensity) {
            RainIntensity.NONE -> null
            RainIntensity.LIGHT -> stringResource(R.string.overview_intensity_light)
            RainIntensity.MODERATE -> stringResource(R.string.overview_intensity_moderate)
            RainIntensity.HEAVY -> stringResource(R.string.overview_intensity_heavy)
        }
    }
    val summary = listOfNotNull(headline, likelihoodText, intensityText).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_OVERVIEW_PRECIP)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(summary, noteText).joinToString(". ")
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.overview_precip_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (wetHours.isNotEmpty()) {
            val barColor = precipitationMetricAccent()
            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
            val percentStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarsHeight)
                    .clearAndSetSemantics { }
            ) {
                val labelBand = 16.dp.toPx()
                val percentBand = 12.dp.toPx()
                val barsTop = percentBand
                val barsBottom = size.height - labelBand
                val barsH = (barsBottom - barsTop).coerceAtLeast(1f)
                val slot = size.width / hours.size.coerceAtLeast(1)
                val barW = (slot * 0.68f).coerceAtLeast(2f)
                val labelEvery = if (slot * 3 < 34.dp.toPx()) 6 else 3

                hours.forEachIndexed { index, hour ->
                    val left = index * slot + (slot - barW) / 2f
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, barsBottom - 2.dp.toPx()),
                        size = Size(barW, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                    if (hour.isWet) {
                        val height = barsH * intensityHeight(hour.intensity)
                        val alpha = 0.25f + 0.75f * likelihoodFraction(hour)
                        drawRoundRect(
                            color = barColor.copy(alpha = alpha.coerceIn(0.25f, 1f)),
                            topLeft = Offset(left, barsBottom - height),
                            size = Size(barW, height),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                    if (index % labelEvery == 0) {
                        val label = hour.instant.atZone(zone).format(hourFormatter)
                        val measured = textMeasurer.measure(label, labelStyle)
                        val x = (index * slot + slot / 2f - measured.size.width / 2f)
                            .clampLabel(0f, size.width - measured.size.width)
                        drawText(measured, topLeft = Offset(x, barsBottom + 3.dp.toPx()))

                        // Pourcentage écrit au-dessus des barres repères : la
                        // vraisemblance ne repose jamais sur la seule opacité.
                        if (hour.isWet) {
                            val percentLabel = "${(likelihoodFraction(hour) * 100).toInt()}%"
                            val percentMeasured = textMeasurer.measure(percentLabel, percentStyle)
                            val px = (index * slot + slot / 2f - percentMeasured.size.width / 2f)
                                .clampLabel(0f, size.width - percentMeasured.size.width)
                            drawText(percentMeasured, topLeft = Offset(px, 0f))
                        }
                    }
                }
            }

            Text(
                text = listOfNotNull(stringResource(R.string.overview_precip_legend), noteText).joinToString(" "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val BarsHeight = 76.dp

internal const val TAG_OVERVIEW_PRECIP = "overview_precip"

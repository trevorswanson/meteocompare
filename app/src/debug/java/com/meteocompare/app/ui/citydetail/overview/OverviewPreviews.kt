package com.meteocompare.app.ui.citydetail.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.forecastSolarWindow
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

private fun previewOverviewModel(): OverviewModel {
    val forecast = PreviewFixtures.forecast()
    val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, PreviewFixtures.now, hourlyHorizonHours = 24)
    val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, PreviewFixtures.now)
    return buildOverviewModel(forecast, hourly, daily, PreviewFixtures.now)
}

@Composable
private fun PreviewOverview() {
    OverviewContent(
        model = previewOverviewModel(),
        timezone = PreviewFixtures.city.timezone,
        now = PreviewFixtures.now,
        currentTemp = 22.0,
        currentCondition = WeatherCondition.PARTLY_CLOUDY,
        currentIsNight = false,
        uvIndex = 4.0,
        solarWindow = forecastSolarWindow(PreviewFixtures.forecast(), PreviewFixtures.today),
        onWhyClick = {},
        onModelDetailsClick = {}
    )
}

@MeteoScreenPreview
@Composable
private fun OverviewContentPreview() {
    MeteoPreviewSurface {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            PreviewOverview()
        }
    }
}

@Preview(name = "Small, large font", group = "MeteoCompare", showBackground = true, widthDp = 360, heightDp = 1600, fontScale = 2f)
@Preview(
    name = "Small, large font, dark",
    group = "MeteoCompare",
    showBackground = true,
    widthDp = 360,
    heightDp = 1600,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun OverviewContentLargeFontPreview() {
    MeteoPreviewSurface {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            PreviewOverview()
        }
    }
}

@MeteoComponentPreview
@Composable
private fun OverviewComponentsPreview() {
    val model = previewOverviewModel()
    MeteoPreviewSurface {
        Column(Modifier.padding(16.dp)) {
            RightNowSection(
                model = model,
                timezone = PreviewFixtures.city.timezone,
                currentTemp = 22.0,
                currentCondition = WeatherCondition.CLEAR,
                currentIsNight = false,
                uvIndex = 6.0,
                solarWindow = null,
                onWhyClick = {}
            )
            HourlyOutlookChart(
                points = model.hourly,
                traces = model.temperatureTraces,
                periods = model.periods,
                timezone = PreviewFixtures.city.timezone,
                now = PreviewFixtures.now
            )
            DailyOutlook(days = model.daily)
        }
    }
}

/** Synthèse seule, avec plusieurs phrases, pour relire les traductions. */
@MeteoComponentPreview
@Composable
private fun OverviewNarrativePreview() {
    val model = previewOverviewModel()
    MeteoPreviewSurface {
        Column(Modifier.padding(16.dp)) {
            androidx.compose.material3.Text(narrativeText(model.narrative, PreviewFixtures.city.timezone))
            androidx.compose.material3.Text(rainSignalText(model.precipitation.maxByOrNull(::likelihoodFraction)) ?: "—")
        }
    }
}

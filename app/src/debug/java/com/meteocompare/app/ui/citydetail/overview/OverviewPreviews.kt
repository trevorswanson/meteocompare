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
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildForecastInsights
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.citydetail.overviewFromTimelines
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

private fun previewOverviewModel(): OverviewModel {
    val forecast = PreviewFixtures.forecast()
    val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, PreviewFixtures.now, hourlyHorizonHours = 24)
    val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, PreviewFixtures.now)
    val insights = buildForecastInsights(overviewFromTimelines(hourly, daily, forecast.city.timezone))
    return buildOverviewModel(
        forecast = forecast,
        hourly = hourly,
        daily = daily,
        insights = insights,
        scenarios = PreviewFixtures.scenarios
    )
}

@MeteoScreenPreview
@Composable
private fun OverviewSectionPreview() {
    MeteoPreviewSurface {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            OverviewSection(
                model = previewOverviewModel(),
                timezone = PreviewFixtures.city.timezone,
                currentTemp = 22.0,
                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                currentIsNight = false,
                expanded = true,
                onExpandedChange = {},
                onWhyClick = {},
                onCompareModelsClick = {}
            )
            ModelComparisonGroupHeader(expanded = false, onToggle = {})
        }
    }
}

@Preview(name = "Small, large font", group = "MeteoCompare", showBackground = true, widthDp = 360, heightDp = 1400, fontScale = 2f)
@Preview(
    name = "Small, large font, dark",
    group = "MeteoCompare",
    showBackground = true,
    widthDp = 360,
    heightDp = 1400,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun OverviewSectionLargeFontPreview() {
    MeteoPreviewSurface {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            OverviewSection(
                model = previewOverviewModel(),
                timezone = PreviewFixtures.city.timezone,
                currentTemp = 22.0,
                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                currentIsNight = false,
                expanded = true,
                onExpandedChange = {},
                onWhyClick = {}
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun OverviewComponentsPreview() {
    val model = previewOverviewModel()
    MeteoPreviewSurface {
        Column(Modifier.padding(16.dp)) {
            HourlyOutlookChart(
                points = model.hourly,
                traces = model.temperatureTraces,
                timezone = PreviewFixtures.city.timezone
            )
            PrecipOutlook(
                hours = model.precipitation,
                onset = model.rainOnset,
                timezone = PreviewFixtures.city.timezone
            )
            DailyOutlook(days = model.daily)
        }
    }
}

@MeteoComponentPreview
@Composable
private fun OverviewDryPreview() {
    val model = previewOverviewModel().let { base ->
        base.copy(
            precipitation = base.precipitation.map { it.copy(intensity = RainIntensity.NONE, likelihoodPercent = 0) },
            rainOnset = null,
            scenarios = emptyList()
        )
    }
    MeteoPreviewSurface {
        Column(Modifier.padding(8.dp)) {
            OverviewSection(
                model = model,
                timezone = PreviewFixtures.city.timezone,
                currentTemp = 18.0,
                currentCondition = WeatherCondition.CLEAR,
                currentIsNight = false,
                expanded = true,
                onExpandedChange = {},
                onWhyClick = {}
            )
        }
    }
}

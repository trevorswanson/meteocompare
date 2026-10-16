package com.meteocompare.app.ui.home

import androidx.compose.runtime.Composable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.CityDetailUiState
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun HomeCityPreview() {
    MeteoPreviewSurface {
        HomeCityContent(
            cityId = PreviewFixtures.city.id,
            cities = listOf(PreviewFixtures.city, PreviewFixtures.coastalCity),
            state = CityDetailUiState.Loaded(
                forecast = PreviewFixtures.forecast(),
                weeklyConfidence = List(7) {
                    PreviewFixtures.dayConfidence(PreviewFixtures.today.plusDays(it.toLong()))
                },
                hourlyBands = PreviewFixtures.confidenceBands(),
                currentTemp = 22.0,
                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                calculatedAt = PreviewFixtures.now,
                fetchedAt = PreviewFixtures.now.minusSeconds(8 * 60L)
            ),
            isRefreshing = false,
            uvForecast = null,
            onRefresh = {},
            onSelectCity = {},
            onAddCityClick = {},
            onManageCitiesClick = {},
            onSettingsClick = {},
            onRadarClick = {},
            onModelDetailsClick = {},
            onConfidenceClick = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun HomeEmptyPreview() {
    MeteoPreviewSurface {
        HomeGateContent(
            state = HomeUiState(isLoaded = true),
            onAddClick = {},
            onSettingsClick = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ManageCitiesPreview() {
    MeteoPreviewSurface {
        ManageCitiesContent(
            state = ManageCitiesUiState(isLoaded = true, cities = listOf(PreviewFixtures.city, PreviewFixtures.coastalCity)),
            onBack = {},
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {}
        )
    }
}

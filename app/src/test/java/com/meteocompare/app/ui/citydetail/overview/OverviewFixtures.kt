package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant

internal val OVERVIEW_TEST_START: Instant = Instant.parse("2026-10-15T12:00:00Z")

internal fun overviewHours(count: Int): List<Instant> =
    List(count) { OVERVIEW_TEST_START.plusSeconds(it * 3_600L) }

internal fun overviewHourly(
    temperatures: List<Double?>,
    precipitations: List<Double?> = List(temperatures.size) { 0.0 },
    timestamps: List<Instant> = overviewHours(temperatures.size),
    humidity: List<Int?> = emptyList()
): HourlyForecast = HourlyForecast(
    timestamps = timestamps,
    temperature2m = temperatures,
    precipitation = precipitations,
    windSpeed10m = List(timestamps.size) { 10.0 },
    weatherCode = List(timestamps.size) { 1 },
    cloudCover = List(timestamps.size) { 20 },
    relativeHumidity2m = humidity
)

internal fun overviewForecast(
    vararg series: Pair<WeatherModel, HourlyForecast>
): CityForecast = overviewForecastWithDaily(series.map { (model, hourly) -> Triple(model, hourly, null) })

internal fun overviewForecastWithDaily(
    series: List<Triple<WeatherModel, HourlyForecast, DailyForecast?>>
): CityForecast =
    CityForecast(
        city = City(
            id = "hartford",
            name = "Hartford",
            country = "United States",
            latitude = 41.76,
            longitude = -72.67,
            timezone = "UTC"
        ),
        seriesByModel = series.associate { (model, hourly, daily) ->
            model to ForecastSeries(
                model = model,
                hourly = hourly,
                daily = daily ?: DailyForecast(
                    dates = emptyList(),
                    tempMax = emptyList(),
                    tempMin = emptyList(),
                    precipitationSum = emptyList(),
                    windSpeedMax = emptyList()
                )
            )
        },
        fetchedAt = OVERVIEW_TEST_START
    )

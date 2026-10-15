package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.ForecastInsight
import com.meteocompare.app.ui.citydetail.ForecastInsightKind
import com.meteocompare.app.ui.citydetail.ForecastInsightLevel
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewModelTest {

    private val dry = List(24) { 0.0 }

    // Trois lignées indépendantes, sans probabilité native : la pluie ne
    // repose que sur des votes déterministes, avec des débuts décalés.
    private val forecast = overviewForecast(
        WeatherModel.GFS to overviewHourly(
            temperatures = List(24) { 12.0 + it * 0.2 },
            precipitations = dry.toMutableList().also { it[3] = 1.2; it[4] = 3.0 }
        ),
        WeatherModel.ECMWF to overviewHourly(
            temperatures = List(24) { 11.5 + it * 0.2 },
            precipitations = dry.toMutableList().also { it[5] = 0.8 }
        ),
        WeatherModel.UKMO_GLOBAL to overviewHourly(
            temperatures = List(24) { 13.0 + it * 0.2 },
            precipitations = dry
        )
    )

    private val hourly = buildSimplifiedTimeline(
        forecast = forecast,
        mode = DisplayMode.HOURLY,
        now = OVERVIEW_TEST_START,
        hourlyHorizonHours = 24
    )

    @Test
    fun `assemble une heure de pluie par point horaire avec une courbe par lignee`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), emptyList(), emptyList())

        assertEquals(hourly.size, model.precipitation.size)
        assertEquals(model.timestamps, model.precipitation.map { it.instant })
        assertEquals(3, model.temperatureTraces.size)
        assertTrue(model.temperatureTraces.all { it.values.size == hourly.size })
    }

    @Test
    fun `sans probabilite native la pluie est formulee en part de lignees`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), emptyList(), emptyList())

        val wetHour = model.precipitation[3]
        assertEquals(RainLikelihoodWording.FAMILY_SHARE, wetHour.wording)
        assertEquals(FamilyRainShare(wet = 1, total = 3), wetHour.familyShare)
        assertTrue(model.hasRain)
    }

    @Test
    fun `la fenetre de debut de pluie couvre les lignees pluvieuses`() {
        val model = buildOverviewModel(forecast, hourly, emptyList(), emptyList(), emptyList())

        val onset = requireNotNull(model.rainOnset)
        assertEquals(model.timestamps[3], onset.earliest)
        assertEquals(model.timestamps[5], onset.latest)
        assertEquals(2, onset.familiesWithRain)
        assertEquals(3, onset.familiesWithData)
    }

    @Test
    fun `le titre retient le message le plus important hors simple constat d'accord`() {
        val insights = listOf(
            ForecastInsight(kind = ForecastInsightKind.HIGH_AGREEMENT, level = ForecastInsightLevel.POSITIVE, priority = 99),
            ForecastInsight(kind = ForecastInsightKind.TEMPERATURE_CHANGE, level = ForecastInsightLevel.INFO, priority = 50),
            ForecastInsight(kind = ForecastInsightKind.RAIN_LIKELY, level = ForecastInsightLevel.WATCH, priority = 10)
        )

        val model = buildOverviewModel(forecast, hourly, emptyList(), insights, emptyList())

        assertEquals(ForecastInsightKind.RAIN_LIKELY, model.headline?.kind)
    }

    @Test
    fun `un scenario unique n'est pas presente comme une alternative`() {
        val single = listOf(WeatherScenario(kind = WeatherScenarioKind.CLEAR, modelCount = 3, totalModelCount = 3))
        val two = single + WeatherScenario(kind = WeatherScenarioKind.RAIN, modelCount = 1, totalModelCount = 3)

        assertEquals(emptyList<WeatherScenario>(), buildOverviewModel(forecast, hourly, emptyList(), emptyList(), single).scenarios)
        assertEquals(two, buildOverviewModel(forecast, hourly, emptyList(), emptyList(), two).scenarios)
    }
}

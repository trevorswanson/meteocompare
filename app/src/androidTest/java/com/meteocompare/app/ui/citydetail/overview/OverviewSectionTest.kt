package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ClockFormatPreference
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OverviewSectionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun model(forecast: CityForecast = TestFixtures.forecast()): OverviewModel {
        val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, TestFixtures.now, hourlyHorizonHours = 24)
        val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, TestFixtures.now)
        return buildOverviewModel(forecast, hourly, daily, insights = emptyList(), scenarios = emptyList())
    }

    private fun render(
        overview: OverviewModel = model(),
        expanded: Boolean = true,
        onWhyClick: () -> Unit = {},
        onCompareModelsClick: (() -> Unit)? = null,
        fontScale: Float? = null
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            val density = fontScale?.let { Density(base.density, it) } ?: base
            CompositionLocalProvider(
                LocalClockFormatPreference provides ClockFormatPreference.HOUR_24,
                LocalDensity provides density
            ) {
                MeteoCompareTheme {
                    Surface {
                        Column(
                            Modifier
                                .then(if (fontScale != null) Modifier.width(360.dp) else Modifier)
                                .verticalScroll(rememberScrollState())
                        ) {
                            OverviewSection(
                                model = overview,
                                timezone = TestFixtures.forecast().city.timezone,
                                currentTemp = 21.0,
                                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                                currentIsNight = false,
                                expanded = expanded,
                                onExpandedChange = {},
                                onWhyClick = onWhyClick,
                                onCompareModelsClick = onCompareModelsClick
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun shows_now_hourly_rain_and_daily_blocks() {
        render()

        composeRule.onNodeWithTag(TAG_OVERVIEW_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_HOURLY_CHART).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_PRECIP).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_DAILY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hourly_chart_exposes_a_spoken_summary() {
        render()

        composeRule.onNodeWithTag(TAG_OVERVIEW_HOURLY_CHART)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun agreement_chip_opens_the_explanation() {
        var clicks = 0
        render(onWhyClick = { clicks++ })

        composeRule.onNodeWithTag(TAG_OVERVIEW_AGREEMENT).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun compare_models_button_only_appears_when_wired() {
        var clicks = 0
        render(onCompareModelsClick = { clicks++ })

        composeRule.onNodeWithTag(TAG_OVERVIEW_COMPARE_MODELS).performScrollTo().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun collapsed_overview_hides_its_content() {
        render(expanded = false)

        composeRule.onNodeWithTag(TAG_OVERVIEW_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_HOURLY_CHART).assertDoesNotExist()
    }

    @Test
    fun native_probabilities_are_worded_as_a_chance() {
        render()

        composeRule.onNodeWithText(context.getString(R.string.overview_rain_chance_note), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun deterministic_votes_are_never_worded_as_a_chance() {
        val base = TestFixtures.forecast(models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.UKMO_GLOBAL))
        val withoutProbability = base.copy(
            seriesByModel = base.seriesByModel.mapValues { (_, series) ->
                series.copy(hourly = series.hourly.copy(precipitationProbability = emptyList()))
            }
        )
        render(overview = model(withoutProbability))

        composeRule.onNodeWithText(context.getString(R.string.overview_rain_family_share_note), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.overview_rain_chance_note), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun renders_on_a_small_screen_with_large_font() {
        render(fontScale = 2f, onCompareModelsClick = {})

        composeRule.onNodeWithTag(TAG_OVERVIEW_DAILY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_COMPARE_MODELS).performScrollTo().assertIsDisplayed()
    }
}

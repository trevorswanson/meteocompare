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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.ClockFormatPreference
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class OverviewSectionTest {
    @get:Rule val composeRule = createComposeRule()

    private fun model(): OverviewModel {
        val forecast = TestFixtures.forecast()
        val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, TestFixtures.now, hourlyHorizonHours = 24)
        val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, TestFixtures.now)
        return buildOverviewModel(forecast, hourly, daily, TestFixtures.now)
    }

    private fun render(
        onWhyClick: () -> Unit = {},
        onModelDetailsClick: (() -> Unit)? = null,
        fontScale: Float? = null,
        dayOutlookFor: (LocalDate) -> DayOutlook? = { null }
    ) {
        val overview = model()
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
                            OverviewContent(
                                model = overview,
                                timezone = TestFixtures.forecast().city.timezone,
                                now = TestFixtures.now,
                                currentTemp = 21.0,
                                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                                currentIsNight = false,
                                uvIndex = 3.0,
                                solarWindow = null,
                                onWhyClick = onWhyClick,
                                onModelDetailsClick = onModelDetailsClick,
                                dayOutlookFor = dayOutlookFor
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun shows_right_now_next_hours_and_next_days() {
        render()

        composeRule.onNodeWithTag(TAG_OVERVIEW_RIGHT_NOW).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_NARRATIVE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_HOURLY_CHART).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_DAILY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hourly_chart_exposes_a_spoken_summary() {
        render()

        composeRule.onNodeWithTag(TAG_OVERVIEW_HOURLY_CHART)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun more_reveals_agreement_which_opens_the_explanation() {
        var clicks = 0
        render(onWhyClick = { clicks++ })

        composeRule.onNodeWithTag(TAG_OVERVIEW_AGREEMENT).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_OVERVIEW_MORE).performClick()
        composeRule.onNodeWithTag(TAG_OVERVIEW_AGREEMENT).performScrollTo().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun model_details_link_only_appears_when_wired() {
        var clicks = 0
        render(onModelDetailsClick = { clicks++ })

        composeRule.onNodeWithTag(TAG_OVERVIEW_MODEL_DETAILS).performScrollTo().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun renders_on_a_small_screen_with_large_font() {
        render(fontScale = 2f, onModelDetailsClick = {})

        composeRule.onNodeWithTag(TAG_OVERVIEW_DAILY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_OVERVIEW_MODEL_DETAILS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expanding_a_day_reveals_its_midnight_to_midnight_outlook() {
        val forecast = TestFixtures.forecast()
        val zone = resolveZoneOrUtc(forecast.city.timezone)
        val tomorrow = TestFixtures.now.atZone(zone).toLocalDate().plusDays(1)
        val requested = mutableListOf<LocalDate>()
        render(dayOutlookFor = { date ->
            requested += date
            val hourly = buildSimplifiedTimeline(
                forecast,
                DisplayMode.HOURLY,
                date.atStartOfDay(zone).toInstant(),
                hourlyHorizonHours = 25
            )
            buildDayOutlook(forecast, date, hourly)
        })

        composeRule.onNodeWithTag("$TAG_OVERVIEW_DAY_DETAILS$tomorrow").assertDoesNotExist()
        composeRule.onNodeWithTag("$TAG_OVERVIEW_DAY_ROW$tomorrow").performScrollTo().performClick()
        composeRule.onNodeWithTag("$TAG_OVERVIEW_DAY_DETAILS$tomorrow").performScrollTo().assertIsDisplayed()

        // La journée n'est calculée qu'à l'ouverture.
        assertEquals(listOf(tomorrow), requested.distinct())
    }
}

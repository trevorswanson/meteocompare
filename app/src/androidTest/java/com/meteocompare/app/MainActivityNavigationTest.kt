package com.meteocompare.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.testutil.FakeCityRepository
import com.meteocompare.app.testutil.FakeClimateNormalsRepository
import com.meteocompare.app.testutil.FakeForecastRepository
import com.meteocompare.app.testutil.FakeRadarRepository
import com.meteocompare.app.testutil.FakeUserPreferencesRepository
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.citydetail.TAG_DETAIL_LOADED
import com.meteocompare.app.ui.citydetail.TAG_ENGINE_COMPARISON_ACTION
import com.meteocompare.app.ui.citydetail.TAG_GRAPHIC_VIEW_ACTION
import com.meteocompare.app.ui.citydetail.confidence.TAG_CONFIDENCE_EXPLANATION_BACK
import com.meteocompare.app.ui.citydetail.confidence.TAG_CONFIDENCE_EXPLANATION_ROOT
import com.meteocompare.app.ui.citydetail.overview.TAG_OVERVIEW_AGREEMENT
import com.meteocompare.app.ui.citydetail.overview.TAG_OVERVIEW_MODEL_DETAILS
import com.meteocompare.app.ui.citydetail.overview.TAG_OVERVIEW_MORE
import com.meteocompare.app.ui.citylist.TAG_ADD_CITY_RESULT
import com.meteocompare.app.ui.citylist.TAG_ADD_CITY_SEARCH_FIELD
import com.meteocompare.app.ui.citylist.TAG_CITY_CARD
import com.meteocompare.app.ui.citylist.TAG_DONATE_BUTTON
import com.meteocompare.app.ui.citylist.TAG_EMPTY_STATE
import com.meteocompare.app.ui.home.TAG_HOME_ADD_CITY
import com.meteocompare.app.ui.home.TAG_HOME_CITY_OPTION
import com.meteocompare.app.ui.home.TAG_HOME_CITY_PICKER
import com.meteocompare.app.ui.home.TAG_HOME_EMPTY
import com.meteocompare.app.ui.home.TAG_HOME_LOADED
import com.meteocompare.app.ui.home.TAG_HOME_MANAGE_CITIES
import com.meteocompare.app.ui.home.TAG_HOME_RADAR
import com.meteocompare.app.ui.home.TAG_HOME_SETTINGS
import com.meteocompare.app.ui.radar.TAG_RADAR_STAGE
import com.meteocompare.app.ui.settings.TAG_SETTINGS_BACK
import com.meteocompare.app.ui.settings.TAG_SETTINGS_ENGINE
import com.meteocompare.app.ui.settings.TAG_SETTINGS_ROOT
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Parcours instrumentés de l'application complète.
 *
 * Les repositories sont remplacés par des fakes Hilt déterministes : aucun
 * réseau, aucune base persistante et aucune dépendance à l'état laissé par un
 * test précédent. La page principale est l'aperçu simple de la ville choisie.
 */
@HiltAndroidTest
class MainActivityNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var cities: FakeCityRepository
    @Inject lateinit var forecasts: FakeForecastRepository
    @Inject lateinit var preferences: FakeUserPreferencesRepository
    @Inject lateinit var normals: FakeClimateNormalsRepository
    @Inject lateinit var radar: FakeRadarRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        cities.reset()
        forecasts.reset()
        preferences.reset()
        normals.reset()
        radar.reset()
        waitForTag(TAG_HOME_EMPTY)
    }

    @Test
    fun launch_without_cities_offers_to_add_one() {
        composeRule.onNodeWithTag(TAG_HOME_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.empty_favorites_title))
            .assertIsDisplayed()
    }

    @Test
    fun settings_round_trip_from_the_empty_home() {
        composeRule.onNodeWithTag(TAG_HOME_SETTINGS).performClick()
        composeRule.onNodeWithTag(TAG_SETTINGS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()
        composeRule.onNodeWithTag(TAG_HOME_EMPTY).assertIsDisplayed()
    }

    @Test
    fun a_saved_city_opens_directly_on_its_overview() {
        openHomeWith(TestFixtures.paris)

        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).assert(hasText(TestFixtures.paris.name))
    }

    @Test
    fun city_picker_switches_between_saved_cities() {
        forecasts.setForecast(TestFixtures.lyon)
        openHomeWith(TestFixtures.paris, TestFixtures.lyon)

        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).performClick()
        composeRule.onNodeWithTag("$TAG_HOME_CITY_OPTION${TestFixtures.lyon.id}").performClick()

        waitForTag(TAG_HOME_LOADED)
        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).assert(hasText(TestFixtures.lyon.name))
    }

    @Test
    fun add_city_from_the_picker_opens_it() {
        forecasts.setForecast(TestFixtures.lyon)
        openHomeWith(TestFixtures.lyon)

        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).performClick()
        composeRule.onNodeWithTag(TAG_HOME_ADD_CITY).performClick()
        composeRule.onNodeWithTag(TAG_ADD_CITY_SEARCH_FIELD).performTextInput("Paris")
        waitForTag("$TAG_ADD_CITY_RESULT${TestFixtures.paris.id}")
        forecasts.setForecast(TestFixtures.paris)
        composeRule.onNodeWithTag("$TAG_ADD_CITY_RESULT${TestFixtures.paris.id}").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).assert(hasText(TestFixtures.paris.name))
            }.isSuccess
        }
        waitForTag(TAG_HOME_LOADED)
    }

    @Test
    fun manage_cities_keeps_the_city_list_and_opens_a_city() {
        forecasts.setForecast(TestFixtures.lyon)
        openHomeWith(TestFixtures.paris, TestFixtures.lyon)

        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).performClick()
        composeRule.onNodeWithTag(TAG_HOME_MANAGE_CITIES).performClick()
        waitForTag("$TAG_CITY_CARD${TestFixtures.lyon.id}")
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.lyon.id}").performClick()

        waitForTag(TAG_HOME_LOADED)
        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).assert(hasText(TestFixtures.lyon.name))
    }

    @Test
    fun manage_cities_still_offers_the_donation_dialog() {
        openHomeWith(TestFixtures.paris)
        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).performClick()
        composeRule.onNodeWithTag(TAG_HOME_MANAGE_CITIES).performClick()

        composeRule.onNodeWithTag(TAG_DONATE_BUTTON).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.donations_dialog_title)
        ).assertIsDisplayed()
    }

    @Test
    fun removing_the_last_city_returns_to_the_empty_home() {
        openHomeWith(TestFixtures.paris)
        composeRule.onNodeWithTag(TAG_HOME_CITY_PICKER).performClick()
        composeRule.onNodeWithTag(TAG_HOME_MANAGE_CITIES).performClick()
        waitForTag("$TAG_CITY_CARD${TestFixtures.paris.id}")

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.action_more_options),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_remove_from_favorites))
            .performClick()
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
        Espresso.pressBack()

        waitForTag(TAG_HOME_EMPTY)
    }

    @Test
    fun select_engine_then_open_model_details_and_comparison() {
        openHomeWith(TestFixtures.paris)

        composeRule.onNodeWithTag(TAG_HOME_SETTINGS).performClick()
        val calibrationTag = "$TAG_SETTINGS_ENGINE${ForecastEngine.CALIBRATION.name}"
        composeRule.onNodeWithTag(TAG_SETTINGS_ROOT).performScrollToNode(hasTestTag(calibrationTag))
        composeRule.onNodeWithTag(calibrationTag).performClick()
        runBlocking {
            withTimeout(5_000) {
                preferences.observeForecastEngine().first { it == ForecastEngine.CALIBRATION }
            }
        }
        composeRule.onNodeWithTag(calibrationTag).assertIsSelected()
        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()

        openModelDetails()
        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_ACTION).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.engine_comparison_title)
        ).assertIsDisplayed()
    }

    @Test
    fun open_radar_from_home() {
        openHomeWith(TestFixtures.paris)

        composeRule.onNodeWithTag(TAG_HOME_RADAR).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.radar_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RADAR_STAGE).assertIsDisplayed()
    }

    @Test
    fun open_graphic_view_from_model_details() {
        openHomeWith(TestFixtures.paris)
        openModelDetails()

        composeRule.onNodeWithTag(TAG_GRAPHIC_VIEW_ACTION).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.graphic_view_title)
        ).assertIsDisplayed()
    }

    @Test
    fun agreement_opens_the_explanation_and_back_returns_home() {
        openHomeWith(TestFixtures.paris)

        composeRule.onNodeWithTag(TAG_OVERVIEW_MORE).performClick()
        composeRule.onNodeWithTag(TAG_OVERVIEW_AGREEMENT).performScrollTo().performClick()

        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_BACK).performClick()
        waitForTag(TAG_HOME_LOADED)
    }

    private fun openHomeWith(vararg saved: com.meteocompare.app.domain.model.City) {
        saved.forEach { forecasts.setForecast(it) }
        cities.setFavorites(saved.toList())
        waitForTag(TAG_HOME_LOADED)
    }

    private fun openModelDetails() {
        composeRule.onNodeWithTag(TAG_HOME_LOADED)
            .performScrollToNode(hasTestTag(TAG_OVERVIEW_MODEL_DETAILS))
        composeRule.onNodeWithTag(TAG_OVERVIEW_MODEL_DETAILS).performClick()
        waitForTag(TAG_DETAIL_LOADED)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

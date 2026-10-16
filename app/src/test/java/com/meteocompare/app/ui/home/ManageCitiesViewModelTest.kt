package com.meteocompare.app.ui.home

import android.content.Context
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.domain.util.reorderFavorites
import com.meteocompare.app.notification.WeatherNotificationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageCitiesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private fun city(id: String, department: String? = null) =
        City(id, id, country = "France", latitude = 0.0, longitude = 0.0, departmentCode = department)

    private val paris = city("paris", "75")
    private val lyon = city("lyon")
    private val nice = city("nice")
    private val favorites = MutableStateFlow(listOf(paris, lyon, nice))
    private val notifications = MutableStateFlow(NotificationSettings(cityIds = setOf("paris", "lyon")))

    private val cities: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns favorites
        coEvery { reorderFavorites(any()) } answers { favorites.value = reorderFavorites(favorites.value, firstArg()) }
        coEvery { removeFavorite(any()) } answers { favorites.value = favorites.value.filterNot { it.id == firstArg<String>() } }
    }
    private val preferences: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { updateNotificationSettings(any()) } answers {
            firstArg<(NotificationSettings) -> NotificationSettings>()(notifications.value).also { notifications.value = it }
        }
    }
    private val forecasts: ForecastRepository = mockk(relaxed = true)
    private val marine: MarineRepository = mockk(relaxed = true)
    private val vigilance: VigilanceRepository = mockk(relaxed = true)
    private lateinit var viewModel: ManageCitiesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(WeatherNotificationScheduler)
        every { WeatherNotificationScheduler.reschedule(any(), any(), any()) } returns Unit
        viewModel = ManageCitiesViewModel(mockk<Context>(relaxed = true), cities, preferences, forecasts, marine, vigilance)
    }

    @After
    fun tearDown() {
        unmockkObject(WeatherNotificationScheduler)
        Dispatchers.resetMain()
    }

    @Test
    fun `deplacer une ville change l'ordre enregistre`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.isLoaded }

        viewModel.moveDown("paris")
        assertEquals(listOf("lyon", "paris", "nice"), favorites.value.map(City::id))

        viewModel.moveUp("nice")
        assertEquals(listOf("lyon", "nice", "paris"), favorites.value.map(City::id))
    }

    @Test
    fun `un deplacement hors limites ne fait rien`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.isLoaded }

        viewModel.moveUp("paris")
        viewModel.moveDown("nice")

        coVerify(exactly = 0) { cities.reorderFavorites(any()) }
    }

    @Test
    fun `supprimer une ville nettoie notifications et caches`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.isLoaded }

        viewModel.remove("paris")

        assertEquals(listOf("lyon", "nice"), favorites.value.map(City::id))
        assertEquals(setOf("lyon"), notifications.value.cityIds)
        coVerify { forecasts.clearCacheForCity("paris") }
        coVerify { marine.clear("paris") }
        coVerify { vigilance.clearCacheForDepartment("75") }
    }
}

package com.meteocompare.app.ui.home

import app.cash.turbine.test
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val paris = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val hartford = City("hartford", "Hartford", country = "United States", latitude = 41.76, longitude = -72.67)

    private val favorites = MutableStateFlow<List<City>>(emptyList())
    private val stored = MutableStateFlow<String?>(null)

    private val store = object : SelectedCityStore {
        override fun observeSelectedCityId(): Flow<String?> = stored
        override suspend fun setSelectedCityId(cityId: String) {
            stored.value = cityId
        }
    }

    private val cities: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns favorites
        coEvery { addFavorite(any()) } answers { favorites.value = favorites.value + firstArg<City>() }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sans ville memorisee la premiere des favoris est ouverte`() = runTest(dispatcher) {
        favorites.value = listOf(paris, hartford)
        val viewModel = HomeViewModel(cities, store)

        val state = viewModel.uiState.first { it.isLoaded }

        assertEquals("paris", state.preferredCityId)
    }

    @Test
    fun `la derniere ville consultee est rouverte tant qu'elle existe`() = runTest(dispatcher) {
        favorites.value = listOf(paris, hartford)
        stored.value = "hartford"
        val viewModel = HomeViewModel(cities, store)

        viewModel.uiState.test {
            assertEquals("hartford", expectMostRecentItem().preferredCityId)
            favorites.value = listOf(paris)
            assertEquals("paris", awaitItem().preferredCityId)
        }
    }

    @Test
    fun `sans favori aucune ville n'est proposee`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(cities, store)

        val state = viewModel.uiState.first { it.isLoaded }

        assertNull(state.preferredCityId)
        assertEquals(emptyList<City>(), state.cities)
    }

    @Test
    fun `ajouter une ville la selectionne et demande son ouverture`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(cities, store)

        viewModel.events.test {
            viewModel.onAddCity(hartford)
            assertEquals(HomeEvent.OpenCity("hartford", "Hartford"), awaitItem())
        }
        assertEquals("hartford", stored.value)
    }

    @Test
    fun `un echec d'ajout est signale sans changer la selection`() = runTest(dispatcher) {
        coEvery { cities.addFavorite(any()) } throws IllegalStateException("disk full")
        stored.value = "paris"
        val viewModel = HomeViewModel(cities, store)

        viewModel.events.test {
            viewModel.onAddCity(hartford)
            assertEquals(HomeEvent.AddFailed("Hartford"), awaitItem())
        }
        assertEquals("paris", stored.value)
    }

    @Test
    fun `la ville affichee est memorisee`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(cities, store)

        viewModel.rememberSelection("hartford")

        assertEquals("hartford", stored.value)
    }
}

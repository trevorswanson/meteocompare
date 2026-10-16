package com.meteocompare.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.ui.citylist.AddCityUiState
import com.meteocompare.app.ui.navigation.resolveSelectedCityId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @property isLoaded vrai dès la première lecture des favoris.
 * @property preferredCityId ville à afficher : la dernière consultée si elle
 *           existe encore, sinon la première des favoris.
 */
data class HomeUiState(
    val isLoaded: Boolean = false,
    val cities: List<City> = emptyList(),
    val preferredCityId: String? = null
)

sealed interface HomeEvent {
    data class OpenCity(val cityId: String, val cityName: String) : HomeEvent
    data class AddFailed(val cityName: String) : HomeEvent
}

/**
 * Sélecteur de ville de la page principale : favoris, dernière ville
 * consultée, recherche et ajout. Les prévisions elles-mêmes sont fournies par
 * le ViewModel de la fiche ville, inchangé.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val selectedCityStore: SelectedCityStore
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        cityRepository.observeFavorites(),
        selectedCityStore.observeSelectedCityId()
    ) { cities, stored ->
        HomeUiState(
            isLoaded = true,
            cities = cities,
            preferredCityId = resolveSelectedCityId(stored, cities.map(City::id))
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** Mémorise la ville affichée pour la rouvrir au prochain lancement. */
    fun rememberSelection(cityId: String) {
        viewModelScope.launch { runSuspendCatching { selectedCityStore.setSelectedCityId(cityId) } }
    }

    // ─── Ajout d'une ville ──────────────────────────────────────────────────

    private val searchQuery = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val searchError = MutableStateFlow<String?>(null)

    private val searchResults: StateFlow<List<City>> = searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length < MIN_QUERY_LENGTH) {
                isSearching.value = false
                flowOf(emptyList())
            } else {
                flow {
                    isSearching.value = true
                    searchError.value = null
                    try {
                        when (val result = runSuspendCatching { cityRepository.searchCities(query) }.getOrNull()) {
                            is ApiResult.Success -> emit(result.data)
                            is ApiResult.Error -> {
                                searchError.value = result.message
                                emit(emptyList())
                            }
                            null -> emit(emptyList())
                        }
                    } finally {
                        isSearching.value = false
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val addCityState: StateFlow<AddCityUiState> = combine(
        searchQuery, searchResults, isSearching, searchError
    ) { query, results, searching, error ->
        AddCityUiState(query, results, searching, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddCityUiState())

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onAddCity(city: City) {
        viewModelScope.launch {
            val added = runSuspendCatching { cityRepository.addFavorite(city) }
            searchQuery.value = ""
            if (added.isFailure) {
                _events.send(HomeEvent.AddFailed(city.name))
                return@launch
            }
            runSuspendCatching { selectedCityStore.setSelectedCityId(city.id) }
            _events.send(HomeEvent.OpenCity(city.id, city.name))
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 700L
        const val MIN_QUERY_LENGTH = 3
    }
}

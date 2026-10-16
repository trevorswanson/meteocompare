package com.meteocompare.app.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.notification.WeatherNotificationScheduler
import com.meteocompare.app.ui.components.AppToastEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManageCitiesUiState(
    val isLoaded: Boolean = false,
    val cities: List<City> = emptyList()
)

/**
 * Gestion des villes enregistrées : ordre et suppression, sans aucune
 * prévision chargée. La suppression reprend le nettoyage de la liste
 * historique (notifications, caches météo, mer et vigilance).
 */
@HiltViewModel
class ManageCitiesViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val cityRepository: CityRepository,
    private val userPreferences: UserPreferencesRepository,
    private val forecastRepository: ForecastRepository,
    private val marineRepository: MarineRepository,
    private val vigilanceRepository: VigilanceRepository
) : ViewModel() {

    val uiState: StateFlow<ManageCitiesUiState> = cityRepository.observeFavorites()
        .map { ManageCitiesUiState(isLoaded = true, cities = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageCitiesUiState())

    private val _feedback = Channel<AppToastEvent>(Channel.BUFFERED)
    val feedback: Flow<AppToastEvent> = _feedback.receiveAsFlow()

    fun moveUp(cityId: String) = move(cityId, -1)

    fun moveDown(cityId: String) = move(cityId, +1)

    private fun move(cityId: String, delta: Int) {
        val ids = uiState.value.cities.map(City::id).toMutableList()
        val from = ids.indexOf(cityId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch {
            runSuspendCatching { cityRepository.reorderFavorites(ids) }
                .onFailure { _feedback.send(AppToastEvent.error(R.string.toast_action_error)) }
        }
    }

    fun remove(cityId: String) {
        viewModelScope.launch {
            val city = uiState.value.cities.firstOrNull { it.id == cityId }
            if (runSuspendCatching { cityRepository.removeFavorite(cityId) }.isFailure) {
                _feedback.send(AppToastEvent.error(R.string.toast_city_remove_error))
                return@launch
            }

            // Une ville supprimée ne doit pas rester suivie par les notifications.
            val notificationsUpdated = runSuspendCatching {
                val settings = userPreferences.updateNotificationSettings { it.copy(cityIds = it.cityIds - cityId) }
                WeatherNotificationScheduler.reschedule(appContext, settings, kickAlertsImmediately = false)
            }.onFailure { Log.w(TAG, "Unable to update notifications after removing city=$cityId", it) }.isSuccess

            runSuspendCatching { forecastRepository.clearCacheForCity(cityId) }
                .onFailure { Log.w(TAG, "Unable to clear forecast cache for city=$cityId", it) }
            runSuspendCatching { marineRepository.clear(cityId) }
                .onFailure { Log.w(TAG, "Unable to clear marine cache for city=$cityId", it) }
            city?.departmentCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { department ->
                // Le repository conserve le cache tant qu'une autre ville du département subsiste.
                runSuspendCatching { vigilanceRepository.clearCacheForDepartment(department) }
                    .onFailure { Log.w(TAG, "Unable to clear vigilance cache for department=$department", it) }
            }

            _feedback.send(
                when {
                    !notificationsUpdated -> AppToastEvent.warning(R.string.toast_city_removed_notification_warning)
                    city != null -> AppToastEvent.success(R.string.toast_city_removed, city.name)
                    else -> AppToastEvent.success(R.string.toast_city_removed_generic)
                }
            )
        }
    }

    private companion object {
        const val TAG = "MeteoCompare/Cities"
    }
}

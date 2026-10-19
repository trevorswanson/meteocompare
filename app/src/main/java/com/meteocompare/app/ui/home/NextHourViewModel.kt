package com.meteocompare.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.repository.NextHourPrecipitationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Précipitations de la prochaine heure pour la ville affichée sur la page
 * principale. Le cache du repository évite une requête à chaque reprise.
 */
@HiltViewModel
class NextHourViewModel @Inject constructor(
    private val repository: NextHourPrecipitationRepository
) : ViewModel() {
    private val _precipitation = MutableStateFlow<NextHourPrecipitation?>(null)
    val precipitation: StateFlow<NextHourPrecipitation?> = _precipitation.asStateFlow()

    private var cityId: String? = null
    private var job: Job? = null

    fun load(city: City) {
        if (city.id != cityId) {
            // Ne jamais montrer la pluie d'une autre ville pendant le chargement.
            cityId = city.id
            _precipitation.value = null
        }
        job?.cancel()
        job = viewModelScope.launch {
            val result = runSuspendCatching { repository.get(city) }.getOrNull()
            if (result != null && cityId == city.id) _precipitation.value = result
        }
    }
}

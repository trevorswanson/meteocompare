package com.meteocompare.app.ui.home

import kotlinx.coroutines.flow.Flow

/** Dernière ville affichée sur la page principale. */
interface SelectedCityStore {
    fun observeSelectedCityId(): Flow<String?>
    suspend fun setSelectedCityId(cityId: String)
}

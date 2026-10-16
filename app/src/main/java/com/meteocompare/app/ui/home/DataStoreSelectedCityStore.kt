package com.meteocompare.app.ui.home

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.homeDataStore by preferencesDataStore(name = "home_prefs")
private val SELECTED_CITY_ID_KEY = stringPreferencesKey("selected_city_id")

/**
 * Stockage séparé des préférences historiques (`user_prefs`) : cette page
 * principale est propre au fork, et rester hors du dépôt de préférences
 * existant limite les conflits lors des mises à jour depuis l'amont.
 */
@Singleton
class DataStoreSelectedCityStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SelectedCityStore {

    override fun observeSelectedCityId(): Flow<String?> =
        context.homeDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> prefs[SELECTED_CITY_ID_KEY] }
            .distinctUntilChanged()

    override suspend fun setSelectedCityId(cityId: String) {
        context.homeDataStore.edit { prefs -> prefs[SELECTED_CITY_ID_KEY] = cityId }
    }
}

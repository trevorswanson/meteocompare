package com.meteocompare.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastDisplayHorizon
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.util.forecastIsNightAt
import com.meteocompare.app.domain.util.forecastSolarWindow
import com.meteocompare.app.ui.citydetail.CityDetailUiState
import com.meteocompare.app.ui.citydetail.CityDetailViewModel
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.RefreshFeedback
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.citydetail.cityLocalDate
import com.meteocompare.app.ui.citydetail.overview.OverviewContent
import com.meteocompare.app.ui.citydetail.overview.buildOverviewModel
import com.meteocompare.app.ui.citylist.AddCitySheet
import com.meteocompare.app.ui.citylist.EmptyState
import com.meteocompare.app.ui.components.AppToastEffect
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.rememberAppToastDispatcher
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Entrée de l'application : ouvre la dernière ville consultée (ou la
 * première des favoris), ou propose d'ajouter une ville s'il n'y en a aucune.
 */
@Composable
fun HomeGateScreen(
    onOpenCity: (cityId: String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val addCityState by viewModel.addCityState.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.preferredCityId) {
        state.preferredCityId?.let(onOpenCity)
    }
    HomeEventsEffect(viewModel = viewModel, onOpenCity = onOpenCity)

    HomeGateContent(
        state = state,
        onAddClick = { showAddSheet = true },
        onSettingsClick = onSettingsClick
    )
    if (showAddSheet) {
        AddCitySheet(
            state = addCityState,
            onQueryChanged = viewModel::onSearchQueryChanged,
            onCitySelected = { city ->
                showAddSheet = false
                viewModel.onAddCity(city)
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeGateContent(
    state: HomeUiState,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontFamily = FontFamily.Serif) },
                actions = { SettingsAction(onSettingsClick) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoaded && state.cities.isEmpty()) {
                Box(modifier = Modifier.testTag(TAG_HOME_EMPTY)) {
                    EmptyState(onAddClick = onAddClick)
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Page principale d'une ville : aperçu simple, sélecteur de ville dans
 * l'en-tête, radar à gauche et paramètres à droite.
 */
@Composable
fun HomeCityScreen(
    cityId: String,
    onSelectCity: (cityId: String) -> Unit,
    onCityGone: (fallbackCityId: String?) -> Unit,
    onManageCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRadarClick: () -> Unit,
    onModelDetailsClick: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    detailViewModel: CityDetailViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val addCityState by homeViewModel.addCityState.collectAsStateWithLifecycle()
    val state by detailViewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by detailViewModel.isRefreshing.collectAsStateWithLifecycle()
    val uvForecast by detailViewModel.uvForecast.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cityId) { homeViewModel.rememberSelection(cityId) }
    // Ville supprimée depuis la gestion des villes : basculer sur une autre.
    LaunchedEffect(homeState) {
        if (homeState.isLoaded && homeState.cities.none { it.id == cityId }) {
            onCityGone(homeState.preferredCityId)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { detailViewModel.refreshIfStale() }
    HomeEventsEffect(viewModel = homeViewModel, onOpenCity = onSelectCity)
    val refreshToasts = remember(detailViewModel) {
        detailViewModel.refreshFeedback.map { feedback ->
            when (feedback) {
                RefreshFeedback.Success -> AppToastEvent.success(R.string.refresh_success)
                is RefreshFeedback.Error -> AppToastEvent.error(R.string.refresh_error, feedback.message)
                else -> null
            }
        }.filterNotNull()
    }
    AppToastEffect(refreshToasts)

    HomeCityContent(
        cityId = cityId,
        cities = homeState.cities,
        state = state,
        isRefreshing = isRefreshing,
        uvForecast = uvForecast,
        onRefresh = detailViewModel::refresh,
        onSelectCity = { selected -> if (selected != cityId) onSelectCity(selected) },
        onAddCityClick = { showAddSheet = true },
        onManageCitiesClick = onManageCitiesClick,
        onSettingsClick = onSettingsClick,
        onRadarClick = onRadarClick,
        onModelDetailsClick = onModelDetailsClick,
        onConfidenceClick = onConfidenceClick
    )
    if (showAddSheet) {
        AddCitySheet(
            state = addCityState,
            onQueryChanged = homeViewModel::onSearchQueryChanged,
            onCitySelected = { city ->
                showAddSheet = false
                homeViewModel.onAddCity(city)
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

/** Consomme les événements one-shot du sélecteur : ouverture de ville et toasts. */
@Composable
private fun HomeEventsEffect(viewModel: HomeViewModel, onOpenCity: (String) -> Unit) {
    val showToast = rememberAppToastDispatcher()
    val openCity by rememberUpdatedState(onOpenCity)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenCity -> {
                    showToast(AppToastEvent.success(R.string.toast_city_added, event.cityName))
                    openCity(event.cityId)
                }
                is HomeEvent.AddFailed ->
                    showToast(AppToastEvent.error(R.string.toast_city_add_error, event.cityName))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeCityContent(
    cityId: String,
    cities: List<City>,
    state: CityDetailUiState,
    isRefreshing: Boolean,
    uvForecast: UvForecast?,
    onRefresh: () -> Unit,
    onSelectCity: (String) -> Unit,
    onAddCityClick: () -> Unit,
    onManageCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRadarClick: () -> Unit,
    onModelDetailsClick: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit
) {
    val loadedCity = (state as? CityDetailUiState.Loaded)?.forecast?.city
    val city = cities.firstOrNull { it.id == cityId } ?: loadedCity
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onRadarClick, modifier = Modifier.testTag(TAG_HOME_RADAR)) {
                        Icon(Icons.Filled.Radar, contentDescription = stringResource(R.string.radar_open))
                    }
                },
                title = {
                    CityPicker(
                        currentCity = city,
                        cities = cities,
                        onSelectCity = onSelectCity,
                        onAddCityClick = onAddCityClick,
                        onManageCitiesClick = onManageCitiesClick
                    )
                },
                actions = { SettingsAction(onSettingsClick) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                CityDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is CityDetailUiState.Error -> HomeError(message = state.message, onRetry = onRefresh)
                is CityDetailUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeOverviewList(
                        loaded = state,
                        uvForecast = uvForecast,
                        onModelDetailsClick = onModelDetailsClick,
                        onConfidenceClick = onConfidenceClick
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeOverviewList(
    loaded: CityDetailUiState.Loaded,
    uvForecast: UvForecast?,
    onModelDetailsClick: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit
) {
    val forecast = loaded.forecast
    val now = loaded.calculatedAt
    val engineContext = loaded.engineContext
    val engineSignature = engineContext.cacheSignature
    val hourly = remember(forecast, now, engineSignature) {
        buildSimplifiedTimeline(
            forecast = forecast,
            mode = DisplayMode.HOURLY,
            now = now,
            engineContext = engineContext,
            hourlyHorizonHours = ForecastDisplayHorizon.HOURLY_HOURS
        )
    }
    val daily = remember(forecast, now, engineSignature) {
        buildSimplifiedTimeline(
            forecast = forecast,
            mode = DisplayMode.DAILY,
            now = now,
            engineContext = engineContext,
            dailyHorizonDays = ForecastDisplayHorizon.DAYS
        )
    }
    val model = remember(forecast, hourly, daily, now) { buildOverviewModel(forecast, hourly, daily, now) }
    val today = remember(forecast.city.timezone, now) { cityLocalDate(forecast.city.timezone, now) }
    val solarWindow = remember(forecast, today) { forecastSolarWindow(forecast, today) }
    val isNight = remember(forecast, now) { forecastIsNightAt(forecast, now) == true }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = MaxContentWidth)
                .testTag(TAG_HOME_LOADED),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item("overview") {
                OverviewContent(
                    model = model,
                    timezone = forecast.city.timezone,
                    now = now,
                    currentTemp = loaded.currentTemp,
                    currentCondition = loaded.currentCondition,
                    currentIsNight = isNight,
                    uvIndex = uvForecast?.at(now),
                    solarWindow = solarWindow,
                    onWhyClick = { onConfidenceClick(today.toString()) },
                    onModelDetailsClick = onModelDetailsClick
                )
            }
            item("attribution") {
                OpenMeteoAttribution(home = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Titre de la page : ville courante, qui déroule les favoris et les actions. */
@Composable
private fun CityPicker(
    currentCity: City?,
    cities: List<City>,
    onSelectCity: (String) -> Unit,
    onAddCityClick: () -> Unit,
    onManageCitiesClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val title = currentCity?.name ?: stringResource(R.string.app_name)
    val pickerDescription = stringResource(R.string.home_city_picker_a11y, title)
    Box {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button) { expanded = true }
                .semantics { contentDescription = pickerDescription }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(TAG_HOME_CITY_PICKER),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            cities.forEach { city ->
                val selected = city.id == currentCity?.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(city.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            listOfNotNull(city.admin1, city.country).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectCity(city.id)
                    },
                    modifier = Modifier.testTag("$TAG_HOME_CITY_OPTION${city.id}")
                )
            }
            if (cities.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_add_city)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddCityClick()
                },
                modifier = Modifier.testTag(TAG_HOME_ADD_CITY)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_manage_cities)) },
                leadingIcon = { Icon(Icons.Outlined.EditLocationAlt, contentDescription = null) },
                onClick = {
                    expanded = false
                    onManageCitiesClick()
                },
                modifier = Modifier.testTag(TAG_HOME_MANAGE_CITIES)
            )
        }
    }
}

@Composable
private fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(TAG_HOME_SETTINGS)) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

private val MaxContentWidth = 720.dp

internal const val TAG_HOME_EMPTY = "home_empty"
internal const val TAG_HOME_LOADED = "home_loaded"
internal const val TAG_HOME_CITY_PICKER = "home_city_picker"
internal const val TAG_HOME_CITY_OPTION = "home_city_option_"
internal const val TAG_HOME_ADD_CITY = "home_add_city"
internal const val TAG_HOME_MANAGE_CITIES = "home_manage_cities"
internal const val TAG_HOME_SETTINGS = "home_settings"
internal const val TAG_HOME_RADAR = "home_radar"

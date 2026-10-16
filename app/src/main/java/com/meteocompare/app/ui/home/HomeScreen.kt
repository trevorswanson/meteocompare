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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
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
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastDisplayHorizon
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.util.forecastIsNightAt
import com.meteocompare.app.domain.util.forecastSolarWindow
import com.meteocompare.app.ui.citydetail.CityDetailUiState
import com.meteocompare.app.ui.citydetail.CityDetailViewModel
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.RefreshFeedback
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.citydetail.cityLocalDate
import com.meteocompare.app.ui.citydetail.overview.DayOutlook
import com.meteocompare.app.ui.citydetail.overview.OverviewContent
import com.meteocompare.app.ui.citydetail.overview.buildDayOutlook
import com.meteocompare.app.ui.citydetail.overview.buildOverviewModel
import com.meteocompare.app.ui.citylist.AddCitySheet
import com.meteocompare.app.ui.citylist.EmptyState
import com.meteocompare.app.ui.components.AppToastEffect
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.rememberAppToastDispatcher
import com.meteocompare.app.ui.graphicview.GraphicForecastContent
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicForecastViewModel
import com.meteocompare.app.ui.radar.RadarContent
import com.meteocompare.app.ui.radar.RadarViewModel
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
 * Page principale d'une ville : sélecteur de ville dans l'en-tête, paramètres
 * à droite, et barre de navigation Météo / Radar / Graphiques en bas.
 */
@Composable
fun HomeCityScreen(
    cityId: String,
    onSelectCity: (cityId: String) -> Unit,
    onCityGone: (fallbackCityId: String?) -> Unit,
    onManageCitiesClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.WEATHER) }

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
        onModelDetailsClick = onModelDetailsClick,
        onConfidenceClick = onConfidenceClick,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        // Les écrans radar et graphiques (et leurs requêtes) ne sont créés
        // qu'à la première ouverture de l'onglet.
        radarContent = { modifier -> HomeRadarTab(modifier) },
        chartsContent = { modifier -> HomeChartsTab(modifier) }
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
    onModelDetailsClick: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit,
    selectedTab: HomeTab = HomeTab.WEATHER,
    onTabSelected: (HomeTab) -> Unit = {},
    radarContent: @Composable (Modifier) -> Unit = {},
    chartsContent: @Composable (Modifier) -> Unit = {}
) {
    val loadedCity = (state as? CityDetailUiState.Loaded)?.forecast?.city
    val city = cities.firstOrNull { it.id == cityId } ?: loadedCity
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
        },
        bottomBar = { HomeNavigationBar(selectedTab = selectedTab, onTabSelected = onTabSelected) }
    ) { padding ->
        when (selectedTab) {
            HomeTab.WEATHER -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                HomeWeatherTab(
                    state = state,
                    isRefreshing = isRefreshing,
                    uvForecast = uvForecast,
                    onRefresh = onRefresh,
                    onModelDetailsClick = onModelDetailsClick,
                    onConfidenceClick = onConfidenceClick
                )
            }
            HomeTab.RADAR -> radarContent(Modifier.fillMaxSize().padding(padding).testTag(TAG_HOME_RADAR_TAB))
            HomeTab.CHARTS -> chartsContent(Modifier.fillMaxSize().padding(padding).testTag(TAG_HOME_CHARTS_TAB))
        }
    }
}

/** Onglets de la page principale ; l'ordre est celui de la barre de navigation. */
internal enum class HomeTab { WEATHER, RADAR, CHARTS }

/** Barre de navigation Material 3 Expressive (format court, icône au-dessus du libellé). */
@Composable
private fun HomeNavigationBar(selectedTab: HomeTab, onTabSelected: (HomeTab) -> Unit) {
    ShortNavigationBar {
        HomeTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            HomeTab.WEATHER -> if (selected) Icons.Filled.Cloud else Icons.Outlined.Cloud
                            HomeTab.RADAR -> Icons.Filled.Radar
                            HomeTab.CHARTS -> Icons.AutoMirrored.Filled.ShowChart
                        },
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        stringResource(
                            when (tab) {
                                HomeTab.WEATHER -> R.string.home_tab_weather
                                HomeTab.RADAR -> R.string.home_tab_radar
                                HomeTab.CHARTS -> R.string.home_tab_charts
                            }
                        )
                    )
                },
                modifier = Modifier.testTag("$TAG_HOME_TAB${tab.name.lowercase()}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWeatherTab(
    state: CityDetailUiState,
    isRefreshing: Boolean,
    uvForecast: UvForecast?,
    onRefresh: () -> Unit,
    onModelDetailsClick: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit
) {
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

/** Radar de la ville affichée (même ViewModel que l'ancien écran plein). */
@Composable
private fun HomeRadarTab(modifier: Modifier, viewModel: RadarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RadarContent(
        state = state,
        onRetry = viewModel::retry,
        onModeChange = viewModel::setMode,
        onRangeChange = viewModel::setRange,
        onHorizonChange = viewModel::setHorizon,
        onFrameChange = viewModel::selectFrame,
        onPlayPause = viewModel::togglePlayback,
        onRecalculate = viewModel::recalculateProjection,
        onViewportSize = viewModel::setViewportSize,
        modifier = modifier
    )
}

/** Graphiques multi-modèles de la ville affichée (même ViewModel que l'ancien écran plein). */
@Composable
private fun HomeChartsTab(modifier: Modifier, viewModel: GraphicForecastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
    WeatherAccentTheme(condition = (state as? GraphicForecastUiState.Loaded)?.points?.firstOrNull()?.condition) {
        when (val current = state) {
            GraphicForecastUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is GraphicForecastUiState.Error -> HomeError(message = current.message, onRetry = viewModel::retry, modifier = modifier)
            is GraphicForecastUiState.Loaded -> GraphicForecastContent(state = current, modifier = modifier)
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
    val dayOutlookFor: (LocalDate) -> DayOutlook? = remember(forecast, engineSignature) {
        { date -> buildDayOutlookFor(forecast, engineContext, date) }
    }
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
                    onModelDetailsClick = onModelDetailsClick,
                    dayOutlookFor = dayOutlookFor
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
private fun HomeError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
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
internal const val TAG_HOME_TAB = "home_tab_"
internal const val TAG_HOME_RADAR_TAB = "home_radar_tab"
internal const val TAG_HOME_CHARTS_TAB = "home_charts_tab"

/** Journée [date] de minuit à minuit, avec le même moteur que l'aperçu ; null si trop peu de données. */
private fun buildDayOutlookFor(
    forecast: CityForecast,
    engineContext: ForecastEngineContext,
    date: LocalDate
): DayOutlook? {
    val midnight = date.atStartOfDay(resolveZoneOrUtc(forecast.city.timezone)).toInstant()
    val hourly = buildSimplifiedTimeline(
        forecast = forecast,
        mode = DisplayMode.HOURLY,
        now = midnight,
        engineContext = engineContext,
        // 25 h couvre les journées de changement d'heure ; la journée est filtrée ensuite.
        hourlyHorizonHours = 25
    )
    return buildDayOutlook(forecast, date, hourly).takeIf { it.hourly.size >= 2 }
}

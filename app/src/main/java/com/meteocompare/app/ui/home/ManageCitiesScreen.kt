package com.meteocompare.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.components.AppToastEffect

/** Liste des villes enregistrées : réordonner ou supprimer, rien d'autre. */
@Composable
fun ManageCitiesScreen(
    onBack: () -> Unit,
    viewModel: ManageCitiesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppToastEffect(viewModel.feedback)
    ManageCitiesContent(
        state = state,
        onBack = onBack,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onRemove = viewModel::remove
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageCitiesContent(
    state: ManageCitiesUiState,
    onBack: () -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_manage_cities)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_MANAGE_BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.isLoaded && state.cities.isEmpty()) {
                Text(
                    text = stringResource(R.string.manage_cities_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp).testTag(TAG_MANAGE_EMPTY)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).testTag(TAG_MANAGE_LIST),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(state.cities, key = { _, city -> city.id }) { index, city ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ManagedCityRow(
                            city = city,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.cities.lastIndex,
                            onMoveUp = { onMoveUp(city.id) },
                            onMoveDown = { onMoveDown(city.id) },
                            onRemove = { pendingRemoval = city.id }
                        )
                    }
                }
            }
        }
    }

    val removing = pendingRemoval?.let { id -> state.cities.firstOrNull { it.id == id } }
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.manage_cities_remove_title, removing.name)) },
            text = { Text(stringResource(R.string.manage_cities_remove_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemove(removing.id)
                    },
                    modifier = Modifier.testTag(TAG_MANAGE_CONFIRM_REMOVE)
                ) { Text(stringResource(R.string.manage_cities_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ManagedCityRow(
    city: City,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("$TAG_MANAGE_ROW${city.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            listOfNotNull(city.admin1, city.country).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.testTag("$TAG_MANAGE_UP${city.id}")) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.manage_cities_move_up, city.name)
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.testTag("$TAG_MANAGE_DOWN${city.id}")) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.manage_cities_move_down, city.name)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.testTag("$TAG_MANAGE_REMOVE${city.id}")) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.manage_cities_remove_a11y, city.name),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

internal const val TAG_MANAGE_LIST = "manage_cities_list"
internal const val TAG_MANAGE_EMPTY = "manage_cities_empty"
internal const val TAG_MANAGE_BACK = "manage_cities_back"
internal const val TAG_MANAGE_ROW = "manage_city_"
internal const val TAG_MANAGE_UP = "manage_city_up_"
internal const val TAG_MANAGE_DOWN = "manage_city_down_"
internal const val TAG_MANAGE_REMOVE = "manage_city_remove_"
internal const val TAG_MANAGE_CONFIRM_REMOVE = "manage_city_confirm_remove"

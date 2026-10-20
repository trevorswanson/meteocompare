package com.meteocompare.app.ui.radar

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meteocompare.app.data.radar.radarTileUrlTemplate
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RADAR_ANALYSIS_SIZE
import com.meteocompare.app.domain.radar.RAINVIEWER_MAX_ZOOM
import com.meteocompare.app.domain.radar.RadarDisplayGeometry
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.radarAnalysisPixelLatLon
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Point

/**
 * Carte radar MapLibre : fond vectoriel OpenFreeMap (Positron en thème clair,
 * Dark en thème sombre), zoom au pincement et déplacement libre, nord en haut.
 *
 * Chaque image radar RainViewer est une couche raster ; seule l'image choisie
 * est opaque, les autres restent chargées à 0 % pour une animation sans
 * scintillement. RainViewer ne sert pas au-delà du zoom [RAINVIEWER_MAX_ZOOM] :
 * la carte agrandit alors ces tuiles.
 *
 * La projection des cellules (nowcast) est dessinée par-dessus, recalée à
 * chaque mouvement de caméra grâce à la projection de la carte.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun RadarMap(state: RadarUiState.Ready, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        // Aperçus Android Studio : pas de moteur natif MapLibre.
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val styleUrl = if (dark) RADAR_MAP_STYLE_DARK else RADAR_MAP_STYLE_LIGHT
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Incrémenté à chaque mouvement de caméra : relance le dessin du nowcast.
    var cameraTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { ready ->
            // Nord toujours en haut : la projection des cellules reste affine.
            ready.uiSettings.apply {
                setRotateGesturesEnabled(false)
                setTiltGesturesEnabled(false)
                setCompassEnabled(false)
                setLogoEnabled(false)
            }
            ready.setMinZoomPreference(RADAR_MAP_MIN_ZOOM)
            ready.setMaxZoomPreference(RADAR_MAP_MAX_ZOOM)
            ready.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(state.city.latitude, state.city.longitude), RADAR_MAP_START_ZOOM)
            )
            ready.addOnCameraMoveListener { cameraTick++ }
            map = ready
        }
    }

    // Changement de thème : nouveau style, puis couches radar et repère recréés.
    LaunchedEffect(map, styleUrl) {
        val ready = map ?: return@LaunchedEffect
        style = null
        ready.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
            addCityMarker(loaded, state.city, markerColor)
            style = loaded
            cameraTick++
        }
    }

    LaunchedEffect(style, state.metadata, state.frames) {
        style?.let { syncRadarLayers(it, state) }
    }
    val visibleOpacity = if (state.mode == RadarMode.PROJECTION) RADAR_PROJECTION_OBSERVATION_ALPHA else RADAR_OBSERVATION_ALPHA
    LaunchedEffect(style, state.frames, state.selectedFrameIndex, visibleOpacity) {
        val loaded = style ?: return@LaunchedEffect
        state.frames.forEachIndexed { index, frame ->
            loaded.getLayer(radarLayerId(frame.timeEpochSeconds))?.setProperties(
                PropertyFactory.rasterOpacity(if (index == state.selectedFrameIndex) visibleOpacity else 0f)
            )
        }
    }

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.apply {
                    // Dans une page défilante, la carte garde ses gestes (déplacement, pincement).
                    setOnTouchListener { view, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (state.projectionVisible) {
            Canvas(Modifier.fillMaxSize()) {
                cameraTick
                val ready = map ?: return@Canvas
                val geometries = mutableMapOf<Int, RadarDisplayGeometry?>()
                drawProjectionOverlay(state, density) { zoom ->
                    geometries.getOrPut(zoom) { analysisGeometry(ready, state.city, zoom, density) }
                }
            }
        }
    }
}

/**
 * Où tombe à l'écran l'image d'analyse du zoom [zoom] : coins (0, 0) et
 * (512, 512) projetés par la carte, en dp. Sans rotation, la relation est affine.
 */
private fun analysisGeometry(map: MapLibreMap, city: City, zoom: Int, density: Float): RadarDisplayGeometry? {
    val size = RADAR_ANALYSIS_SIZE.toDouble()
    val (lat0, lon0) = radarAnalysisPixelLatLon(city.latitude, city.longitude, zoom, 0.0, 0.0)
    val (lat1, lon1) = radarAnalysisPixelLatLon(city.latitude, city.longitude, zoom, size, size)
    val topLeft = map.projection.toScreenLocation(LatLng(lat0, lon0))
    val bottomRight = map.projection.toScreenLocation(LatLng(lat1, lon1))
    val scale = (bottomRight.x - topLeft.x) / density / size
    if (!scale.isFinite() || scale <= 0.0) return null
    return RadarDisplayGeometry(
        sourceLeft = topLeft.x / density.toDouble(),
        sourceTop = topLeft.y / density.toDouble(),
        sourceScale = scale,
        sourceSize = size * scale,
        analysisZoom = zoom
    )
}

/** Une source et une couche par image ; les images disparues sont retirées. */
private fun syncRadarLayers(style: Style, state: RadarUiState.Ready) {
    val wanted = state.frames.associateBy { radarLayerId(it.timeEpochSeconds) }
    style.layers.map { it.id }
        .filter { it.startsWith(RADAR_LAYER_PREFIX) && it !in wanted }
        .forEach { id ->
            style.removeLayer(id)
            style.removeSource(id)
        }
    // Sous les libellés du fond de carte, pour garder les noms de lieux lisibles.
    val firstLabel = style.layers.firstOrNull { it is SymbolLayer }?.id
    wanted.forEach { (id, frame) ->
        if (style.getLayer(id) != null) return@forEach
        val tiles = TileSet("2.2.0", radarTileUrlTemplate(state.metadata, frame)).apply {
            maxZoom = RAINVIEWER_MAX_ZOOM.toFloat()
        }
        style.addSource(RasterSource(id, tiles, RADAR_TILE_SIZE))
        val layer = RasterLayer(id, id).withProperties(
            PropertyFactory.rasterOpacity(0f),
            PropertyFactory.rasterFadeDuration(0f),
            PropertyFactory.rasterResampling("nearest")
        )
        if (firstLabel != null) style.addLayerBelow(layer, firstLabel) else style.addLayer(layer)
    }
}

private fun addCityMarker(style: Style, city: City, color: Int) {
    style.addSource(GeoJsonSource(CITY_SOURCE_ID, Point.fromLngLat(city.longitude, city.latitude)))
    style.addLayer(
        CircleLayer(CITY_LAYER_ID, CITY_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor(color),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
        )
    )
}

private fun radarLayerId(time: Long) = "$RADAR_LAYER_PREFIX$time"

/** Styles OpenFreeMap (OpenMapTiles), gratuits et sans clé. */
internal const val RADAR_MAP_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
internal const val RADAR_MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val RADAR_LAYER_PREFIX = "radar-"
private const val CITY_SOURCE_ID = "city"
private const val CITY_LAYER_ID = "city-marker"
private const val RADAR_TILE_SIZE = 256

/** Zoom de départ (tuiles 512 px de MapLibre) : environ l'ancien « Proche ». */
private const val RADAR_MAP_START_ZOOM = 7.0
private const val RADAR_MAP_MIN_ZOOM = 3.0
/** Au-delà, les pixels radar (zoom 7 max) deviennent trop grossiers pour servir. */
private const val RADAR_MAP_MAX_ZOOM = 11.0

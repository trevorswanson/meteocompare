package com.meteocompare.app.ui.radar

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.meteocompare.app.data.radar.radarTileUrlTemplate
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RAINVIEWER_MAX_ZOOM
import com.meteocompare.app.domain.radar.RadarMode
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.FeatureCollection
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
 * La projection des cellules (nowcast) est faite de couches MapLibre natives
 * (voir [nowcastFeatures]) : elle se déplace avec la carte, sans décalage.
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
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val styleUrl = if (dark) RADAR_MAP_STYLE_DARK else RADAR_MAP_STYLE_LIGHT
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

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
            map = ready
        }
    }

    // Changement de thème : nouveau style, puis couches radar et repère recréés.
    LaunchedEffect(map, styleUrl) {
        val ready = map ?: return@LaunchedEffect
        style = null
        ready.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
            addCityMarker(loaded, state.city, markerColor)
            addNowcastLayers(loaded)
            style = loaded
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

    // Projection : recalculée seulement quand les cellules ou l'horizon changent.
    LaunchedEffect(style, state.nowcast, state.horizonMinutes, state.projectionVisible) {
        val loaded = style ?: return@LaunchedEffect
        val cells = state.nowcast?.cells.orEmpty().takeIf { state.projectionVisible }.orEmpty()
        val features = withContext(Dispatchers.Default) {
            nowcastFeatures(cells, state.city, state.horizonMinutes)
        }
        (loaded.getSource(NOWCAST_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(features)
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
    }
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
            // Lissage bilinéaire : au-delà du zoom 7, les pixels RainViewer
            // agrandis restent doux au lieu de former des carrés.
            PropertyFactory.rasterResampling("linear")
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

/**
 * Couches de la projection, sous le repère de la ville : remplissages,
 * contours (pointillés pour l'enveloppe), trajectoires avec liseré sombre,
 * points, flèches orientées et libellés. Toutes lisent les propriétés posées
 * par [nowcastFeatures].
 */
private fun addNowcastLayers(style: Style) {
    style.addSource(GeoJsonSource(NOWCAST_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
    style.addImage(NOWCAST_ARROW_IMAGE, arrowBitmap(), true)
    fun kind(vararg kinds: NowcastKind): Expression =
        if (kinds.size == 1) eq(get("kind"), kinds.single().id)
        else any(*kinds.map { eq(get("kind"), it.id) }.toTypedArray())
    val tint = toColor(get("color"))
    val casing = color(NOWCAST_CASING)
    val layers = listOf(
        FillLayer("nowcast-fill", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.ENVELOPE, NowcastKind.CONTOUR))
            .withProperties(fillColor(tint), fillOpacity(get("fillOpacity"))),
        LineLayer("nowcast-envelope", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.ENVELOPE))
            .withProperties(lineColor(tint), lineOpacity(get("opacity")), lineWidth(get("width")), lineDasharray(arrayOf(3.5f, 2.5f))),
        LineLayer("nowcast-contour", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.CONTOUR))
            .withProperties(lineColor(tint), lineOpacity(get("opacity")), lineWidth(get("width")), lineJoin(Property.LINE_JOIN_ROUND)),
        LineLayer("nowcast-observed-casing", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.OBSERVED))
            .withProperties(lineColor(casing), lineOpacity(product(get("opacity"), literal(.66f))), lineWidth(3.8f), lineDasharray(arrayOf(1.1f, 1.1f))),
        LineLayer("nowcast-observed", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.OBSERVED))
            .withProperties(lineColor(tint), lineOpacity(get("opacity")), lineWidth(get("width")), lineDasharray(arrayOf(2.4f, 2.4f))),
        LineLayer("nowcast-trajectory-casing", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.TRAJECTORY))
            .withProperties(lineColor(casing), lineOpacity(product(get("opacity"), literal(.72f))), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)),
        LineLayer("nowcast-trajectory", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.TRAJECTORY))
            .withProperties(lineColor(tint), lineOpacity(get("opacity")), lineWidth(get("width")), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)),
        CircleLayer("nowcast-dots", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.DOT))
            .withProperties(circleColor(tint), circleOpacity(get("opacity")), circleRadius(get("radius"))),
        SymbolLayer("nowcast-arrows", NOWCAST_SOURCE_ID)
            .withFilter(kind(NowcastKind.ARROW))
            .withProperties(
                iconImage(NOWCAST_ARROW_IMAGE),
                iconColor(tint),
                iconOpacity(get("opacity")),
                iconHaloColor(casing),
                iconHaloWidth(1.5f),
                iconRotate(get("bearing")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            ),
        nowcastLabels("nowcast-horizon", kind(NowcastKind.HORIZON_LABEL), tint, casing, offsetEm = -1.2f),
        nowcastLabels("nowcast-names", kind(NowcastKind.NAME_LABEL), tint, casing, offsetEm = 1.6f)
    )
    layers.forEach { style.addLayerBelow(it, CITY_LAYER_ID) }
}

private fun nowcastLabels(id: String, filter: Expression, color: Expression, casing: Expression, offsetEm: Float) =
    SymbolLayer(id, NOWCAST_SOURCE_ID)
        .withFilter(filter)
        .withProperties(
            textField(get("label")),
            textFont(arrayOf(NOWCAST_LABEL_FONT)),
            textSize(10.5f),
            textColor(color),
            textHaloColor(casing),
            textHaloWidth(1.6f),
            textOffset(arrayOf(0f, offsetEm)),
            textAllowOverlap(true),
            textIgnorePlacement(true)
        )

/** Chevron pointant vers le nord ; image SDF, teintée par couche (iconColor). */
private fun arrowBitmap(): Bitmap {
    val size = 32
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = android.graphics.Path().apply {
        moveTo(size * .22f, size * .62f)
        lineTo(size * .5f, size * .3f)
        lineTo(size * .78f, size * .62f)
    }
    Canvas(bitmap).drawPath(path, paint)
    return bitmap
}

private fun radarLayerId(time: Long) = "$RADAR_LAYER_PREFIX$time"

/** Styles OpenFreeMap (OpenMapTiles), gratuits et sans clé. */
internal const val RADAR_MAP_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
internal const val RADAR_MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val RADAR_LAYER_PREFIX = "radar-"
private const val CITY_SOURCE_ID = "city"
private const val CITY_LAYER_ID = "city-marker"
private const val NOWCAST_SOURCE_ID = "nowcast"
private const val NOWCAST_ARROW_IMAGE = "nowcast-arrow"
private const val NOWCAST_CASING = 0xC70F172A.toInt()
/** Police servie par les styles OpenFreeMap (glyphes Noto Sans). */
private const val NOWCAST_LABEL_FONT = "Noto Sans Bold"
private const val RADAR_TILE_SIZE = 256

/** Zoom de départ (tuiles 512 px de MapLibre) : environ l'ancien « Proche ». */
private const val RADAR_MAP_START_ZOOM = 7.0
private const val RADAR_MAP_MIN_ZOOM = 3.0
/** Au-delà, les pixels radar (zoom 7 max) deviennent trop grossiers pour servir. */
private const val RADAR_MAP_MAX_ZOOM = 11.0

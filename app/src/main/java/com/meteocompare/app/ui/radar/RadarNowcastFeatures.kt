package com.meteocompare.app.ui.radar

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RADAR_ANALYSIS_ZOOM
import com.meteocompare.app.domain.radar.RadarPoint
import com.meteocompare.app.domain.radar.RadarRainCell
import com.meteocompare.app.domain.radar.projectRainCell
import com.meteocompare.app.domain.radar.radarAnalysisPixelLatLon
import com.meteocompare.app.domain.radar.rainCellContours
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Projection des cellules (nowcast) en entités GeoJSON pour la carte.
 *
 * Dessinées par MapLibre lui-même (couches remplissage / ligne / cercle /
 * symbole), elles suivent la carte image par image pendant un déplacement ou
 * un pincement, au lieu d'un calque Compose recalé avec un temps de retard.
 * Couleurs, opacités et épaisseurs reprennent l'ancien dessin à l'identique.
 *
 * Propriétés : `kind` (voir [NowcastKind]), `color` (#RRGGBB), `opacity`,
 * `fillOpacity`, `width`, `radius`, `bearing`, `label`.
 */
internal fun nowcastFeatures(cells: List<RadarRainCell>, city: City, horizonMinutes: Int): FeatureCollection {
    val features = mutableListOf<Feature>()
    val sorted = cells.sortedBy { it.impact?.relevanceScore ?: .0 }
    sorted.forEachIndexed { index, cell ->
        val projection = projectRainCell(cell, horizonMinutes.toDouble()) ?: return@forEachIndexed
        val color = NOWCAST_CELL_COLORS[(cell.colorIndex ?: index) % NOWCAST_CELL_COLORS.size]
        val relevance = (cell.impact?.relevanceScore ?: .35).coerceIn(0.0, 1.0)
        val priority = index >= (sorted.size - 4).coerceAtLeast(0) || relevance >= .62
        val emphasis = if (priority) .72 + .28 * relevance else .42 + .28 * relevance
        fun point(x: Double, y: Double): Point {
            val (lat, lon) = radarAnalysisPixelLatLon(city.latitude, city.longitude, cell.analysisZoom, x, y)
            return Point.fromLngLat(lon, lat)
        }
        fun projected(p: RadarPoint): Point = point(
            cell.centroid.x + (p.x - cell.centroid.x) * projection.scaleX + projection.dx,
            cell.centroid.y + (p.y - cell.centroid.y) * projection.scaleY + projection.dy
        )

        // Enveloppe probable (pointillés) puis contour prévu (trait plein).
        rainCellContours(cell, projection.uncertaintyPx).filter { it.size >= 3 }.forEach { loop ->
            features += polygon(loop.map(::projected), NowcastKind.ENVELOPE, color) {
                addNumberProperty("fillOpacity", .035 + .035 * emphasis)
                addNumberProperty("opacity", .56 + .34 * emphasis)
                addNumberProperty("width", 1.7 + .5 * emphasis)
            }
        }
        rainCellContours(cell, 0.0).filter { it.size >= 3 }.forEach { loop ->
            features += polygon(loop.map(::projected), NowcastKind.CONTOUR, color) {
                addNumberProperty("fillOpacity", .045 + .045 * emphasis)
                addNumberProperty("opacity", .76 + .22 * emphasis)
                addNumberProperty("width", 2.1 + .6 * emphasis)
            }
        }

        if (priority) {
            val alpha = .34 + .30 * emphasis
            val source = if (cell.motion?.advection != null) cell.observedTrack else cell.track
            val track = source.takeLast(5).map { RadarPoint(it.x, it.y) }
            if (track.size >= 2 && distance(track.first(), track.last()) >= minDistancePx(cell, 2.0)) {
                features += line(track.map { point(it.x, it.y) }, NowcastKind.OBSERVED, color) {
                    addNumberProperty("opacity", alpha)
                    addNumberProperty("width", 1.7)
                }
                track.forEachIndexed { i, p ->
                    features += dot(point(p.x, p.y), color, alpha * (.42 + .10 * i), radius = 2.2)
                }
            }
        }

        // Trajectoire projetée, avec flèche, si le déplacement est visible.
        if (hypot(projection.dx, projection.dy) >= minDistancePx(cell, 9.0)) {
            val alpha = .20 + .68 * emphasis
            val path = listOf(0.0, .33, .66, 1.0).map { fraction ->
                val step = if (fraction == 0.0) null else projectRainCell(cell, horizonMinutes * fraction)
                RadarPoint(step?.x ?: cell.centroid.x, step?.y ?: cell.centroid.y)
            }
            features += line(path.map { point(it.x, it.y) }, NowcastKind.TRAJECTORY, color) {
                addNumberProperty("opacity", alpha)
                addNumberProperty("width", 2.4)
            }
            path.drop(1).dropLast(1).forEach { features += dot(point(it.x, it.y), color, alpha, radius = 2.6) }
            val from = path[path.lastIndex - 1]
            val to = path.last()
            // Cap en degrés depuis le nord, sens horaire (y de l'image vers le sud).
            val bearing = Math.toDegrees(atan2(to.x - from.x, -(to.y - from.y)))
            features += Feature.fromGeometry(point(to.x, to.y)).apply {
                addStringProperty("kind", NowcastKind.ARROW.id)
                addStringProperty("color", color)
                addNumberProperty("opacity", alpha)
                addNumberProperty("bearing", bearing)
            }
        }

        features += label(point(projection.x, projection.y), "+$horizonMinutes", NowcastKind.HORIZON_LABEL, color)
        if (priority) cell.stableLabel?.let { name ->
            features += label(point(cell.centroid.x, cell.centroid.y), name, NowcastKind.NAME_LABEL, color)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

internal enum class NowcastKind(val id: String) {
    ENVELOPE("envelope"),
    CONTOUR("contour"),
    OBSERVED("observed"),
    TRAJECTORY("trajectory"),
    DOT("dot"),
    ARROW("arrow"),
    HORIZON_LABEL("horizon"),
    NAME_LABEL("name")
}

/** Mêmes teintes que l'ancien calque, une par cellule (identité stable). */
internal val NOWCAST_CELL_COLORS = listOf(
    "#0EA5E9", "#8B5CF6", "#F97316", "#10B981", "#E11D48", "#EAB308", "#14B8A6", "#6366F1"
)

/**
 * Seuils exprimés pour l'image d'analyse au zoom 7 ; une cellule de l'image
 * large (zoom 5) a des pixels 4 fois plus grands.
 */
private fun minDistancePx(cell: RadarRainCell, atAnalysisZoom: Double): Double =
    atAnalysisZoom / 2.0.pow(RADAR_ANALYSIS_ZOOM - cell.analysisZoom)

private fun distance(a: RadarPoint, b: RadarPoint) = hypot(b.x - a.x, b.y - a.y)

private fun polygon(points: List<Point>, kind: NowcastKind, color: String, extra: Feature.() -> Unit): Feature {
    val ring = if (points.isNotEmpty() && points.first() != points.last()) points + points.first() else points
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
        addStringProperty("kind", kind.id)
        addStringProperty("color", color)
        extra()
    }
}

private fun line(points: List<Point>, kind: NowcastKind, color: String, extra: Feature.() -> Unit): Feature =
    Feature.fromGeometry(LineString.fromLngLats(points)).apply {
        addStringProperty("kind", kind.id)
        addStringProperty("color", color)
        extra()
    }

private fun dot(point: Point, color: String, opacity: Double, radius: Double): Feature =
    Feature.fromGeometry(point).apply {
        addStringProperty("kind", NowcastKind.DOT.id)
        addStringProperty("color", color)
        addNumberProperty("opacity", opacity)
        addNumberProperty("radius", radius)
    }

private fun label(point: Point, text: String, kind: NowcastKind, color: String): Feature =
    Feature.fromGeometry(point).apply {
        addStringProperty("kind", kind.id)
        addStringProperty("color", color)
        addStringProperty("label", text)
    }

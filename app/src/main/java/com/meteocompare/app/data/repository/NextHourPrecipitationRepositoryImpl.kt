package com.meteocompare.app.data.repository

import android.util.Log
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationByModelDto
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NATIVE_15_MINUTE_MODELS
import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.NextHourPrecipitationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Précipitations au pas de 15 minutes, gardées en mémoire seulement : elles
 * vieillissent trop vite pour valoir un cache disque. Les appels simultanés
 * pour une même position partagent une seule requête (verrou unique).
 *
 * Deux requêtes parallèles : le « best match » (prévision affichée) et les
 * modèles natifs 15 minutes (dispersion). L'échec de la seconde n'empêche
 * jamais l'affichage : la section retombe alors sur le seul best match.
 */
@Singleton
class NextHourPrecipitationRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NextHourPrecipitationRepository {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, NextHourPrecipitation>()
    private val lastFailure = mutableMapOf<String, Long>()

    override suspend fun get(city: City): NextHourPrecipitation? = withContext(ioDispatcher) {
        val key = locationKey(city)
        mutex.withLock {
            val previous = cache[key]
            val now = clock.millis()
            if (previous != null && now - previous.fetchedAt.toEpochMilli() in 0 until NEXT_HOUR_CACHE_TTL_MS) {
                return@withLock previous
            }
            if (lastFailure[key]?.let { now - it in 0 until NEXT_HOUR_FAILURE_BACKOFF_MS } == true) {
                return@withLock previous
            }
            try {
                val parsed = coroutineScope {
                    val members = async { fetchMembers(city) }
                    val best = api.getMinutelyPrecipitation(city.latitude, city.longitude)
                        .toDomainOrNull(clock.instant())
                    best?.copy(members = members.await())
                }
                if (parsed != null) {
                    cache[key] = parsed
                    lastFailure.remove(key)
                } else {
                    lastFailure[key] = clock.millis()
                }
                parsed ?: previous
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w("MeteoCompare/NextHour", "Minutely precipitation refresh failed", error)
                lastFailure[key] = clock.millis()
                previous
            }
        }
    }

    private suspend fun fetchMembers(city: City): Map<WeatherModel, List<NextHourPrecipitation.Step>> = try {
        api.getMinutelyPrecipitationByModel(
            latitude = city.latitude,
            longitude = city.longitude,
            models = NATIVE_15_MINUTE_MODELS.joinToString(",") { it.apiKey }
        ).toMembers()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.w("MeteoCompare/NextHour", "Native 15-minute models unavailable; using best match only", error)
        emptyMap()
    }
}

internal const val NEXT_HOUR_CACHE_TTL_MS = 10 * 60 * 1000L
internal const val NEXT_HOUR_FAILURE_BACKOFF_MS = 5 * 60 * 1000L

internal fun MinutelyPrecipitationDto.toDomainOrNull(fetchedAt: Instant): NextHourPrecipitation? {
    val zone = runCatching { ZoneId.of(timezone) }.getOrNull() ?: return null
    val series = minutely15 ?: return null
    val steps = series.time.mapIndexedNotNull { index, text ->
        val mm = series.precipitation?.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return@mapIndexedNotNull null
        val endsAt = runCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()
            ?: return@mapIndexedNotNull null
        val snow = series.snowfall?.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        NextHourPrecipitation.Step(endsAt = endsAt, precipitationMm = mm, snowfallCm = snow)
    }
    return if (steps.isEmpty()) null else NextHourPrecipitation(fetchedAt, steps)
}

/**
 * Séries par modèle natif 15 minutes. Un modèle hors domaine renvoie des
 * nulls : il disparaît ici, et la couverture de l'heure est revérifiée au
 * moment de l'affichage.
 */
internal fun MinutelyPrecipitationByModelDto.toMembers(): Map<WeatherModel, List<NextHourPrecipitation.Step>> {
    val zone = runCatching { ZoneId.of(timezone) }.getOrNull() ?: return emptyMap()
    val series = minutely15 ?: return emptyMap()
    val times = (series["time"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content }.orEmpty()
    return NATIVE_15_MINUTE_MODELS.mapNotNull { model ->
        val precipitation = series["precipitation_${model.apiKey}"].doubles() ?: return@mapNotNull null
        val snowfall = series["snowfall_${model.apiKey}"].doubles()
        val steps = times.mapIndexedNotNull { index, text ->
            val mm = precipitation.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapIndexedNotNull null
            val endsAt = text?.let { runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrNull() }
                ?: return@mapIndexedNotNull null
            val snow = snowfall?.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            NextHourPrecipitation.Step(endsAt = endsAt, precipitationMm = mm, snowfallCm = snow)
        }
        if (steps.isEmpty()) null else model to steps
    }.toMap()
}

private fun JsonElement?.doubles(): List<Double?>? = (this as? JsonArray)?.map { element ->
    if (element is JsonNull) null else (element as? JsonPrimitive)?.doubleOrNull
}

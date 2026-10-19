package com.meteocompare.app.data.repository

import android.util.Log
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.repository.NextHourPrecipitationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Précipitations au pas de 15 minutes, gardées en mémoire seulement : elles
 * vieillissent trop vite pour valoir un cache disque. Les appels simultanés
 * pour une même position partagent une seule requête (verrou unique).
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
                val parsed = api.getMinutelyPrecipitation(city.latitude, city.longitude)
                    .toDomainOrNull(clock.instant())
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

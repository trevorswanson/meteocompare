package com.meteocompare.app.testutil

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NextHourPrecipitation
import com.meteocompare.app.domain.repository.NextHourPrecipitationRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Next-hour repository used by Hilt instrumentation tests instead of the Open-Meteo endpoint.
 * Returns no data by default, so the "Next hour" section stays hidden and tests remain offline.
 */
@Singleton
class FakeNextHourPrecipitationRepository @Inject constructor() : NextHourPrecipitationRepository {
    private val precipitation = ConcurrentHashMap<String, NextHourPrecipitation>()

    override suspend fun get(city: City): NextHourPrecipitation? = precipitation[city.id]

    fun setPrecipitation(city: City, value: NextHourPrecipitation?) {
        if (value == null) precipitation.remove(city.id) else precipitation[city.id] = value
    }

    fun reset() {
        precipitation.clear()
    }
}

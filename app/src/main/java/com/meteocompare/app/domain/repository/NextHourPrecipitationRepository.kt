package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NextHourPrecipitation

/** Précipitations des prochaines minutes, mises en cache brièvement par position. */
interface NextHourPrecipitationRepository {
    suspend fun get(city: City): NextHourPrecipitation?
}

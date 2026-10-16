package com.meteocompare.app.ui.citydetail.overview

import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyRainShareTest {

    private fun trace(group: ForecastConsensus.Group, vararg values: Double?) =
        FamilyTrace(group = group, models = listOf(WeatherModel.GFS), values = values.toList())

    @Test
    fun `la part humide ne compte que les lignees ayant une valeur`() {
        val traces = listOf(
            trace(ForecastConsensus.Group.NOAA_GFS, 1.0),
            trace(ForecastConsensus.Group.ECMWF_GLOBAL, 0.0),
            trace(ForecastConsensus.Group.UKMO_GLOBAL, null)
        )

        assertEquals(FamilyRainShare(wet = 1, total = 2), familyRainShare(traces, 0))
        assertEquals(FamilyRainShare(wet = 0, total = 0), familyRainShare(traces, 5))
    }
}

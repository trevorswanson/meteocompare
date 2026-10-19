package com.meteocompare.app.data.repository

import com.meteocompare.app.data.remote.dto.Minutely15Dto
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationByModelDto
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationDto
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinutelyPrecipitationMappingTest {

    private val fetchedAt = Instant.parse("2026-10-19T18:50:00Z")

    @Test
    fun `heures locales converties en instants, valeurs invalides ignorees`() {
        val dto = MinutelyPrecipitationDto(
            timezone = "America/Los_Angeles",
            minutely15 = Minutely15Dto(
                time = listOf("2026-10-19T11:45", "2026-10-19T12:00", "2026-10-19T12:15"),
                precipitation = listOf(0.1, null, 0.4),
                snowfall = listOf(0.0, 0.0, null)
            )
        )
        val parsed = dto.toDomainOrNull(fetchedAt)!!
        assertEquals(2, parsed.steps.size)
        assertEquals(Instant.parse("2026-10-19T18:45:00Z"), parsed.steps[0].endsAt)
        assertEquals(0.4, parsed.steps[1].precipitationMm, 0.0)
        assertEquals(0.0, parsed.steps[1].snowfallCm, 0.0)
    }

    @Test
    fun `reponse vide ou fuseau inconnu`() {
        assertNull(MinutelyPrecipitationDto("UTC", null).toDomainOrNull(fetchedAt))
        assertNull(MinutelyPrecipitationDto("Not/AZone", Minutely15Dto(listOf("2026-10-19T11:45"), listOf(0.1))).toDomainOrNull(fetchedAt))
    }

    @Test
    fun `series par modele, modeles hors domaine ecartes`() {
        val minutely = Json.parseToJsonElement(
            """
            {
              "time": ["2026-10-19T11:45", "2026-10-19T12:00"],
              "precipitation_icon_d2": [0.1, 0.3],
              "snowfall_icon_d2": [0.0, 0.07],
              "precipitation_meteofrance_arome_france_hd": [0.0, 0.2],
              "precipitation_ncep_hrrr_conus": [null, null]
            }
            """.trimIndent()
        ) as JsonObject
        val members = MinutelyPrecipitationByModelDto("Europe/Paris", minutely).toMembers()
        assertEquals(setOf(WeatherModel.ICON_D2, WeatherModel.AROME_FRANCE_HD), members.keys)
        assertEquals(Instant.parse("2026-10-19T09:45:00Z"), members.getValue(WeatherModel.ICON_D2)[0].endsAt)
        assertEquals(0.07, members.getValue(WeatherModel.ICON_D2)[1].snowfallCm, 0.0)
        assertEquals(0.0, members.getValue(WeatherModel.AROME_FRANCE_HD)[1].snowfallCm, 0.0)
    }
}

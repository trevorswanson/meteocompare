package com.meteocompare.app.data.repository

import com.meteocompare.app.data.remote.dto.Minutely15Dto
import com.meteocompare.app.data.remote.dto.MinutelyPrecipitationDto
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
}

package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinutelyPrecipitationDto(
    val timezone: String,
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null
)

@Serializable
data class Minutely15Dto(
    val time: List<String>,
    val precipitation: List<Double?>? = null,
    val snowfall: List<Double?>? = null
)

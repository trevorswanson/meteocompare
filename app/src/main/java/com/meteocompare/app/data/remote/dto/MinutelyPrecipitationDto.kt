package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

/**
 * Même requête pour plusieurs modèles : les variables sont suffixées par la
 * clé du modèle (`precipitation_icon_d2`), d'où un [JsonObject] brut.
 */
@Serializable
data class MinutelyPrecipitationByModelDto(
    val timezone: String,
    @SerialName("minutely_15") val minutely15: JsonObject? = null
)

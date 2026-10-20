package com.meteocompare.app.ui.radar

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RadarNowcast

enum class RadarErrorReason { CITY_NOT_FOUND, NETWORK }
enum class RadarNowcastReason { UNCERTAIN, ERROR }

sealed interface RadarUiState {
    data object Loading : RadarUiState

    data class Error(val reason: RadarErrorReason) : RadarUiState

    data class Ready(
        val city: City,
        val metadata: RadarMetadata,
        val frames: List<RadarFrame>,
        val selectedFrameIndex: Int,
        val mode: RadarMode = RadarMode.OBSERVATION,
        val horizonMinutes: Int = 30,
        val isPlaying: Boolean = false,
        val isAnalyzing: Boolean = false,
        val isRecalculating: Boolean = false,
        val isFullscreen: Boolean = false,
        val nowcast: RadarNowcast? = null,
        val nowcastReason: RadarNowcastReason? = null,
        val recalculationFailed: Boolean = false
    ) : RadarUiState {
        val selectedFrame: RadarFrame get() = frames[selectedFrameIndex.coerceIn(frames.indices)]
        val projectionVisible: Boolean
            get() = mode == RadarMode.PROJECTION && nowcast != null && selectedFrameIndex == frames.lastIndex
    }
}

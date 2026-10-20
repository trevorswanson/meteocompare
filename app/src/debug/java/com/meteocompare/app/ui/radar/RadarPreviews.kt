package com.meteocompare.app.ui.radar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview

@MeteoScreenPreview
@Composable
private fun RadarReadyPreview() {
    val frames = listOf(
        RadarFrame(1_799_750_400L, "/v2/radar/preview-1"),
        RadarFrame(1_799_751_000L, "/v2/radar/preview-2")
    )
    MeteoPreviewSurface {
        RadarContent(
            state = RadarUiState.Ready(
                city = City(
                    id = "preview-lyon",
                    name = "Lyon",
                    country = "France",
                    latitude = 45.7640,
                    longitude = 4.8357,
                    timezone = "Europe/Paris"
                ),
                metadata = RadarMetadata(
                    host = "https://tilecache.rainviewer.com",
                    past = frames,
                    generatedEpochSeconds = frames.last().timeEpochSeconds
                ),
                frames = frames,
                selectedFrameIndex = frames.lastIndex
            )
        )
    }
}

@MeteoScreenPreview
@Composable
private fun RadarNetworkErrorPreview() {
    MeteoPreviewSurface {
        RadarContent(state = RadarUiState.Error(RadarErrorReason.NETWORK))
    }
}

/** La carte MapLibre n'a pas de moteur natif en aperçu : simple fond neutre. */
@MeteoScreenPreview
@Composable
private fun RadarMapPlaceholderPreview() {
    MeteoPreviewSurface {
        val frames = listOf(RadarFrame(1_700_000_000, "/v2/radar/a"))
        RadarMap(
            state = RadarUiState.Ready(
                city = City(id = "preview", name = "Lyon", country = "France", latitude = 45.764, longitude = 4.8357, timezone = "Europe/Paris"),
                metadata = RadarMetadata(host = "https://tilecache.rainviewer.com", past = frames),
                frames = frames,
                selectedFrameIndex = 0
            ),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(240.dp)
        )
    }
}

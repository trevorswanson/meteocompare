package com.meteocompare.app.ui.citydetail.overview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Infobulle des courbes horaires : une seule à la fois sur la page.
 * [activeKey] désigne la courbe qui l'affiche ; null = aucune.
 */
@Stable
internal class TimelineTooltipHost {
    var activeKey: Any? by mutableStateOf(null)
}

internal val LocalTimelineTooltipHost = compositionLocalOf<TimelineTooltipHost?> { null }

/**
 * Ferme l'infobulle au premier contact n'importe où dans la zone. La passe
 * Initial précède celle des courbes : un toucher sur une courbe la rouvre
 * aussitôt à la nouvelle heure, un toucher ailleurs la laisse fermée.
 * Rien n'est consommé, les clics et défilements continuent normalement.
 */
internal fun Modifier.dismissTimelineTooltipOnTouch(host: TimelineTooltipHost): Modifier =
    pointerInput(host) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            host.activeKey = null
        }
    }

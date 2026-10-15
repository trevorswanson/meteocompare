package com.meteocompare.app.ui.accessibility

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Vrai lorsque l'utilisateur a demandé de réduire les animations au niveau du
 * système (Accessibilité → « Supprimer les animations », ou options
 * développeur avec une échelle d'animation à 0).
 *
 * Les composants décoratifs (icônes météo animées, shimmer, transitions de
 * contenu) doivent alors afficher un état statique lisible plutôt qu'une
 * animation figée sur une image arbitraire. La valeur par défaut est `false`
 * afin que les previews et les tests conservent le rendu habituel.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Règle pure : Android expose la préférence « Supprimer les animations » via
 * une échelle de durée nulle. Une valeur absente ou invalide est traitée comme
 * une échelle normale pour ne jamais désactiver les animations par erreur.
 */
internal fun isReducedMotion(animatorDurationScale: Float?): Boolean =
    animatorDurationScale == 0f

/**
 * Lit la préférence système et la met à jour si l'utilisateur la modifie
 * pendant que l'écran est affiché.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion(resolver)
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
        }.isSuccess
        onDispose {
            if (registered) resolver.unregisterContentObserver(observer)
        }
    }
    return reduced
}

private fun readReducedMotion(resolver: ContentResolver): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrNull()
    return isReducedMotion(scale)
}

package com.meteocompare.app.ui.citydetail.overview

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.citydetail.resolveCityZone
import java.time.Instant
import java.util.Locale

/** Catégorie de ciel en début de phrase (« Ciel dégagé »). */
@StringRes
internal fun skyStartRes(kind: SkyKind): Int = when (kind) {
    SkyKind.CLEAR -> R.string.overview_sky_clear
    SkyKind.PARTLY_CLOUDY -> R.string.overview_sky_partly_cloudy
    SkyKind.CLOUDY -> R.string.overview_sky_cloudy
    SkyKind.FOG -> R.string.overview_sky_fog
    SkyKind.DRIZZLE -> R.string.overview_sky_drizzle
    SkyKind.SHOWERS -> R.string.overview_sky_showers
    SkyKind.RAIN -> R.string.overview_sky_rain
    SkyKind.SNOW -> R.string.overview_sky_snow
    SkyKind.FREEZING_RAIN -> R.string.overview_sky_freezing_rain
    SkyKind.THUNDERSTORM -> R.string.overview_sky_thunderstorm
}

/** Même catégorie en milieu de phrase (« puis nuageux »). */
@StringRes
internal fun skyMidRes(kind: SkyKind): Int = when (kind) {
    SkyKind.CLEAR -> R.string.overview_sky_clear_mid
    SkyKind.PARTLY_CLOUDY -> R.string.overview_sky_partly_cloudy_mid
    SkyKind.CLOUDY -> R.string.overview_sky_cloudy_mid
    SkyKind.FOG -> R.string.overview_sky_fog_mid
    SkyKind.DRIZZLE -> R.string.overview_sky_drizzle_mid
    SkyKind.SHOWERS -> R.string.overview_sky_showers_mid
    SkyKind.RAIN -> R.string.overview_sky_rain_mid
    SkyKind.SNOW -> R.string.overview_sky_snow_mid
    SkyKind.FREEZING_RAIN -> R.string.overview_sky_freezing_rain_mid
    SkyKind.THUNDERSTORM -> R.string.overview_sky_thunderstorm_mid
}

@StringRes
internal fun periodPhraseRes(phrase: PeriodPhrase): Int = when (phrase) {
    PeriodPhrase.THIS_MORNING -> R.string.overview_when_this_morning
    PeriodPhrase.THIS_AFTERNOON -> R.string.overview_when_this_afternoon
    PeriodPhrase.THIS_EVENING -> R.string.overview_when_this_evening
    PeriodPhrase.OVERNIGHT -> R.string.overview_when_overnight
    PeriodPhrase.TOMORROW_MORNING -> R.string.overview_when_tomorrow_morning
    PeriodPhrase.TOMORROW_AFTERNOON -> R.string.overview_when_tomorrow_afternoon
    PeriodPhrase.TOMORROW_EVENING -> R.string.overview_when_tomorrow_evening
    PeriodPhrase.IN_THE_MORNING -> R.string.overview_when_in_the_morning
    PeriodPhrase.IN_THE_AFTERNOON -> R.string.overview_when_in_the_afternoon
    PeriodPhrase.IN_THE_EVENING -> R.string.overview_when_in_the_evening
}

/**
 * Température affichée sans unité (« 46° ») : sur les courbes et la liste des
 * jours, l'unité choisie dans les réglages est implicite. Les descriptions
 * TalkBack gardent l'unité complète.
 */
internal fun WeatherUnits.degrees(celsius: Double?, locale: Locale): String =
    value(celsius, WeatherUnit.TEMPERATURE_COMPACT, 0, locale).let { if (it == "—") it else "$it°" }

/** Signal pluie compact : pourcentage seulement s'il vient de probabilités natives. */
@Composable
internal fun rainSignalText(hour: OverviewPrecipHour?): String? = when (hour?.wording) {
    RainLikelihoodWording.CHANCE -> hour.likelihoodPercent?.let { stringResource(R.string.overview_percent, it) }
    RainLikelihoodWording.FAMILY_SHARE -> hour.familyShare.takeIf { it.total > 0 }?.let {
        stringResource(R.string.overview_daily_rain_models, it.wet, it.total)
    }
    null -> null
}

/** Synthèse « Prochaines 24 heures » en texte, dans la langue de l'application. */
@Composable
internal fun narrativeText(sentences: List<NarrativeSentence>, timezone: String?): String {
    val locale = LocalConfiguration.current.locales[0]
    val clock = LocalClockFormatPreference.current
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale, clock) { WeatherTimeFormat.hour(locale, clock) }
    fun hour(at: Instant) = at.atZone(zone).format(hourFormatter)

    return sentences.map { sentence ->
        when (sentence) {
            is NarrativeSentence.Sky -> {
                val then = sentence.then
                val thenWhen = sentence.thenWhen
                if (then == null || thenWhen == null) {
                    stringResource(
                        when (sentence.scope) {
                            OutlookScope.NEXT_24_HOURS -> R.string.overview_narrative_sky_throughout
                            OutlookScope.SINGLE_DAY -> R.string.overview_narrative_sky_all_day
                        },
                        stringResource(skyStartRes(sentence.first))
                    )
                } else {
                    stringResource(
                        R.string.overview_narrative_sky_then,
                        stringResource(skyStartRes(sentence.first)),
                        stringResource(periodPhraseRes(sentence.firstWhen)),
                        stringResource(skyMidRes(then)),
                        stringResource(periodPhraseRes(thenWhen))
                    )
                }
            }
            is NarrativeSentence.RainStart -> stringResource(
                when (sentence.likelihood) {
                    RainStartLikelihood.LIKELY ->
                        if (sentence.snow) R.string.overview_narrative_snow_likely else R.string.overview_narrative_rain_likely
                    RainStartLikelihood.POSSIBLE ->
                        if (sentence.snow) R.string.overview_narrative_snow_possible else R.string.overview_narrative_rain_possible
                    RainStartLikelihood.SOME_FORECASTS ->
                        if (sentence.snow) R.string.overview_narrative_snow_some else R.string.overview_narrative_rain_some
                },
                hour(sentence.at)
            )
            is NarrativeSentence.TomorrowTrend -> stringResource(
                when {
                    sentence.warmer && sentence.strong -> R.string.overview_narrative_tomorrow_much_warmer
                    sentence.warmer -> R.string.overview_narrative_tomorrow_warmer
                    sentence.strong -> R.string.overview_narrative_tomorrow_much_cooler
                    else -> R.string.overview_narrative_tomorrow_cooler
                }
            )
        }
    }.joinToString(" ")
}

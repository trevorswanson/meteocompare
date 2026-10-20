package com.meteocompare.app.core.locale

import androidx.compose.runtime.compositionLocalOf
import com.meteocompare.app.domain.model.ClockFormatPreference
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Clock formatting for the explicitly selected 12 h / 24 h preference.
 * AUTO preserves the previous language-based presentation: English uses 12 h
 * with uppercase AM/PM, other supported languages use 24 h. An explicit choice
 * always overrides the locale and Android's clock setting.
 *
 * This class only formats local times. Callers first convert Instants into the
 * destination city's timezone; it must never change or infer that timezone.
 */
val LocalClockFormatPreference = compositionLocalOf { ClockFormatPreference.AUTO }

object WeatherTimeFormat {
    /** Explicit preferences override locale; AUTO retains V7.4 language defaults. */
    fun usesTwelveHourClock(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): Boolean =
        when (preference) {
            ClockFormatPreference.AUTO -> locale.language.equals("en", ignoreCase = true)
            ClockFormatPreference.HOUR_12 -> true
            ClockFormatPreference.HOUR_24 -> false
        }

    private fun twelveHour(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("h ")
        // Locale.UK / en-AU otherwise output lowercase "am" and "pm" on Android/JVM.
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    private fun twelveClock(locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("h:mm ")
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    /** Compact labels for hourly forecasts, timelines, charts and widgets. */
    fun hour(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = if (usesTwelveHourClock(locale, preference)) {
        twelveHour(locale)
    } else DateTimeFormatter.ofPattern("H'h'", locale)

    /** Hour headings that historically pad 00–09 in 24-hour locales. */
    fun hourPadded(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = if (usesTwelveHourClock(locale, preference)) {
        twelveHour(locale)
    } else DateTimeFormatter.ofPattern("HH'h'", locale)

    /** Exact clock time for sunrise/sunset, radar, tides and notifications. */
    fun clock(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = if (usesTwelveHourClock(locale, preference)) {
        twelveClock(locale)
    } else DateTimeFormatter.ofPattern("HH:mm", locale)

    /**
     * Heure exacte sans AM/PM (« 7:18 »), là où le moment de la journée se lit
     * déjà sur l'axe horaire voisin, comme le lever et le coucher sur la courbe.
     */
    fun clockCompact(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (usesTwelveHourClock(locale, preference)) "h:mm" else "HH:mm", locale)

    /** Selected point on Chart View. */
    fun dateClock(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE d MMM")
        .appendLiteral(" · ")
        .append(clock(locale, preference))
        .toFormatter(locale)

    /** Chart and vigilance day-hour axes. */
    fun dayHour(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE")
        .appendLiteral(' ')
        .append(hour(locale, preference))
        .toFormatter(locale)

    /** Marine chart axes with day on the first line. */
    fun dayClockLines(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("EEE")
        .appendLiteral('\n')
        .append(clock(locale, preference))
        .toFormatter(locale)

    /** Tide details use the localized date but the same application clock. */
    fun dateTime(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendLocalized(FormatStyle.MEDIUM, null)
        .appendLiteral(" · ")
        .append(clock(locale, preference))
        .toFormatter(locale)

    /** Compact date and clock in official weather alert updates. */
    fun shortDateClock(locale: Locale, preference: ClockFormatPreference = ClockFormatPreference.AUTO): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("dd/MM")
        .appendLiteral(" · ")
        .append(clock(locale, preference))
        .toFormatter(locale)
}

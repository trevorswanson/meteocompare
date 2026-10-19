package com.meteocompare.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Échelle typographique Material 3 Expressive sur la police système (Roboto,
 * ou la police du constructeur) : titres plus gras et légèrement resserrés,
 * texte courant inchangé.
 */
val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Medium),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

/** Titre de section « emphasized » de la page principale. */
val SectionHeadlineStyle: TextStyle = Base.headlineMedium.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp
)

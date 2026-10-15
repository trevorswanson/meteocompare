package com.meteocompare.app.domain.model

/** Granularité persistante de la comparaison détaillée d'une ville. */
enum class CityDetailViewMode {
    HOURLY,
    DAILY;

    companion object {
        val DEFAULT: CityDetailViewMode = DAILY

        fun fromString(value: String?): CityDetailViewMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Famille de données affichée dans la comparaison détaillée.
 * Une seule famille est rendue à la fois afin de garder la fiche courte.
 */
enum class CityDetailContentTab {
    CONDITIONS,
    TEMPERATURE,
    PRECIPITATION,
    WIND;

    companion object {
        val DEFAULT: CityDetailContentTab = CONDITIONS

        fun fromString(value: String?): CityDetailContentTab =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Organisation de la fiche ville.
 *
 * [OVERVIEW] place un aperçu simple en tête et regroupe la comparaison
 * détaillée des modèles dessous, repliée tant que l'utilisateur ne l'ouvre pas.
 * [FULL_COMPARISON] conserve la fiche historique, sans aperçu. C'est la valeur
 * par défaut : la mise à jour ne change rien tant que l'utilisateur ne choisit
 * pas l'aperçu dans les paramètres.
 */
enum class CityDetailLayout {
    OVERVIEW,
    FULL_COMPARISON;

    companion object {
        val DEFAULT: CityDetailLayout = FULL_COMPARISON

        fun fromString(value: String?): CityDetailLayout =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

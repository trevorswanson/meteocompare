package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City

/**
 * Réordonne [current] selon [orderedIds]. Les identifiants inconnus sont
 * ignorés ; les villes absentes de [orderedIds] (ajoutées entre-temps) gardent
 * leur ordre relatif, à la fin. Aucune ville n'est ajoutée ni perdue.
 */
fun reorderFavorites(current: List<City>, orderedIds: List<String>): List<City> {
    val byId = current.associateBy(City::id)
    val ordered = orderedIds.distinct().mapNotNull(byId::get)
    val placed = ordered.mapTo(HashSet(), City::id)
    return ordered + current.filterNot { it.id in placed }
}

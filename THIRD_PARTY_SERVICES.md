# Services tiers utilisés par MeteoCompare

*Vérifié pour la release 1.17.0 le 7 octobre 2026.*

Ce document complète `PRIVACY.md`. Il sert de **gate de release** pour les services
réseau dont les conditions ou politiques d'usage peuvent évoluer indépendamment du
code de MeteoCompare.

## RainViewer — radar pluie

MeteoCompare utilise l'API publique RainViewer uniquement lorsque l'utilisateur ouvre
explicitement l'écran radar.

- API : `https://api.rainviewer.com/public/weather-maps.json`
- Tuiles/images : hôte HTTPS retourné par l'API et limité par le code aux sous-domaines
  `*.rainviewer.com`.
- Attribution visible dans l'écran radar : **« Radar météo par RainViewer »**, avec
  lien vers `https://www.rainviewer.com/`.
- Les images radar déjà nécessaires à l'affichage/au nowcast sont conservées dans un
  cache mémoire borné ; aucune prélecture régionale ou téléchargement hors-ligne n'est
  effectué.

Conditions à vérifier avant chaque release :

- documentation API : `https://www.rainviewer.com/api.html` ;
- conditions générales : `https://www.rainviewer.com/terms.html`.

Au 7 octobre 2026, la documentation présente l'API publique gratuite comme destinée
aux usages personnels, éducatifs et aux petits projets communautaires, demande une
attribution visible et recommande la mise en cache. Elle indique de contacter
RainViewer pour les intégrations commerciales, les volumes élevés ou les usages
nécessitant une disponibilité garantie.

**Gate release :** si le modèle de distribution de MeteoCompare devient commercial,
si le trafic radar devient important, ou si les conditions RainViewer changent,
obtenir un accord/plan adapté ou désactiver/remplacer le fournisseur radar avant la
publication. Le caractère gratuit, open-source et sans fonctionnalités premium de
MeteoCompare ne dispense pas de cette vérification.

## OpenFreeMap — fond cartographique du radar

La carte radar utilise MapLibre Native avec les styles vectoriels OpenFreeMap
(schéma OpenMapTiles, données OpenStreetMap), gratuits et sans clé :

- `https://tiles.openfreemap.org/styles/positron` (thème clair) ;
- `https://tiles.openfreemap.org/styles/dark` (thème sombre).

Conformité implémentée :

- attribution visible **« OpenFreeMap © OpenMapTiles © OpenStreetMap »** avec
  lien, en plus du bouton d'attribution MapLibre qui reprend celle du style ;
- tuiles mises en cache par MapLibre selon les en-têtes HTTP du serveur ;
- aucune prélecture hors de la zone affichée ni téléchargement hors-ligne.

Conditions à vérifier avant chaque release : `https://openfreemap.org`.

Les images RainViewer sont superposées comme couches raster, demandées au plus
au zoom 7 (limite de l'API publique) puis agrandies par la carte.


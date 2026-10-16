# Fork notes

This fork of [Pat0chat/meteocompare](https://github.com/Pat0chat/meteocompare) keeps
the upstream app and adds a simpler main page.

## What changes

- **Main page = simple overview of one city** (`ui/home/`, `ui/citydetail/overview/`):
  Right now, a short "next 24 hours" summary, an hourly chart (bold forecast line,
  faint line per independent model family), and the next days. Each daily bar sits on
  a faded pill showing the range of the other model families; expanding a day shows
  its own midnight-to-midnight summary and hourly chart.
- **Header**: city picker dropdown (saved cities, "Add a city", "Manage cities") and
  settings. The last viewed city reopens on launch (`home_prefs` DataStore).
- **Bottom navigation bar** (Material 3 `ShortNavigationBar`): Weather, Radar, Charts.
  Radar and Charts reuse the upstream radar and multi-model chart content and
  ViewModels; they only load when their tab is first opened.
- **Manage cities**: a plain list to reorder or delete saved cities
  (`ManageCitiesScreen`); the upstream city list is no longer reachable.
- **Model details** (link at the bottom of the overview) opens the original upstream
  city page without its Radar / Models / Charts shortcuts (`showHeaderActions = false`).
  The engine is chosen in Settings.
- **Adaptive engine** is used when no engine has been chosen (`ForecastEngine.USER_DEFAULT`).
- Respects the system "Remove animations" setting.

## Keeping up with upstream

Fork-specific code lives mostly in new files. Upstream files are touched as little as
possible; in particular the original phone/tablet navigation in `AppNavHost.kt` and
`CityListScreen.kt` are left in place (the former is no longer called) so upstream
changes keep merging cleanly.

```bash
git remote add upstream https://github.com/Pat0chat/meteocompare   # once
git fetch upstream
git merge upstream/devel        # or upstream/master for releases
```

Files most likely to conflict: `ui/navigation/AppNavHost.kt`, `ui/navigation/Destinations.kt`,
`ui/citydetail/CityDetailScreen.kt` (one `showHeaderActions` flag), the five
`res/values*/strings.xml`, and `MainActivityNavigationTest.kt`.

Upstream screens not reachable from the fork's navigation: the city list (and its
locality comparison / help / donation entries), the engine comparison screen, and the
stand-alone radar and chart routes (still registered, now shown as tabs instead).

## Checks

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
python3 scripts/check_android_strings.py
python3 scripts/validate-compose-previews.py
```

# Fork notes

This fork of [Pat0chat/meteocompare](https://github.com/Pat0chat/meteocompare) keeps
the upstream app and adds a simpler main page.

## What changes

- **Main page = simple overview of one city** (`ui/home/`, `ui/citydetail/overview/`):
  Right now, a short "next 24 hours" summary, an hourly chart (bold forecast line,
  faint line per independent model family), and the next days.
- **Header**: radar (left), city picker dropdown (saved cities, "Add a city",
  "Manage cities"), settings (right). The last viewed city reopens on launch
  (`home_prefs` DataStore).
- **Manage cities** opens the original upstream city list.
- **Model details** (link at the bottom of the overview) opens the original upstream
  city page, with engine comparison, charts, bias, etc.
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
the five `res/values*/strings.xml`, and `MainActivityNavigationTest.kt`.

## Checks

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
python3 scripts/check_android_strings.py
python3 scripts/validate-compose-previews.py
```

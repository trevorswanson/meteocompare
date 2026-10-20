package com.meteocompare.app.ui.radar

import androidx.lifecycle.SavedStateHandle
import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.radar.RADAR_ANALYSIS_ZOOM
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RADAR_WIDE_ANALYSIS_ZOOM
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val city = City(
        id = "radar-city",
        name = "Radar City",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial load exposes observation animation and projection can select latest frame`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()

        val initial = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.OBSERVATION, initial.mode)
        assertTrue(initial.isPlaying)
        assertEquals(0, initial.selectedFrameIndex)

        vm.setMode(RadarMode.PROJECTION)
        runCurrent()
        val projection = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, projection.mode)
        assertFalse(projection.isPlaying)
        assertEquals(projection.frames.lastIndex, projection.selectedFrameIndex)
    }

    @Test
    fun `playback downloads nothing for display, only analysis images`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertFalse((vm.state.value as RadarUiState.Ready).isPlaying)
        // La carte charge elle-même les tuiles radar : seules les images
        // d'analyse (zoom 7, puis zoom 5 pour les cellules lointaines) passent
        // par le repository, une fois chacune.
        assertTrue(repo.radarZooms.all { it == RADAR_ANALYSIS_ZOOM || it == RADAR_WIDE_ANALYSIS_ZOOM })
        assertEquals(1, repo.radarFramePaths.zip(repo.radarZooms).count { it == "/v2/radar/frame0" to RADAR_ANALYSIS_ZOOM })
    }

    @Test
    fun `only supported projection horizons are accepted`() = runTest(dispatcher) {
        val vm = viewModel(FakeRadarRepository())
        runCurrent()
        vm.setHorizon(45)
        assertEquals(45, (vm.state.value as RadarUiState.Ready).horizonMinutes)
        vm.setHorizon(20)
        assertEquals(45, (vm.state.value as RadarUiState.Ready).horizonMinutes)
    }

    @Test
    fun `nowcast frame downloads stay sequential like the web implementation`() = runTest(dispatcher) {
        val repo = SequentialAnalysisRepository()
        val handle = SavedStateHandle(
            mapOf(
                Destinations.CITY_DETAIL_ARG to city.id,
                "radar.mode" to RadarMode.PROJECTION.name
            )
        )
        viewModel(repo, handle)
        advanceUntilIdle()

        assertEquals(7, repo.analysisDownloads)
        assertEquals(1, repo.maxConcurrentAnalysisDownloads)
    }

    @Test
    fun `changing projection horizon is local and triggers no repository call`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()
        val metadataCalls = repo.metadataForceFlags.size
        val radarCalls = repo.radarZooms.size

        vm.setHorizon(15)
        vm.setHorizon(60)
        runCurrent()

        assertEquals(metadataCalls, repo.metadataForceFlags.size)
        assertEquals(radarCalls, repo.radarZooms.size)
        assertEquals(60, (vm.state.value as RadarUiState.Ready).horizonMinutes)
    }

    @Test
    fun `saved radar controls are restored across recreation`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                Destinations.CITY_DETAIL_ARG to city.id,
                "radar.mode" to RadarMode.PROJECTION.name,
                "radar.horizon" to 60,
                "radar.fullscreen" to true
            )
        )
        val vm = viewModel(FakeRadarRepository(), handle)
        runCurrent()

        val restored = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, restored.mode)
        assertEquals(60, restored.horizonMinutes)
        assertTrue(restored.isFullscreen)
        assertFalse(restored.isPlaying)
        assertEquals(restored.frames.lastIndex, restored.selectedFrameIndex)

        vm.setHorizon(45)
        vm.toggleFullscreen()
        assertEquals(45, handle.get<Int>("radar.horizon"))
        assertEquals(false, handle.get<Boolean>("radar.fullscreen"))
    }

    @Test
    fun `failed manual recalculation preserves the last usable radar state`() = runTest(dispatcher) {
        val repo = FakeRadarRepository(failForcedRefresh = true)
        val vm = viewModel(repo)
        runCurrent()
        vm.setMode(RadarMode.PROJECTION)
        val before = vm.state.value as RadarUiState.Ready

        vm.recalculateProjection()
        runCurrent()

        val after = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, after.mode)
        assertEquals(before.frames, after.frames)
        assertEquals(before.metadata, after.metadata)
        assertTrue(after.recalculationFailed)
        assertFalse(after.isRecalculating)
        assertFalse(after.isAnalyzing)
    }

    @Test
    fun `manual projection recalculation refreshes metadata`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()
        vm.recalculateProjection()
        runCurrent()
        assertTrue(repo.metadataForceFlags.contains(true))
        assertFalse((vm.state.value as RadarUiState.Ready).isRecalculating)
    }

    private fun viewModel(
        repository: RadarRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id))
    ): RadarViewModel {
        val cityRepository = mockk<CityRepository> {
            every { observeFavorites() } returns flowOf(listOf(city))
        }
        return RadarViewModel(
            savedStateHandle = savedStateHandle,
            cityRepository = cityRepository,
            radarRepository = repository,
            computationDispatcher = dispatcher
        )
    }

    private class SequentialAnalysisRepository : RadarRepository {
        private val frames = List(7) { i -> RadarFrame(1_700_000_000L + i * 600, "/v2/radar/frame$i") }
        private val image = RadarImage(8, 8, IntArray(64))
        var analysisDownloads = 0
        var maxConcurrentAnalysisDownloads = 0
        private var activeAnalysisDownloads = 0

        override suspend fun metadata(forceRefresh: Boolean): RadarMetadata =
            RadarMetadata("https://tilecache.rainviewer.com", frames)

        override suspend fun radarImage(
            metadata: RadarMetadata,
            frame: RadarFrame,
            city: City,
            zoom: Int
        ): RadarImage {
            if (zoom == RADAR_ANALYSIS_ZOOM) {
                analysisDownloads++
                activeAnalysisDownloads++
                maxConcurrentAnalysisDownloads = maxOf(maxConcurrentAnalysisDownloads, activeAnalysisDownloads)
                try {
                    delay(1)
                } finally {
                    activeAnalysisDownloads--
                }
            }
            return image
        }
    }

    private class FakeRadarRepository(
        private val failForcedRefresh: Boolean = false
    ) : RadarRepository {
        val metadataForceFlags = mutableListOf<Boolean>()
        val radarZooms = mutableListOf<Int>()
        val radarFramePaths = mutableListOf<String>()
        private val image = RadarImage(8, 8, IntArray(64))
        private val frames = List(7) { i -> RadarFrame(1_700_000_000L + i * 600, "/v2/radar/frame$i") }

        override suspend fun metadata(forceRefresh: Boolean): RadarMetadata {
            metadataForceFlags += forceRefresh
            if (forceRefresh && failForcedRefresh) error("forced refresh failed")
            return RadarMetadata("https://tilecache.rainviewer.com", frames)
        }

        override suspend fun radarImage(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): RadarImage {
            radarZooms += zoom
            radarFramePaths += frame.path
            return image
        }
    }
}

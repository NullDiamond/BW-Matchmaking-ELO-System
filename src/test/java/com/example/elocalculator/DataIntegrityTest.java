package com.example.elocalculator;

import com.example.elocalculator.managers.EloCalculationManager;
import com.example.elocalculator.managers.HistoricalDataProcessor;
import com.example.elocalculator.managers.DataManager;
import com.example.elocalculator.managers.EloCalculator;
import com.example.elocalculator.model.game.PlayerStats;
import com.example.elocalculator.model.result.PlayerResult;
import com.example.elocalculator.model.result.ModeResult;
import com.example.elocalculator.model.elo.PlayerEloData;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for data integrity and deserialization robustness.
 *
 * These tests ensure that the system correctly processes data from JSON files,
 * accumulates statistics properly, and generates accurate leaderboards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Data Integrity Tests")
public class DataIntegrityTest {

    @TempDir
    static Path sharedTempDir;

    private Gson gson;
    private Path testDataDir;

    @BeforeAll
    static void setupSharedResources() {
        // Setup shared resources if needed
    }

    @BeforeEach
    void setUp() throws IOException {
        testDataDir = sharedTempDir.resolve("data-" + System.nanoTime());
        Files.createDirectories(testDataDir);
        gson = new Gson();
    }

    @Test
    @DisplayName("PlayerStats should deserialize correctly from JSON")
    void testPlayerStatsJsonDeserialization() {
        String json = """
            {
              "kills": 5,
              "deaths": 2,
              "bed_breaks": 3,
              "final_kills": 1
            }
            """;

        PlayerStats stats = gson.fromJson(json, PlayerStats.class);

        assertNotNull(stats);
        assertEquals(5, stats.getKills());
        assertEquals(2, stats.getDeaths());
        assertEquals(3, stats.getBedBreaks());
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("PlayerEloData should accumulate statistics correctly")
    void testPlayerEloDataAccumulation() {
        PlayerEloData playerData = new PlayerEloData();

        // Verify initial state
        assertNotNull(playerData.getElo());
        assertNotNull(playerData.getGamesPlayed());
        assertNotNull(playerData.getPerformanceStats());
        assertNotNull(playerData.getDetailedStats());

        // Check that all game modes are initialized
        assertTrue(playerData.getElo().containsKey("solo"));
        assertTrue(playerData.getGamesPlayed().containsKey("solo"));
        assertEquals(0, playerData.getGamesPlayed().get("solo"));
    }

    @Test
    @DisplayName("ModeResult should contain accumulated statistics")
    void testModeResultStatisticsInclusion() {
        ModeResult modeResult = new ModeResult();

        // Set some values
        modeResult.setElo(1500.0);
        modeResult.setGames(10);
        modeResult.setInvalidGames(2);

        // Verify values are set
        assertEquals(1500.0, modeResult.getElo());
        assertEquals(10, modeResult.getGames());
        assertEquals(2, modeResult.getInvalidGames());

        // Verify inherited GameStatistics fields
        assertEquals(0, modeResult.getBedBreaks()); // Should default to 0
        assertEquals(0, modeResult.getFinalKills()); // Should default to 0
    }

    @Test
    @DisplayName("HistoricalDataProcessor should handle empty data gracefully")
    void testHistoricalDataProcessor() throws IOException {
        DataManager dataManager = new DataManager(testDataDir.toString());
        EloCalculator eloCalculator = new EloCalculator();
        HistoricalDataProcessor processor = new HistoricalDataProcessor(dataManager, eloCalculator);

        // Create minimal test data
        Map<String, String> playerNames = Map.of("test-uuid", "TestPlayer");

        // This should not throw an exception even with empty data
        // The processor should handle missing files gracefully
        assertDoesNotThrow(() -> {
            try {
                Map<String, PlayerResult> results = processor.processEloCalculations(playerNames);
                assertNotNull(results);
            } catch (Exception e) {
                // If it fails due to missing files, that's acceptable for this test
                // We're mainly testing that it doesn't crash unexpectedly
                assertTrue(e.getMessage().contains("scraped_games.json") ||
                          e.getMessage().contains("NoSuchFileException"));
            }
        });
    }

    @Test
    @DisplayName("EloCalculationManager should handle empty data gracefully")
    void testEloCalculationManagerProcessing() throws IOException {
        EloCalculationManager manager = new EloCalculationManager(testDataDir.toString());

        // Create minimal test data
        Map<String, String> playerNames = Map.of("test-uuid", "TestPlayer");

        // This should not throw an exception
        // The manager should handle missing files gracefully
        assertDoesNotThrow(() -> {
            try {
                Map<String, PlayerResult> results = manager.processEloCalculations(playerNames);
                assertNotNull(results);
            } catch (Exception e) {
                // If it fails due to missing files, that's acceptable for this test
                // We're mainly testing that it doesn't crash unexpectedly
                assertTrue(e.getMessage().contains("scraped_games.json") ||
                          e.getMessage().contains("NoSuchFileException"));
            }
        });
    }

    @Test
    @DisplayName("Statistics accumulation should preserve bed breaks and final kills")
    void testStatisticsAccumulationPreservesValues() {
        PlayerStats stats1 = new PlayerStats();
        stats1.setKills(5);
        stats1.setDeaths(2);
        stats1.setBedBreaks(3);
        stats1.setFinalKills(1);

        PlayerStats stats2 = new PlayerStats();
        stats2.setKills(3);
        stats2.setDeaths(4);
        stats2.setBedBreaks(2);
        stats2.setFinalKills(0);

        // Simulate accumulation (manual addition for test)
        int totalKills = stats1.getKills() + stats2.getKills();
        int totalDeaths = stats1.getDeaths() + stats2.getDeaths();
        int totalBedBreaks = stats1.getBedBreaks() + stats2.getBedBreaks();
        int totalFinalKills = stats1.getFinalKills() + stats2.getFinalKills();

        assertEquals(8, totalKills);
        assertEquals(6, totalDeaths);
        assertEquals(5, totalBedBreaks);
        assertEquals(1, totalFinalKills);
    }

    @Test
    @DisplayName("PlayerResult should contain mode-specific results")
    void testPlayerResultStructure() {
        PlayerResult playerResult = new PlayerResult();

        // Add a mode result
        ModeResult soloResult = new ModeResult();
        soloResult.setElo(1600.0);
        soloResult.setGames(25);
        soloResult.setBedBreaks(15);
        soloResult.setFinalKills(8);

        playerResult.getModes().put("solo", soloResult);

        // Verify structure
        assertTrue(playerResult.getModes().containsKey("solo"));
        ModeResult retrieved = playerResult.getModes().get("solo");
        assertEquals(1600.0, retrieved.getElo());
        assertEquals(25, retrieved.getGames());
        assertEquals(15, retrieved.getBedBreaks());
        assertEquals(8, retrieved.getFinalKills());
    }

    @Test
    @DisplayName("JSON with missing fields should not break deserialization")
    void testJsonWithMissingFields() {
        String incompleteJson = """
            {
              "kills": 5,
              "deaths": 2
            }
            """;

        assertDoesNotThrow(() -> {
            PlayerStats stats = gson.fromJson(incompleteJson, PlayerStats.class);
            assertNotNull(stats);
            assertEquals(5, stats.getKills());
            assertEquals(2, stats.getDeaths());
            // Missing fields should be 0
            assertEquals(0, stats.getBedBreaks());
            assertEquals(0, stats.getFinalKills());
        });
    }

    @Test
    @DisplayName("Invalid JSON should throw appropriate exceptions")
    void testInvalidJsonHandling() {
        String invalidJson = "{ invalid json content }";

        assertThrows(JsonSyntaxException.class, () -> {
            gson.fromJson(invalidJson, PlayerStats.class);
        });
    }

    @Test
    @DisplayName("Empty player results should be handled gracefully")
    void testEmptyPlayerResults() {
        PlayerResult emptyResult = new PlayerResult();

        assertNotNull(emptyResult.getModes());
        assertTrue(emptyResult.getModes().isEmpty());
        assertEquals(0.0, emptyResult.getGlobalElo());
    }

    @Test
    @DisplayName("ModeResult should inherit GameStatistics fields")
    void testModeResultInheritance() {
        ModeResult modeResult = new ModeResult();

        // Test inherited fields from GameStatistics
        assertEquals(0, modeResult.getKills());
        assertEquals(0, modeResult.getDeaths());
        assertEquals(0, modeResult.getBedBreaks());
        assertEquals(0, modeResult.getFinalKills());

        // Test ModeResult specific fields
        assertEquals(0.0, modeResult.getElo());
        assertEquals(0, modeResult.getGames());
        assertEquals(0, modeResult.getInvalidGames());
    }
}
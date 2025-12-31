package com.nulldiamond.elocalculator;

import com.nulldiamond.elocalculator.config.Config;
import com.nulldiamond.elocalculator.managers.EloCalculationManager;
import com.nulldiamond.elocalculator.managers.DataManager;
import com.nulldiamond.elocalculator.model.elo.PlayerEloHistory;
import com.nulldiamond.elocalculator.model.elo.EloHistoryEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ELO continuity after removing recent matches.
 *
 * This test ensures that removing a recent match properly restores ELOs
 * and maintains history consistency without discontinuities.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ELO Continuity After Match Removal Tests")
public class EloContinuityTest {

    // Flag to control whether real data integration tests should run
    // Set to true only when you want to test with real scraped data
    // WARNING: This will process real games and may take time
    private static final boolean RUN_REAL_DATA_TESTS = true;

    @TempDir
    static Path sharedTempDir;

    private EloCalculationManager manager;
    @SuppressWarnings("unused")
    private DataManager dataManager;
    private Path testDataDir;

    @BeforeEach
    void setUp() throws IOException {
        testDataDir = sharedTempDir.resolve("data-" + System.nanoTime());
        Files.createDirectories(testDataDir);
        manager = new EloCalculationManager(testDataDir.toString());
        dataManager = new DataManager(testDataDir.toString());

        setupTestData();
    }

    private void setupTestData() throws IOException {
        // Create minimal test data files
        Path playerNamesPath = testDataDir.resolve("input/player_uuid_map.json");
        Files.createDirectories(playerNamesPath.getParent());

        // Create output directory
        Files.createDirectories(testDataDir.resolve("output"));

        // Simple player UUID map
        String playerNamesJson = """
            {
                "uuid1": "Player1",
                "uuid2": "Player2",
                "uuid3": "Player3"
            }
            """;
        Files.writeString(playerNamesPath, playerNamesJson);

        // Create empty processed IDs
        Path processedIdsPath = testDataDir.resolve("temp/processed_game_ids.json");
        Files.createDirectories(processedIdsPath.getParent());
        Files.writeString(processedIdsPath, "[]");

        // Create empty blacklisted IDs
        Path blacklistedIdsPath = testDataDir.resolve("temp/blacklisted_game_ids.json");
        Files.createDirectories(blacklistedIdsPath.getParent());
        Files.writeString(blacklistedIdsPath, "[]");

        // Create empty processed matches
        Path processedMatchesPath = testDataDir.resolve("temp/processed_matches.json");
        Files.writeString(processedMatchesPath, "{}");
    }

    @Test
    @DisplayName("ELO continuity maintained after removing recent match")
    void testEloContinuityAfterRemoval() throws IOException {
        // Process multiple test matches with different modes and timestamps
        String match1 = """
            {
                "game1": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}, {"uuid": "uuid2", "name": "Player2"}],
                        "team2": [{"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 0, "deaths": 2}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000000
                }
            }
            """;

        String match2 = """
            {
                "game2": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}, {"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000001
                }
            }
            """;

        String match3 = """
            {
                "game3": {
                    "winner": "team2",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}, {"uuid": "uuid3", "name": "Player3"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 0, "deaths": 1},
                        "uuid2": {"bed_breaks": 1, "deaths": 0},
                        "uuid3": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000002
                }
            }
            """;

        String match4 = """
            {
                "game4": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDD1",
                    "unix_time": 1000000003
                }
            }
            """;

        String match5 = """
            {
                "game5": {
                    "winner": "team2",
                    "teams": {
                        "team1": [{"uuid": "uuid2", "name": "Player2"}],
                        "team2": [{"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 1, "deaths": 0}
                    },
                    "lobby_id": "BEDD1",
                    "unix_time": 1000000004
                }
            }
            """;

        // Process matches
        Set<String> processedIds = new HashSet<>();
        manager.processMatch(match1, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());
        manager.processMatch(match2, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());
        manager.processMatch(match3, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());
        manager.processMatch(match4, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());
        manager.processMatch(match5, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());

        // Test removing different indices (now includes all game modes: mega + duo)
        // Games processed: game1 (mega), game2 (mega), game3 (mega), game4 (duo), game5 (duo)
        // Most recent is game5, then game4, game3, game2, game1
        testRemovalAtIndex(1, "game5"); // Remove most recent (game5 - duo)
        testRemovalAtIndex(2, "game4"); // Remove second most recent (game4 - duo)
        testRemovalAtIndex(3, "game3"); // Remove third most recent (game3 - mega)
    }

    private void testRemovalAtIndex(int index, String expectedRemovedGame) throws IOException {
        // Get fresh manager and data for each test to avoid state interference
        Path freshTestDataDir = sharedTempDir.resolve("data-" + System.nanoTime());
        Files.createDirectories(freshTestDataDir);
        EloCalculationManager freshManager = new EloCalculationManager(freshTestDataDir.toString());
        DataManager freshDataManager = new DataManager(freshTestDataDir.toString());

        // Re-setup test data
        setupTestDataForDir(freshTestDataDir);

        // Re-process matches
        String[] matches = getTestMatches();
        Set<String> processedIds = new HashSet<>();
        for (String match : matches) {
            freshManager.processMatch(match, Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), processedIds, Set.of());
        }

        // Remove the specified index
        String removedGameId = freshManager.removeRecentMatch(Map.of("uuid1", "Player1", "uuid2", "Player2", "uuid3", "Player3"), index);
        assertEquals(expectedRemovedGame, removedGameId, "Failed to remove correct game at index " + index);

        // Get history after removal
        Map<String, PlayerEloHistory> historyAfter = freshDataManager.loadEloHistory();

        // Check ELO continuity
        for (PlayerEloHistory playerHistory : historyAfter.values()) {
            for (List<EloHistoryEntry> modeEntries : playerHistory.getHistoryByMode().values()) {
                for (int i = 0; i < modeEntries.size() - 1; i++) {
                    EloHistoryEntry current = modeEntries.get(i);
                    EloHistoryEntry next = modeEntries.get(i + 1);
                    assertEquals(current.getNewElo(), next.getPreviousElo(), 0.001,
                        "ELO discontinuity detected for player " + playerHistory.getPlayerUuid() +
                        " in game " + next.getGameId() + " (removed index " + index + ")");
                }
            }
        }

        // Check that removed game is not in the history
        for (PlayerEloHistory playerHistory : historyAfter.values()) {
            for (EloHistoryEntry entry : playerHistory.getAllEntries()) {
                assertNotEquals(removedGameId, entry.getGameId(), "Removed game still in history (removed index " + index + ")");
            }
        }
    }

    @Test
    @DisplayName("ELO continuity with real data - exact Main.java workflow")
    void testEloContinuityWithRealData() throws IOException {
        // Skip if real data tests are disabled
        Assumptions.assumeTrue(RUN_REAL_DATA_TESTS, "Real data tests are disabled. Set RUN_REAL_DATA_TESTS=true to enable.");

        // Enable DEBUG_MODE to capture debug output for parsing
        boolean originalDebugMode = Config.DEBUG_MODE;
        Config.DEBUG_MODE = true;
        
        try {
            // Use a fresh directory for real data testing to avoid messing with actual data
            Path realDataTestDir = sharedTempDir.resolve("real-data-test-" + System.nanoTime());
            Files.createDirectories(realDataTestDir);

            // Copy real data files to test directory
            copyRealDataFiles(realDataTestDir);

            // Create a Main instance with the test directory
            Main mainInstance = new Main(realDataTestDir.toString());

        // Step 1: Press 1 - Process ALL data (equivalent to run())
        System.out.println("=== STEP 1: Processing ALL data (Main.java option 1) ===");
        mainInstance.run();

        // Step 2: Press 6 - Remove recent match at index 3
        System.out.println("\n=== STEP 2: Removing recent match at index 3 (Main.java option 6) ===");
        mainInstance.removeRecentMatch(3, true); // Skip blacklist prompt during testing

        // Step 3: Press 7 - View last 5 games and check ELO integrity
        System.out.println("\n=== STEP 3: Viewing last 5 games (Main.java option 7) ===");

        // Capture the output to check ELO continuity
        // We'll redirect System.out temporarily to capture the viewLast5GamesElo output
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(outputStream));

        try {
            mainInstance.viewLast5GamesElo();
        } finally {
            System.setOut(originalOut);
        }

        String output = outputStream.toString();
        System.out.println("Captured output from viewLast5GamesElo:");
        System.out.println(output);

        // Parse the output and check ELO continuity in the displayed games
        checkEloContinuityFromOutput(output, realDataTestDir);

        // Additionally, check ELO continuity across the entire reconstructed history
        checkFullEloHistoryContinuity(realDataTestDir);

        System.out.println("Real data workflow test completed successfully!");
        } finally {
            // Restore original DEBUG_MODE
            Config.DEBUG_MODE = originalDebugMode;
        }
    }

    /**
     * Checks ELO continuity across the entire ELO history for all players.
     * This catches discontinuities that may not be visible in the last 5 games display.
     */
    private void checkFullEloHistoryContinuity(Path testDir) throws IOException {
        DataManager dataManager = new DataManager(testDir.toString());
        Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();

        if (eloHistory == null || eloHistory.isEmpty()) {
            System.out.println("No ELO history found to check.");
            return;
        }

        System.out.println("Checking ELO continuity across entire history...");

        for (Map.Entry<String, PlayerEloHistory> playerEntry : eloHistory.entrySet()) {
            String playerUuid = playerEntry.getKey();
            PlayerEloHistory playerHistory = playerEntry.getValue();

            for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega"}) {
                List<EloHistoryEntry> modeEntries = playerHistory.getHistoryForMode(mode);

                // Sort by timestamp to ensure chronological order
                modeEntries.sort(Comparator.comparingLong(EloHistoryEntry::getTimestamp));

                if (modeEntries.size() > 1) {
                    String playerName = playerHistory.getPlayerName();
                    if (playerName == null || playerName.equals("Unknown")) {
                        playerName = playerUuid.substring(0, Math.min(8, playerUuid.length()));
                    }

                    if(playerName == "FrostedFlynx") {
                        System.out.println("  Checking player " + playerName + " (" + playerUuid + ") in mode " + mode + ":");
                    }

                    for (int i = 0; i < modeEntries.size(); i++) {
                        EloHistoryEntry entry = modeEntries.get(i);
                        if(playerName == "FrostedFlynx") {
                            System.out.printf("    Game %s: %.1f -> %.1f%n", entry.getGameId(), entry.getPreviousElo(), entry.getNewElo());
                        }
                    }

                    for (int i = 0; i < modeEntries.size() - 1; i++) {
                        EloHistoryEntry current = modeEntries.get(i);
                        EloHistoryEntry next = modeEntries.get(i + 1);

                        double finalElo = current.getNewElo();
                        double nextPrevElo = next.getPreviousElo();

                        if(playerName == "FrostedFlynx") {
                            System.out.printf("    Continuity check: Game %s final ELO %.1f vs Game %s previous ELO %.1f%n",
                                            current.getGameId(), finalElo, next.getGameId(), nextPrevElo);
                        }

                        if (Math.abs(finalElo - nextPrevElo) > 0.01) { // Allow small floating point differences
                            Assertions.fail(String.format(
                                "ELO discontinuity detected for player %s (%s) in mode %s between games %s and %s: " +
                                "final ELO %.2f does not match next game's previous ELO %.2f",
                                playerName, playerUuid, mode, current.getGameId(), next.getGameId(),
                                finalElo, nextPrevElo));
                        }
                    }
                }
            }
        }

        System.out.println("Full ELO history continuity check passed!");
    }

    private void copyRealDataFiles(Path testDir) throws IOException {
        String projectRoot = System.getProperty("user.dir");

        // Copy player UUID map
        Path sourceUuidMap = Path.of(projectRoot, "data", "config", "player_uuid_map.json");
        Path targetUuidMap = testDir.resolve("config/player_uuid_map.json");
        Files.createDirectories(targetUuidMap.getParent());
        if (Files.exists(sourceUuidMap)) {
            Files.copy(sourceUuidMap, targetUuidMap);
        } else {
            // Fallback to minimal map if source doesn't exist
            Files.writeString(targetUuidMap, "{}");
        }

        // Copy scraped games (full real data for comprehensive testing)
        Path sourceScrapedGames = Path.of(projectRoot, "data", "input", "scraped_games.json");
        Path targetScrapedGames = testDir.resolve("input/scraped_games.json");
        Files.createDirectories(targetScrapedGames.getParent());
        if (Files.exists(sourceScrapedGames)) {
            Files.copy(sourceScrapedGames, targetScrapedGames);
            System.out.println("Copied full scraped_games.json for real data testing");
        } else {
            // Fallback to empty games if source doesn't exist
            Files.writeString(targetScrapedGames, "{}");
        }

        // Copy processed game IDs (empty for fresh processing)
        Path targetProcessedIds = testDir.resolve("temp/processed_game_ids.json");
        Files.createDirectories(targetProcessedIds.getParent());
        Files.writeString(targetProcessedIds, "[]"); // Start with no processed games

        // Copy blacklisted game IDs
        Path sourceBlacklistedIds = Path.of(projectRoot, "data", "config", "blacklisted_game_ids.json");
        Path targetBlacklistedIds = testDir.resolve("config/blacklisted_game_ids.json");
        if (Files.exists(sourceBlacklistedIds)) {
            Files.copy(sourceBlacklistedIds, targetBlacklistedIds);
        } else {
            Files.writeString(targetBlacklistedIds, "[]");
        }

        // Create empty processed matches
        Path processedMatchesPath = testDir.resolve("temp/processed_matches.json");
        Files.writeString(processedMatchesPath, "{}");

        // Create output directory
        Files.createDirectories(testDir.resolve("output"));
    }

    private void setupTestDataForDir(Path dir) throws IOException {
        Path playerNamesPath = dir.resolve("input/player_uuid_map.json");
        Files.createDirectories(playerNamesPath.getParent());
        Files.createDirectories(dir.resolve("output"));

        String playerNamesJson = """
            {
                "uuid1": "Player1",
                "uuid2": "Player2",
                "uuid3": "Player3"
            }
            """;
        Files.writeString(playerNamesPath, playerNamesJson);

        Path processedIdsPath = dir.resolve("temp/processed_game_ids.json");
        Files.createDirectories(processedIdsPath.getParent());
        Files.writeString(processedIdsPath, "[]");

        Path blacklistedIdsPath = dir.resolve("temp/blacklisted_game_ids.json");
        Files.writeString(blacklistedIdsPath, "[]");

        Path processedMatchesPath = dir.resolve("temp/processed_matches.json");
        Files.writeString(processedMatchesPath, "{}");
    }

    private String[] getTestMatches() {
        return new String[]{
            """
            {
                "game1": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}, {"uuid": "uuid2", "name": "Player2"}],
                        "team2": [{"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 0, "deaths": 2}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000000
                }
            }
            """,
            """
            {
                "game2": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}, {"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000001
                }
            }
            """,
            """
            {
                "game3": {
                    "winner": "team2",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}, {"uuid": "uuid3", "name": "Player3"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 0, "deaths": 1},
                        "uuid2": {"bed_breaks": 1, "deaths": 0},
                        "uuid3": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDM1",
                    "unix_time": 1000000002
                }
            }
            """,
            """
            {
                "game4": {
                    "winner": "team1",
                    "teams": {
                        "team1": [{"uuid": "uuid1", "name": "Player1"}],
                        "team2": [{"uuid": "uuid2", "name": "Player2"}]
                    },
                    "player_stats": {
                        "uuid1": {"bed_breaks": 1, "deaths": 0},
                        "uuid2": {"bed_breaks": 0, "deaths": 1}
                    },
                    "lobby_id": "BEDD1",
                    "unix_time": 1000000003
                }
            }
            """,
            """
            {
                "game5": {
                    "winner": "team2",
                    "teams": {
                        "team1": [{"uuid": "uuid2", "name": "Player2"}],
                        "team2": [{"uuid": "uuid3", "name": "Player3"}]
                    },
                    "player_stats": {
                        "uuid2": {"bed_breaks": 0, "deaths": 1},
                        "uuid3": {"bed_breaks": 1, "deaths": 0}
                    },
                    "lobby_id": "BEDD1",
                    "unix_time": 1000000004
                }
            }
            """
        };
    }

    /**
     * Parses the output from viewLast5GamesElo() and validates ELO continuity.
     * Parses the debug output lines that show ELO transitions.
     */
    private void checkEloContinuityFromOutput(String output, Path testDir) throws IOException {
        // Parse the debug output lines
        Map<String, Map<String, List<double[]>>> gamePlayerElos = parseDebugOutput(output);

        // For each game, check ELO continuity
        for (Map.Entry<String, Map<String, List<double[]>>> gameEntry : gamePlayerElos.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<String, List<double[]>> playerElos = gameEntry.getValue();

            System.out.println("Checking ELO continuity for game: " + gameId);

            for (Map.Entry<String, List<double[]>> playerEntry : playerElos.entrySet()) {
                String playerKey = playerEntry.getKey(); // "PlayerName (mode)"
                List<double[]> elos = playerEntry.getValue();

                // Sort by previous ELO ascending (assuming chronological order)
                elos.sort(Comparator.comparingDouble(a -> a[0]));

                String playerName = playerKey.split(" \\(")[0];
                if(playerName.equals("FrostedFlynx")) {
                    System.out.println("  Player " + playerKey + " ELO transitions:");
                }
                for (int i = 0; i < elos.size(); i++) {
                    double[] eloPair = elos.get(i);
                    if(playerName.equals("FrostedFlynx")) {
                        System.out.printf("    Transition %d: %.1f -> %.1f%n", i + 1, eloPair[0], eloPair[1]);
                    }
                }

                // Check that consecutive transitions have continuous ELO
                for (int i = 0; i < elos.size() - 1; i++) {
                    double[] current = elos.get(i);
                    double[] next = elos.get(i + 1);

                    double finalElo = current[1]; // newElo of current
                    double nextPreviousElo = next[0]; // previousElo of next
                    if(playerName == "FrostedFlynx") {
                        System.out.printf("  Checking continuity for %s between transitions %d and %d: %.1f vs %.1f%n",
                                playerKey, i + 1, i + 2, finalElo, nextPreviousElo);
                    }
                    if(playerName == "FrostedFlynx") {
                    System.out.printf("    Continuity check: Game %d final ELO %.1f vs Game %d previous ELO %.1f%n",
                                    i + 1, finalElo, i + 2, nextPreviousElo);
                    }

                    if (Math.abs(finalElo - nextPreviousElo) > 0.1) { // Allow small floating point differences
                        Assertions.fail(String.format("ELO continuity broken for %s in game %s: " +
                                         "final ELO %.1f does not match next game's previous ELO %.1f",
                                         playerKey, gameId, finalElo, nextPreviousElo));
                    }
                }
            }
        }

        System.out.println("ELO continuity validation passed for all games!");
    }

    /**
     * Parses the debug output from buildLast5GamesFromHistory.
     * Expected format: "DEBUG: Game gameId, Player playerName (mode): prevElo -> newElo (change)"
     */
    private Map<String, Map<String, List<double[]>>> parseDebugOutput(String output) {
        Map<String, Map<String, List<double[]>>> result = new LinkedHashMap<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.startsWith("DEBUG: Game ")) {
                try {
                    // Format: "DEBUG: Game test_game_3, Player Player2 (solo): 1200.0 -> 1223.9 (23.9)"
                    String content = line.substring("DEBUG: Game ".length());
                    int commaIndex = content.indexOf(", Player ");
                    if (commaIndex == -1) continue;

                    String gameId = content.substring(0, commaIndex).trim();
                    String rest = content.substring(commaIndex + ", Player ".length());

                    int parenIndex = rest.indexOf(" (");
                    if (parenIndex == -1) continue;

                    String playerName = rest.substring(0, parenIndex);
                    String rest2 = rest.substring(parenIndex + " (".length());

                    int closingParenIndex = rest2.indexOf("): ");
                    if (closingParenIndex == -1) continue;

                    String mode = rest2.substring(0, closingParenIndex);
                    String numbers = rest2.substring(closingParenIndex + "): ".length());

                    String[] parts = numbers.split(" -> ");
                    if (parts.length != 2) continue;

                    double prevElo = Double.parseDouble(parts[0]);
                    String rest3 = parts[1];
                    int spaceIndex = rest3.indexOf(" ");
                    double newElo = Double.parseDouble(rest3.substring(0, spaceIndex));

                    String playerKey = playerName + " (" + mode + ")";

                    result.computeIfAbsent(gameId, k -> new HashMap<>())
                          .computeIfAbsent(playerKey, k -> new ArrayList<>())
                          .add(new double[]{prevElo, newElo});

                } catch (Exception e) {
                    System.out.println("Skipping malformed debug line: " + line);
                }
            }
        }

        return result;
    }

}



package com.example.elocalculator.managers;

import com.example.elocalculator.model.game.GameClassification;
import com.example.elocalculator.model.game.GameData;
import com.example.elocalculator.model.game.GameMode;
import com.example.elocalculator.model.game.InvalidGamesData;
import com.example.elocalculator.model.result.PlayerResult;
import com.example.elocalculator.config.Config;
import static com.example.elocalculator.managers.GameModeUtils.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Facade class that orchestrates ELO calculations and data processing.
 * Delegates to specialized managers for different operations.
 * 
 * This class provides a unified API for the application while internally
 * using specialized managers:
 * - DataManager: File I/O operations
 * - EloCalculator: Core ELO calculation logic
 * - MatchManager: Live match processing
 * - HistoricalDataProcessor: Batch historical processing
 * - MatchAnalyzer: Match analysis and debugging
 */
public class EloCalculationManager {

    private final String dataDirectory;
    private final DataManager dataManager;
    private final EloCalculator eloCalculator;
    private final MatchManager matchManager;
    private final HistoricalDataProcessor historicalDataProcessor;
    private final MatchAnalyzer matchAnalyzer;

    /**
     * Default constructor using "data" directory.
     */
    public EloCalculationManager() {
        this("data");
    }

    /**
     * Constructor with configurable data directory.
     * @param dataDirectory the directory containing data files
     */
    public EloCalculationManager(String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.dataManager = new DataManager(dataDirectory);
        this.eloCalculator = new EloCalculator();
        this.matchManager = new MatchManager(dataManager, eloCalculator);
        this.historicalDataProcessor = new HistoricalDataProcessor(dataManager, eloCalculator);
        this.matchAnalyzer = new MatchAnalyzer(dataManager, eloCalculator);
    }

    // ========== Manager Accessors ==========
    
    /**
     * Gets the underlying DataManager for direct access.
     * @return the DataManager instance
     */
    public DataManager getDataManager() {
        return dataManager;
    }

    /**
     * Gets the underlying EloCalculator for direct access.
     * @return the EloCalculator instance
     */
    public EloCalculator getEloCalculator() {
        return eloCalculator;
    }

    /**
     * Gets the underlying MatchManager for direct access.
     * @return the MatchManager instance
     */
    public MatchManager getMatchManager() {
        return matchManager;
    }

    /**
     * Gets the underlying HistoricalDataProcessor for direct access.
     * @return the HistoricalDataProcessor instance
     */
    public HistoricalDataProcessor getHistoricalDataProcessor() {
        return historicalDataProcessor;
    }

    /**
     * Gets the underlying MatchAnalyzer for direct access.
     * @return the MatchAnalyzer instance
     */
    public MatchAnalyzer getMatchAnalyzer() {
        return matchAnalyzer;
    }

    // ========== DataManager Delegations ==========

    /**
     * Loads game data from the JSON file.
     * @return Map of game data
     * @throws IOException if file cannot be read
     */
    public Map<String, GameData> loadGameData() throws IOException {
        return dataManager.loadGameData();
    }

    /**
     * Loads player name mappings from the JSON file.
     * @return Map of player UUIDs to names
     * @throws IOException if file cannot be read
     */
    public Map<String, String> loadPlayerNames() throws IOException {
        return dataManager.loadPlayerNames();
    }

    /**
     * Loads the set of processed game IDs to avoid duplicates.
     * @return Set of processed game IDs
     * @throws IOException if file cannot be read
     */
    public Set<String> loadProcessedGameIds() throws IOException {
        return dataManager.loadProcessedGameIds();
    }

    /**
     * Saves the set of processed game IDs.
     * @param processedIds the set of processed game IDs
     * @throws IOException if file cannot be written
     */
    public void saveProcessedGameIds(Set<String> processedIds) throws IOException {
        dataManager.saveProcessedGameIds(processedIds);
    }

    /**
     * Loads the set of blacklisted game IDs.
     * @return Set of blacklisted game IDs
     * @throws IOException if file cannot be read
     */
    public Set<String> loadBlacklistedGameIds() throws IOException {
        return dataManager.loadBlacklistedGameIds();
    }

    /**
     * Saves the set of blacklisted game IDs.
     * @param blacklistedIds the set of blacklisted game IDs
     * @throws IOException if file cannot be written
     */
    public void saveBlacklistedGameIds(Set<String> blacklistedIds) throws IOException {
        dataManager.saveBlacklistedGameIds(blacklistedIds);
    }

    /**
     * Loads historical player results from the JSON file.
     * @return Map of historical player results, or empty map if file doesn't exist
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerResult> loadHistoricalResults() throws IOException {
        return dataManager.loadHistoricalResults();
    }

    /**
     * Loads live mega leaderboard from the JSON file.
     * @return Map of live mega player results, or empty map if file doesn't exist
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerResult> loadLiveMegaLeaderboard() throws IOException {
        return dataManager.loadLiveMegaLeaderboard();
    }

    /**
     * Saves player name mappings to the JSON file.
     * @param playerNames the player name mappings
     * @throws IOException if file cannot be written
     */
    public void savePlayerNames(Map<String, String> playerNames) throws IOException {
        dataManager.savePlayerNames(playerNames);
    }

    // ========== Game Classification ==========

    /**
     * Detects the game mode from game data.
     * Delegates to GameModeUtils.
     * @param gameData the game data
     * @return the detected game mode, or null if undetermined
     */
    public String detectGameMode(GameData gameData) {
        return GameModeUtils.detectGameMode(gameData);
    }

    /**
     * Filters and classifies games by mode.
     * @param gameData the raw game data
     * @param invalidGamesByPlayer map to track invalid games per player
     * @return map of games classified by mode
     */
    public Map<String, List<GameClassification>> filterAndClassifyGames(
            Map<String, GameData> gameData, 
            Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer) {
        
        Map<String, List<GameClassification>> modes = new HashMap<>();
        for (GameMode mode : GameMode.values()) {
            modes.put(mode.getModeName(), new ArrayList<>());
        }

        int skipped = 0;

        for (Map.Entry<String, GameData> entry : gameData.entrySet()) {
            String gameId = entry.getKey();
            GameData data = entry.getValue();

            if (data.getWinner() == null || data.getWinner().isEmpty()) {
                skipped++;
                continue;
            }

            if ("STAFFBED".equals(data.getLobby_id()) || "Unknown".equals(data.getLobby_id())) {
                skipped++;
                continue;
            }

            String mode = detectGameMode(data);
            if (mode == null) {
                skipped++;
                continue;
            }

            // Log game team sizes
            List<Integer> teamSizes = data.getTeams().values().stream()
                .filter(team -> !team.isEmpty())
                .map(List::size)
                .collect(Collectors.toList());
            GameModeUtils.logGameTeamSizes(dataDirectory, gameId, data.getLobby_id(), mode, teamSizes);

            String winnerTeam;
            if ("Tie".equals(data.getWinner())) {
                winnerTeam = "Tie";
            } else {
                winnerTeam = data.getTeams().containsKey(data.getWinner()) ? data.getWinner() : null;
            }

            if (winnerTeam == null) {
                skipped++;
                continue;
            }

            // Check game activity: skip games with no bed breaks and insufficient deaths
            int totalBedBreaks = data.getPlayer_stats().values().stream()
                .mapToInt(ps -> ps.getBed_breaks())
                .sum();
            int totalDeaths = data.getPlayer_stats().values().stream()
                .mapToInt(ps -> ps.getDeaths())
                .sum();

            if (totalBedBreaks == 0 && totalDeaths < Config.NO_BED_MINIMUM_DEATHS) {
                skipped++;
                trackInvalidGame(gameId, data, mode, winnerTeam, invalidGamesByPlayer);
                continue;
            }

            modes.get(mode).add(new GameClassification(gameId, data, winnerTeam));
        }

        if (Config.DEBUG_MODE) {
            int totalValid = modes.values().stream().mapToInt(List::size).sum();
            System.out.println("Filtered games: " + totalValid + " valid, " + skipped + " skipped");
            for (GameMode mode : GameMode.values()) {
                System.out.println("  " + capitalize(mode.getModeName()) + ": " + modes.get(mode.getModeName()).size() + " games");
            }
        }

        return modes;
    }

    /**
     * Tracks an invalid game for each player.
     */
    private void trackInvalidGame(String gameId, GameData data, String mode, String winnerTeam,
                                   Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer) {
        for (String playerUuid : data.getPlayer_stats().keySet()) {
            Map<String, InvalidGamesData> playerData = invalidGamesByPlayer.computeIfAbsent(playerUuid, k -> new HashMap<>());
            InvalidGamesData modeData = playerData.computeIfAbsent(mode, k -> new InvalidGamesData());

            modeData.incrementTotal();

            if (winnerTeam != null && !"Tie".equals(winnerTeam)) {
                String playerTeam = data.getTeamsAsUuids().entrySet().stream()
                    .filter(teamEntry -> teamEntry.getValue().contains(playerUuid))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

                if (winnerTeam.equals(playerTeam)) {
                    modeData.incrementWins();
                }
            }

            modeData.addGameId(gameId);
        }
    }

    // ========== MatchManager Delegations ==========

    /**
     * Processes a single match and updates leaderboards and history.
     * @param matchJson JSON string containing a single match
     * @param playerNames player name mappings
     * @param processedIds set of already processed game IDs (will be updated)
     * @return true if the match was processed, false if it was already processed or invalid
     * @throws IOException if files cannot be read/written
     */
    public boolean processMatch(String matchJson, Map<String, String> playerNames, Set<String> processedIds) throws IOException {
        return matchManager.processMatch(matchJson, playerNames, processedIds, null, false, 0L, null);
    }

    /**
     * Processes a single match with optional blacklist checking.
     * @param matchJson the match JSON string
     * @param playerNames map of player UUIDs to names
     * @param processedIds set of already processed game IDs
     * @param blacklistedIds set of blacklisted game IDs (can be null to skip blacklist check)
     * @return true if the match was processed successfully
     * @throws IOException if files cannot be read or written
     */
    public boolean processMatch(String matchJson, Map<String, String> playerNames, 
                                Set<String> processedIds, Set<String> blacklistedIds) throws IOException {
        return matchManager.processMatch(matchJson, playerNames, processedIds, blacklistedIds, false, 0L, null);
    }

    /**
     * Gets the game ID of a recent match by index without removing it.
     * @param index the index of the recent match (1=most recent, 2=second most recent, etc.)
     * @return the game ID, or null if invalid index or no matches
     * @throws IOException if files cannot be read
     */
    public String getRecentMatchGameId(int index) throws IOException {
        return matchManager.getRecentMatchGameId(index);
    }

    /**
     * Removes a recent match by index (1=most recent, 2=second most recent, etc.).
     * @param playerNames player name mappings
     * @param index the index of the recent match to remove (1-5)
     * @return the game ID of the removed match, or null if invalid index or no matches
     * @throws IOException if files cannot be read/written
     */
    public String removeRecentMatch(Map<String, String> playerNames, int index) throws IOException {
        return matchManager.removeRecentMatch(playerNames, index);
    }

    /**
     * Removes a specific match by game ID.
     * @param gameId the game ID to remove
     * @param playerNames player name mappings
     * @return true if the match was removed, false if game ID not found
     * @throws IOException if files cannot be read/written
     */
    public boolean removeMatch(String gameId, Map<String, String> playerNames) throws IOException {
        return matchManager.removeMatch(gameId, playerNames);
    }

    // ========== HistoricalDataProcessor Delegations ==========

    /**
     * Processes ELO calculations for historical data.
     * @param playerNames map of player UUIDs to names
     * @return map of processed player results
     * @throws IOException if files cannot be read
     */
    public Map<String, PlayerResult> processEloCalculations(Map<String, String> playerNames) throws IOException {
        return historicalDataProcessor.processEloCalculations(playerNames);
    }

    /**
     * Analyzes a specific match and displays detailed calculation breakdown.
     * This replays history up to the specified match to get accurate ELO ratings.
     * @param gameId the game ID to analyze
     * @param playerNames map of player UUIDs to names
     * @throws IOException if files cannot be read
     */
    public void analyzeMatch(String gameId, Map<String, String> playerNames) throws IOException {
        historicalDataProcessor.analyzeSpecificMatch(gameId, playerNames, matchAnalyzer);
    }
}

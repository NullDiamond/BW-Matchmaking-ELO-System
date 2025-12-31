package com.nulldiamond.elocalculator.managers;

import com.nulldiamond.elocalculator.model.game.*;
import com.nulldiamond.elocalculator.model.elo.*;
import com.nulldiamond.elocalculator.model.result.*;
import com.nulldiamond.elocalculator.config.Config;
import static com.nulldiamond.elocalculator.managers.GameModeUtils.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager class responsible for processing individual matches and handling recent match removal.
 */
public class MatchManager {

    /**
     * Data class to hold parsed match processing information.
     */
    private static class MatchProcessingData {
        final String gameId;
        final GameData gameData;
        final String mode;
        final String winnerTeam;

        MatchProcessingData(String gameId, GameData gameData, String mode, String winnerTeam) {
            this.gameId = gameId;
            this.gameData = gameData;
            this.mode = mode;
            this.winnerTeam = winnerTeam;
        }
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DataManager dataManager;
    private final EloCalculator eloCalculator;
    private final OutputManager outputManager;
    private final EloHistoryManager historyManager;
    private final GameProcessor gameProcessor;

    public MatchManager(DataManager dataManager, EloCalculator eloCalculator) {
        this.dataManager = dataManager;
        this.eloCalculator = eloCalculator;
        this.outputManager = new OutputManager(dataManager.getDataDirectory());
        this.historyManager = new EloHistoryManager(dataManager.getDataDirectory());
        this.gameProcessor = new GameProcessor(eloCalculator);
    }

    /**
     * Parses and validates a match JSON string.
     * @param matchJson the match JSON string
     * @param processedIds set of already processed game IDs
     * @param blacklistedIds set of blacklisted game IDs
     * @param reprocessMode whether this is reprocessing mode
     * @return MatchProcessingData if valid, null if invalid
     */
    private MatchProcessingData parseAndValidateMatch(String matchJson, Set<String> processedIds, Set<String> blacklistedIds, boolean reprocessMode) {
        // Parse the single match JSON into GameData
        Map<String, GameData> matchData = gson.fromJson(matchJson, new com.google.gson.reflect.TypeToken<Map<String, GameData>>(){}.getType());

        if (matchData.size() != 1) {
            System.out.println("Match JSON must contain exactly one match");
            return null;
        }

        String gameId = matchData.keySet().iterator().next();
        GameData gameData = matchData.values().iterator().next();

        // Check if already processed
        if (!reprocessMode && processedIds.contains(gameId)) {
            System.out.println("Game ID " + gameId + " already processed, skipping");
            return null;
        }

        // Check if blacklisted
        if (blacklistedIds != null && blacklistedIds.contains(gameId)) {
            System.out.println("Game ID " + gameId + " is blacklisted, skipping");
            return null;
        }

        // Check chronological order: new match timestamp must be >= most recent game's timestamp
        // This prevents adding old games that would disrupt ELO history ordering
        if (!reprocessMode) {
            try {
                Long newMatchTimestamp = gameData.getUnix_time();
                if (newMatchTimestamp != null && newMatchTimestamp > 0) {
                    long mostRecentTimestamp = getMostRecentGameTimestamp();
                    if (mostRecentTimestamp > 0 && newMatchTimestamp < mostRecentTimestamp) {
                        System.out.println("Invalid match: timestamp (" + newMatchTimestamp + ") is older than the most recent game (" + mostRecentTimestamp + ")");
                        System.out.println("Cannot add games out of chronological order. New games must have a timestamp >= the most recent processed game.");
                        return null;
                    }
                }
            } catch (IOException e) {
                // If we can't load history, continue with processing (fail-open for new systems)
                System.out.println("Warning: Could not verify timestamp order: " + e.getMessage());
            }
        }

        // Validate the match
        if (gameData.getWinner() == null || gameData.getWinner().isEmpty()) {
            System.out.println("Invalid match: no winner specified");
            return null;
        }

        if (isStaffOrUnknownLobby(gameData.getLobby_id())) {
            System.out.println("Invalid match: staff or unknown lobby");
            return null;
        }

        // Determine game mode using GameModeUtils
        String mode = determineBestMode(gameData);
        // Note: Mode filtering removed - processMatch now handles all game modes
        // This allows both historical reprocessing and normal operation to work correctly

        // Check game activity using GameModeUtils
        if (!isGameActive(gameData)) {
            System.out.println("Invalid match: no bed breaks and insufficient deaths");
            return null;
        }

        // Validate winner team using GameModeUtils
        String winnerTeam = validateWinnerTeam(gameData);
        if (winnerTeam == null) {
            System.out.println("Invalid match: winner team not found");
            return null;
        }

        return new MatchProcessingData(gameId, gameData, mode, winnerTeam);
    }

    /**
     * Gets the timestamp of the most recent game in ELO history.
     * @return the most recent timestamp, or 0 if no history exists
     * @throws IOException if ELO history cannot be loaded
     */
    private long getMostRecentGameTimestamp() throws IOException {
        historyManager.loadEloHistory();
        Map<String, PlayerEloHistory> eloHistory = historyManager.getPlayerHistories();
        
        if (eloHistory == null || eloHistory.isEmpty()) {
            return 0L;
        }
        
        long mostRecent = 0L;
        for (PlayerEloHistory playerHistory : eloHistory.values()) {
            for (EloHistoryEntry entry : playerHistory.getAllEntries()) {
                if (entry.getTimestamp() > mostRecent) {
                    mostRecent = entry.getTimestamp();
                }
            }
        }
        
        return mostRecent;
    }

    // isGameActive and validateWinnerTeam now provided by GameModeUtils (static import)

    /**
     * Initializes new players and updates existing player names.
     * @param currentResults current player results
     * @param matchPlayers set of players in this match
     * @param gameData the game data
     * @param playerNames player name mappings
     * @param mode the game mode
     * @return true if any player names were updated
     * @throws IOException if files cannot be read/written
     */
    private boolean initializeAndUpdatePlayers(Map<String, PlayerResult> currentResults, Set<String> matchPlayers,
                                             GameData gameData, Map<String, String> playerNames, String mode) throws IOException {
        // Load legacy top players list
        Set<String> legacyTopPlayers = dataManager.loadLegacyTopPlayers();

        boolean playerNamesUpdated = false;
        for (String uuid : matchPlayers) {
            if (!currentResults.containsKey(uuid)) {
                PlayerResult result = initializeNewPlayer(uuid, currentResults, legacyTopPlayers, gameData, mode);
                currentResults.put(uuid, result);
            }
        }

        // Update player names for all players in this match
        for (String uuid : matchPlayers) {
            String playerName = getPlayerNameFromMatch(gameData, uuid);
            if (playerName != null && !playerName.equals("Unknown")) {
                String existingName = playerNames.get(uuid);
                if (!playerName.equals(existingName)) {
                    playerNames.put(uuid, playerName);
                    playerNamesUpdated = true;
                }
            }
        }

        // Save updated player names if any were updated
        if (playerNamesUpdated) {
            dataManager.savePlayerNames(playerNames);
        }

        return playerNamesUpdated;
    }

    /**
     * Initializes a new player result.
     * @param uuid the player's UUID
     * @param currentResults current player results (for historical lookup)
     * @param legacyTopPlayers set of legacy top players
     * @param gameData the game data
     * @param mode the game mode
     * @return the initialized PlayerResult
     */
    private PlayerResult initializeNewPlayer(String uuid, Map<String, PlayerResult> currentResults,
                                           Set<String> legacyTopPlayers, GameData gameData, String mode) {
        PlayerResult result = new PlayerResult();
        result.setUuid(uuid);

        // Check if player exists in historical data
        if (currentResults.containsKey(uuid)) {
            // Player exists in historical data
            PlayerResult historicalPlayer = currentResults.get(uuid);
            result.setName(historicalPlayer.getName());

            // Initialize mode if not present
            if (!result.getModes().containsKey(mode)) {
                ModeResult mr = new ModeResult();
                double initialElo = legacyTopPlayers.contains(uuid) ? Config.LEGACY_TOP_INITIAL_ELO : Config.INITIAL_ELO;
                mr.setElo(initialElo);
                mr.setGames(0);
                result.getModes().put(mode, mr);
            }
        } else {
            // New player - get their name from the match data
            String playerName = getPlayerNameFromMatch(gameData, uuid);
            result.setName(playerName);

            ModeResult mr = new ModeResult();
            double initialElo = legacyTopPlayers.contains(uuid) ? Config.LEGACY_TOP_INITIAL_ELO : Config.INITIAL_ELO;
            mr.setElo(initialElo);
            mr.setGames(0);
            result.getModes().put(mode, mr);
        }

        return result;
    }

    /**
     * Prepares ELO data for calculation from player results.
     * @param currentResults current player results
     * @param matchPlayers players in this match
     * @param mode the game mode
     * @return map of player ELO data
     */
    private Map<String, PlayerEloData> prepareEloData(Map<String, PlayerResult> currentResults, Set<String> matchPlayers, String mode) {
        Map<String, PlayerEloData> eloRatings = new HashMap<>();
        for (String uuid : matchPlayers) {
            PlayerResult pr = currentResults.get(uuid);
            if (pr != null) {
                PlayerEloData data = new PlayerEloData();

                if (pr.getModes().containsKey(mode)) {
                    ModeResult mr = pr.getModes().get(mode);
                    data.getElo().put(mode, mr.getElo());
                    data.getGamesPlayed().put(mode, mr.getGames());
                } else {
                    // New mode for existing player
                    ModeResult mr = new ModeResult();
                    mr.setElo(Config.INITIAL_ELO);
                    mr.setGames(0);
                    pr.getModes().put(mode, mr);
                    data.getElo().put(mode, Config.INITIAL_ELO);
                    data.getGamesPlayed().put(mode, 0);
                }

                eloRatings.put(uuid, data);
            }
        }
        return eloRatings;
    }

    /**
     * Applies ELO changes to player data and updates statistics.
     * @param eloChanges the calculated ELO changes
     * @param eloRatings the current ELO ratings
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param mode the game mode
     * @param reprocessMode whether this is reprocessing
     * @param originalChanges original changes for reprocessing
     * @return map of effective changes applied
     */
    private Map<String, Double> applyEloChanges(Map<String, EloChange> eloChanges, Map<String, PlayerEloData> eloRatings,
                                              GameData gameData, String winnerTeam, String mode, boolean reprocessMode,
                                              Map<String, Double> originalChanges) {
        Map<String, Double> effectiveChanges = new HashMap<>();
        for (Map.Entry<String, EloChange> entry : eloChanges.entrySet()) {
            String uuid = entry.getKey();
            EloChange change = entry.getValue();
            double ch = change.getChange();

            // In reprocess mode, override with original changes to maintain continuity
            if (reprocessMode && originalChanges != null && originalChanges.containsKey(uuid)) {
                ch = originalChanges.get(uuid);
            }
            effectiveChanges.put(uuid, ch);

            PlayerEloData data = eloRatings.get(uuid);
            data.getElo().put(mode, data.getElo().get(mode) + ch);
            data.getGamesPlayed().put(mode, data.getGamesPlayed().get(mode) + 1);

            // Update performance tracking
            updatePerformanceStats(data, change, mode);

            // Update detailed stats
            updateDetailedStats(data, gameData, winnerTeam, uuid, mode);
        }

        // Round ELO ratings to 1 decimal place for consistency
        roundEloRatings(eloRatings);

        return effectiveChanges;
    }

    /**
     * Updates performance statistics for a player.
     * Delegates to GameProcessor for consistent logic.
     * @param data the player ELO data
     * @param change the ELO change
     * @param mode the game mode
     */
    private void updatePerformanceStats(PlayerEloData data, EloChange change, String mode) {
        gameProcessor.updatePerformanceStats(data, change, mode);
    }

    /**
     * Updates detailed game statistics for a player.
     * Delegates to GameProcessor for consistent logic.
     * @param data the player ELO data
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param uuid the player UUID
     * @param mode the game mode
     */
    private void updateDetailedStats(PlayerEloData data, GameData gameData, String winnerTeam, String uuid, String mode) {
        gameProcessor.updateDetailedStats(data, gameData, winnerTeam, uuid, mode);
    }

    /**
     * Rounds ELO ratings to 1 decimal place for consistency.
     * Delegates to GameProcessor for consistent rounding logic.
     * @param eloRatings the ELO ratings to round
     */
    private void roundEloRatings(Map<String, PlayerEloData> eloRatings) {
        gameProcessor.roundEloRatings(eloRatings);
    }

    /**
     * Records match history for all players.
     * @param eloChanges the ELO changes
     * @param effectiveChanges the effective changes applied
     * @param eloRatings the current ELO ratings
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param gameId the game ID
     * @param mode the game mode
     * @param playerNames player name mappings
     * @param reprocessMode whether this is reprocessing
     * @param reprocessTimestamp timestamp for reprocessing
     * @throws IOException if files cannot be written
     */
    private void recordMatchHistory(Map<String, EloChange> eloChanges, Map<String, Double> effectiveChanges,
                                  Map<String, PlayerEloData> eloRatings, GameData gameData, String winnerTeam,
                                  String gameId, String mode, Map<String, String> playerNames, boolean reprocessMode,
                                  long reprocessTimestamp) throws IOException {
        historyManager.loadEloHistory();

        boolean isTie = "Tie".equals(winnerTeam);
        long matchTimestamp = determineMatchTimestamp(gameData, reprocessMode, reprocessTimestamp, gameId);

        for (Map.Entry<String, EloChange> entry : eloChanges.entrySet()) {
            String uuid = entry.getKey();
            double previousElo = Math.round((eloRatings.get(uuid).getElo().get(mode) - effectiveChanges.get(uuid)) * 10.0) / 10.0;
            double newElo = eloRatings.get(uuid).getElo().get(mode);
            boolean won = !isTie && isPlayerOnWinningTeam(uuid, gameData, winnerTeam);

            EloHistoryEntry historyEntry = new EloHistoryEntry(gameId, mode, matchTimestamp, previousElo, newElo, won, isTie);
            String playerName = playerNames.getOrDefault(uuid, "Unknown");
            historyManager.recordEloChange(uuid, playerName, historyEntry);
        }

        historyManager.saveEloHistory();
    }

    /**
     * Determines the appropriate timestamp for a match.
     * @param gameData the game data
     * @param reprocessMode whether this is reprocessing
     * @param reprocessTimestamp timestamp for reprocessing
     * @param gameId the game ID
     * @return the determined timestamp
     * @throws IOException if warning file cannot be written
     */
    private long determineMatchTimestamp(GameData gameData, boolean reprocessMode, long reprocessTimestamp, String gameId) throws IOException {
        if (reprocessMode && reprocessTimestamp > 0) {
            return gameData.getUnix_time() == null ? 0L : reprocessTimestamp;
        } else if (gameData.getUnix_time() != null) {
            return gameData.getUnix_time();
        } else {
            // Log matches with null unix_time
            String warningMessage = "WARNING: Match " + gameId + " has null unix_time, using timestamp 0";
            System.out.println(warningMessage);
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get(dataManager.getDataDirectory(), "null_unix_time_warnings.txt"),
                    (warningMessage + "\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
                System.err.println("Failed to write warning to file: " + e.getMessage());
            }
            return 0L; // Use 0 for games with null unix_time
        }
    }

    /**
     * Saves match data including snapshots, results, and processed IDs.
     * @param gameId the game ID
     * @param eloRatings the ELO ratings
     * @param effectiveChanges the effective changes
     * @param playerNames player name mappings
     * @param mode the game mode
     * @param currentResults current player results
     * @param processedIds processed game IDs
     * @param matchJson the match JSON
     * @param reprocessMode whether this is reprocessing
     * @param playerNamesUpdated whether player names were updated
     * @throws IOException if files cannot be written
     */
    private void saveMatchData(String gameId, Map<String, PlayerEloData> eloRatings, Map<String, Double> effectiveChanges,
                             Map<String, String> playerNames, String mode, Map<String, PlayerResult> currentResults,
                             Set<String> processedIds, String matchJson, boolean reprocessMode, boolean playerNamesUpdated) throws IOException {
        // Save snapshot if not reprocessing
        if (!reprocessMode) {
            saveSnapshot(gameId, eloRatings);
        }

        // Update player results with new ELO data
        updatePlayerResults(currentResults, eloRatings, mode);

        // Save updated main results and regenerate leaderboards
        outputManager.saveLeaderboards(currentResults);
        outputManager.saveResults(currentResults);

        // Mark as processed if not reprocessing
        if (!reprocessMode) {
            processedIds.add(gameId);
            dataManager.saveProcessedGameIds(processedIds);
        }

        // Update last 5 games data
        updateLast5Games(gameId, effectiveChanges, playerNames, eloRatings, mode);

        // Save processed match data for potential reprocessing on removal
        // Extract just the inner game data (without the gameId wrapper) for storage
        Map<String, GameData> matchData = gson.fromJson(matchJson, new com.google.gson.reflect.TypeToken<Map<String, GameData>>(){}.getType());
        GameData innerGameData = matchData.values().iterator().next();
        String innerMatchJson = gson.toJson(innerGameData);
        
        Map<String, String> processedMatches = dataManager.loadProcessedMatches();
        processedMatches.put(gameId, innerMatchJson);
        dataManager.saveProcessedMatches(processedMatches);
    }

    /**
     * Updates player results with new ELO data.
     * @param currentResults current player results
     * @param eloRatings the ELO ratings
     * @param mode the game mode
     */
    private void updatePlayerResults(Map<String, PlayerResult> currentResults, Map<String, PlayerEloData> eloRatings, String mode) {
        for (Map.Entry<String, PlayerEloData> entry : eloRatings.entrySet()) {
            String uuid = entry.getKey();
            PlayerEloData data = entry.getValue();

            PlayerResult result = currentResults.get(uuid);
            if (result != null && result.getModes().containsKey(mode)) {
                ModeResult mr = result.getModes().get(mode);
                mr.setElo(Math.round(data.getElo().get(mode) * 10.0) / 10.0);
                mr.setGames(data.getGamesPlayed().get(mode));

                PerformanceTracker pt = data.getPerformanceStats().get(mode);
                mr.setAvgPerformance(pt.getCount() > 0 ?
                    Math.round((pt.getTotalScore() / pt.getCount()) * 1000.0) / 1000.0 : 1.0);
                mr.setAvgNormalizedPerformance(pt.getCount() > 0 ?
                    Math.round((pt.getTotalNormalizedScore() / pt.getCount()) * 1000.0) / 1000.0 : 1.0);

                GameStatistics ds = data.getDetailedStats().get(mode);
                mr.setBedBreaks(ds.getBedBreaks());
                mr.setKills(ds.getKills());
                mr.setDeaths(ds.getDeaths());
                mr.setFinalKills(ds.getFinalKills());
                mr.setVictories(ds.getVictories());
            }
        }
    }

    /**
     * Processes a single match and updates leaderboards and history.
     * @param matchJson JSON string containing a single match (same format as scraped_games.json)
     * @param playerNames player name mappings
     * @param processedIds set of already processed game IDs (will be updated)
     * @return true if the match was processed, false if it was already processed or invalid
     * @throws IOException if files cannot be read/written
     */
    public boolean processMatch(String matchJson, Map<String, String> playerNames, Set<String> processedIds) throws IOException {
        return processMatch(matchJson, playerNames, processedIds, null, false, 0L, null);
    }

    /**
     * Processes a single match with optional reprocessing mode.
     * @param matchJson the match JSON string
     * @param playerNames map of player UUIDs to names
     * @param processedIds set of already processed game IDs
     * @param blacklistedIds set of blacklisted game IDs (can be null to skip blacklist check)
     * @param reprocessMode if true, skips saving snapshots and processed IDs (for reprocessing after removal)
     * @param reprocessTimestamp if reprocessMode and > 0, uses this timestamp instead of from match
     * @return true if the match was processed successfully
     * @throws IOException if files cannot be read or written
     */
    public boolean processMatch(String matchJson, Map<String, String> playerNames, Set<String> processedIds, Set<String> blacklistedIds, boolean reprocessMode, long reprocessTimestamp, Map<String, Double> originalChanges) throws IOException {
        // Parse and validate the match
        MatchProcessingData processingData = parseAndValidateMatch(matchJson, processedIds, blacklistedIds, reprocessMode);
        if (processingData == null) {
            return false;
        }

        String gameId = processingData.gameId;
        GameData gameData = processingData.gameData;
        String mode = processingData.mode;
        String winnerTeam = processingData.winnerTeam;

        // Load current results and initialize players
        Map<String, PlayerResult> currentResults = dataManager.loadHistoricalResults();
        Set<String> matchPlayers = gameData.getTeamsAsUuids().values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet());

        boolean playerNamesUpdated = initializeAndUpdatePlayers(currentResults, matchPlayers, gameData, playerNames, mode);

        // Prepare ELO data and calculate changes
        Map<String, PlayerEloData> eloRatings = prepareEloData(currentResults, matchPlayers, mode);
        Map<String, EloChange> eloChanges = eloCalculator.calculateMultiTeamEloChanges(
            gameData, winnerTeam, eloRatings, mode, getKFactorForMode(mode), gameId
        );

        // Apply ELO changes and update statistics
        Map<String, Double> effectiveChanges = applyEloChanges(eloChanges, eloRatings, gameData, winnerTeam, mode, reprocessMode, originalChanges);

        // Record history and save data
        recordMatchHistory(eloChanges, effectiveChanges, eloRatings, gameData, winnerTeam, gameId, mode, playerNames, reprocessMode, reprocessTimestamp);

        // Save snapshots and update data files
        saveMatchData(gameId, eloRatings, effectiveChanges, playerNames, mode, currentResults, processedIds, matchJson, reprocessMode, playerNamesUpdated);

        System.out.println("Successfully processed match: " + gameId);
        return true;
    }

    /**
     * Updates the last 5 games data with the new match.
     * @param gameId the game ID
     * @param effectiveChanges the effective ELO changes for each player
     * @param playerNames player name mappings
     * @param eloRatings the current ELO ratings
     * @param mode the game mode
     * @throws IOException if file cannot be read/written
     */
    private void updateLast5Games(String gameId, Map<String, Double> effectiveChanges, Map<String, String> playerNames, Map<String, PlayerEloData> eloRatings, String mode) throws IOException {
        List<Map<String, Object>> last5Games = dataManager.loadLast5Games();
        
        // Create game data
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("gameId", gameId);
        
        Map<String, Map<String, Object>> playerChanges = new HashMap<>();
        for (Map.Entry<String, Double> entry : effectiveChanges.entrySet()) {
            String uuid = entry.getKey();
            double change = entry.getValue();
            
            // Calculate previous and new ELO
            double newElo = eloRatings.get(uuid).getElo().get(mode);
            double previousElo = Math.round((newElo - change) * 10.0) / 10.0;
            
            Map<String, Object> changeData = new HashMap<>();
            changeData.put("gameMode", mode);
            changeData.put("previousElo", previousElo);
            changeData.put("newElo", newElo);
            changeData.put("eloChange", change);
            
            playerChanges.put(uuid, changeData);
        }
        
        gameData.put("playerChanges", playerChanges);
        
        // Check if this game is already in the list, if so update it
        boolean updated = false;
        for (Map<String, Object> game : last5Games) {
            if (gameId.equals(game.get("gameId"))) {
                game.put("playerChanges", playerChanges);
                updated = true;
                break;
            }
        }
        
        if (!updated) {
            // Add to the beginning of the list
            last5Games.add(0, gameData);
        }
        
        // Keep only last 5
        if (last5Games.size() > 5) {
            last5Games = last5Games.subList(0, 5);
        }
        
        dataManager.saveLast5Games(last5Games);
    }

    /**
     * Saves a snapshot of the current ELO state before processing a match.
     * @param gameId the game ID
     * @param eloRatings the current ELO ratings
     * @throws IOException if file cannot be written
     */
    private void saveSnapshot(String gameId, Map<String, PlayerEloData> eloRatings) throws IOException {
        List<Map<String, Object>> snapshots = dataManager.loadRecentSnapshots();

        // Create snapshot data
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("gameId", gameId);

        Map<String, Map<String, Double>> eloSnapshot = new HashMap<>();
        for (Map.Entry<String, PlayerEloData> entry : eloRatings.entrySet()) {
            String uuid = entry.getKey();
            PlayerEloData data = entry.getValue();
            Map<String, Double> playerElos = new HashMap<>();
            for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega"}) {
                Double elo = data.getElo().get(mode);
                if (elo != null) {
                    playerElos.put(mode, elo);
                }
            }
            if (!playerElos.isEmpty()) {
                eloSnapshot.put(uuid, playerElos);
            }
        }
        snapshot.put("eloSnapshot", eloSnapshot);

        // Add to beginning and keep only last 5
        snapshots.add(0, snapshot);
        if (snapshots.size() > 5) {
            snapshots = snapshots.subList(0, 5);
        }

        dataManager.saveRecentSnapshots(snapshots);
    }

    // detectGameMode, getModeFromLobby, getKFactorForMode now provided by GameModeUtils (static import)


    /**
     * Gets a player's name from the match data.
     * Delegates to GameProcessor for consistent name extraction.
     * @param gameData the game data
     * @param uuid the player's UUID
     * @return the player's name, or "Unknown" if not found
     */
    private String getPlayerNameFromMatch(GameData gameData, String uuid) {
        return gameProcessor.getPlayerNameFromMatch(gameData, uuid);
    }

    /**
     * Gets the game ID of a recent match by index without removing it.
     * @param index the index of the recent match (1=most recent, 2=second most recent, etc.)
     * @return the game ID, or null if invalid index or no matches
     * @throws IOException if files cannot be read
     */
    public String getRecentMatchGameId(int index) throws IOException {
        // Load ELO history
        historyManager.loadEloHistory();
        Map<String, PlayerEloHistory> eloHistory = historyManager.getPlayerHistories();
        if (eloHistory == null || eloHistory.isEmpty()) {
            return null;
        }
        
        // Get the last 5 unique game IDs from ELO history (most recent first)
        List<String> removableGames = getLast5GameIdsFromHistory(eloHistory);

        if (removableGames.isEmpty()) {
            return null;
        }

        if (index < 1 || index > removableGames.size()) {
            return null;
        }

        // Get the game ID (index 1 is most recent, so removableGames.get(index - 1))
        return removableGames.get(index - 1);
    }

    /**
     * Removes a recent match by index (1=most recent, 2=second most recent, etc.).
     * @param playerNames player name mappings
     * @param index the index of the recent match to remove (1-5)
     * @return the game ID of the removed match, or null if invalid index or no matches
     * @throws IOException if files cannot be read/written
     */
    public String removeRecentMatch(Map<String, String> playerNames, int index) throws IOException {
        // Load ELO history to get the last 5 processed games
        historyManager.loadEloHistory();
        Map<String, PlayerEloHistory> eloHistory = historyManager.getPlayerHistories();
        if (eloHistory == null || eloHistory.isEmpty()) {
            System.out.println("No recent matches available for removal");
            return null;
        }
        
        // Get the last 5 unique game IDs from ELO history (most recent first)
        List<String> removableGames = getLast5GameIdsFromHistory(eloHistory);

        if (removableGames.isEmpty()) {
            System.out.println("No recent matches available for removal");
            return null;
        }

        if (index < 1 || index > removableGames.size()) {
            System.out.println("Invalid index: " + index + " (available: 1-" + removableGames.size() + ")");
            return null;
        }

        // Get games to remove: from most recent to the target (index 1 is most recent)
        List<String> gamesToRemove = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            gamesToRemove.add(removableGames.get(i));
        }
        String targetGameId = gamesToRemove.get(gamesToRemove.size() - 1);

        System.out.println("Removing recent matches: " + gamesToRemove + " (target: " + targetGameId + ")");

        // Re-add the intermediate games (all except the target)
        List<String> gamesToReAdd = gamesToRemove.subList(0, gamesToRemove.size() - 1);

        // Load current main results and processed data
        Map<String, PlayerResult> currentResults = dataManager.loadHistoricalResults();
        Set<String> blacklistedIds = dataManager.loadBlacklistedGameIds();
        Map<String, String> processedMatches = dataManager.loadProcessedMatches();
        Set<String> processedIds = new HashSet<>();

        // Collect restore ELOs and timestamps: for each player-mode, the previousElo from the earliest game that affected it
        Map<String, Map<String, Double>> restoreElo = new HashMap<>(); // uuid -> mode -> elo
        Map<String, Long> gameTimestamps = new HashMap<>(); // gameId -> timestamp
        Map<String, Map<String, Integer>> bestGameIndex = new HashMap<>(); // uuid -> mode -> index in gamesToRemove (largest index = earliest game)
        for (int i = 0; i < gamesToRemove.size(); i++) {
            String gameId = gamesToRemove.get(i);
            for (PlayerEloHistory playerHistory : eloHistory.values()) {
                String uuid = playerHistory.getPlayerUuid();
                for (EloHistoryEntry entry : playerHistory.getAllEntries()) {
                    if (entry.getGameId().equals(gameId)) {
                        String mode = entry.getGameMode();
                        bestGameIndex.computeIfAbsent(uuid, k -> new HashMap<>()).merge(mode, i, Integer::max);
                        gameTimestamps.put(gameId, entry.getTimestamp());
                    }
                }
            }
        }
        // Collect original changes for re-added games
        Map<String, Map<String, Double>> originalChangesMap = new HashMap<>();
        for (String gameId : gamesToReAdd) {
            Map<String, Double> changes = new HashMap<>();
            for (PlayerEloHistory playerHistory : eloHistory.values()) {
                for (EloHistoryEntry entry : playerHistory.getAllEntries()) {
                    if (entry.getGameId().equals(gameId)) {
                        double change = entry.getNewElo() - entry.getPreviousElo();
                        changes.put(playerHistory.getPlayerUuid(), change);
                    }
                }
            }
            originalChangesMap.put(gameId, changes);
        }
        // Now set restoreElo using the earliest game for each player-mode
        for (Map.Entry<String, Map<String, Integer>> uuidEntry : bestGameIndex.entrySet()) {
            String uuid = uuidEntry.getKey();
            for (Map.Entry<String, Integer> modeEntry : uuidEntry.getValue().entrySet()) {
                String mode = modeEntry.getKey();
                int indexInRemove = modeEntry.getValue();
                String gameId = gamesToRemove.get(indexInRemove);
                // Find the previousElo for this uuid, mode, gameId
                for (PlayerEloHistory playerHistory : eloHistory.values()) {
                    if (playerHistory.getPlayerUuid().equals(uuid)) {
                        for (EloHistoryEntry entry : playerHistory.getAllEntries()) {
                            if (entry.getGameId().equals(gameId) && entry.getGameMode().equals(mode)) {
                                restoreElo.computeIfAbsent(uuid, k -> new HashMap<>()).put(mode, entry.getPreviousElo());
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Create filtered history without the removed games
        Map<String, PlayerEloHistory> filteredHistory = new HashMap<>();
        for (Map.Entry<String, PlayerEloHistory> entry : eloHistory.entrySet()) {
            String uuid = entry.getKey();
            PlayerEloHistory original = entry.getValue();
            PlayerEloHistory filteredPlayer = new PlayerEloHistory(original.getPlayerUuid(), original.getPlayerName());
            for (Map.Entry<String, List<EloHistoryEntry>> modeEntry : original.getHistoryByMode().entrySet()) {
                List<EloHistoryEntry> filteredEntries = modeEntry.getValue().stream()
                    .filter(e -> !gamesToRemove.contains(e.getGameId()))
                    .collect(Collectors.toList());
                if (!filteredEntries.isEmpty()) {
                    filteredPlayer.getHistoryByMode().put(modeEntry.getKey(), filteredEntries);
                }
            }
            if (!filteredPlayer.getHistoryByMode().isEmpty()) {
                filteredHistory.put(uuid, filteredPlayer);
            }
        }
        historyManager.setPlayerHistories(filteredHistory);
        
        // Save filtered history to disk BEFORE re-adding games
        // This ensures that when re-adding games calls loadEloHistory(), it gets the filtered version
        historyManager.saveEloHistory();

        // Restore current ELOs to the saved previousElo values (rounded to 1 decimal place for consistency)
        for (Map.Entry<String, Map<String, Double>> entry : restoreElo.entrySet()) {
            String uuid = entry.getKey();
            if (currentResults.containsKey(uuid)) {
                PlayerResult pr = currentResults.get(uuid);
                for (Map.Entry<String, Double> modeEntry : entry.getValue().entrySet()) {
                    String mode = modeEntry.getKey();
                    double elo = Math.round(modeEntry.getValue() * 10.0) / 10.0; // Round to 1 decimal place
                    if (pr.getModes().containsKey(mode)) {
                        pr.getModes().get(mode).setElo(elo);
                    }
                }
            }
        }

        // CRITICAL: Save restored ELOs to disk BEFORE re-adding games
        // When processMatch() is called for re-adding, it loads currentResults from disk via loadHistoricalResults()
        // If we don't save here, it will load the old (pre-restoration) values, causing incorrect ELO calculations
        outputManager.saveResults(currentResults);

        // Re-add the intermediate games (all except the target) in CHRONOLOGICAL ORDER (oldest first)
        // gamesToReAdd is in reverse chronological order (newest first), so we reverse it
        List<String> gamesToReAddChronological = new ArrayList<>(gamesToReAdd);
        java.util.Collections.reverse(gamesToReAddChronological);
        
        for (String gameId : gamesToReAddChronological) {
            String matchJson = processedMatches.get(gameId);
            if (matchJson != null) {
                System.out.println("Re-adding game: " + gameId);
                String wrappedJson = "{\"" + gameId + "\": " + matchJson + "}";
                long timestamp = gameTimestamps.getOrDefault(gameId, 0L);
                processMatch(wrappedJson, playerNames, processedIds, blacklistedIds, true, timestamp, originalChangesMap.get(gameId));
            } else {
                System.out.println("WARNING: No match JSON found for game: " + gameId);
            }
        }

        // Remove the target game from processed IDs
        Set<String> actualProcessedIds = dataManager.loadProcessedGameIds();
        actualProcessedIds.remove(targetGameId);
        dataManager.saveProcessedGameIds(actualProcessedIds);

        // Save updated data
        outputManager.saveLeaderboards(currentResults);
        outputManager.saveResults(currentResults);
        historyManager.saveEloHistory();

        System.out.println("Successfully removed recent match: " + targetGameId);
        return targetGameId;
    }

    /**
     * Removes a specific match by game ID.
     * @param gameId the game ID to remove
     * @param playerNames player name mappings
     * @return true if the match was removed, false if game ID not found
     * @throws IOException if files cannot be read/written
     */
    public boolean removeMatch(String gameId, Map<String, String> playerNames) throws IOException {
        List<Map<String, Object>> snapshots = dataManager.loadRecentSnapshots();

        // Find the snapshot that contains this game ID
        int snapshotIndex = -1;
        for (int i = 0; i < snapshots.size(); i++) {
            Map<String, Object> snapshot = snapshots.get(i);
            String snapshotGameId = (String) snapshot.get("gameId");
            if (gameId.equals(snapshotGameId)) {
                snapshotIndex = i;
                break;
            }
        }

        if (snapshotIndex == -1) {
            System.out.println("Game ID not found in recent snapshots: " + gameId);
            return false;
        }

        // Get the snapshot for the game to remove
        Map<String, Object> snapshot = snapshots.get(snapshotIndex);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Double>> eloSnapshot = (Map<String, Map<String, Double>>) snapshot.get("eloSnapshot");

        System.out.println("Removing match: " + gameId);

        // Load current main results
        Map<String, PlayerResult> currentResults = dataManager.loadHistoricalResults();
        if (currentResults == null) {
            currentResults = new HashMap<>();
        }

        // Load ELO history
        Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();
        if (eloHistory == null) {
            eloHistory = new HashMap<>();
        }

        // Restore ELO values from snapshot
        for (Map.Entry<String, Map<String, Double>> playerEntry : eloSnapshot.entrySet()) {
            String uuid = playerEntry.getKey();
            Map<String, Double> modeElos = playerEntry.getValue();

            // Update main results
            PlayerResult playerResult = currentResults.computeIfAbsent(uuid, k -> new PlayerResult(uuid, playerNames.getOrDefault(uuid, "Unknown")));
            for (Map.Entry<String, Double> modeEntry : modeElos.entrySet()) {
                String mode = modeEntry.getKey();
                double elo = modeEntry.getValue();
                ModeResult modeResult = new ModeResult();
                modeResult.setElo(Math.round(elo * 10.0) / 10.0);
                modeResult.setGames(0);
                modeResult.setAvgPerformance(0);
                modeResult.setAvgNormalizedPerformance(0);
                modeResult.setInvalidGames(0);
                modeResult.setInvalidWins(0);
                playerResult.getModes().put(mode, modeResult);
            }

            // Update ELO history - remove the entries for this game
            PlayerEloHistory playerHistory = eloHistory.get(uuid);
            if (playerHistory != null) {
                playerHistory.removeEntriesByGameId(gameId);
            }
        }

        // Remove this snapshot and all subsequent snapshots (since they depend on this one)
        snapshots.subList(snapshotIndex, snapshots.size()).clear();

        // Update processed game IDs - remove this game ID
        Set<String> processedIds = dataManager.loadProcessedGameIds();
        processedIds.remove(gameId);
        dataManager.saveProcessedGameIds(processedIds);

        // Save updated data
        dataManager.saveRecentSnapshots(snapshots);

        // Save updated data
        outputManager.saveLeaderboards(currentResults);
        outputManager.saveResults(currentResults);
        dataManager.saveEloHistory(eloHistory);

        System.out.println("Successfully removed match: " + gameId);
        return true;
    }

    /**
     * Determines if a player is on the winning team.
     * @param playerUuid the player's UUID
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @return true if the player is on the winning team
     */
    private boolean isPlayerOnWinningTeam(String playerUuid, GameData gameData, String winnerTeam) {
        return gameData.getTeamsAsUuids().getOrDefault(winnerTeam, Collections.emptyList()).contains(playerUuid);
    }
    
    /**
     * Gets the last 5 unique game IDs from ELO history (most recent first).
     */
    private List<String> getLast5GameIdsFromHistory(Map<String, PlayerEloHistory> eloHistory) {
        // Collect all EloHistoryEntry
        List<EloHistoryEntry> allEntries = new ArrayList<>();
        for (PlayerEloHistory playerHistory : eloHistory.values()) {
            for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega"}) {
                allEntries.addAll(playerHistory.getHistoryForMode(mode));
            }
        }
        
        // Sort by timestamp descending (most recent first)
        allEntries.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        
        // Get unique game IDs for the last 5 games
        Set<String> last5GameIds = new LinkedHashSet<>();
        for (EloHistoryEntry entry : allEntries) {
            last5GameIds.add(entry.getGameId());
            if (last5GameIds.size() >= 5) break;
        }
        
        return new ArrayList<>(last5GameIds);
    }
}



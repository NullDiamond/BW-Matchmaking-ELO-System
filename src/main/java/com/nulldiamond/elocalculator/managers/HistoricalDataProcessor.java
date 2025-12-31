package com.nulldiamond.elocalculator.managers;

import com.nulldiamond.elocalculator.model.game.GameStatistics;
import com.nulldiamond.elocalculator.model.elo.EloChange;
import com.nulldiamond.elocalculator.model.elo.EloHistoryEntry;
import com.nulldiamond.elocalculator.model.game.GameClassification;
import com.nulldiamond.elocalculator.model.game.GameData;
import com.nulldiamond.elocalculator.model.game.GameMode;
import com.nulldiamond.elocalculator.model.game.InvalidGamesData;
import com.nulldiamond.elocalculator.model.result.PlayerInvalidGamesEntry;
import com.nulldiamond.elocalculator.model.result.ModeResult;
import com.nulldiamond.elocalculator.model.elo.PerformanceTracker;
import com.nulldiamond.elocalculator.model.elo.PlayerEloData;
import com.nulldiamond.elocalculator.model.result.PlayerResult;
import com.nulldiamond.elocalculator.model.game.PlayerStats;
import com.nulldiamond.elocalculator.config.Config;
import static com.nulldiamond.elocalculator.managers.GameModeUtils.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manager class responsible for processing historical ELO calculations.
 */
public class HistoricalDataProcessor {

    private final DataManager dataManager;
    private final EloCalculator eloCalculator;
    private final GameProcessor gameProcessor;
    private final Gson gson = new Gson();
    private EloHistoryManager historyManager;
    private Map<String, JsonElement> scrapedJson;
    private Map<String, String> matchJsons;

    /**
     * Constructor.
     * @param dataManager the data manager for file operations
     * @param eloCalculator the ELO calculator for computation
     */
    public HistoricalDataProcessor(DataManager dataManager, EloCalculator eloCalculator) {
        this.dataManager = dataManager;
        this.eloCalculator = eloCalculator;
        this.gameProcessor = new GameProcessor(eloCalculator);
    }

    /**
     * Processes ELO calculations for historical data.
     * @param playerNames map of player UUIDs to names
     * @return map of processed player results
     * @throws IOException if files cannot be read/written
     */
    public Map<String, PlayerResult> processEloCalculations(Map<String, String> playerNames) throws IOException {
        Map<String, GameData> gameData = loadGameDataAndMatchJsons();
        Set<String> blacklistedGameIds = dataManager.loadBlacklistedGameIds();
        Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer = new HashMap<>();

        // Clean up temp files and enable batch mode for efficient discrepancy logging
        cleanupBeforeProcessing();

        // Initialize history manager and clear any existing history
        initializeHistoryManager();

        // Filter and classify games
        Map<String, List<GameClassification>> gamesByMode = filterAndClassifyGames(gameData, invalidGamesByPlayer, blacklistedGameIds);

        // Initialize ELO ratings
        Map<String, PlayerEloData> eloRatings = initializeEloRatings(gameData, playerNames);

        // Process each mode
        processRegularModes(gamesByMode, eloRatings, playerNames);

        // Process mega mode separately with additional iterations
        processMegaMode(gamesByMode, eloRatings, playerNames);

        // Save ELO history
        System.out.println("\nSaving ELO history...");
        historyManager.saveEloHistory();

        // Flush discrepancy batch to disk (efficient single write)
        GameModeUtils.flushDiscrepancyBatch(dataManager.getDataDirectory());

        // Format results
        Map<String, PlayerResult> results = formatResults(eloRatings, playerNames, invalidGamesByPlayer);

        // Save invalid games data
        saveInvalidGames(invalidGamesByPlayer, playerNames, results);

        // Save processed game IDs
        dataManager.saveProcessedGameIds(gameData.keySet());

        // Save processed matches for future reprocessing
        Map<String, String> processedMatches = new HashMap<>();
        for (Map.Entry<String, String> entry : matchJsons.entrySet()) {
            processedMatches.put(entry.getKey(), entry.getValue());
        }
        dataManager.saveProcessedMatches(processedMatches);

        return results;
    }

    /**
     * Loads game data and match JSONs for processing.
     * @return map of game data
     * @throws IOException if files cannot be read
     */
    private Map<String, GameData> loadGameDataAndMatchJsons() throws IOException {
        Map<String, GameData> gameData = dataManager.loadGameData();

        // Load raw match JSONs for saving processed matches
        String scrapedContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dataManager.getDataDirectory(), "input/scraped_games.json")));
        scrapedJson = gson.fromJson(scrapedContent, new TypeToken<Map<String, JsonElement>>(){}.getType());
        matchJsons = new HashMap<>();
        for (String gameId : gameData.keySet()) {
            if (scrapedJson.containsKey(gameId)) {
                JsonElement element = scrapedJson.get(gameId);
                if (element.isJsonObject()) {
                    matchJsons.put(gameId, element.toString());
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    matchJsons.put(gameId, element.getAsString());
                }
                // Skip other types
            }
        }

        return gameData;
    }

    /**
     * Cleans up temporary files before processing.
     * This ensures a fresh start for history and discrepancy tracking.
     */
    private void cleanupBeforeProcessing() {
        // Clear discrepancy file and enable batch mode for efficient collection
        GameModeUtils.clearDiscrepancyFile(dataManager.getDataDirectory());
        GameModeUtils.enableDiscrepancyBatchMode();
    }

    /**
     * Initializes the history manager and clears any existing history.
     */
    private void initializeHistoryManager() {
        // Initialize history manager and clear any existing history
        // This ensures we rebuild history from scratch on each run
        historyManager = new EloHistoryManager(dataManager.getDataDirectory());
        historyManager.clearCache();

        // Ensure the history file is deleted to prevent loading old data
        try {
            java.nio.file.Path historyFile = java.nio.file.Paths.get(dataManager.getDataDirectory(), "temp/elo_history.json");
            if (java.nio.file.Files.exists(historyFile)) {
                java.nio.file.Files.delete(historyFile);
                System.out.println("Ensured history file is deleted for clean rebuild.");
            }
        } catch (java.io.IOException e) {
            System.out.println("Warning: Could not delete existing history file: " + e.getMessage());
        }
    }

    /**
     * Processes regular game modes (solo, duo, trio, fours) with standard iterations.
     * @param gamesByMode classified games by mode
     * @param eloRatings player ELO ratings
     * @param playerNames player name mappings
     */
    private void processRegularModes(Map<String, List<GameClassification>> gamesByMode,
                                   Map<String, PlayerEloData> eloRatings,
                                   Map<String, String> playerNames) {
        for (GameMode mode : GameMode.values()) {
            if (mode == GameMode.MEGA) continue; // Mega is processed separately

            String modeName = mode.getModeName();
            List<GameClassification> games = gamesByMode.get(modeName);
            if (games.isEmpty()) continue;

            double kFactor = getKFactorForMode(modeName);

            System.out.println("\nProcessing " + modeName + " games (" + games.size() + " games)...");

            for (int iteration = 1; iteration <= Config.NUM_ITERATIONS; iteration++) {
                System.out.println("Iteration " + iteration + "/" + Config.NUM_ITERATIONS);
                processModeIteration(games, eloRatings, modeName, kFactor, iteration, playerNames, false);
            }
        }
    }

    /**
     * Processes mega mode with additional iterations.
     * @param gamesByMode classified games by mode
     * @param eloRatings player ELO ratings
     * @param playerNames player name mappings
     */
    private void processMegaMode(Map<String, List<GameClassification>> gamesByMode,
                               Map<String, PlayerEloData> eloRatings,
                               Map<String, String> playerNames) {
        String modeName = GameMode.MEGA.getModeName();
        List<GameClassification> games = gamesByMode.get(modeName);
        if (!games.isEmpty()) {
            System.out.println("\nProcessing " + modeName + " games (" + games.size() + " games)...");

            // Process mega games with their own iteration count, starting at 1
            for (int megaIteration = 1; megaIteration <= Config.NUM_ITERATIONS_MEGA; megaIteration++) {
                System.out.println("Mega Iteration " + megaIteration + "/" + Config.NUM_ITERATIONS_MEGA);
                double kFactor = Config.K_FACTOR_MEGA;
                processModeIteration(games, eloRatings, modeName, kFactor, megaIteration, playerNames, true);
            }
        }
    }

    /**
     * Processes a single iteration for a game mode.
     * @param games games to process
     * @param eloRatings player ELO ratings
     * @param modeName the mode name
     * @param kFactor the K-factor for this mode
     * @param iteration the current iteration number
     * @param playerNames player name mappings
     * @param isMega whether this is mega mode processing
     */
    private void processModeIteration(List<GameClassification> games,
                                    Map<String, PlayerEloData> eloRatings,
                                    String modeName,
                                    double kFactor,
                                    int iteration,
                                    Map<String, String> playerNames,
                                    boolean isMega) {
        for (GameClassification gc : games) {
            Map<String, EloChange> eloChanges = eloCalculator.calculateMultiTeamEloChanges(
                gc.getGameData(), gc.getWinnerTeam(), eloRatings, modeName, kFactor, gc.getGameId()
            );

            for (Map.Entry<String, EloChange> entry : eloChanges.entrySet()) {
                String uuid = entry.getKey();
                EloChange change = entry.getValue();
                PlayerEloData data = eloRatings.get(uuid);

                double previousElo = data.getElo().get(modeName);
                double newElo = previousElo + change.getChange();
                data.getElo().put(modeName, newElo);

                // Track performance scores
                PerformanceTracker pt = data.getPerformanceStats().get(modeName);
                pt.setTotalScore(pt.getTotalScore() + change.getRawPerformanceScore());
                pt.setTotalNormalizedScore(pt.getTotalNormalizedScore() + change.getNormalizedPerformanceScore());
                pt.setCount(pt.getCount() + 1);

                // Record history on the last iteration
                boolean isLastIteration = isMega ?
                    (iteration == Config.NUM_ITERATIONS_MEGA) :
                    (iteration == Config.NUM_ITERATIONS);

                if (isLastIteration) {
                    // Increment games played once per game
                    data.getGamesPlayed().put(modeName, data.getGamesPlayed().get(modeName) + 1);

                    // Determine if player won or if it was a tie
                    boolean isTie = "Tie".equals(gc.getWinnerTeam());
                    boolean won = !isTie && isPlayerOnWinningTeam(uuid, gc.getGameData(), gc.getWinnerTeam());

                    // Get actual match timestamp (use current time as fallback if not available)
                    long matchTimestamp = gc.getGameData().getUnix_time() != null
                        ? gc.getGameData().getUnix_time()
                        : System.currentTimeMillis();

                    // Record ELO change in history with rounded values for consistency
                    double roundedPreviousElo = Math.round(previousElo * 10.0) / 10.0;
                    double roundedNewElo = Math.round(newElo * 10.0) / 10.0;
                    EloHistoryEntry historyEntry = new EloHistoryEntry(
                        gc.getGameId(), modeName, matchTimestamp, roundedPreviousElo, roundedNewElo, won, isTie
                    );
                    String playerName = playerNames.getOrDefault(uuid, "Unknown");
                    historyManager.recordEloChange(uuid, playerName, historyEntry);
                }
            }

            // Count victories for all players in winning teams during first iteration
            if (iteration == 1) {
                countVictoriesForWinningTeams(gc, eloRatings, modeName);
            }

            // Accumulate stats for all players with stats during first iteration
            if (iteration == 1) {
                accumulatePlayerStats(gc, eloRatings, modeName);
            }
        }
    }

    /**
     * Counts victories for players on winning teams.
     * @param gc the game classification
     * @param eloRatings player ELO ratings
     * @param modeName the mode name
     */
    private void countVictoriesForWinningTeams(GameClassification gc,
                                             Map<String, PlayerEloData> eloRatings,
                                             String modeName) {
        for (String team : gc.getGameData().getTeamsAsUuids().keySet()) {
            if (team.equals(gc.getWinnerTeam())) {
                for (String playerUuid : gc.getGameData().getTeamsAsUuids().get(team)) {
                    if (eloRatings.containsKey(playerUuid)) {
                        GameStatistics ds = eloRatings.get(playerUuid).getDetailedStats().get(modeName);
                        ds.setVictories(ds.getVictories() + 1);
                    }
                }
            }
        }
    }

    /**
     * Accumulates detailed stats for all players in the game.
     * @param gc the game classification
     * @param eloRatings player ELO ratings
     * @param modeName the mode name
     */
    private void accumulatePlayerStats(GameClassification gc,
                                     Map<String, PlayerEloData> eloRatings,
                                     String modeName) {
        Map<String, PlayerStats> playerStats = gc.getGameData().getPlayer_stats();
        if (playerStats != null) {
            for (String playerUuid : playerStats.keySet()) {
                if (eloRatings.containsKey(playerUuid)) {
                    PlayerStats stats = playerStats.get(playerUuid);
                    GameStatistics ds = eloRatings.get(playerUuid).getDetailedStats().get(modeName);
                    ds.setBedBreaks(ds.getBedBreaks() + stats.getBed_breaks());
                    ds.setKills(ds.getKills() + stats.getKills());
                    ds.setDeaths(ds.getDeaths() + stats.getDeaths());
                    ds.setFinalKills(ds.getFinalKills() + stats.getFinal_kills());
                }
            }
        }
    }

    /**
     * Checks if a player is on the winning team.
     * Delegates to GameProcessor for consistent logic.
     * @param playerUuid the player's UUID
     * @param gameData the game data
     * @param winnerTeam the winning team name
     * @return true if the player was on the winning team
     */
    private boolean isPlayerOnWinningTeam(String playerUuid, GameData gameData, String winnerTeam) {
        return gameProcessor.isPlayerOnWinningTeam(playerUuid, gameData, winnerTeam);
    }

    /**
     * Filters and classifies games by mode.
     * @param gameData the raw game data
     * @param invalidGamesByPlayer map to track invalid games per player
     * @return map of games classified by mode
     */
    private Map<String, List<GameClassification>> filterAndClassifyGames(Map<String, GameData> gameData, Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer, Set<String> blacklistedGameIds) {
        Map<String, List<GameClassification>> modes = new HashMap<>();
        for (GameMode mode : GameMode.values()) {
            modes.put(mode.getModeName(), new ArrayList<>());
        }

        int skipped = 0;
        int blacklisted = 0;

        for (Map.Entry<String, GameData> entry : gameData.entrySet()) {
            String gameId = entry.getKey();
            GameData data = entry.getValue();

            // Skip blacklisted games
            if (blacklistedGameIds.contains(gameId)) {
                blacklisted++;
                continue;
            }

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
            GameModeUtils.logGameTeamSizes("data", gameId, data.getLobby_id(), mode, teamSizes);

            String winnerTeam = determineWinnerTeam(data);

            if (winnerTeam == null) {
                skipped++;
                continue;
            }

            // Check game activity: skip games with no bed breaks and insufficient deaths
            boolean isGameValid = isGameValidForEloCalculation(data);

            if (!isGameValid) {
                skipped++;
                // Track invalid games for each player in this game
                trackInvalidGameForPlayers(data, invalidGamesByPlayer, mode, winnerTeam, gameId);
                continue;
            }

            // Add valid game to the appropriate mode
            modes.get(mode).add(new GameClassification(gameId, data, winnerTeam));
        }

        // Sort games by timestamp to ensure chronological processing
        for (List<GameClassification> gameList : modes.values()) {
            gameList.sort((a, b) -> Long.compare(a.getGameData().getUnix_time(), b.getGameData().getUnix_time()));
        }

        System.out.println("Filtered " + skipped + " invalid games, " + blacklisted + " blacklisted games");
        return modes;
    }

    /**
     * Determines the winning team from game data.
     * @param gameData the game data
     * @return the winning team name, or null if invalid
     */
    private String determineWinnerTeam(GameData gameData) {
        if ("Tie".equals(gameData.getWinner())) {
            return "Tie";
        }
        return gameData.getTeams().containsKey(gameData.getWinner()) ? gameData.getWinner() : null;
    }

    /**
     * Checks if a game is valid for ELO calculation based on activity levels.
     * @param gameData the game data
     * @return true if the game is valid for ELO calculation
     */
    private boolean isGameValidForEloCalculation(GameData gameData) {
        int totalBedBreaks = gameData.getPlayer_stats().values().stream()
            .mapToInt(ps -> ps.getBed_breaks())
            .sum();
        int totalDeaths = gameData.getPlayer_stats().values().stream()
            .mapToInt(ps -> ps.getDeaths())
            .sum();

        return !(totalBedBreaks == 0 && totalDeaths < Config.NO_BED_MINIMUM_DEATHS);
    }

    /**
     * Tracks invalid games for all players in the game.
     * @param gameData the game data
     * @param invalidGamesByPlayer the tracking map
     * @param mode the game mode
     * @param winnerTeam the winning team
     * @param gameId the game ID
     */
    private void trackInvalidGameForPlayers(GameData gameData, Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer,
                                           String mode, String winnerTeam, String gameId) {
        for (String playerUuid : gameData.getPlayer_stats().keySet()) {
            Map<String, InvalidGamesData> playerData = invalidGamesByPlayer.computeIfAbsent(playerUuid, k -> new HashMap<>());
            InvalidGamesData modeData = playerData.computeIfAbsent(mode, k -> new InvalidGamesData());

            // Increment total
            modeData.incrementTotal();

            // Check if this player was on the winning team
            if (winnerTeam != null && !"Tie".equals(winnerTeam)) {
                String playerTeam = findPlayerTeam(gameData, playerUuid);
                if (winnerTeam.equals(playerTeam)) {
                    modeData.incrementWins();
                }
            }

            modeData.addGameId(gameId);
        }
    }

    /**
     * Finds which team a player belongs to.
     * Delegates to GameProcessor for consistent logic.
     * @param gameData the game data
     * @param playerUuid the player UUID
     * @return the team name, or null if not found
     */
    private String findPlayerTeam(GameData gameData, String playerUuid) {
        return gameProcessor.findPlayerTeam(gameData, playerUuid);
    }

    /**
     * Initializes ELO ratings for all players found in the game data.
     * @param gameData the game data
     * @param playerNames map of player names
     * @return initialized ELO ratings
     */
    private Map<String, PlayerEloData> initializeEloRatings(Map<String, GameData> gameData, Map<String, String> playerNames) {
        Map<String, PlayerEloData> eloRatings = new HashMap<>();
        Map<String, Double> globalElos = loadGlobalElos();

        // Load premium players
        Set<String> legacyTopPlayers = new HashSet<>();
        try {
            legacyTopPlayers = dataManager.loadLegacyTopPlayers();
        } catch (IOException e) {
            System.err.println("Warning: Could not load legacy top players list: " + e.getMessage());
        }

        for (GameData data : gameData.values()) {
            if (data.getPlayer_stats() != null) {
                for (String uuid : data.getPlayer_stats().keySet()) {
                    if (!eloRatings.containsKey(uuid)) {
                        PlayerEloData playerData = new PlayerEloData();

                        // Initialize ELO for all modes
                        for (GameMode mode : GameMode.values()) {
                            String modeName = mode.getModeName();
                            double initialElo = legacyTopPlayers.contains(uuid) ? Config.LEGACY_TOP_INITIAL_ELO : Config.INITIAL_ELO;
                            playerData.getElo().put(modeName, initialElo);
                            playerData.getGamesPlayed().put(modeName, 0);
                        }

                        // Set global ELO if available
                        if (globalElos.containsKey(uuid)) {
                            playerData.getElo().put("global", globalElos.get(uuid));
                        }

                        eloRatings.put(uuid, playerData);
                    }
                }
            }
        }

        System.out.println("Initialized ELO ratings for " + eloRatings.size() + " players");
        return eloRatings;
    }

    // getKFactorForMode now provided by GameModeUtils (static import)

    /**
     * Loads global ELOs from the global leaderboard file.
     * @return map of player UUIDs to global ELOs
     */

    private Map<String, Double> loadGlobalElos() {
        Map<String, Double> globalElos = new HashMap<>();
        try {
            java.io.File file = new java.io.File("data/output/leaderboards_java/global_leaderboard.json");
            if (file.exists()) {
                Gson gson = new Gson();
                java.io.FileReader reader = new java.io.FileReader(file);
                TypeToken<List<Map<String, Object>>> listType = new TypeToken<List<Map<String, Object>>>() {};
                List<Map<String, Object>> list = gson.fromJson(reader, listType.getType());
                for (Map<String, Object> map : list) {
                    String uuid = (String) map.get("uuid");
                    Object eloObj = map.get("globalElo");
                    double elo = ((Number) eloObj).doubleValue();
                    globalElos.put(uuid, elo);
                }
                reader.close();
            }
        } catch (Exception e) {
            System.out.println("Could not load global ELOs: " + e.getMessage());
        }
        return globalElos;
    }

    /**
     * Formats the final results into PlayerResult objects.
     * @param eloRatings the calculated ELO ratings
     * @param playerNames map of player names
     * @param invalidGamesByPlayer invalid games tracking
     * @return formatted results
     */
    private Map<String, PlayerResult> formatResults(Map<String, PlayerEloData> eloRatings, Map<String, String> playerNames, Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer) {
        Map<String, PlayerResult> results = new HashMap<>();

        for (String uuid : eloRatings.keySet()) {
            PlayerEloData data = eloRatings.get(uuid);
            String playerName = playerNames.getOrDefault(uuid, "Unknown");

            PlayerResult result = new PlayerResult();
            result.setName(playerName);

            for (GameMode mode : GameMode.values()) {
                String modeName = mode.getModeName();
                int games = data.getGamesPlayed().get(modeName);
                if (games > 0) {
                    ModeResult mr = new ModeResult();
                    mr.setElo(Math.round(data.getElo().get(modeName) * 10.0) / 10.0);
                    mr.setGames(games);

                    PerformanceTracker pt = data.getPerformanceStats().get(modeName);
                    mr.setAvgPerformance(pt.getCount() > 0 ?
                        Math.round((pt.getTotalScore() / pt.getCount()) * 1000.0) / 1000.0 : 1.0);
                    mr.setAvgNormalizedPerformance(pt.getCount() > 0 ?
                        Math.round((pt.getTotalNormalizedScore() / pt.getCount()) * 1000.0) / 1000.0 : 1.0);

                    GameStatistics ds = data.getDetailedStats().get(modeName);
                    mr.setBedBreaks(ds.getBedBreaks());
                    mr.setKills(ds.getKills());
                    mr.setDeaths(ds.getDeaths());
                    mr.setFinalKills(ds.getFinalKills());
                    mr.setVictories(ds.getVictories());
                    if (Config.DEBUG_MODE) {
                        System.out.println("ModeResult for " + uuid + " in " + modeName + ": victories=" + ds.getVictories() + ", kills=" + ds.getKills() + ", deaths=" + ds.getDeaths());
                    }
                    InvalidGamesData invalidGamesData = invalidGamesByPlayer.getOrDefault(uuid, new HashMap<>()).get(modeName);
                    if (invalidGamesData != null) {
                        mr.setInvalidGames(invalidGamesData.getTotal());
                        mr.setInvalidWins(invalidGamesData.getWins());
                    } else {
                        mr.setInvalidGames(0);
                        mr.setInvalidWins(0);
                    }

                    result.getModes().put(modeName, mr);
                }
            }

            // Set global ELO - use loaded value if available, otherwise calculate
            if (data.getElo().containsKey("global")) {
                result.setGlobalElo(data.getElo().get("global"));
            } else {
                double totalWeightedElo = 0.0;
                int totalGames = 0;
                for (GameMode mode : GameMode.values()) {
                    String modeName = mode.getModeName();
                    if (result.getModes().containsKey(modeName)) {
                        ModeResult mr = result.getModes().get(modeName);
                        totalWeightedElo += mr.getElo() * mr.getGames();
                        totalGames += mr.getGames();
                    }
                }
                result.setGlobalElo(totalGames > 0 ?
                    Math.round((totalWeightedElo / totalGames) * 10.0) / 10.0 : Config.INITIAL_ELO);
            }

            // Calculate adjusted global ELO with more weight to Mega games
            double adjustedWeightedElo = 0.0;
            double adjustedGames = 0.0;
            for (GameMode mode : GameMode.values()) {
                String modeName = mode.getModeName();
                if (result.getModes().containsKey(modeName)) {
                    ModeResult mr = result.getModes().get(modeName);
                    double weight = mode == GameMode.MEGA ? Config.MEGA_GLOBAL_WEIGHT : 1.0;
                    adjustedWeightedElo += mr.getElo() * mr.getGames() * weight;
                    adjustedGames += mr.getGames() * weight;
                }
            }
            result.setAdjustedGlobalElo(adjustedGames > 0 ?
                Math.round((adjustedWeightedElo / adjustedGames) * 10.0) / 10.0 : Config.INITIAL_ELO);

            results.put(uuid, result);
        }

        return results;
    }

    // detectGameMode and getModeFromLobby now provided by GameModeUtils (static import)

    /**
     * Saves invalid games data as JSON, ranked by total invalid games.
     * @param invalidGamesByPlayer the invalid games data by player and mode
     * @param playerNames the player name mappings
     * @param results the player results for calculating total games
     * @throws IOException if file cannot be written
     */
    private void saveInvalidGames(Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer,
                                   Map<String, String> playerNames,
                                   Map<String, PlayerResult> results) throws IOException {
        List<PlayerInvalidGamesEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Map<String, InvalidGamesData>> playerEntry : invalidGamesByPlayer.entrySet()) {
            String uuid = playerEntry.getKey();
            String playerName = playerNames.getOrDefault(uuid, "Unknown");
            Map<String, InvalidGamesData> modeData = playerEntry.getValue();

            int totalInvalidGames = 0;
            int totalInvalidWins = 0;
            List<String> allGameIds = new ArrayList<>();

            for (InvalidGamesData modeStats : modeData.values()) {
                totalInvalidGames += modeStats.getTotal();
                totalInvalidWins += modeStats.getWins();
                allGameIds.addAll(modeStats.getGameIds());
            }

            if (totalInvalidGames > 0) {
                PlayerInvalidGamesEntry entry = new PlayerInvalidGamesEntry();
                entry.setUuid(uuid);
                entry.setName(playerName);
                entry.setInvalidGames(totalInvalidGames);
                entry.setInvalidWins(totalInvalidWins);
                entry.setGameIds(allGameIds);

                // Calculate total legitimate games played
                PlayerResult pr = results.get(uuid);
                int totalGames = 0;
                if (pr != null) {
                    for (ModeResult mr : pr.getModes().values()) {
                        totalGames += mr.getGames();
                    }
                }
                entry.setValidGames(totalGames);
                
                // Calculate invalid percentage (invalid games out of total games including invalid)
                int totalAllGames = totalGames + totalInvalidGames;
                double invalidPct = totalAllGames > 0 ? 
                    Math.round((double) totalInvalidGames / totalAllGames * 1000.0) / 10.0 : 0.0;
                entry.setInvalidPercentage(invalidPct);

                entries.add(entry);
            }
        }

        // Sort by total invalid games descending
        entries.sort((a, b) -> Integer.compare(b.getInvalidGames(), a.getInvalidGames()));
        
        // Set ranks after sorting
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        // Save to JSON file
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String output = gson.toJson(entries);
        java.nio.file.Files.write(java.nio.file.Paths.get("data/temp/invalid_games_java.json"), output.getBytes());
        System.out.println("Saved invalid games data to data/temp/invalid_games_java.json");
    }

    /**
     * Analyzes a specific match by replaying history up to that point.
     * @param gameId the game ID to analyze
     * @param playerNames map of player UUIDs to names
     * @param matchAnalyzer the match analyzer to use
     * @throws IOException if files cannot be read
     */
    public void analyzeSpecificMatch(String gameId, Map<String, String> playerNames, 
                                     MatchAnalyzer matchAnalyzer) throws IOException {
        Map<String, GameData> allGames = dataManager.loadGameData();
        
        // Find the target game
        GameData targetGame = allGames.get(gameId);
        if (targetGame == null) {
            System.out.println("Game ID not found: " + gameId);
            return;
        }

        // Detect game mode using GameModeUtils
        String modeName = determineBestMode(targetGame);
        
        if (modeName == null) {
            System.out.println("Could not determine game mode for game: " + gameId);
            return;
        }

        System.out.println("Found game " + gameId + " in mode: " + modeName);
        System.out.println("Replaying match history to calculate accurate ELO ratings at this point...");

        // Track invalid games (needed for classification)
        Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer = new HashMap<>();

        // Load blacklisted game IDs
        Set<String> blacklistedGameIds = dataManager.loadBlacklistedGameIds();

        // Filter and classify all games
        Map<String, List<GameClassification>> gamesByMode = filterAndClassifyGames(allGames, invalidGamesByPlayer, blacklistedGameIds);

        // Initialize ELO ratings
        Map<String, PlayerEloData> eloRatings = initializeEloRatings(allGames, playerNames);

        // Find the position of our target game in the mode's game list
        List<GameClassification> modeGames = gamesByMode.get(modeName);
        int targetIndex = -1;
        for (int i = 0; i < modeGames.size(); i++) {
            if (modeGames.get(i).getGameId().equals(gameId)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            System.out.println("Game not found in valid games list (may be classified as invalid)");
            return;
        }

        double kFactor = getKFactorForMode(modeName);

        // Process all games in this mode up to (but not including) the target game
        // Use the final iteration count to get accurate ratings
        for (int iteration = 1; iteration <= Config.NUM_ITERATIONS; iteration++) {
            for (int i = 0; i < targetIndex; i++) {
                GameClassification gc = modeGames.get(i);
                Map<String, EloChange> eloChanges = eloCalculator.calculateMultiTeamEloChanges(
                    gc.getGameData(), gc.getWinnerTeam(), eloRatings, modeName, kFactor, gc.getGameId()
                );

                for (Map.Entry<String, EloChange> entry : eloChanges.entrySet()) {
                    String uuid = entry.getKey();
                    EloChange change = entry.getValue();
                    PlayerEloData data = eloRatings.get(uuid);
                    double previousElo = data.getElo().get(modeName);
                    double newElo = previousElo + change.getChange();
                    data.getElo().put(modeName, newElo);
                }
            }
        }

        System.out.println("ELO ratings calculated at match point (after " + targetIndex + " games)");
        System.out.println();

        // Now analyze the target match with the calculated ELO ratings
        matchAnalyzer.analyzeMatch(gameId, playerNames, eloRatings, modeName);
    }

}







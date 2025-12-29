package com.example.elocalculator;

import com.example.elocalculator.managers.EloCalculationManager;
import com.example.elocalculator.managers.EloCalculator;
import com.example.elocalculator.managers.GameModeUtils;
import com.example.elocalculator.managers.MatchAnalyzer;
import com.example.elocalculator.managers.OutputManager;
import com.example.elocalculator.managers.TeamBalancer;
import com.example.elocalculator.managers.DataManager;
import com.example.elocalculator.model.game.GameData;
import com.example.elocalculator.model.game.GameMode;
import com.example.elocalculator.model.result.PlayerResult;
import com.example.elocalculator.model.elo.PlayerEloData;
import com.example.elocalculator.model.elo.PlayerEloHistory;
import com.example.elocalculator.model.elo.EloHistoryEntry;
import com.example.elocalculator.config.Config;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Main application class for the Bedwars ELO Calculator.
 *
 * This version implements a zero-sum performance-based ELO rating system
 * that accounts for individual player performance within team contexts.
 *
 * Key features:
 * - Separate ELO ratings for each game mode (solo/duo/trio/fours/mega)
 * - Zero-sum performance modifiers based on bed breaks and K/D ratio
 * - Base ELO change determined by win/loss outcomes
 * - Performance modifiers applied proportionally to maintain zero-sum
 * - Mega mode seeding from best other mode ratings
 *
 * @author NullDiamond
 * @version 5.0
 */
public class Main {

    private final String dataDirectory;

    /**
     * Default constructor using "data" directory.
     */
    public Main() {
        this("data");
    }

    /**
     * Constructor with configurable data directory.
     * @param dataDirectory the directory containing data files
     */
    public Main(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Main entry point for the application.
     * @param args command line arguments (optional)
     *             If no args provided, shows interactive menu
     *             --match <json> : Process a single match JSON and update leaderboards
     *             --remove-recent <index> : Remove a recent match by index (1=last, 2=second last, etc., up to 5)
     *             --history <playerName> : Display ELO history for a specific player
     *             --summary <playerName> : Display ELO summary for a specific player
     *             --match-analysis <gameId> : Analyze detailed ELO calculations for a specific match
     *             --balance <jsonFile> : Balance teams for mega mode based on player UUIDs
     *             --clean : Remove test player entries from data files
     */
    public static void main(String[] args) {
        if (args.length >= 2 && "--match".equals(args[0])) {
            // Single match mode: process single match
            String matchJson = args[1];
            new Main().processSingleMatch(matchJson);
        } else if (args.length >= 2 && "--remove-recent".equals(args[0])) {
            // Remove recent match mode
            int index = Integer.parseInt(args[1]);
            new Main().removeRecentMatch(index);
        } else if (args.length == 1 && "--remove-last".equals(args[0])) {
            // Backward compatibility: remove last (index 1)
            new Main().removeRecentMatch(1);
        } else if (args.length >= 2 && "--history".equals(args[0])) {
            // History mode: display player ELO history
            String playerName = args[1];
            new Main().showPlayerHistory(playerName);
        } else if (args.length >= 2 && "--summary".equals(args[0])) {
            // Summary mode: display player ELO summary
            String playerName = args[1];
            new Main().showPlayerSummary(playerName);
        } else if (args.length >= 2 && "--match-analysis".equals(args[0])) {
            // Match analysis mode: show detailed ELO calculations for a specific game
            String gameId = args[1];
            new Main().analyzeMatch(gameId);
        } else if (args.length >= 2 && "--balance".equals(args[0])) {
            // Team balancing mode: balance teams based on player list JSON
            String inputFile = args[1];
            new Main().balanceTeams(inputFile);
        } else if (args.length == 0) {
            // Always run interactive mode
            new Main().runInteractive();
        } else {
            // Historical mode: process all games (backward compatibility)
            new Main().run();
        }
    }

    /**
     * Runs the interactive terminal menu.
     */
    public void runInteractive() {
        InteractiveMenuHandler menuHandler = new InteractiveMenuHandler(this);
        menuHandler.runInteractive();
    }



    /**
     * Runs the complete ELO calculation process.
     */
    public void run() {
        try {
            // Hard reset: clear any leftover data from previous executions
            DataManager dataManager = new DataManager(dataDirectory);
            System.out.println("Performing hard reset - clearing previous execution data...");
            dataManager.saveProcessedGameIds(new java.util.HashSet<>()); // Clear processed live game IDs
            dataManager.saveRecentSnapshots(new java.util.ArrayList<>()); // Clear recent snapshots
            dataManager.saveRemovableGameIds(new java.util.ArrayList<>()); // Clear removable games list
            dataManager.saveLast5Games(new java.util.ArrayList<>()); // Clear last 5 games data
            dataManager.saveProcessedMatches(new java.util.HashMap<>()); // Clear processed matches
            System.out.println("Hard reset completed.\n");

            EloCalculationManager calculationManager = new EloCalculationManager(dataDirectory);
            OutputManager outputManager = new OutputManager(dataDirectory);

            System.out.println("Loading player names...");
            Map<String, String> playerNames = calculationManager.loadPlayerNames();

            System.out.println("Loaded " + playerNames.size() + " players");

            System.out.println("\nCalculating ELO ratings with zero-sum performance modifiers...");
            Map<String, PlayerResult> results = calculationManager.processEloCalculations(playerNames);

            outputManager.printStatistics(results);

            for (GameMode mode : GameMode.values()) {
                String modeName = mode.getModeName();
                // Check if mode has any results
                boolean hasModeResults = results.values().stream()
                    .anyMatch(pr -> pr.getModes().containsKey(modeName));
                if (hasModeResults) {
                    outputManager.printTopPlayers(results, modeName, Config.TOP_PLAYERS_DISPLAY);
                }
            }

            outputManager.printTopGlobalPlayers(results, Config.TOP_PLAYERS_DISPLAY);

            outputManager.saveLeaderboards(results);
            outputManager.saveResults(results);
            // Note: invalid games tracking is now handled internally by HistoricalDataProcessor

            // Update last 5 games data for removal functionality
            updateLast5GamesFromHistory();

            System.out.println("\nDone!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processes a single match and updates leaderboards.
     * @param matchJson JSON string containing a single match
     */
    public void processSingleMatch(String matchJson) {
        try {
            EloCalculationManager calculationManager = new EloCalculationManager();

            System.out.println("Loading player names...");
            Map<String, String> playerNames = calculationManager.loadPlayerNames();

            System.out.println("Loading processed game IDs...");
            Set<String> processedIds = calculationManager.loadProcessedGameIds();

            System.out.println("Loading blacklisted game IDs...");
            Set<String> blacklistedIds = calculationManager.loadBlacklistedGameIds();

            System.out.println("Processing single match...");
            boolean processed = calculationManager.processMatch(matchJson, playerNames, processedIds, blacklistedIds);

            if (processed) {
                System.out.println("Match processed successfully!");
            } else {
                System.out.println("Match was not processed (duplicate, blacklisted, or invalid)");
            }

        } catch (IOException e) {
            System.err.println("Error processing live match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes a specific match by game ID.
     * @param gameId the game ID to remove
     */
    public void removeMatch(String gameId) {
        try {
            EloCalculationManager calculationManager = new EloCalculationManager();

            System.out.println("Loading player names...");
            Map<String, String> playerNames = calculationManager.loadPlayerNames();

            System.out.println("Removing match: " + gameId);
            boolean removed = calculationManager.removeMatch(gameId, playerNames);

            if (removed) {
                System.out.println("Match removed successfully!");
            } else {
                System.out.println("Match was not found or could not be removed");
            }

        } catch (IOException e) {
            System.err.println("Error removing live match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes a recent match by index (1=last, 2=second last, etc.).
     * All games (historical or live) are treated the same way.
     * @param index the index of the recent match to remove (1-5)
     */
    public void removeRecentMatch(int index) {
        removeRecentMatch(index, false);
    }

    /**
     * Removes a recent match by index (1-5) from the last 5 games.
     * All games (historical or live) are treated the same way.
     * @param index the index of the recent match to remove (1-5)
     * @param skipBlacklistPrompt if true, skip the user prompt for blacklisting (useful for testing)
     */
    public void removeRecentMatch(int index, boolean skipBlacklistPrompt) {
        try {
            EloCalculationManager calculationManager = new EloCalculationManager(dataDirectory);

            System.out.println("Loading player names...");
            Map<String, String> playerNames = calculationManager.loadPlayerNames();

            System.out.println("Getting recent match at index " + index + "...");
            String removedGameId = calculationManager.getRecentMatchGameId(index);

            if (removedGameId == null) {
                System.out.println("Could not find recent match at index " + index);
                return;
            }

            // Unified removal: use the same logic for all games (historical or live)
            // This properly restores ELOs and re-adds intermediate games
            System.out.println("Removing match: " + removedGameId);
            calculationManager.removeRecentMatch(playerNames, index);
            
            // Update the last 5 games data after removal
            updateLast5GamesFromHistory();
                
            // Ask user if they want to blacklist the removed game (skip if in test mode)
            if (!skipBlacklistPrompt) {
                System.out.print("Do you want to blacklist game ID '" + removedGameId + "' to prevent re-processing? (y/n): ");
                String confirmation = "";
                // Note: We don't close this scanner as it wraps System.in which should stay open
                @SuppressWarnings("resource")
                Scanner inputScanner = new Scanner(System.in);
                try {
                    confirmation = inputScanner.nextLine().trim().toLowerCase();
                } catch (java.util.NoSuchElementException e) {
                    // No input available, default to no
                    confirmation = "n";
                }
                
                if (confirmation.equals("y") || confirmation.equals("yes")) {
                    Set<String> blacklistedIds = calculationManager.loadBlacklistedGameIds();
                    blacklistedIds.add(removedGameId);
                    calculationManager.saveBlacklistedGameIds(blacklistedIds);
                    System.out.println("Game ID '" + removedGameId + "' has been blacklisted.");
                } else {
                    System.out.println("Game ID '" + removedGameId + "' was not blacklisted.");
                }
            } else {
                System.out.println("Skipping blacklist prompt (test mode).");
            }
            
            System.out.println("Recent match removed successfully!");

        } catch (IOException e) {
            System.err.println("Error removing match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays the ELO history for a specific player.
     * @param playerName the player's name
     */
    public void showPlayerHistory(String playerName) {
        try {
            com.example.elocalculator.managers.EloHistoryManager historyManager = 
                new com.example.elocalculator.managers.EloHistoryManager(dataDirectory);
            
            System.out.println("Loading ELO history...");
            historyManager.loadEloHistory();
            
            historyManager.printPlayerHistory(playerName);
            
        } catch (IOException e) {
            System.err.println("Error loading ELO history: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays the ELO summary for a specific player.
     * @param playerName the player's name
     */
    public void showPlayerSummary(String playerName) {
        try {
            com.example.elocalculator.managers.EloHistoryManager historyManager = 
                new com.example.elocalculator.managers.EloHistoryManager(dataDirectory);
            
            System.out.println("Loading ELO history...");
            historyManager.loadEloHistory();
            
            historyManager.printPlayerSummary(playerName);
            
        } catch (IOException e) {
            System.err.println("Error loading ELO history: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Analyzes detailed ELO calculations for a specific match.
     * Uses stored ELO history for fast lookup instead of recalculating everything.
     * @param gameId the game ID to analyze
     */
    public void analyzeMatch(String gameId) {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            EloCalculator eloCalculator = new EloCalculator();
            
            // Load player names (fast - small file)
            Map<String, String> playerNames = dataManager.loadPlayerNames();
            
            // Load ELO history (contains pre-match ELO values for each game)
            Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();
            if (eloHistory == null || eloHistory.isEmpty()) {
                System.out.println("No ELO history found. Run option 1 first to process historical data.");
                return;
            }
            
            // Load game data (fast version - doesn't update player names)
            Map<String, GameData> allGames = dataManager.loadGameDataFast();
            GameData targetGame = allGames.get(gameId);
            if (targetGame == null) {
                System.out.println("Game ID not found: " + gameId);
                return;
            }
            
            // Determine game mode
            String modeName = GameModeUtils.determineBestMode(targetGame);
            if (modeName == null) {
                System.out.println("Could not determine game mode for game: " + gameId);
                return;
            }
            
            // Build ELO ratings map from history (find pre-match ELO for each player in this game)
            Map<String, PlayerEloData> eloRatings = buildEloRatingsFromHistory(
                targetGame, gameId, modeName, eloHistory, playerNames);
            
            if (eloRatings == null) {
                System.out.println("Could not find ELO history for this game. It may not have been processed yet.");
                return;
            }
            
            // Now run the detailed analysis
            MatchAnalyzer matchAnalyzer = new MatchAnalyzer(dataManager, eloCalculator);
            matchAnalyzer.analyzeMatchWithGameData(gameId, playerNames, eloRatings, modeName, targetGame);
            
        } catch (IOException e) {
            System.err.println("Error analyzing match: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Builds ELO ratings map from stored history for a specific game.
     * Extracts the pre-match ELO values from the history entries.
     */
    private Map<String, PlayerEloData> buildEloRatingsFromHistory(
            GameData targetGame, String gameId, String modeName,
            Map<String, PlayerEloHistory> eloHistory, Map<String, String> playerNames) {
        
        Map<String, PlayerEloData> eloRatings = new HashMap<>();
        Map<String, List<String>> teams = targetGame.getTeamsAsUuids();
        
        // Get all players in this game
        Set<String> playersInGame = new java.util.HashSet<>();
        for (List<String> team : teams.values()) {
            playersInGame.addAll(team);
        }
        
        // For each player, find their ELO entry for this game and extract previousElo
        for (String uuid : playersInGame) {
            PlayerEloHistory playerHistory = eloHistory.get(uuid);
            if (playerHistory == null) {
                // Player not in history - use default starting ELO
                PlayerEloData data = new PlayerEloData();
                data.getElo().put(modeName, Config.INITIAL_ELO);
                eloRatings.put(uuid, data);
                continue;
            }
            
            // Find the history entry for this specific game
            List<EloHistoryEntry> modeHistory = playerHistory.getHistoryForMode(modeName);
            EloHistoryEntry matchEntry = null;
            for (EloHistoryEntry entry : modeHistory) {
                if (gameId.equals(entry.getGameId())) {
                    matchEntry = entry;
                    break;
                }
            }
            
            if (matchEntry == null) {
                // Game not found in player's history - use their current ELO or default
                PlayerEloData data = new PlayerEloData();
                // Try to get their last known ELO in this mode
                double lastElo = Config.INITIAL_ELO;
                if (!modeHistory.isEmpty()) {
                    lastElo = modeHistory.get(modeHistory.size() - 1).getNewElo();
                }
                data.getElo().put(modeName, lastElo);
                eloRatings.put(uuid, data);
                continue;
            }
            
            // Use the previousElo from the history entry (ELO before this game was played)
            PlayerEloData data = new PlayerEloData();
            data.getElo().put(modeName, matchEntry.getPreviousElo());
            eloRatings.put(uuid, data);
        }
        
        return eloRatings;
    }

    /**
     * Balances teams for mega mode based on player list JSON file.
     * @param inputFilePath path to JSON file containing player UUIDs
     */
    public void balanceTeams(String inputFilePath) {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            TeamBalancer balancer = new TeamBalancer(dataManager);
            
            balancer.balanceTeams(inputFilePath);
            
        } catch (Exception e) {
            System.err.println("Error balancing teams: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Balances teams for mega mode based on a list of player names.
     * Looks up UUIDs from player names and calls the team balancer.
     * Note: Players not in player_uuid_map.json cannot be included since we need their UUID.
     * For new players, use option 8a with a JSON file containing their UUIDs.
     * @param playerNames list of player names to balance
     */
    public void balanceTeamsFromNames(List<String> playerNames) {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            
            // Load name to UUID mapping (reverse of uuid to name)
            Map<String, String> uuidToName = dataManager.loadPlayerNames();
            Map<String, String> nameToUuid = new HashMap<>();
            for (Map.Entry<String, String> entry : uuidToName.entrySet()) {
                nameToUuid.put(entry.getValue().toLowerCase(), entry.getKey());
            }
            
            // Convert names to UUIDs
            List<String> playerUuids = new ArrayList<>();
            List<String> notFoundNames = new ArrayList<>();
            
            for (String name : playerNames) {
                String uuid = nameToUuid.get(name.toLowerCase());
                if (uuid != null) {
                    playerUuids.add(uuid);
                } else {
                    notFoundNames.add(name);
                }
            }
            
            if (!notFoundNames.isEmpty()) {
                System.out.println("\nWARNING: The following players are not in the system and will be EXCLUDED:");
                for (String name : notFoundNames) {
                    System.out.println("  - " + name);
                }
                System.out.println("\nNote: To include new players, use option 8a with a JSON file containing their UUIDs.");
                System.out.println("      New players will then be assigned default ELO (" + Config.INITIAL_ELO + ").");
            }
            
            if (playerUuids.size() < 2) {
                System.out.println("Error: Need at least 2 known players to balance teams.");
                return;
            }
            
            // Create TeamBalancer and balance with UUIDs
            TeamBalancer balancer = new TeamBalancer(dataManager);
            balancer.balanceTeamsFromUuids(playerUuids);
            
        } catch (Exception e) {
            System.err.println("Error balancing teams: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays the last 5 games played overall, showing ELO changes for all players in each game.
     */
    public void viewLast5GamesElo() {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            
            // Always rebuild from current ELO history to ensure fresh data
            Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();
            if (eloHistory == null || eloHistory.isEmpty()) {
                System.out.println("No ELO history data found.");
                return;
            }
            
            // Create a map from UUID to player name for quick lookup
            // First, load names from player_uuid_map.json
            Map<String, String> uuidToName = new java.util.HashMap<>();
            try {
                Map<String, String> playerNamesFromMap = dataManager.loadPlayerNames();
                if (playerNamesFromMap != null) {
                    uuidToName.putAll(playerNamesFromMap);
                }
            } catch (Exception e) {
                // Ignore if file doesn't exist or can't be read
            }
            
            // Then overlay with names from ELO history (in case ELO history has more recent names)
            for (PlayerEloHistory playerHistory : eloHistory.values()) {
                String name = playerHistory.getPlayerName();
                if (name != null && !name.equals("Unknown")) {
                    uuidToName.put(playerHistory.getPlayerUuid(), name);
                }
            }
            
            // Build last5Games from history
            List<Map<String, Object>> last5Games = buildLast5GamesFromHistory(eloHistory, uuidToName);
            
            // Save for future use
            dataManager.saveLast5Games(last5Games);
            
            if (last5Games.isEmpty()) {
                System.out.println("No games found.");
                return;
            }
            
            System.out.println("\n" + "=".repeat(120));
            System.out.println("LAST 5 GAMES (RECENT MATCHES - REMOVABLE)");
            System.out.println("=".repeat(120));
            
            int gameNumber = 1;
            for (Map<String, Object> gameData : last5Games) {
                String gameId = (String) gameData.get("gameId");
                System.out.println("\nGame " + gameNumber + ": " + gameId);
                System.out.println("-".repeat(80));
                System.out.println(String.format("%-25s %-10s %-15s %-10s %-10s", 
                    "Player Name", "Mode", "Previous ELO", "New ELO", "Change"));
                System.out.println("-".repeat(80));
                
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> playerChanges = (Map<String, Map<String, Object>>) gameData.get("playerChanges");
                
                // Sort by player name
                List<Map.Entry<String, Map<String, Object>>> sortedPlayers = new java.util.ArrayList<>(playerChanges.entrySet());
                sortedPlayers.sort((a, b) -> {
                    String nameA = uuidToName.getOrDefault(a.getKey(), "Unknown");
                    String nameB = uuidToName.getOrDefault(b.getKey(), "Unknown");
                    return nameA.compareTo(nameB);
                });
                
                for (Map.Entry<String, Map<String, Object>> entry : sortedPlayers) {
                    String playerUuid = entry.getKey();
                    Map<String, Object> changeData = entry.getValue();
                    
                    String playerName = uuidToName.getOrDefault(playerUuid, "Unknown (" + playerUuid.substring(0, Math.min(8, playerUuid.length())) + ")");
                    String mode = (String) changeData.get("gameMode");
                    double previousElo = ((Number) changeData.get("previousElo")).doubleValue();
                    double newElo = ((Number) changeData.get("newElo")).doubleValue();
                    double eloChange = ((Number) changeData.get("eloChange")).doubleValue();
                    
                    System.out.println(String.format("%-25s %-10s %-15.1f %-10.1f %+10.1f", 
                        playerName, 
                        mode.toUpperCase(), 
                        previousElo, 
                        newElo, 
                        eloChange));
                }
                
                gameNumber++;
            }
            
            System.out.println("\nNote: Shows the last 5 games (recent matches that can be removed), with ELO changes for all players in each game.");
            
        } catch (Exception e) {
            System.err.println("Error viewing last 5 games: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Displays the current games available for removal.
     */
    public void displayGamesForRemoval() {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            
            // Load ELO history to get the last 5 processed games
            Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();
            if (eloHistory == null || eloHistory.isEmpty()) {
                System.out.println("No recent matches available for removal.");
                return;
            }
            
            // Get the last 5 unique game IDs from ELO history (most recent first)
            List<String> removableGames = getLast5GameIdsFromHistory(eloHistory);
            
            if (!removableGames.isEmpty()) {
                System.out.println("\n" + "=".repeat(80));
                System.out.println("RECENT MATCHES (REMOVABLE)");
                System.out.println("=".repeat(80));
                
                // Display removable games (most recent first)
                for (int i = 0; i < removableGames.size(); i++) {
                    String gameId = removableGames.get(i);
                    System.out.println((i + 1) + ": " + gameId);
                }
                
                System.out.println("\nSelect the number of the recent match you want to remove.");
                return;
            }
            
            // If no removable games, show message
            System.out.println("No recent matches available for removal.");
            
        } catch (Exception e) {
            System.err.println("Error displaying games for removal: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Updates the last 5 games data from ELO history for removal functionality.
     */
    private void updateLast5GamesFromHistory() {
        try {
            DataManager dataManager = new DataManager(dataDirectory);
            
            // Load current ELO history
            Map<String, PlayerEloHistory> eloHistory = dataManager.loadEloHistory();
            if (eloHistory == null || eloHistory.isEmpty()) {
                return;
            }
            
            // Create a map from UUID to player name for quick lookup
            Map<String, String> uuidToName = new java.util.HashMap<>();
            try {
                Map<String, String> playerNamesFromMap = dataManager.loadPlayerNames();
                if (playerNamesFromMap != null) {
                    uuidToName.putAll(playerNamesFromMap);
                }
            } catch (Exception e) {
                // Ignore if file doesn't exist or can't be read
            }
            
            // Then overlay with names from ELO history
            for (PlayerEloHistory playerHistory : eloHistory.values()) {
                String name = playerHistory.getPlayerName();
                if (name != null && !name.equals("Unknown")) {
                    uuidToName.put(playerHistory.getPlayerUuid(), name);
                }
            }
            
            // Build last5Games from history
            List<Map<String, Object>> last5Games = buildLast5GamesFromHistory(eloHistory, uuidToName);
            
            // Save for future use
            dataManager.saveLast5Games(last5Games);
            
        } catch (Exception e) {
            System.err.println("Error updating last 5 games: " + e.getMessage());
        }
    }
    
    /**
     * Builds the last 5 games data from ELO history.
     */
    private List<Map<String, Object>> buildLast5GamesFromHistory(Map<String, PlayerEloHistory> eloHistory, Map<String, String> uuidToName) {
        // Collect all EloHistoryEntry with their player UUIDs
        List<java.util.AbstractMap.SimpleEntry<String, EloHistoryEntry>> allEntriesWithUuid = new java.util.ArrayList<>();
        for (Map.Entry<String, PlayerEloHistory> histEntry : eloHistory.entrySet()) {
            String uuid = histEntry.getKey();
            PlayerEloHistory playerHistory = histEntry.getValue();
            for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega"}) {
                List<EloHistoryEntry> modeEntries = playerHistory.getHistoryForMode(mode);
                for (EloHistoryEntry entry : modeEntries) {
                    allEntriesWithUuid.add(new java.util.AbstractMap.SimpleEntry<>(uuid, entry));
                }
            }
        }
        
        // Sort by timestamp descending (most recent first)
        allEntriesWithUuid.sort((a, b) -> Long.compare(b.getValue().getTimestamp(), a.getValue().getTimestamp()));
        
        // Debug: print top 10 entries (only in debug mode)
        if (Config.DEBUG_MODE) {
            System.out.println("Top 10 entries by timestamp:");
            for (int i = 0; i < Math.min(10, allEntriesWithUuid.size()); i++) {
                var pair = allEntriesWithUuid.get(i);
                System.out.println(pair.getValue().getGameId() + " " + pair.getValue().getTimestamp());
            }
        }
        
        // Get unique game IDs for the last 5 games
        Set<String> last5GameIds = new java.util.LinkedHashSet<>();
        for (java.util.AbstractMap.SimpleEntry<String, EloHistoryEntry> pair : allEntriesWithUuid) {
            last5GameIds.add(pair.getValue().getGameId());
            if (last5GameIds.size() >= 5) break;
        }
        
        // Debug: print last5GameIds (only in debug mode)
        if (Config.DEBUG_MODE) {
            System.out.println("Last 5 game IDs: " + last5GameIds);
        }
        
        // Build the data structure
        List<Map<String, Object>> last5Games = new java.util.ArrayList<>();
        for (String gameId : last5GameIds) {
            Map<String, Object> gameData = new java.util.HashMap<>();
            gameData.put("gameId", gameId);
            
            Map<String, Map<String, Object>> playerChanges = new java.util.HashMap<>();
            for (java.util.AbstractMap.SimpleEntry<String, EloHistoryEntry> pair : allEntriesWithUuid) {
                if (pair.getValue().getGameId().equals(gameId)) {
                    String uuid = pair.getKey();
                    EloHistoryEntry entry = pair.getValue();
                    
                    Map<String, Object> changeData = new java.util.HashMap<>();
                    changeData.put("gameMode", entry.getGameMode());
                    changeData.put("previousElo", entry.getPreviousElo());
                    changeData.put("newElo", entry.getNewElo());
                    changeData.put("eloChange", entry.getEloChange());
                    
                    playerChanges.put(uuid, changeData);
                    
                    // Debug: print ELO transition for this player in this game (only in debug mode)
                    if (Config.DEBUG_MODE) {
                        String playerName = uuidToName.getOrDefault(uuid, "Unknown (" + uuid.substring(0, Math.min(8, uuid.length())) + ")");
                        System.out.println(String.format("DEBUG: Game %s, Player %s (%s): %.1f -> %.1f (%.1f)", 
                            gameId, playerName, entry.getGameMode(), entry.getPreviousElo(), entry.getNewElo(), entry.getEloChange()));
                    }
                }
            }
            
            gameData.put("playerChanges", playerChanges);
            last5Games.add(gameData);
        }
        
        return last5Games;
    }
    
    /**
     * Gets the last 5 unique game IDs from ELO history (most recent first).
     */
    private List<String> getLast5GameIdsFromHistory(Map<String, PlayerEloHistory> eloHistory) {
        // Collect all EloHistoryEntry
        List<EloHistoryEntry> allEntries = new java.util.ArrayList<>();
        for (PlayerEloHistory playerHistory : eloHistory.values()) {
            for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega"}) {
                allEntries.addAll(playerHistory.getHistoryForMode(mode));
            }
        }
        
        // Sort by timestamp descending (most recent first)
        allEntries.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        
        // Get unique game IDs for the last 5 games
        Set<String> last5GameIds = new java.util.LinkedHashSet<>();
        for (EloHistoryEntry entry : allEntries) {
            last5GameIds.add(entry.getGameId());
            if (last5GameIds.size() >= 5) break;
        }
        
        return new java.util.ArrayList<>(last5GameIds);
    }
}
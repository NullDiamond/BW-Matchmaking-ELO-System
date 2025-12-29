package com.example.elocalculator.managers;

import com.example.elocalculator.model.game.GameData;
import com.example.elocalculator.model.player.PlayerIdentifier;
import com.example.elocalculator.model.result.PlayerResult;
import com.example.elocalculator.model.result.ModeResult;
import com.example.elocalculator.model.leaderboard.LeaderboardEntry;
import com.example.elocalculator.model.leaderboard.GlobalLeaderboardEntry;
import com.example.elocalculator.model.elo.PlayerEloHistory;
import com.example.elocalculator.model.elo.EloRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manager class responsible for all data loading and saving operations.
 */
public class DataManager {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String dataDirectory;

    /**
     * Constructor with configurable data directory.
     * @param dataDirectory the directory containing data files
     */
    public DataManager(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Gets the data directory path.
     * @return the data directory path
     */
    public String getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Loads game data from the JSON file.
     * @return Map of game data
     * @throws IOException if file cannot be read
     */
    public Map<String, GameData> loadGameData() throws IOException {
        Map<String, GameData> gameData = loadRawGameData();
        updatePlayerNamesFromGameData(gameData);
        return gameData;
    }

    /**
     * Loads game data from the JSON file without updating player names.
     * Faster than loadGameData() when player names are already up to date.
     * @return Map of game data
     * @throws IOException if file cannot be read
     */
    public Map<String, GameData> loadGameDataFast() throws IOException {
        return loadRawGameData();
    }

    /**
     * Loads game data from the scraped_games.json file.
     * @return Map of game IDs to GameData
     * @throws IOException if file cannot be read
     */
    private Map<String, GameData> loadRawGameData() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(dataDirectory, "input/scraped_games.json")));
        Map<String, GameData> gameData = gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, GameData>>(){}.getType());
        return gameData;
    }

    /**
     * Updates player_uuid_map.json with names found in game data.
     * @param gameData the game data containing player names
     * @throws IOException if file operations fail
     */
    private void updatePlayerNamesFromGameData(Map<String, GameData> gameData) throws IOException {
        // Load existing player names
        Map<String, String> existingNames = new HashMap<>();
        File playerMapFile = Paths.get(dataDirectory, "config/player_uuid_map.json").toFile();
        if (playerMapFile.exists()) {
            String content = new String(Files.readAllBytes(playerMapFile.toPath()));
            existingNames = gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
            if (existingNames == null) {
                existingNames = new HashMap<>();
            }
        }

        // Extract names from game data
        boolean updated = false;
        for (GameData game : gameData.values()) {
            if (game.getTeams() != null) {
                for (List<PlayerIdentifier> team : game.getTeams().values()) {
                    for (PlayerIdentifier player : team) {
                        String uuid = player.getUuid();
                        String name = player.getName();

                        // Strip "___" prefix if present (common in live match data)
                        if (name != null && name.startsWith("___")) {
                            name = name.substring(3);
                        }

                        // Update if name is different or new
                        if (name != null && !name.trim().isEmpty()) {
                            String existingName = existingNames.get(uuid);
                            if (!name.equals(existingName)) {
                                existingNames.put(uuid, name);
                                updated = true;
                            }
                        }
                    }
                }
            }
        }

        // Save updated names if any changes were made
        if (updated) {
            savePlayerNames(existingNames);
        }
    }



    /**
     * Loads player name mappings from the JSON file.
     * @return Map of player UUIDs to names
     * @throws IOException if file cannot be read
     */
    public Map<String, String> loadPlayerNames() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(dataDirectory, "config/player_uuid_map.json")));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
    }

    /**
     * Loads the set of legacy top player UUIDs.
     * @return Set of legacy top player UUIDs
     * @throws IOException if file cannot be read
     */
    public Set<String> loadLegacyTopPlayers() throws IOException {
        File file = Paths.get(dataDirectory, "config/legacy_top_players.json").toFile();
        if (!file.exists()) {
            return new HashSet<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Set<String>>(){}.getType());
    }

    /**
     * Loads the set of processed game IDs.
     * @return Set of processed game IDs
     * @throws IOException if file cannot be read
     */
    public Set<String> loadProcessedGameIds() throws IOException {
        File file = Paths.get(dataDirectory, "temp/processed_game_ids.json").toFile();
        if (!file.exists()) {
            return new HashSet<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Set<String>>(){}.getType());
    }

    /**
     * Saves the set of processed game IDs.
     * @param processedIds the set of processed game IDs
     * @throws IOException if file cannot be written
     */
    public void saveProcessedGameIds(Set<String> processedIds) throws IOException {
        String output = gson.toJson(processedIds);
        Files.write(Paths.get(dataDirectory, "temp/processed_game_ids.json"), output.getBytes());
    }

    /**
     * Loads the set of blacklisted game IDs.
     * @return Set of blacklisted game IDs
     * @throws IOException if file cannot be read
     */
    public Set<String> loadBlacklistedGameIds() throws IOException {
        File file = Paths.get(dataDirectory, "config/blacklisted_game_ids.json").toFile();
        if (!file.exists()) {
            return new HashSet<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Set<String>>(){}.getType());
    }

    /**
     * Saves the set of blacklisted game IDs.
     * @param blacklistedIds the set of blacklisted game IDs
     * @throws IOException if file cannot be written
     */
    public void saveBlacklistedGameIds(Set<String> blacklistedIds) throws IOException {
        String output = gson.toJson(blacklistedIds);
        Files.write(Paths.get(dataDirectory, "config/blacklisted_game_ids.json"), output.getBytes());
    }

    /**
     * Loads recent ELO snapshots for rollback.
     * @return List of recent snapshots, each containing gameId and eloSnapshot
     * @throws IOException if file cannot be read
     */
    public List<Map<String, Object>> loadRecentSnapshots() throws IOException {
        File file = Paths.get(dataDirectory, "temp/recent_snapshots.json").toFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    /**
     * Saves recent ELO snapshots for rollback.
     * @param snapshots list of recent snapshots
     * @throws IOException if file cannot be written
     */
    public void saveRecentSnapshots(List<Map<String, Object>> snapshots) throws IOException {
        String output = gson.toJson(snapshots);
        Files.write(Paths.get(dataDirectory, "temp/recent_snapshots.json"), output.getBytes());
    }

    /**
     * Loads the list of removable game IDs.
     * @return list of game IDs that can be removed
     * @throws IOException if file cannot be read
     */
    public List<String> loadRemovableGameIds() throws IOException {
        File file = Paths.get(dataDirectory, "temp/removable_games.json").toFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
    }

    /**
     * Saves the list of removable game IDs.
     * @param gameIds list of game IDs that can be removed
     * @throws IOException if file cannot be written
     */
    public void saveRemovableGameIds(List<String> gameIds) throws IOException {
        String output = gson.toJson(gameIds);
        Files.write(Paths.get(dataDirectory, "temp/removable_games.json"), output.getBytes());
    }

    /**
     * Loads processed matches data.
     * @return Map of gameId to matchJson
     * @throws IOException if file cannot be read
     */
    public Map<String, String> loadProcessedMatches() throws IOException {
        File file = Paths.get(dataDirectory, "temp/processed_matches.json").toFile();
        if (!file.exists()) {
            return new HashMap<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
    }

    /**
     * Saves processed matches data.
     * @param processedMatches Map of gameId to matchJson
     * @throws IOException if file cannot be written
     */
    public void saveProcessedMatches(Map<String, String> processedMatches) throws IOException {
        String output = gson.toJson(processedMatches);
        Files.write(Paths.get(dataDirectory, "temp/processed_matches.json"), output.getBytes());
    }

    /**
     * Loads historical player results from the JSON file.
     * @return Map of historical player results, or empty map if file doesn't exist
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerResult> loadHistoricalResults() throws IOException {
        File file = Paths.get(dataDirectory, "output/zero_sum_elo_java.json").toFile();
        if (!file.exists()) {
            return new HashMap<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, PlayerResult>>(){}.getType());
    }

    /**
     * Saves historical player results to the JSON file.
     * @param historicalResults the historical player results to save
     * @throws IOException if file cannot be written
     */
    public void saveHistoricalResults(Map<String, PlayerResult> historicalResults) throws IOException {
        String output = gson.toJson(historicalResults);
        Files.write(Paths.get(dataDirectory, "output/zero_sum_elo_java.json"), output.getBytes());
    }

    /**
     * Saves player name mappings to the JSON file.
     * @param playerNames the player name mappings
     * @throws IOException if file cannot be written
     */
    public void savePlayerNames(Map<String, String> playerNames) throws IOException {
        String output = gson.toJson(playerNames);
        Files.write(Paths.get(dataDirectory, "config/player_uuid_map.json"), output.getBytes());
    }

    /**
     * Loads the live mega leaderboard. If it doesn't exist, copies from the historical mega leaderboard.
     * @return Map of player results for live mega leaderboard
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerResult> loadLiveMegaLeaderboard() throws IOException {
        File liveFile = Paths.get(dataDirectory, "temp/live_mega_leaderboard.json").toFile();
        if (liveFile.exists()) {
            String content = new String(Files.readAllBytes(liveFile.toPath()));
            
            // Try to parse as Map first (new format)
            try {
                return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, PlayerResult>>(){}.getType());
            } catch (Exception e) {
                // If that fails, try parsing as List (old format) and convert
                try {
                    List<GlobalLeaderboardEntry> leaderboard = gson.fromJson(content, new TypeToken<List<GlobalLeaderboardEntry>>(){}.getType());
                    Map<String, PlayerResult> liveResults = new HashMap<>();
                    for (GlobalLeaderboardEntry entry : leaderboard) {
                        PlayerResult pr = new PlayerResult();
                        pr.setUuid(entry.getUuid());
                        pr.setName(entry.getName());
                        
                        ModeResult mr = new ModeResult();
                        if (entry.getModes().containsKey("mega")) {
                            EloRecord er = entry.getModes().get("mega");
                            mr.setElo(er.getElo());
                            mr.setGames(er.getGames());
                        } else {
                            mr.setElo(entry.getGlobalElo());
                            mr.setGames(entry.getTotalGames());
                        }
                        // Set default values for other fields
                        mr.setAvgPerformance(1.0);
                        mr.setAvgNormalizedPerformance(1.0);
                        mr.setVictories(0);
                        mr.setBedBreaks(0);
                        mr.setKills(0);
                        mr.setDeaths(0);
                        mr.setFinalKills(0);
                        mr.setInvalidGames(0);
                        
                        pr.getModes().put("mega", mr);
                        pr.setAdjustedGlobalElo(entry.getGlobalElo());
                        liveResults.put(entry.getUuid(), pr);
                    }
                    return liveResults;
                } catch (Exception e2) {
                    System.err.println("Failed to parse live_mega_leaderboard.json: " + e2.getMessage());
                    return new HashMap<>();
                }
            }
        } else {
            // Load from historical mega leaderboard
            File megaFile = Paths.get(dataDirectory, "output", "leaderboards_java", "mega_leaderboard.json").toFile();
            if (megaFile.exists()) {
                String content = new String(Files.readAllBytes(megaFile.toPath()));
                List<LeaderboardEntry> megaLeaderboard = gson.fromJson(content, new TypeToken<List<LeaderboardEntry>>(){}.getType());
                
                // Convert to PlayerResult format
                Map<String, PlayerResult> liveResults = new HashMap<>();
                for (LeaderboardEntry entry : megaLeaderboard) {
                    PlayerResult pr = new PlayerResult();
                    pr.setUuid(entry.getUuid());
                    pr.setName(entry.getName());
                    
                    ModeResult mr = new ModeResult();
                    mr.setElo(entry.getElo());
                    mr.setGames(entry.getGames());
                    mr.setAvgPerformance(entry.getAvgPerformance());
                    mr.setAvgNormalizedPerformance(entry.getAvgNormalizedPerformance());
                    mr.setVictories(entry.getVictories());
                    mr.setBedBreaks(entry.getBedBreaks());
                    mr.setKills(entry.getKills());
                    mr.setDeaths(entry.getDeaths());
                    mr.setFinalKills(entry.getFinalKills());
                    mr.setInvalidGames(entry.getInvalidGames());
                    
                    pr.getModes().put("mega", mr);
                    liveResults.put(entry.getUuid(), pr);
                }
                
                // Save as live leaderboard
                saveLiveMegaLeaderboard(liveResults);
                return liveResults;
            } else {
                return new HashMap<>();
            }
        }
    }

    /**
     * Saves the live mega leaderboard.
     * @param results the player results to save
     * @throws IOException if file cannot be written
     */
    public void saveLiveMegaLeaderboard(Map<String, PlayerResult> results) throws IOException {
        String output = gson.toJson(results);
        Files.write(Paths.get(dataDirectory, "live_mega_leaderboard.json"), output.getBytes());
    }

    /**
     * Loads ELO history from the JSON file.
     * @return Map of player ELO histories, or empty map if file doesn't exist
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerEloHistory> loadEloHistory() throws IOException {
        File file = Paths.get(dataDirectory, "temp/elo_history.json").toFile();
        if (!file.exists()) {
            return new HashMap<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        
        // Try to parse as Map first (new format)
        try {
            return gson.fromJson(content, new com.google.gson.reflect.TypeToken<Map<String, PlayerEloHistory>>(){}.getType());
        } catch (Exception e) {
            // If that fails, try parsing as List (old format) and convert
            try {
                List<PlayerEloHistory> historyList = gson.fromJson(content, new TypeToken<List<PlayerEloHistory>>(){}.getType());
                Map<String, PlayerEloHistory> historyMap = new HashMap<>();
                for (PlayerEloHistory history : historyList) {
                    historyMap.put(history.getPlayerUuid(), history);
                }
                return historyMap;
            } catch (Exception e2) {
                // If both fail, return empty map
                return new HashMap<>();
            }
        }
    }

    /**
     * Saves ELO history to the JSON file.
     * @param eloHistory the ELO history to save
     * @throws IOException if file cannot be written
     */
    public void saveEloHistory(Map<String, PlayerEloHistory> eloHistory) throws IOException {
        String output = gson.toJson(eloHistory);
        Files.write(Paths.get(dataDirectory, "temp/elo_history.json"), output.getBytes());
    }

    /**
     * Loads the last 5 games data.
     * @return List of maps, each containing game data with player ELO changes
     * @throws IOException if file cannot be read
     */
    public List<Map<String, Object>> loadLast5Games() throws IOException {
        File file = Paths.get(dataDirectory, "temp/last_5_games.json").toFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        return gson.fromJson(content, new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    /**
     * Saves the last 5 games data.
     * @param last5Games list of maps containing game data
     * @throws IOException if file cannot be written
     */
    public void saveLast5Games(List<Map<String, Object>> last5Games) throws IOException {
        String output = gson.toJson(last5Games);
        Files.write(Paths.get(dataDirectory, "temp/last_5_games.json"), output.getBytes());
    }
}


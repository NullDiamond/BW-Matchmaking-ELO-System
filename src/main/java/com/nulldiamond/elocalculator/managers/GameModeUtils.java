package com.nulldiamond.elocalculator.managers;

import com.nulldiamond.elocalculator.model.game.GameData;
import com.nulldiamond.elocalculator.config.Config;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for game mode detection and validation.
 * Consolidates common game mode logic used across processors.
 */
public final class GameModeUtils {

    // In-memory batch collection for discrepancies to avoid per-game file I/O
    private static List<Map<String, Object>> discrepancyBatch = new ArrayList<>();
    private static boolean batchMode = false;

    private GameModeUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Enables batch mode for discrepancy logging.
     * When enabled, discrepancies are collected in memory instead of written per-game.
     * Call flushDiscrepancyBatch() when processing is complete to write all at once.
     */
    public static void enableDiscrepancyBatchMode() {
        batchMode = true;
        discrepancyBatch.clear();
    }

    /**
     * Flushes the discrepancy batch to disk and disables batch mode.
     * @param dataDirectory the data directory to write to
     */
    public static void flushDiscrepancyBatch(String dataDirectory) {
        if (!discrepancyBatch.isEmpty()) {
            saveDiscrepancies(dataDirectory, discrepancyBatch);
            if (Config.DEBUG_MODE) {
                System.out.println("Flushed " + discrepancyBatch.size() + " discrepancies to disk");
            }
        }
        discrepancyBatch.clear();
        batchMode = false;
    }

    /**
     * Clears any existing discrepancy file for a fresh start.
     * @param dataDirectory the data directory
     */
    public static void clearDiscrepancyFile(String dataDirectory) {
        try {
            java.nio.file.Path discrepancyFile = Paths.get(dataDirectory, "lobby_discrepancies_java.json");
            if (Files.exists(discrepancyFile)) {
                Files.delete(discrepancyFile);
            }
        } catch (IOException e) {
            if (Config.DEBUG_MODE) {
                System.out.println("Warning: Could not delete discrepancy file: " + e.getMessage());
            }
        }
    }

    /**
     * Detects the game mode from game data based on team sizes.
     * @param gameData the game data
     * @return the detected game mode (solo/duo/trio/fours/mega), or null if undetermined
     */
    public static String detectGameMode(GameData gameData) {
        if (gameData.getTeams() == null || gameData.getTeams().isEmpty()) {
            return null;
        }

        List<Integer> teamSizes = gameData.getTeams().values().stream()
            .filter(team -> !team.isEmpty())
            .map(List::size)
            .collect(Collectors.toList());

        if (teamSizes.isEmpty()) {
            return null;
        }

        // Find maximum team size
        int maxSize = teamSizes.stream().mapToInt(Integer::intValue).max().orElse(0);

        if (maxSize >= 8) return "mega";
        else if (maxSize == 1) return "solo";
        else if (maxSize == 2) return "duo";
        else if (maxSize == 3) return "trio";
        else if (maxSize == 4) return "fours";
        else return "fours"; // Fallback
    }

    /**
     * Gets the game mode from a lobby ID prefix.
     * @param lobbyId the lobby ID
     * @return the mode string (solo/duo/trio/fours/mega), or null if unknown
     */
    public static String getModeFromLobby(String lobbyId) {
        if (lobbyId == null) {
            return null;
        }
        if (lobbyId.startsWith("MEGA") || lobbyId.startsWith("BEDM")) {
            return "mega";
        } else if (lobbyId.startsWith("BEDF")) {
            return "fours";
        } else if (lobbyId.startsWith("BEDT")) {
            return "trio";
        } else if (lobbyId.startsWith("BEDD")) {
            return "duo";
        } else if (lobbyId.startsWith("BED")) {
            return "solo";
        } else {
            return null;
        }
    }

    /**
     * Checks if a game has sufficient activity to be processed.
     * Games with no bed breaks and insufficient deaths are considered inactive.
     * @param gameData the game data
     * @return true if the game has sufficient activity
     */
    public static boolean isGameActive(GameData gameData) {
        if (gameData.getPlayer_stats() == null) {
            return false;
        }
        
        int totalBedBreaks = gameData.getPlayer_stats().values().stream()
            .mapToInt(ps -> ps.getBed_breaks())
            .sum();
        int totalDeaths = gameData.getPlayer_stats().values().stream()
            .mapToInt(ps -> ps.getDeaths())
            .sum();

        return totalBedBreaks > 0 || totalDeaths >= Config.NO_BED_MINIMUM_DEATHS;
    }

    /**
     * Validates and returns the winner team from game data.
     * @param gameData the game data
     * @return the winner team name, "Tie" for ties, or null if invalid
     */
    public static String validateWinnerTeam(GameData gameData) {
        if (gameData.getWinner() == null || gameData.getWinner().isEmpty()) {
            return null;
        }
        
        if ("Tie".equals(gameData.getWinner())) {
            return "Tie";
        } else {
            return gameData.getTeams().containsKey(gameData.getWinner()) ? gameData.getWinner() : null;
        }
    }

    /**
     * Gets the K-factor for a given mode.
     * @param modeName the mode name
     * @return the K-factor for ELO calculations
     */
    public static double getKFactorForMode(String modeName) {
        switch (modeName) {
            case "solo": return Config.K_FACTOR_SOLO;
            case "duo": return Config.K_FACTOR_DUO;
            case "trio": return Config.K_FACTOR_TRIO;
            case "fours": return Config.K_FACTOR_FOURS;
            case "mega": return Config.K_FACTOR_MEGA;
            default: return Config.K_FACTOR_FOURS;
        }
    }

    /**
     * Checks if a lobby ID is from a staff or unknown lobby.
     * @param lobbyId the lobby ID
     * @return true if this is a staff or unknown lobby
     */
    public static boolean isStaffOrUnknownLobby(String lobbyId) {
        return "STAFFBED".equals(lobbyId) || "Unknown".equals(lobbyId);
    }

    /**
     * Capitalizes the first letter of a string.
     * @param str the string to capitalize
     * @return the capitalized string
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Determines the best mode to use for a game.
     * Prefers lobby-based detection, falls back to team size detection.
     * @param gameData the game data
     * @return the detected mode, or null if undetermined
     */
    public static String determineBestMode(GameData gameData) {
        String mode = getModeFromLobby(gameData.getLobby_id());
        if (mode == null) {
            mode = detectGameMode(gameData);
        }
        return mode;
    }

    /**
     * Checks if a game mode represents a mega game.
     * @param mode the mode name
     * @return true if this is a mega mode
     */
    public static boolean isMegaMode(String mode) {
        return "mega".equals(mode);
    }

    /**
     * Gets all valid mode names.
     * @return array of valid mode names
     */
    public static String[] getAllModeNames() {
        return new String[]{"solo", "duo", "trio", "fours", "mega"};
    }

    /**
     * Loads the discrepancies list from JSON file.
     * @param dataDirectory the data directory
     * @return list of game discrepancy objects
     */
    private static List<Map<String, Object>> loadDiscrepancies(String dataDirectory) {
        try {
            java.io.File file = Paths.get(dataDirectory, "lobby_discrepancies_java.json").toFile();
            if (!file.exists()) {
                return new java.util.ArrayList<>();
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            
            // Check if content is empty or whitespace
            if (content.trim().isEmpty()) {
                return new java.util.ArrayList<>();
            }
            
            // Try to parse as new format (array) first
            try {
                Gson gson = new Gson();
                List<Map<String, Object>> result = gson.fromJson(content, new TypeToken<List<Map<String, Object>>>(){}.getType());
                return result != null ? result : new java.util.ArrayList<>();
            } catch (Exception e) {
                // If that fails, try to parse as old format (object) and convert
                try {
                    Gson gson = new Gson();
                    Map<String, List<Integer>> oldFormat = gson.fromJson(content, new TypeToken<Map<String, List<Integer>>>(){}.getType());
                    
                    // Convert old format to new format
                    List<Map<String, Object>> newFormat = new java.util.ArrayList<>();
                    for (Map.Entry<String, List<Integer>> entry : oldFormat.entrySet()) {
                        String key = entry.getKey();
                        List<Integer> teamSizes = entry.getValue();
                        
                        // Try to parse the old key format: "gameId (lobbyId) - mode"
                        String gameId = key;
                        String lobbyId = "Unknown";
                        String mode = "unknown";
                        
                        if (key.contains(" (") && key.contains(") - ")) {
                            int lobbyStart = key.indexOf(" (");
                            int lobbyEnd = key.indexOf(") - ");
                            if (lobbyStart >= 0 && lobbyEnd > lobbyStart) {
                                gameId = key.substring(0, lobbyStart);
                                lobbyId = key.substring(lobbyStart + 2, lobbyEnd);
                                mode = key.substring(lobbyEnd + 3);
                            }
                        }
                        
                        Map<String, Object> newEntry = new HashMap<>();
                        newEntry.put("Game_id", gameId);
                        newEntry.put("lobby_id", lobbyId);
                        newEntry.put("calculated_size", mode);
                        newEntry.put("team_sizes", teamSizes.toString());
                        newFormat.add(newEntry);
                    }
                    
                    // Save in new format
                    saveDiscrepancies(dataDirectory, newFormat);
                    
                    return newFormat;
                } catch (Exception e2) {
                    // If both fail, return empty list
                    if (Config.DEBUG_MODE) {
                        System.out.println("Error parsing discrepancies file: " + e2.getMessage());
                    }
                    return new java.util.ArrayList<>();
                }
            }
        } catch (IOException e) {
            if (Config.DEBUG_MODE) {
                System.out.println("Error loading discrepancies: " + e.getMessage());
            }
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Saves the discrepancies list to JSON file.
     * @param dataDirectory the data directory
     * @param discrepancies list of game discrepancy objects
     */
    private static void saveDiscrepancies(String dataDirectory, List<Map<String, Object>> discrepancies) {
        try {
            Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String output = gson.toJson(discrepancies);
            Files.write(Paths.get(dataDirectory, "lobby_discrepancies_java.json"), output.getBytes());
        } catch (IOException e) {
            if (Config.DEBUG_MODE) {
                System.out.println("Error saving discrepancies: " + e.getMessage());
            }
        }
    }

    /**
     * Logs game team sizes to a JSON file.
     * 
     * @param dataDirectory the data directory to write to
     * @param gameId the game ID
     * @param lobbyId the lobby ID
     * @param mode the detected game mode
     * @param teamSizes the list of team sizes
     */
    public static void logGameTeamSizes(String dataDirectory, String gameId, String lobbyId, String mode, List<Integer> teamSizes) {
        Map<String, Object> newEntry = new HashMap<>();
        newEntry.put("game_id", gameId);
        newEntry.put("lobby_id", lobbyId);
        newEntry.put("calculated_size", mode);
        newEntry.put("team_sizes", teamSizes);

        if (batchMode) {
            // In batch mode, just collect in memory (much faster)
            discrepancyBatch.add(newEntry);
        } else {
            // Legacy mode: read/write per game (slower, for backward compatibility)
            if (Config.DEBUG_MODE) {
                System.out.println("Logging discrepancy for " + gameId + ": teamSizes=" + teamSizes);
            }
            List<Map<String, Object>> discrepancies = loadDiscrepancies(dataDirectory);
            
            // Check if this gameId already exists, if so update it
            boolean found = false;
            for (Map<String, Object> entry : discrepancies) {
                if (gameId.equals(entry.get("game_id"))) {
                    entry.put("lobby_id", lobbyId);
                    entry.put("calculated_size", mode);
                    entry.put("team_sizes", teamSizes);
                    found = true;
                    break;
                }
            }
            
            // If not found, add new entry
            if (!found) {
                discrepancies.add(newEntry);
            }
            
            saveDiscrepancies(dataDirectory, discrepancies);
        }
    }
}




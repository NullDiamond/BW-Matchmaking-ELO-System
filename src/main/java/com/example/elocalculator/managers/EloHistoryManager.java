package com.example.elocalculator.managers;

import com.example.elocalculator.model.elo.EloHistoryEntry;
import com.example.elocalculator.model.elo.PlayerEloHistory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class responsible for tracking and persisting player ELO history.
 */
public class EloHistoryManager {
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String dataDirectory;
    private Map<String, PlayerEloHistory> playerHistories;

    /**
     * Constructor with configurable data directory.
     * @param dataDirectory the directory containing data files
     */
    public EloHistoryManager(String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.playerHistories = new HashMap<>();
    }

    /**
     * Gets the player histories map.
     * @return the player histories
     */
    public Map<String, PlayerEloHistory> getPlayerHistories() {
        return playerHistories;
    }

    /**
     * Sets the player histories map.
     * @param playerHistories the player histories
     */
    public void setPlayerHistories(Map<String, PlayerEloHistory> playerHistories) {
        this.playerHistories = playerHistories;
    }/**
     * Loads ELO history from JSON file.
     * @return Map of player UUID to their ELO history
     * @throws IOException if file cannot be read
     */
    public Map<String, PlayerEloHistory> loadEloHistory() throws IOException {
        File file = Paths.get(dataDirectory, "temp/elo_history.json").toFile();
        if (!file.exists()) {
            playerHistories = new HashMap<>();
            return playerHistories;
        }
        String content = new String(Files.readAllBytes(file.toPath()));
        playerHistories = gson.fromJson(content, 
            new TypeToken<Map<String, PlayerEloHistory>>(){}.getType());
        if (playerHistories == null) {
            playerHistories = new HashMap<>();
        }
        return playerHistories;
    }

    /**
     * Saves ELO history to JSON file.
     * @throws IOException if file cannot be written
     */
    public void saveEloHistory() throws IOException {
        String output = gson.toJson(playerHistories);
        Files.write(Paths.get(dataDirectory, "temp/elo_history.json"), output.getBytes());
    }

    /**
     * Records an ELO change for a player.
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     * @param entry the ELO history entry to record
     */
    public void recordEloChange(String playerUuid, String playerName, EloHistoryEntry entry) {
        PlayerEloHistory history = playerHistories.computeIfAbsent(playerUuid, 
            k -> new PlayerEloHistory(playerUuid, playerName));
        history.setPlayerName(playerName); // Update name in case it changed
        history.addEntry(entry);
    }

    /**
     * Gets the ELO history for a specific player by UUID.
     * @param playerUuid the player's UUID
     * @return the player's ELO history, or null if not found
     */
    public PlayerEloHistory getPlayerHistory(String playerUuid) {
        return playerHistories.get(playerUuid);
    }

    /**
     * Gets the ELO history for a specific player by name (case-insensitive).
     * @param playerName the player's name
     * @return the player's ELO history, or null if not found
     */
    public PlayerEloHistory getPlayerHistoryByName(String playerName) {
        return playerHistories.values().stream()
                .filter(h -> h.getPlayerName() != null && 
                        h.getPlayerName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the most recent ELO history entry across all players.
     * @return the most recent EloHistoryEntry, or null if no history exists
     */
    public EloHistoryEntry findMostRecentEntry() {
        return playerHistories.values().stream()
                .flatMap(history -> history.getAllEntries().stream())
                .max((e1, e2) -> Long.compare(e1.getTimestamp(), e2.getTimestamp()))
                .orElse(null);
    }

    /**
     * Clears all cached history data.
     */
    public void clearCache() {
        playerHistories.clear();
    }

    /**
     * Prints the ELO history for a player in a readable format.
     * @param playerName the player's name
     */
    public void printPlayerHistory(String playerName) {
        PlayerEloHistory history = getPlayerHistoryByName(playerName);
        if (history == null) {
            System.out.println("No history found for player: " + playerName);
            return;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("ELO HISTORY FOR: " + history.getPlayerName());
        System.out.println("Player UUID: " + history.getPlayerUuid());
        System.out.println("Total Matches: " + history.getTotalMatches());
        System.out.println("=".repeat(80));

        // Print history for each mode
        for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega", "global"}) {
            List<EloHistoryEntry> modeHistory = history.getHistoryForMode(mode);
            if (!modeHistory.isEmpty()) {
                System.out.println("\n" + mode.toUpperCase() + " MODE (" + modeHistory.size() + " matches):");
                System.out.println("-".repeat(80));
                
                for (int i = 0; i < modeHistory.size(); i++) {
                    EloHistoryEntry entry = modeHistory.get(i);
                    String dateTime = java.time.Instant.ofEpochMilli(entry.getTimestamp())
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    
                    String changeStr = String.format("%+.2f", entry.getEloChange());
                    String resultStr;
                    if (entry.isTie()) {
                        resultStr = "TIE ";
                    } else {
                        resultStr = entry.isWon() ? "WIN " : "LOSS";
                    }
                    
                    System.out.printf("%3d. [%s] %s | %s | %.2f -> %.2f (%s)%n",
                            i + 1,
                            dateTime,
                            entry.getGameId(),
                            resultStr,
                            entry.getPreviousElo(),
                            entry.getNewElo(),
                            changeStr);
                }
            }
        }

        System.out.println("\n" + "=".repeat(80));
    }

    /**
     * Prints a summary of a player's ELO progression.
     * @param playerName the player's name
     */
    public void printPlayerSummary(String playerName) {
        PlayerEloHistory history = getPlayerHistoryByName(playerName);
        if (history == null) {
            System.out.println("No history found for player: " + playerName);
            return;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("ELO SUMMARY FOR: " + history.getPlayerName());
        System.out.println("=".repeat(80));

        for (String mode : new String[]{"solo", "duo", "trio", "fours", "mega", "global"}) {
            List<EloHistoryEntry> modeHistory = history.getHistoryForMode(mode);
            if (!modeHistory.isEmpty()) {
                EloHistoryEntry first = modeHistory.get(0);
                EloHistoryEntry last = modeHistory.get(modeHistory.size() - 1);
                
                double totalChange = last.getNewElo() - first.getPreviousElo();
                long wins = modeHistory.stream().filter(EloHistoryEntry::isWon).count();
                long losses = modeHistory.size() - wins;
                double winRate = (wins * 100.0) / modeHistory.size();

                System.out.printf("\n%s MODE:%n", mode.toUpperCase());
                System.out.printf("  Matches: %d (W: %d, L: %d, WR: %.1f%%)%n", 
                        modeHistory.size(), wins, losses, winRate);
                System.out.printf("  Starting ELO: %.2f%n", first.getPreviousElo());
                System.out.printf("  Current ELO:  %.2f%n", last.getNewElo());
                System.out.printf("  Total Change: %+.2f%n", totalChange);
                System.out.printf("  Peak ELO:     %.2f%n", 
                        modeHistory.stream().mapToDouble(EloHistoryEntry::getNewElo).max().orElse(0));
                System.out.printf("  Lowest ELO:   %.2f%n", 
                        modeHistory.stream().mapToDouble(EloHistoryEntry::getNewElo).min().orElse(0));
            }
        }

        System.out.println("\n" + "=".repeat(80));
    }
}


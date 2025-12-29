package com.example.elocalculator.model.elo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains the complete ELO history for a player across all game modes.
 */
public class PlayerEloHistory {
    private String playerUuid;
    private String playerName;
    private Map<String, List<EloHistoryEntry>> historyByMode;

    /**
     * Default constructor for JSON deserialization.
     */
    public PlayerEloHistory() {
        this.historyByMode = new HashMap<>();
    }

    /**
     * Constructor with player information.
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     */
    public PlayerEloHistory(String playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.historyByMode = new HashMap<>();
    }

    /**
     * Adds an ELO history entry for a specific mode.
     * @param entry the history entry to add
     */
    public void addEntry(EloHistoryEntry entry) {
        String mode = entry.getGameMode();
        historyByMode.computeIfAbsent(mode, k -> new ArrayList<>()).add(entry);
    }

    /**
     * Gets the complete history for a specific game mode.
     * @param mode the game mode
     * @return list of history entries for that mode
     */
    public List<EloHistoryEntry> getHistoryForMode(String mode) {
        return historyByMode.getOrDefault(mode, new ArrayList<>());
    }

    /**
     * Gets all history entries across all modes, sorted by timestamp.
     * @return list of all history entries
     */
    public List<EloHistoryEntry> getAllHistory() {
        return historyByMode.values().stream()
                .flatMap(List::stream)
                .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all history entries across all modes (unsorted).
     * @return list of all history entries
     */
    public List<EloHistoryEntry> getAllEntries() {
        return historyByMode.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of matches tracked.
     * @return total match count
     */
    public int getTotalMatches() {
        return historyByMode.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Gets the number of matches for a specific mode.
     * @param mode the game mode
     * @return match count for that mode
     */
    public int getMatchesForMode(String mode) {
        return historyByMode.getOrDefault(mode, new ArrayList<>()).size();
    }

    /**
     * Removes all entries for a specific game ID across all modes.
     * @param gameId the game ID to remove entries for
     * @return true if any entries were removed, false otherwise
     */
    public boolean removeEntriesByGameId(String gameId) {
        boolean removed = false;
        for (List<EloHistoryEntry> entries : historyByMode.values()) {
            removed |= entries.removeIf(entry -> gameId.equals(entry.getGameId()));
        }
        return removed;
    }

    // Getters and setters
    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Map<String, List<EloHistoryEntry>> getHistoryByMode() {
        return historyByMode;
    }

    public void setHistoryByMode(Map<String, List<EloHistoryEntry>> historyByMode) {
        this.historyByMode = historyByMode;
    }
}


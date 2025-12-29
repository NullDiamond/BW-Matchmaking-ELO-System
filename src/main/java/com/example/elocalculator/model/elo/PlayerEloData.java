package com.example.elocalculator.model.elo;

import com.example.elocalculator.model.game.GameStatistics;
import com.example.elocalculator.model.game.GameMode;
import java.util.HashMap;
import java.util.Map;
import com.example.elocalculator.config.Config;

/**
 * Contains all ELO-related data for a single player across all game modes.
 */
public class PlayerEloData {
    public final Map<String, Double> elo = new HashMap<>();
    public final Map<String, Integer> gamesPlayed = new HashMap<>();
    public final Map<String, PerformanceTracker> performanceStats = new HashMap<>();
    public final Map<String, GameStatistics> detailedStats = new HashMap<>();

    public PlayerEloData() {
        for (GameMode mode : GameMode.values()) {
            String modeName = mode.getModeName();
            elo.put(modeName, Config.INITIAL_ELO);
            gamesPlayed.put(modeName, 0);
            performanceStats.put(modeName, new PerformanceTracker());
            detailedStats.put(modeName, new GameStatistics());
        }
    }

    public Map<String, Double> getElo() {
        return elo;
    }

    public Map<String, Integer> getGamesPlayed() {
        return gamesPlayed;
    }

    public Map<String, PerformanceTracker> getPerformanceStats() {
        return performanceStats;
    }

    public Map<String, GameStatistics> getDetailedStats() {
        return detailedStats;
    }
}

package com.nulldiamond.elocalculator.core.model;

import java.util.*;

/**
 * Represents a player in the CORE ELO system.
 * Supports multiple ELO ratings per game mode (like the full version).
 */
public class CorePlayer {
    private final String id;
    private String name;
    
    // ELO ratings per game mode
    private final Map<CoreGameMode, Double> eloByMode;
    private final Map<CoreGameMode, Integer> gamesPlayedByMode;
    
    // Statistics per mode
    private final Map<CoreGameMode, CorePlayerStats> statsByMode;
    
    // Invalid games count per mode
    private final Map<CoreGameMode, Integer> invalidGamesCountByMode;

    /**
     * Creates a new player with the specified ID and initial ELO for all modes.
     * @param id unique player identifier
     * @param name player display name
     * @param initialElo starting ELO rating for all modes
     */
    public CorePlayer(String id, String name, double initialElo) {
        this.id = id;
        this.name = name;
        this.eloByMode = new EnumMap<>(CoreGameMode.class);
        this.gamesPlayedByMode = new EnumMap<>(CoreGameMode.class);
        this.statsByMode = new EnumMap<>(CoreGameMode.class);
        this.invalidGamesCountByMode = new EnumMap<>(CoreGameMode.class);
        
        // Initialize all modes with the initial ELO
        for (CoreGameMode mode : CoreGameMode.values()) {
            eloByMode.put(mode, initialElo);
            gamesPlayedByMode.put(mode, 0);
            statsByMode.put(mode, new CorePlayerStats());
            invalidGamesCountByMode.put(mode, 0);
        }
    }

    /**
     * Creates a new player with default initial ELO of 1200.
     */
    public CorePlayer(String id, String name) {
        this(id, name, 1200.0);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // ========================================================================
    // ELO BY MODE
    // ========================================================================

    /**
     * Gets the ELO rating for a specific game mode.
     * @param mode the game mode
     * @return the ELO rating for that mode
     */
    public double getElo(CoreGameMode mode) {
        return eloByMode.getOrDefault(mode, 1200.0);
    }

    /**
     * Gets the ELO rating for a specific game mode by name.
     * @param modeName the game mode name (e.g., "solo", "mega")
     * @return the ELO rating for that mode
     */
    public double getElo(String modeName) {
        return getElo(CoreGameMode.fromString(modeName));
    }

    /**
     * Gets the ELO for the default mode (SOLO).
     * For backwards compatibility.
     */
    public double getElo() {
        return getElo(CoreGameMode.SOLO);
    }

    /**
     * Sets the ELO rating for a specific game mode.
     * @param mode the game mode
     * @param elo the new ELO rating
     */
    public void setElo(CoreGameMode mode, double elo) {
        eloByMode.put(mode, elo);
    }

    /**
     * Sets the ELO rating for a specific game mode by name.
     */
    public void setElo(String modeName, double elo) {
        setElo(CoreGameMode.fromString(modeName), elo);
    }

    /**
     * Applies an ELO change to a specific game mode.
     * @param mode the game mode
     * @param change the ELO change (positive or negative)
     */
    public void applyEloChange(CoreGameMode mode, double change) {
        double currentElo = getElo(mode);
        setElo(mode, currentElo + change);
    }

    /**
     * Applies an ELO change to a specific game mode by name.
     */
    public void applyEloChange(String modeName, double change) {
        applyEloChange(CoreGameMode.fromString(modeName), change);
    }

    /**
     * Gets all ELO ratings by mode.
     * @return unmodifiable map of mode to ELO
     */
    public Map<CoreGameMode, Double> getAllElo() {
        return Collections.unmodifiableMap(eloByMode);
    }

    // ========================================================================
    // GAMES PLAYED BY MODE
    // ========================================================================

    /**
     * Gets the number of games played in a specific mode.
     * @param mode the game mode
     * @return the number of games played
     */
    public int getGamesPlayed(CoreGameMode mode) {
        return gamesPlayedByMode.getOrDefault(mode, 0);
    }

    /**
     * Gets the number of games played in a specific mode by name.
     */
    public int getGamesPlayed(String modeName) {
        return getGamesPlayed(CoreGameMode.fromString(modeName));
    }

    /**
     * Gets total games played across all modes.
     */
    public int getTotalGamesPlayed() {
        return gamesPlayedByMode.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Sets the games played for a specific mode.
     */
    public void setGamesPlayed(CoreGameMode mode, int games) {
        gamesPlayedByMode.put(mode, Math.max(0, games));
    }

    /**
     * Increments the games played counter for a specific mode.
     */
    public void incrementGamesPlayed(CoreGameMode mode) {
        int current = getGamesPlayed(mode);
        setGamesPlayed(mode, current + 1);
    }

    /**
     * Gets all games played by mode.
     */
    public Map<CoreGameMode, Integer> getAllGamesPlayed() {
        return Collections.unmodifiableMap(gamesPlayedByMode);
    }

    // ========================================================================
    // STATISTICS BY MODE
    // ========================================================================

    /**
     * Gets cumulative stats for a specific mode.
     */
    public CorePlayerStats getStats(CoreGameMode mode) {
        return statsByMode.computeIfAbsent(mode, k -> new CorePlayerStats());
    }

    /**
     * Adds game stats to the cumulative stats for a mode.
     */
    public void addStats(CoreGameMode mode, CorePlayerStats gameStats) {
        CorePlayerStats cumulative = getStats(mode);
        cumulative.setKills(cumulative.getKills() + gameStats.getKills());
        cumulative.setDeaths(cumulative.getDeaths() + gameStats.getDeaths());
        cumulative.setBedBreaks(cumulative.getBedBreaks() + gameStats.getBedBreaks());
        cumulative.setFinalKills(cumulative.getFinalKills() + gameStats.getFinalKills());
    }

    // ========================================================================
    // INVALID GAMES TRACKING
    // ========================================================================

    /**
     * Gets the number of invalid games for a specific mode.
     * @param mode the game mode
     * @return the number of invalid games
     */
    public int getInvalidGamesCount(CoreGameMode mode) {
        return invalidGamesCountByMode.getOrDefault(mode, 0);
    }

    /**
     * Gets total invalid games across all modes.
     */
    public int getTotalInvalidGamesCount() {
        return invalidGamesCountByMode.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Sets the invalid games count for a specific mode.
     */
    public void setInvalidGamesCount(CoreGameMode mode, int count) {
        invalidGamesCountByMode.put(mode, Math.max(0, count));
    }

    /**
     * Increments the invalid games counter for a specific mode.
     */
    public void incrementInvalidGamesCount(CoreGameMode mode) {
        int current = getInvalidGamesCount(mode);
        setInvalidGamesCount(mode, current + 1);
    }

    /**
     * Gets all invalid games counts by mode.
     */
    public Map<CoreGameMode, Integer> getAllInvalidGamesCounts() {
        return Collections.unmodifiableMap(invalidGamesCountByMode);
    }

    // ========================================================================
    // GLOBAL ELO (calculated from standard modes)
    // ========================================================================

    /**
     * Calculates the global ELO from standard modes (Solo, Duo, Trio, Fours).
     * Uses weighted average based on games played in each mode.
     * @return the calculated global ELO
     */
    public double getGlobalElo() {
        double totalWeightedElo = 0;
        int totalGames = 0;
        
        for (CoreGameMode mode : CoreGameMode.standardModes()) {
            int games = getGamesPlayed(mode);
            if (games > 0) {
                totalWeightedElo += getElo(mode) * games;
                totalGames += games;
            }
        }
        
        if (totalGames == 0) {
            // No games played, return average of all standard mode ELOs
            double sum = 0;
            for (CoreGameMode mode : CoreGameMode.standardModes()) {
                sum += getElo(mode);
            }
            return sum / CoreGameMode.standardModes().length;
        }
        
        return totalWeightedElo / totalGames;
    }

    /**
     * Calculates the adjusted global ELO with Mega mode weighting.
     * This calculation includes Mega mode with a configurable weight multiplier,
     * making it suitable for mega-specific team balancing and seeding.
     * 
     * @param megaWeight the weight multiplier for Mega mode games (typically 4.0)
     * @return the calculated adjusted global ELO
     */
    public double getAdjustedGlobalElo(double megaWeight) {
        double totalWeightedElo = 0.0;
        double totalWeight = 0.0;

        // Include all standard modes with regular weighting
        for (CoreGameMode mode : CoreGameMode.standardModes()) {
            int games = getGamesPlayed(mode);
            if (games > 0) {
                totalWeightedElo += getElo(mode) * games;
                totalWeight += games;
            }
        }

        // Include Mega mode with higher weight
        int megaGames = getGamesPlayed(CoreGameMode.MEGA);
        if (megaGames > 0) {
            totalWeightedElo += getElo(CoreGameMode.MEGA) * megaGames * megaWeight;
            totalWeight += megaGames * megaWeight;
        }

        if (totalWeight > 0) {
            return totalWeightedElo / totalWeight;
        }
        
        // No games played, return average of all ELOs
        double sum = 0;
        for (CoreGameMode mode : CoreGameMode.values()) {
            sum += getElo(mode);
        }
        return sum / CoreGameMode.values().length;
    }

    /**
     * Calculates the adjusted ELO for team balancing.
     * For players who have only played Mega mode, returns their Mega ELO directly.
     * For players with other modes, returns the adjusted global ELO.
     * 
     * @param megaWeight the weight multiplier for Mega mode games
     * @return the calculated balancing ELO
     */
    public double getBalancingElo(double megaWeight) {
        // Check if player has any non-Mega games
        boolean hasNonMegaGames = false;
        for (CoreGameMode mode : CoreGameMode.standardModes()) {
            if (getGamesPlayed(mode) > 0) {
                hasNonMegaGames = true;
                break;
            }
        }

        // If only Mega mode, return Mega ELO directly
        if (!hasNonMegaGames && getGamesPlayed(CoreGameMode.MEGA) > 0) {
            return getElo(CoreGameMode.MEGA);
        }

        // Otherwise, calculate adjusted global ELO
        return getAdjustedGlobalElo(megaWeight);
    }

    @Override
    public String toString() {
        return String.format("%s (%s): Global=%.1f, Games=%d", 
                name, id, getGlobalElo(), getTotalGamesPlayed());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CorePlayer other = (CorePlayer) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

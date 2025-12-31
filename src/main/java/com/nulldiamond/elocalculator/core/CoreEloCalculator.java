package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.*;

import java.util.*;

/**
 * Core ELO calculator - performs ELO calculations entirely in memory.
 * No file I/O, no JSON dependencies.
 * 
 * Supports per-mode ELO: each player has separate ELO ratings for different game modes.
 */
public class CoreEloCalculator {
    private final CoreConfig config;

    public CoreEloCalculator(CoreConfig config) {
        this.config = config;
    }

    public CoreEloCalculator() {
        this(new CoreConfig());
    }

    /**
     * Calculates ELO changes for all players in a game.
     * Uses the game's mode to determine which ELO rating to use for each player.
     * 
     * @param game the game to process
     * @param players map of player IDs to their CorePlayer objects
     * @return map of player IDs to their ELO changes
     */
    public Map<String, CoreEloChange> calculateEloChanges(CoreGame game, Map<String, CorePlayer> players) {
        Map<String, Double> totalChanges = new HashMap<>();
        Map<String, Double> performanceScores = new HashMap<>();

        // Get game mode and K-factor
        CoreGameMode mode = game.getGameMode();
        double kFactor = mode.isMega() ? config.kFactorMega : config.kFactor;

        // First pass: Calculate raw performance scores for all players
        Map<String, Double> rawPerformanceScores = new HashMap<>();
        for (Map.Entry<String, List<String>> teamEntry : game.getTeams().entrySet()) {
            List<String> teamPlayers = teamEntry.getValue();
            for (String playerId : teamPlayers) {
                double rawScore = calculatePerformanceScore(game, playerId, teamPlayers);
                rawPerformanceScores.put(playerId, rawScore);
            }
        }

        // Normalize performance scores so the average is 1.0
        double avgPerformance = rawPerformanceScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1.0);

        for (Map.Entry<String, Double> entry : rawPerformanceScores.entrySet()) {
            double normalizedScore = entry.getValue() / avgPerformance;
            performanceScores.put(entry.getKey(), normalizedScore);
        }

        // Get all teams
        Set<String> allTeams = game.getTeams().keySet();

        // Second pass: Calculate ELO changes using normalized performance scores
        for (Map.Entry<String, List<String>> teamEntry : game.getTeams().entrySet()) {
            String teamName = teamEntry.getKey();
            List<String> teamPlayers = teamEntry.getValue();

            // Determine if this team won
            boolean isWinner = game.isWinner(teamName);
            boolean isTie = game.isTie();
            double actualScore;

            if (isTie) {
                actualScore = 0.5;
            } else {
                actualScore = isWinner ? 1.0 : 0.0;
            }

            // Calculate team average ELO for this specific game mode
            double teamAvgElo = calculateTeamAverageElo(teamPlayers, players, mode);

            // Count opponents for this team
            int numOpponents = (int) allTeams.stream().filter(t -> !t.equals(teamName)).count();

            // Calculate expected score against each other team
            for (Map.Entry<String, List<String>> opponentEntry : game.getTeams().entrySet()) {
                String opponentTeam = opponentEntry.getKey();
                if (teamName.equals(opponentTeam)) continue;

                List<String> opponentPlayers = opponentEntry.getValue();
                double opponentAvgElo = calculateTeamAverageElo(opponentPlayers, players, mode);

                // Calculate expected score using ELO formula
                double eloDifference = opponentAvgElo - teamAvgElo;
                double expectedScore = calculateExpectedScore(eloDifference);

                // Apply the ELO change to each player in the team
                for (String playerId : teamPlayers) {
                    double performanceScore = performanceScores.get(playerId);

                    // Calculate the base ELO change
                    double scoreDifference = actualScore - expectedScore;

                    // Apply performance multiplier
                    double performanceMultiplier;
                    if (scoreDifference >= 0) {
                        performanceMultiplier = performanceScore;
                    } else {
                        performanceMultiplier = 1.0 / performanceScore;
                    }

                    double baseChange = kFactor * scoreDifference * performanceMultiplier;
                    double weightedChange = baseChange / numOpponents;

                    totalChanges.merge(playerId, weightedChange, Double::sum);
                }
            }
        }

        // Apply zero-sum normalization
        double sum = totalChanges.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum) > 0.0001) {
            int numPlayers = totalChanges.size();
            double correction = sum / numPlayers;
            for (String playerId : totalChanges.keySet()) {
                totalChanges.put(playerId, totalChanges.get(playerId) - correction);
            }
        }

        // Create final EloChange objects with game mode
        Map<String, CoreEloChange> eloChanges = new HashMap<>();
        for (String playerId : totalChanges.keySet()) {
            CorePlayer player = players.get(playerId);
            double previousElo = player != null ? player.getElo(mode) : config.initialElo;
            eloChanges.put(playerId, new CoreEloChange(
                    playerId,
                    mode,
                    previousElo,
                    totalChanges.get(playerId),
                    performanceScores.get(playerId)
            ));
        }

        return eloChanges;
    }

    /**
     * Calculates a performance score for a player in a game.
     */
    private double calculatePerformanceScore(CoreGame game, String playerId, List<String> teamPlayers) {
        CorePlayerStats stats = game.getPlayerStats().get(playerId);
        if (stats == null) {
            return 1.0;
        }

        boolean isMega = game.getGameMode().isMega();

        // Use mode-specific weights
        double weightBeds = isMega ? config.weightBedBreaksMega : config.weightBedBreaks;
        double weightKd = isMega ? config.weightKdMega : config.weightKd;
        double weightFinals = isMega ? config.weightFinalKillsMega : config.weightFinalKills;
        int finalCap = isMega ? config.finalKillCapMega : config.finalKillCap;

        double performanceScore = 1.0;

        // Bed breaks contribution
        performanceScore += weightBeds * stats.getBedBreaks();

        // K/D ratio - symmetric transformation around 1.0
        double playerKd = calculateKdRatio(stats.getKills(), stats.getDeaths());
        double kdTransform;
        if (playerKd >= 1.0) {
            kdTransform = playerKd - 1.0;
        } else {
            kdTransform = -(1.0 / playerKd - 1.0);
        }
        performanceScore += weightKd * kdTransform;

        // Final kills
        int finalKillsCount = Math.min(stats.getFinalKills(), finalCap);
        performanceScore += weightFinals * finalKillsCount;

        // Clamp performance score
        performanceScore = Math.max(config.minPerformanceMultiplier,
                Math.min(config.maxPerformanceMultiplier, performanceScore));

        return performanceScore;
    }

    /**
     * Calculates the average ELO rating for a team of players for a specific game mode.
     * @param teamPlayers list of player IDs
     * @param players map of all players
     * @param mode the game mode to get ELO for
     * @return the team's average ELO for the specified mode
     */
    private double calculateTeamAverageElo(List<String> teamPlayers, Map<String, CorePlayer> players, CoreGameMode mode) {
        return teamPlayers.stream()
                .mapToDouble(id -> {
                    CorePlayer player = players.get(id);
                    return player != null ? player.getElo(mode) : config.initialElo;
                })
                .average()
                .orElse(config.initialElo);
    }

    /**
     * Calculates the expected score using the ELO formula.
     */
    private double calculateExpectedScore(double eloDifference) {
        return 1.0 / (1.0 + Math.pow(10.0, eloDifference / 400.0));
    }

    /**
     * Calculates a K/D ratio with caps applied.
     */
    private double calculateKdRatio(double kills, double deaths) {
        double kdRatio = (kills + 1.0) / (deaths + 1.0);
        kdRatio = Math.min(kdRatio, config.kdCap);
        kdRatio = Math.max(kdRatio, config.kdMin);
        return kdRatio;
    }
}

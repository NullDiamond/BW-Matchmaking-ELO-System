package com.example.elocalculator.managers;

import com.example.elocalculator.model.game.GameData;
import com.example.elocalculator.model.game.PlayerStats;
import com.example.elocalculator.model.elo.PlayerEloData;
import com.example.elocalculator.model.elo.EloChange;
import com.example.elocalculator.config.Config;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager class responsible for core ELO calculation logic.
 */
public class EloCalculator {

    /**
     * Calculates ELO changes for a multi-team game.
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param eloRatings current ELO ratings for all players
     * @param modeName the game mode
     * @param kFactor the K-factor for ELO calculation
     * @param gameId the game ID for logging
     * @return map of player UUIDs to their ELO changes
     */
    public Map<String, EloChange> calculateMultiTeamEloChanges(
            GameData gameData, String winnerTeam, Map<String, PlayerEloData> eloRatings,
            String modeName, double kFactor, String gameId) {

        Map<String, Double> totalChanges = new HashMap<>();
        Map<String, Double> performanceScores = new HashMap<>();

        // First pass: Calculate raw performance scores for all players
        Map<String, Double> rawPerformanceScores = new HashMap<>();
        for (Map.Entry<String, List<String>> teamEntry : gameData.getTeamsAsUuids().entrySet()) {
            List<String> teamPlayers = teamEntry.getValue();
            for (String uuid : teamPlayers) {
                double rawScore = calculatePerformanceScore(gameData, uuid, teamPlayers, modeName.equals("mega"));
                rawPerformanceScores.put(uuid, rawScore);
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
        Set<String> allTeams = gameData.getTeams().keySet();

        // Second pass: Calculate ELO changes using normalized performance scores
        for (Map.Entry<String, List<String>> teamEntry : gameData.getTeamsAsUuids().entrySet()) {
            String teamName = teamEntry.getKey();
            List<String> teamPlayers = teamEntry.getValue();

            // Determine if this team won
            boolean isWinner = winnerTeam.equals(teamName);
            boolean isTie = "Tie".equals(winnerTeam);
            double actualScore;
            
            if (isTie) {
                actualScore = 0.5;
            } else {
                actualScore = isWinner ? 1.0 : 0.0;
            }

            // Calculate team average ELO
            double teamAvgElo = calculateTeamAverageElo(teamPlayers, eloRatings, modeName);

            // Count opponents for this team
            int numOpponents = (int) allTeams.stream().filter(t -> !t.equals(teamName)).count();

            // Calculate expected score against each other team
            for (Map.Entry<String, List<String>> opponentEntry : gameData.getTeamsAsUuids().entrySet()) {
                String opponentTeam = opponentEntry.getKey();
                if (teamName.equals(opponentTeam)) continue;

                List<String> opponentPlayers = opponentEntry.getValue();
                double opponentAvgElo = calculateTeamAverageElo(opponentPlayers, eloRatings, modeName);

                // Calculate expected score using ELO formula
                double eloDifference = opponentAvgElo - teamAvgElo;
                double expectedScore = calculateExpectedScore(eloDifference);

                // Apply the ELO change to each player in the team
                for (String uuid : teamPlayers) {
                    double performanceScore = performanceScores.get(uuid);

                    // Calculate the base ELO change
                    double scoreDifference = actualScore - expectedScore;
                    
                    // Apply performance multiplier:
                    // - On wins (scoreDiff > 0): multiply by performanceScore (higher perf = more gain)
                    // - On losses (scoreDiff < 0): divide by performanceScore (higher perf = less loss)
                    double performanceMultiplier;
                    if (scoreDifference >= 0) {
                        // Win or draw: higher performance = more ELO gain
                        performanceMultiplier = performanceScore;
                    } else {
                        // Loss: higher performance = less ELO loss (divide by performance)
                        performanceMultiplier = 1.0 / performanceScore;
                    }
                    
                    double baseChange = kFactor * scoreDifference * performanceMultiplier;

                    // Weight the ELO change by performance and divide by number of opponents
                    // This ensures fair ELO changes regardless of number of teams in the game
                    double weightedChange = baseChange / numOpponents;

                    // Accumulate changes
                    totalChanges.merge(uuid, weightedChange, Double::sum);
                }
            }
        }

        // Apply zero-sum normalization
        // Calculate the sum of all changes
        double sum = totalChanges.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // If the sum is not zero, distribute the excess proportionally
        if (Math.abs(sum) > 0.0001) {
            int numPlayers = totalChanges.size();
            double correction = sum / numPlayers;
            
            // Subtract the correction from each player to achieve zero-sum
            for (String uuid : totalChanges.keySet()) {
                totalChanges.put(uuid, totalChanges.get(uuid) - correction);
            }
        }

        // Create final EloChange objects
        Map<String, EloChange> eloChanges = new HashMap<>();
        for (String uuid : totalChanges.keySet()) {
            eloChanges.put(uuid, new EloChange(
                totalChanges.get(uuid), 
                rawPerformanceScores.get(uuid),
                performanceScores.get(uuid)
            ));
        }

        return eloChanges;
    }

    /**
     * Calculates the average ELO rating for a team of players.
     * @param teamPlayers the list of player UUIDs on the team
     * @param eloRatings the current ELO ratings for all players
     * @param modeName the game mode
     * @return the average ELO rating for the team
     */
    private double calculateTeamAverageElo(List<String> teamPlayers, Map<String, PlayerEloData> eloRatings, String modeName) {
        return teamPlayers.stream()
            .mapToDouble(uuid -> eloRatings.get(uuid).getElo().get(modeName))
            .average()
            .orElse(Config.INITIAL_ELO);
    }

    /**
     * Calculates the expected score using the ELO formula.
     * @param eloDifference the difference in ELO ratings (opponent - team)
     * @return the expected score (probability of winning)
     */
    private double calculateExpectedScore(double eloDifference) {
        return 1.0 / (1.0 + Math.pow(10.0, eloDifference / 400.0));
    }

    /**
     * Calculates a K/D ratio with caps applied.
     * @param kills the number of kills
     * @param deaths the number of deaths
     * @return the capped K/D ratio
     */
    private double calculateKdRatio(double kills, double deaths) {
        double kdRatio = (kills + 1.0) / (deaths + 1.0);
        kdRatio = Math.min(kdRatio, Config.KD_CAP);
        kdRatio = Math.max(kdRatio, Config.KD_MIN);
        return kdRatio;
    }

    /**
     * Calculates a performance score for a player in a game.
     * @param gameData the game data
     * @param playerUuid the player's UUID
     * @param teamPlayers the list of players on the player's team
     * @param isMega whether this is mega mode
     * @return performance score (1.0 = average, >1.0 = above average, <1.0 = below average)
     */
    public double calculatePerformanceScore(GameData gameData, String playerUuid, List<String> teamPlayers, boolean isMega) {
        if (!gameData.getPlayer_stats().containsKey(playerUuid)) {
            return 1.0;
        }

        PlayerStats stats = gameData.getPlayer_stats().get(playerUuid);

        // Get comparison group (teammates for team modes, all other players for solo)
        List<String> teammates = teamPlayers.stream()
            .filter(uuid -> !uuid.equals(playerUuid))
            .collect(Collectors.toList());

        // For solo mode (no teammates), compare against all other players in the game
        boolean isSoloMode = teammates.isEmpty();
        List<String> comparisonGroup;

        if (isSoloMode) {
            comparisonGroup = gameData.getPlayer_stats().keySet().stream()
                .filter(uuid -> !uuid.equals(playerUuid))
                .collect(Collectors.toList());
        } else {
            comparisonGroup = teammates;
        }

        if (comparisonGroup.isEmpty()) {
            return 1.0;
        }

        // Use mode-specific weights
        double weightBeds = isMega ? Config.WEIGHT_BED_BREAKS_MEGA : Config.WEIGHT_BED_BREAKS;
        double weightKd = isMega ? Config.WEIGHT_KD_MEGA : Config.WEIGHT_KD;
        double weightFinals = isMega ? Config.FINAL_KILL_WEIGHT_MEGA : Config.FINAL_KILL_WEIGHT;
        int finalCap = isMega ? Config.FINAL_KILL_CAP_MEGA : Config.FINAL_KILL_CAP;

        double performanceScore = 1.0;

        performanceScore += weightBeds * stats.getBed_breaks();

        // K/D ratio - symmetric transformation around 1.0
        double playerKd = calculateKdRatio(stats.getKills(), stats.getDeaths());

        // Symmetric transformation: reward/penalty magnitude is equal for reciprocal KDs
        // e.g., 2.0 KD gets +1.0 reward, 0.5 KD gets -1.0 penalty
        double kdTransform;
        if (playerKd >= 1.0) {
            kdTransform = playerKd - 1.0;
        } else {
            kdTransform = -(1.0 / playerKd - 1.0);
        }

        performanceScore += weightKd * kdTransform;

        // Final kills
        int finalKillsCount = Math.min(stats.getFinal_kills(), finalCap);
        performanceScore += weightFinals * finalKillsCount;

        // Clamp performance score to prevent extreme ELO swings
        performanceScore = Math.max(Config.MIN_PERFORMANCE_MULTIPLIER,
                          Math.min(Config.MAX_PERFORMANCE_MULTIPLIER, performanceScore));

        return performanceScore;
    }
}
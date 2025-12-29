package com.example.elocalculator.managers;

import com.example.elocalculator.model.game.GameData;
import com.example.elocalculator.model.game.PlayerStats;
import com.example.elocalculator.model.elo.PlayerEloData;
import com.example.elocalculator.model.elo.EloChange;
import com.example.elocalculator.config.Config;
import java.util.*;

/**
 * Analyzes and displays detailed ELO calculation breakdown for specific matches.
 */
public class MatchAnalyzer {

    private final DataManager dataManager;
    private final EloCalculator eloCalculator;

    public MatchAnalyzer(DataManager dataManager, EloCalculator eloCalculator) {
        this.dataManager = dataManager;
        this.eloCalculator = eloCalculator;
    }

    /**
     * Analyzes and displays detailed calculation breakdown for a specific game.
     * This version loads game data from storage.
     * @param gameId the game ID to analyze
     * @param playerNames map of player UUIDs to names
     * @param eloRatings current ELO ratings before this match
     * @param modeName the game mode
     */
    public void analyzeMatch(String gameId, Map<String, String> playerNames, 
                            Map<String, PlayerEloData> eloRatings, String modeName) {
        try {
            Map<String, GameData> allGames = dataManager.loadGameData();
            GameData gameData = allGames.get(gameId);
            if (gameData == null) {
                System.out.println("Game ID not found: " + gameId);
                return;
            }
            analyzeMatchWithGameData(gameId, playerNames, eloRatings, modeName, gameData);
        } catch (Exception e) {
            System.err.println("Error analyzing match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Analyzes and displays detailed calculation breakdown for a specific game.
     * This version accepts game data directly for faster execution.
     * @param gameId the game ID to analyze
     * @param playerNames map of player UUIDs to names
     * @param eloRatings current ELO ratings before this match
     * @param modeName the game mode
     * @param gameData the game data (already loaded)
     */
    public void analyzeMatchWithGameData(String gameId, Map<String, String> playerNames, 
                            Map<String, PlayerEloData> eloRatings, String modeName, GameData gameData) {
        try {
            String winnerTeam = gameData.getWinner();
            if (winnerTeam == null) {
                System.out.println("No winner team found for game: " + gameId);
                return;
            }

            double kFactor = modeName.equals("mega") ? Config.K_FACTOR_MEGA : Config.K_FACTOR;
            boolean isMega = modeName.equals("mega");
            boolean isTie = "Tie".equals(winnerTeam);

            System.out.println("\n" + "=".repeat(100));
            System.out.println("DETAILED MATCH ANALYSIS - Game ID: " + gameId);
            System.out.println("Mode: " + modeName.toUpperCase() + " | K-Factor: " + kFactor);
            if (isTie) {
                System.out.println("Result: TIE - No Winner (All teams tied)");
            } else {
                System.out.println("Winner: Team " + winnerTeam);
            }
            System.out.println("=".repeat(100));

            // Get all teams
            Map<String, List<String>> teams = gameData.getTeamsAsUuids();
            Set<String> allTeams = teams.keySet();

            // Print initial team information
            System.out.println("\n--- TEAMS AND INITIAL ELOS ---\n");
            Map<String, Double> teamAvgElos = new HashMap<>();
            
            for (Map.Entry<String, List<String>> teamEntry : teams.entrySet()) {
                String teamName = teamEntry.getKey();
                List<String> teamPlayers = teamEntry.getValue();
                boolean isWinner = winnerTeam.equals(teamName);
                
                String teamStatus;
                if (isTie) {
                    teamStatus = " [TIED]";
                } else {
                    teamStatus = isWinner ? " [WINNER]" : " [LOSER]";
                }
                
                System.out.println("Team " + teamName + teamStatus + ":");
                
                double sumElo = 0.0;
                for (String uuid : teamPlayers) {
                    String name = playerNames.getOrDefault(uuid, "Unknown");
                    double elo = eloRatings.get(uuid).getElo().get(modeName);
                    sumElo += elo;
                    System.out.printf("  - %s: %.2f ELO%n", name, elo);
                }
                
                double avgElo = sumElo / teamPlayers.size();
                teamAvgElos.put(teamName, avgElo);
                System.out.printf("  Team Average: %.2f ELO%n%n", avgElo);
            }

            // Calculate and display expected scores
            System.out.println("--- EXPECTED SCORES (Probability of Winning) ---\n");
            
            Map<String, Map<String, Double>> expectedScores = new HashMap<>();
            for (String teamName : allTeams) {
                expectedScores.put(teamName, new HashMap<>());
                double teamAvg = teamAvgElos.get(teamName);
                
                for (String opponentTeam : allTeams) {
                    if (!teamName.equals(opponentTeam)) {
                        double opponentAvg = teamAvgElos.get(opponentTeam);
                        double eloDiff = opponentAvg - teamAvg;
                        double expected = 1.0 / (1.0 + Math.pow(10.0, eloDiff / 400.0));
                        expectedScores.get(teamName).put(opponentTeam, expected);
                        
                        System.out.printf("Team %s vs Team %s:%n", teamName, opponentTeam);
                        System.out.printf("  ELO Difference: %.2f - %.2f = %.2f%n", 
                                        opponentAvg, teamAvg, eloDiff);
                        System.out.printf("  Expected Score: %.4f (%.1f%%)%n%n", 
                                        expected, expected * 100);
                    }
                }
            }

            // Calculate performance scores
            System.out.println("--- PERFORMANCE SCORES ---\n");
            
            Map<String, Double> performanceScores = new HashMap<>();
            
            for (Map.Entry<String, List<String>> teamEntry : teams.entrySet()) {
                String teamName = teamEntry.getKey();
                List<String> teamPlayers = teamEntry.getValue();
                
                System.out.println("Team " + teamName + ":");
                
                for (String uuid : teamPlayers) {
                    String name = playerNames.getOrDefault(uuid, "Unknown");
                    PlayerStats stats = gameData.getPlayer_stats().get(uuid);
                    
                    if (stats == null) {
                        System.out.printf("  %s: No stats available%n", name);
                        performanceScores.put(uuid, 1.0);
                        continue;
                    }
                    
                    double perfScore = eloCalculator.calculatePerformanceScore(
                        gameData, uuid, teamPlayers, isMega);
                    performanceScores.put(uuid, perfScore);
                    
                    System.out.printf("  %s:%n", name);
                    System.out.printf("    Stats: %d kills, %d deaths, %d bed breaks, %d finals%n",
                                    stats.getKills(), stats.getDeaths(), 
                                    stats.getBed_breaks(), stats.getFinal_kills());
                    System.out.printf("    Performance Score: %.3f (%.1f%% of baseline)%n%n",
                                    perfScore, perfScore * 100);
                }
            }

            // Calculate ELO changes using the actual calculator (includes zero-sum normalization)
            Map<String, EloChange> eloChanges = eloCalculator.calculateMultiTeamEloChanges(
                gameData, winnerTeam, eloRatings, modeName, kFactor, gameId);

            // Display actual ELO calculation results
            System.out.println("--- ELO CALCULATIONS (ACTUAL RESULTS) ---\n");
            
            int numOpponents = allTeams.size() - 1;
            System.out.printf("Number of opponent teams: %d%n", numOpponents);
            System.out.printf("K-Factor: %.2f%n", kFactor);
            System.out.printf("Note: These are the ACTUAL ELO changes calculated by the system%n%n", numOpponents);
            
            for (Map.Entry<String, List<String>> teamEntry : teams.entrySet()) {
                String teamName = teamEntry.getKey();
                List<String> teamPlayers = teamEntry.getValue();
                boolean isWinner = winnerTeam.equals(teamName);
                String resultLabel;
                
                if (isTie) {
                    resultLabel = "TIE";
                } else {
                    resultLabel = isWinner ? "WIN" : "LOSS";
                }
                
                System.out.println("Team " + teamName + " (" + resultLabel + "):");
                
                for (String uuid : teamPlayers) {
                    String name = playerNames.getOrDefault(uuid, "Unknown");
                    EloChange eloChange = eloChanges.get(uuid);
                    double change = eloChange.getChange();
                    double rawPerf = eloChange.getRawPerformanceScore();
                    double normPerf = eloChange.getNormalizedPerformanceScore();
                    
                    System.out.printf("  %s:%n", name);
                    System.out.printf("    Raw Performance Score: %.3f%n", rawPerf);
                    System.out.printf("    Normalized Performance Score: %.3f%n", normPerf);
                    System.out.printf("    ELO Change: %+.2f%n%n", change);
                }
            }

            // Summary
            System.out.println("--- SUMMARY ---\n");
            System.out.printf("%-25s %12s %12s %12s %12s %12s %8s %8s %8s %8s%n", 
                            "Player", "Initial ELO", "Change", "Final ELO", "Base Perf", "Normalized",
                            "Kills", "Deaths", "Beds", "Finals");
            System.out.println("-".repeat(155));
            
            for (Map.Entry<String, List<String>> teamEntry : teams.entrySet()) {
                String teamName = teamEntry.getKey();
                List<String> teamPlayers = teamEntry.getValue();
                boolean isWinner = winnerTeam.equals(teamName);
                
                String teamStatus;
                if (isTie) {
                    teamStatus = " [TIED]";
                } else {
                    teamStatus = isWinner ? " [WINNER]" : " [LOSER]";
                }
                
                System.out.println("Team " + teamName + teamStatus + ":");
                
                for (String uuid : teamPlayers) {
                    String name = playerNames.getOrDefault(uuid, "Unknown");
                    double initialElo = eloRatings.get(uuid).getElo().get(modeName);
                    EloChange eloChange = eloChanges.get(uuid);
                    double change = eloChange.getChange();
                    double finalElo = initialElo + change;
                    double rawPerf = eloChange.getRawPerformanceScore();
                    double normPerf = eloChange.getNormalizedPerformanceScore();
                    
                    // Get player stats
                    PlayerStats stats = gameData.getPlayer_stats().get(uuid);
                    int kills = stats != null ? stats.getKills() : 0;
                    int deaths = stats != null ? stats.getDeaths() : 0;
                    int bedBreaks = stats != null ? stats.getBed_breaks() : 0;
                    int finals = stats != null ? stats.getFinal_kills() : 0;
                    
                    System.out.printf("  %-23s %12.2f %+12.2f %12.2f %12.3f %12.3f %8d %8d %8d %8d%n",
                                    name, initialElo, change, finalElo, rawPerf, normPerf,
                                    kills, deaths, bedBreaks, finals);
                }
                System.out.println();
            }
            
            // Verify zero-sum
            double totalChange = eloChanges.values().stream()
                .mapToDouble(EloChange::getChange)
                .sum();
            System.out.printf("Total ELO change (should be ~0.0): %+.6f%n", totalChange);
            
            if (Math.abs(totalChange) < 0.01) {
                System.out.println("[OK] Zero-sum property maintained!");
            } else {
                System.out.println("[WARNING] Zero-sum property violated");
            }
            
            System.out.println("=".repeat(100));

        } catch (Exception e) {
            System.err.println("Error analyzing match: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

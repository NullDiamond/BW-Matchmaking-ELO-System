package com.example.elocalculator.managers;

import com.example.elocalculator.config.Config;
import com.example.elocalculator.model.team.BalancedTeamsOutput;
import com.example.elocalculator.model.leaderboard.LeaderboardEntry;
import com.example.elocalculator.model.player.PlayerInfo;
import com.example.elocalculator.model.player.PlayerList;
import com.example.elocalculator.model.team.TeamBalanceResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Balances teams for mega mode matches based on player ELO ratings.
 * Uses a greedy algorithm with controlled randomization for optimal balance and variability.
 */
public class TeamBalancer {
    private final Gson gson;
    private final DataManager dataManager;
    private Map<String, LeaderboardEntry> playerDataMap;
    private Set<String> legacyTopPlayers;
    private Map<String, String> uuidToName;

    public TeamBalancer(DataManager dataManager) {
        this.gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        this.dataManager = dataManager;
        this.playerDataMap = new HashMap<>();
        this.legacyTopPlayers = new HashSet<>();
        this.uuidToName = new HashMap<>();
    }

    /**
     * Loads the main mega ELO leaderboard and creates a lookup map.
     * Also loads legacy top players and player names for proper ELO assignment.
     */
    private void loadMegaLeaderboard() throws IOException {
        // Use the main mega leaderboard
        String leaderboardPath = dataManager.getDataDirectory() + "/output/leaderboards_java/mega_leaderboard.json";
        
        System.out.println("Loading main mega ELO leaderboard from: " + leaderboardPath);
        
        String json = new String(Files.readAllBytes(Paths.get(leaderboardPath)));
        List<LeaderboardEntry> leaderboard = gson.fromJson(json, new TypeToken<List<LeaderboardEntry>>() {}.getType());
        
        playerDataMap.clear();
        for (LeaderboardEntry entry : leaderboard) {
            playerDataMap.put(entry.getUuid(), entry);
        }
        
        // Load legacy top players for proper initial ELO assignment
        legacyTopPlayers = dataManager.loadLegacyTopPlayers();
        
        // Load player names for UUID-to-name lookup
        uuidToName = dataManager.loadPlayerNames();
        
        System.out.println("Loaded " + playerDataMap.size() + " players from mega ELO leaderboard");
    }

    /**
     * Balances teams using a greedy algorithm with controlled randomization.
     * Players are sorted by ELO, shuffled within ELO brackets for variability,
     * then assigned to the team with lower total ELO.
     */
    public void balanceTeams(String inputFilePath) {
        try {
            // Load the mega leaderboard
            loadMegaLeaderboard();

            // Read the input file with player UUIDs
            String json = new String(Files.readAllBytes(Paths.get(inputFilePath)));
            PlayerList playerList = gson.fromJson(json, PlayerList.class);
            
            if (playerList == null || playerList.getPlayers() == null || playerList.getPlayers().isEmpty()) {
                System.out.println("Error: No players found in input file");
                return;
            }

            List<String> playerUuids = playerList.getPlayers();
            System.out.println("\n========================================");
            System.out.println("TEAM BALANCER - MEGA MODE");
            System.out.println("========================================\n");
            System.out.println("Total players: " + playerUuids.size());

            // Get player data and sort by ELO
            List<PlayerInfo> players = new ArrayList<>();
            List<String> newPlayers = new ArrayList<>();
            List<String> legacyNewPlayers = new ArrayList<>();
            
            for (String uuid : playerUuids) {
                LeaderboardEntry entry = playerDataMap.get(uuid);
                if (entry != null) {
                    players.add(new PlayerInfo(uuid, entry.getName(), entry.getElo(), entry.getGames()));
                } else {
                    // New player not in mega leaderboard - check if legacy top player
                    String playerName = uuidToName.getOrDefault(uuid, "Unknown-" + uuid.substring(0, 8));
                    double initialElo;
                    if (legacyTopPlayers.contains(uuid)) {
                        initialElo = Config.LEGACY_TOP_INITIAL_ELO;
                        legacyNewPlayers.add(playerName + " (" + uuid.substring(0, 8) + ")");
                    } else {
                        initialElo = Config.INITIAL_ELO;
                        newPlayers.add(playerName + " (" + uuid.substring(0, 8) + ")");
                    }
                    players.add(new PlayerInfo(uuid, playerName, initialElo, 0));
                }
            }

            if (!legacyNewPlayers.isEmpty()) {
                System.out.println("\nLegacy top players (new to mega, assigned " + Config.LEGACY_TOP_INITIAL_ELO + " ELO):");
                for (String player : legacyNewPlayers) {
                    System.out.println("  * " + player);
                }
            }
            
            if (!newPlayers.isEmpty()) {
                System.out.println("\nNew players (assigned " + Config.INITIAL_ELO + " ELO):");
                for (String player : newPlayers) {
                    System.out.println("  - " + player);
                }
            }

            // Sort by ELO descending
            players.sort((a, b) -> Double.compare(b.getElo(), a.getElo()));

            // Add controlled randomization to prevent repetitive teams
            // Shuffle players within ELO brackets to add variability
            shuffleWithinEloBrackets(players, Config.ELO_BRACKET_SIZE);

            // Balance teams with iterative threshold adjustment
            double targetMaxDifference = 20.0;
            TeamBalanceResult result = null;
            int attempts = 0;
            final int maxAttempts = 10;
            
            System.out.println("\nBalancing teams with target max difference: " + targetMaxDifference + " ELO");
            
            while (result == null && attempts < maxAttempts) {
                result = tryBalanceTeams(players, targetMaxDifference);
                
                if (result == null) {
                    attempts++;
                    targetMaxDifference += 10.0; // Increase threshold by 10 ELO
                    System.out.println("Unable to achieve target, increasing threshold to: " + targetMaxDifference + " ELO");
                    
                    // Re-shuffle for next attempt
                    players.sort((a, b) -> Double.compare(b.getElo(), a.getElo()));
                    shuffleWithinEloBrackets(players, Config.ELO_BRACKET_SIZE);
                }
            }
            
            // If still no result, fall back to greedy algorithm
            if (result == null) {
                System.out.println("Using fallback greedy algorithm...");
                result = greedyBalance(players);
            }
            
            List<PlayerInfo> team1 = result.getTeam1();
            List<PlayerInfo> team2 = result.getTeam2();

            // Display results
            displayTeam("TEAM 1", team1);
            displayTeam("TEAM 2", team2);
            
            // Summary
            double team1Total = team1.stream().mapToDouble(PlayerInfo::getElo).sum();
            double team2Total = team2.stream().mapToDouble(PlayerInfo::getElo).sum();
            double team1Avg = team1.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0);
            double team2Avg = team2.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0);
            
            // Use total ELO difference for balance assessment (fairer when team sizes differ)
            double totalEloDifference = Math.abs(team1Total - team2Total);
            double avgEloDifference = Math.abs(team1Avg - team2Avg);
            
            System.out.println("\n========================================");
            System.out.println("BALANCE SUMMARY");
            System.out.println("========================================");
            System.out.printf("Team 1: %d players, Total ELO: %.1f, Average: %.1f%n", team1.size(), team1Total, team1Avg);
            System.out.printf("Team 2: %d players, Total ELO: %.1f, Average: %.1f%n", team2.size(), team2Total, team2Avg);
            System.out.printf("Total ELO Difference: %.1f%n", totalEloDifference);
            System.out.printf("Average ELO Difference: %.1f%n", avgEloDifference);
            
            // Balance assessment based on total ELO difference
            int teamSize = team1.size();
            double threshold20 = 20.0 * teamSize;
            double threshold50 = 50.0 * teamSize;
            
            if (totalEloDifference < threshold20) {
                System.out.println("[OK] Teams are well balanced!");
            } else if (totalEloDifference < threshold50) {
                System.out.println("[NOTE] Teams are reasonably balanced");
            } else {
                System.out.println("[WARNING] Teams may be unbalanced - consider manual adjustments");
            }
            
            // Save balanced teams to output file
            saveBalancedTeams(team1, team2, inputFilePath);

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Balances teams using a list of player UUIDs directly (without reading from file).
     * Used when player names are entered manually and converted to UUIDs.
     * @param playerUuids list of player UUIDs
     */
    public void balanceTeamsFromUuids(List<String> playerUuids) {
        try {
            // Load the mega leaderboard
            loadMegaLeaderboard();
            
            if (playerUuids == null || playerUuids.isEmpty()) {
                System.out.println("Error: No players provided");
                return;
            }

            System.out.println("\n========================================");
            System.out.println("TEAM BALANCER - MEGA MODE");
            System.out.println("========================================\n");
            System.out.println("Total players: " + playerUuids.size());

            // Get player data and sort by ELO
            List<PlayerInfo> players = new ArrayList<>();
            List<String> newPlayers = new ArrayList<>();
            List<String> legacyNewPlayers = new ArrayList<>();
            
            for (String uuid : playerUuids) {
                LeaderboardEntry entry = playerDataMap.get(uuid);
                if (entry != null) {
                    players.add(new PlayerInfo(uuid, entry.getName(), entry.getElo(), entry.getGames()));
                } else {
                    // New player not in mega leaderboard - check if legacy top player
                    String playerName = uuidToName.getOrDefault(uuid, "Unknown-" + uuid.substring(0, 8));
                    double initialElo;
                    if (legacyTopPlayers.contains(uuid)) {
                        initialElo = Config.LEGACY_TOP_INITIAL_ELO;
                        legacyNewPlayers.add(playerName);
                    } else {
                        initialElo = Config.INITIAL_ELO;
                        newPlayers.add(playerName);
                    }
                    players.add(new PlayerInfo(uuid, playerName, initialElo, 0));
                }
            }

            if (!legacyNewPlayers.isEmpty()) {
                System.out.println("\nLegacy top players (new to mega, assigned " + Config.LEGACY_TOP_INITIAL_ELO + " ELO):");
                for (String player : legacyNewPlayers) {
                    System.out.println("  * " + player);
                }
            }
            
            if (!newPlayers.isEmpty()) {
                System.out.println("\nNew players (assigned " + Config.INITIAL_ELO + " ELO):");
                for (String player : newPlayers) {
                    System.out.println("  - " + player);
                }
            }

            // Sort by ELO descending
            players.sort((a, b) -> Double.compare(b.getElo(), a.getElo()));

            // Add controlled randomization to prevent repetitive teams
            shuffleWithinEloBrackets(players, Config.ELO_BRACKET_SIZE);

            // Balance teams with iterative threshold adjustment
            double targetMaxDifference = 20.0;
            TeamBalanceResult result = null;
            int attempts = 0;
            final int maxAttempts = 10;
            
            System.out.println("\nBalancing teams with target max difference: " + targetMaxDifference + " ELO");
            
            while (result == null && attempts < maxAttempts) {
                result = tryBalanceTeams(players, targetMaxDifference);
                
                if (result == null) {
                    attempts++;
                    targetMaxDifference += 10.0;
                    System.out.println("Unable to achieve target, increasing threshold to: " + targetMaxDifference + " ELO");
                    
                    players.sort((a, b) -> Double.compare(b.getElo(), a.getElo()));
                    shuffleWithinEloBrackets(players, Config.ELO_BRACKET_SIZE);
                }
            }
            
            // If still no result, fall back to greedy algorithm
            if (result == null) {
                System.out.println("Using fallback greedy algorithm...");
                result = greedyBalance(players);
            }
            
            List<PlayerInfo> team1 = result.getTeam1();
            List<PlayerInfo> team2 = result.getTeam2();

            // Display results
            displayTeam("TEAM 1", team1);
            displayTeam("TEAM 2", team2);
            
            // Summary
            double team1Total = team1.stream().mapToDouble(PlayerInfo::getElo).sum();
            double team2Total = team2.stream().mapToDouble(PlayerInfo::getElo).sum();
            double team1Avg = team1.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0);
            double team2Avg = team2.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0);
            
            double totalEloDifference = Math.abs(team1Total - team2Total);
            double avgEloDifference = Math.abs(team1Avg - team2Avg);
            
            System.out.println("\n========================================");
            System.out.println("BALANCE SUMMARY");
            System.out.println("========================================");
            System.out.printf("Team 1: %d players, Total ELO: %.1f, Average: %.1f%n", team1.size(), team1Total, team1Avg);
            System.out.printf("Team 2: %d players, Total ELO: %.1f, Average: %.1f%n", team2.size(), team2Total, team2Avg);
            System.out.printf("Total ELO Difference: %.1f%n", totalEloDifference);
            System.out.printf("Average ELO Difference: %.1f%n", avgEloDifference);
            
            int teamSize = team1.size();
            double threshold20 = 20.0 * teamSize;
            double threshold50 = 50.0 * teamSize;
            
            if (totalEloDifference < threshold20) {
                System.out.println("[OK] Teams are well balanced!");
            } else if (totalEloDifference < threshold50) {
                System.out.println("[NOTE] Teams are reasonably balanced");
            } else {
                System.out.println("[WARNING] Teams may be unbalanced - consider manual adjustments");
            }
            
            System.out.println("\n(Teams not saved to file - manual entry mode)");

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Attempts to balance teams within the specified ELO difference threshold.
     * Uses a greedy approach with validation and enforces equal team sizes.
     */
    private TeamBalanceResult tryBalanceTeams(List<PlayerInfo> players, double maxDifference) {
        List<PlayerInfo> team1 = new ArrayList<>();
        List<PlayerInfo> team2 = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;
        
        int targetTeamSize = players.size() / 2;
        
        for (PlayerInfo player : players) {
            // Enforce equal team sizes (or differ by 1 if odd number)
            if (team1.size() >= targetTeamSize + 1) {
                // Team 1 is full, must assign to team 2
                team2.add(player);
                team2TotalElo += player.getElo();
            } else if (team2.size() >= targetTeamSize + 1) {
                // Team 2 is full, must assign to team 1
                team1.add(player);
                team1TotalElo += player.getElo();
            } else if (team1.size() == targetTeamSize && team2.size() < targetTeamSize) {
                // Team 1 reached target, continue filling team 2
                team2.add(player);
                team2TotalElo += player.getElo();
            } else if (team2.size() == targetTeamSize && team1.size() < targetTeamSize) {
                // Team 2 reached target, continue filling team 1
                team1.add(player);
                team1TotalElo += player.getElo();
            } else {
                // Both teams have space, assign to weaker team (by total ELO)
                if (team1TotalElo <= team2TotalElo) {
                    team1.add(player);
                    team1TotalElo += player.getElo();
                } else {
                    team2.add(player);
                    team2TotalElo += player.getElo();
                }
            }
        }
        
        // Verify final balance meets the threshold (using total ELO difference)
        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);
        
        if (eloDifference <= maxDifference * targetTeamSize) {
            return new TeamBalanceResult(team1, team2);
        }
        
        return null; // Failed to meet threshold
    }

    /**
     * Fallback greedy balancing without threshold constraints.
     * Ensures equal team sizes.
     */
    private TeamBalanceResult greedyBalance(List<PlayerInfo> players) {
        List<PlayerInfo> team1 = new ArrayList<>();
        List<PlayerInfo> team2 = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;
        
        int targetTeamSize = players.size() / 2;
        
        for (PlayerInfo player : players) {
            // Enforce equal team sizes
            if (team1.size() >= targetTeamSize + 1) {
                team2.add(player);
                team2TotalElo += player.getElo();
            } else if (team2.size() >= targetTeamSize + 1) {
                team1.add(player);
                team1TotalElo += player.getElo();
            } else if (team1TotalElo <= team2TotalElo) {
                team1.add(player);
                team1TotalElo += player.getElo();
            } else {
                team2.add(player);
                team2TotalElo += player.getElo();
            }
        }
        
        return new TeamBalanceResult(team1, team2);
    }

    /**
     * Shuffles players within ELO brackets to add variability while maintaining fairness.
     * Players within the same bracket are randomly shuffled, but brackets maintain their order.
     * This prevents getting the same teams every time while keeping balance.
     */
    private void shuffleWithinEloBrackets(List<PlayerInfo> players, double bracketSize) {
        Random random = new Random();
        int start = 0;
        
        while (start < players.size()) {
            // Find all players in the same ELO bracket
            double bracketStart = players.get(start).getElo();
            int end = start;
            
            while (end < players.size() && 
                   Math.abs(players.get(end).getElo() - bracketStart) < bracketSize) {
                end++;
            }
            
            // Shuffle players within this bracket
            if (end - start > 1) {
                List<PlayerInfo> bracket = players.subList(start, end);
                Collections.shuffle(bracket, random);
            }
            
            start = end;
        }
    }

    /**
     * Displays team information in a formatted table.
     */
    private void displayTeam(String teamName, List<PlayerInfo> team) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println(teamName + " (" + team.size() + " players)");
        System.out.println("=".repeat(80));
        System.out.printf("%-4s %-25s %-12s %-8s%n", "#", "Player", "ELO", "Games");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < team.size(); i++) {
            PlayerInfo p = team.get(i);
            System.out.printf("%-4d %-25s %-12.1f %-8d%n", 
                i + 1, 
                p.getName(), 
                p.getElo(), 
                p.getGames());
        }
    }

    /**
     * Saves the balanced teams to a JSON output file.
     */
    private void saveBalancedTeams(List<PlayerInfo> team1, List<PlayerInfo> team2, String inputFilePath) {
        try {
            BalancedTeamsOutput output = new BalancedTeamsOutput();
            output.setTeam1(team1.stream().map(PlayerInfo::getUuid).collect(Collectors.toList()));
            output.setTeam2(team2.stream().map(PlayerInfo::getUuid).collect(Collectors.toList()));
            output.setTeam1AvgElo(team1.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0));
            output.setTeam2AvgElo(team2.stream().mapToDouble(PlayerInfo::getElo).average().orElse(0));
            
            String outputPath = inputFilePath.replace(".json", "_balanced.json");
            String json = gson.toJson(output);
            Files.write(Paths.get(outputPath), json.getBytes());
            
            System.out.println("\n[OK] Balanced teams saved to: " + outputPath);
        } catch (IOException e) {
            System.out.println("Warning: Could not save balanced teams: " + e.getMessage());
        }
    }
}




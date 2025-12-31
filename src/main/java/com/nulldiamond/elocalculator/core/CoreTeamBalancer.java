package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.*;

import java.util.*;

/**
 * Core team balancer - balances teams entirely in memory.
 * Uses the same algorithm as the full version TeamBalancer:
 * - Greedy assignment with iterative threshold adjustment
 * - ELO bracket shuffling for variability
 * - Total ELO difference as balance metric
 * 
 * No file I/O, no JSON dependencies.
 */
public class CoreTeamBalancer {
    private final CoreConfig config;
    private final Random random;

    public CoreTeamBalancer(CoreConfig config) {
        this.config = config;
        this.random = new Random();
    }

    public CoreTeamBalancer() {
        this(new CoreConfig());
    }

    /**
     * Balances players into 2 teams using the specified game mode for ELO lookup.
     * Uses the same algorithm as the full version TeamBalancer.
     * 
     * @param players list of players to balance
     * @param gameMode the game mode to use for ELO lookup
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeams(List<CorePlayer> players, CoreGameMode gameMode) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Cannot balance empty player list");
        }
        if (players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players");
        }

        // Sort players by ELO descending for the specified mode
        List<CorePlayer> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort((a, b) -> Double.compare(b.getElo(gameMode), a.getElo(gameMode)));

        // Add controlled randomization - shuffle within ELO brackets
        shuffleWithinEloBrackets(sortedPlayers, config.eloBracketSize, gameMode);

        // Balance teams with iterative threshold adjustment
        double targetMaxDifference = config.targetEloDifference;
        CoreBalanceResult result = null;
        int attempts = 0;
        final int maxAttempts = config.maxBalanceAttempts;

        while (result == null && attempts < maxAttempts) {
            result = tryBalanceTeams(sortedPlayers, targetMaxDifference, gameMode);

            if (result == null) {
                attempts++;
                targetMaxDifference += 10.0; // Increase threshold by 10 ELO

                // Re-shuffle for next attempt
                sortedPlayers.sort((a, b) -> Double.compare(b.getElo(gameMode), a.getElo(gameMode)));
                shuffleWithinEloBrackets(sortedPlayers, config.eloBracketSize, gameMode);
            }
        }

        // If still no result, fall back to greedy algorithm
        if (result == null) {
            result = greedyBalance(sortedPlayers, gameMode);
        }

        return result;
    }

    /**
     * Balances players into 2 teams using MEGA mode ELO (default for team balancing).
     * @param players list of players to balance
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeams(List<CorePlayer> players) {
        return balanceTeams(players, CoreGameMode.MEGA);
    }

    /**
     * Balances players into 2 teams using the adjusted global ELO.
     * This weighs each mode by games played, with Mega mode having extra weight.
     * This is the recommended method for Mega mode team balancing as it accounts
     * for each player's experience across all modes.
     * 
     * @param players list of players to balance
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeamsWeighted(List<CorePlayer> players) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Cannot balance empty player list");
        }
        if (players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players");
        }

        double megaWeight = config.megaWeight;

        // Sort players by adjusted global ELO descending
        List<CorePlayer> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort((a, b) -> Double.compare(
            b.getBalancingElo(megaWeight), 
            a.getBalancingElo(megaWeight)
        ));

        // Add controlled randomization - shuffle within ELO brackets
        shuffleWithinEloBracketsWeighted(sortedPlayers, config.eloBracketSize, megaWeight);

        // Balance teams with iterative threshold adjustment
        double targetMaxDifference = config.targetEloDifference;
        CoreBalanceResult result = null;
        int attempts = 0;
        final int maxAttempts = config.maxBalanceAttempts;

        while (result == null && attempts < maxAttempts) {
            result = tryBalanceTeamsWeighted(sortedPlayers, targetMaxDifference, megaWeight);

            if (result == null) {
                attempts++;
                targetMaxDifference += 10.0;

                // Re-shuffle for next attempt
                sortedPlayers.sort((a, b) -> Double.compare(
                    b.getBalancingElo(megaWeight), 
                    a.getBalancingElo(megaWeight)
                ));
                shuffleWithinEloBracketsWeighted(sortedPlayers, config.eloBracketSize, megaWeight);
            }
        }

        // If still no result, fall back to greedy algorithm
        if (result == null) {
            result = greedyBalanceWeighted(sortedPlayers, megaWeight);
        }

        return result;
    }

    /**
     * Balances players into the specified number of teams using the specified game mode.
     * Note: Currently only 2 teams are supported; more teams will fall back to 2.
     * 
     * @param players list of players to balance
     * @param numTeams number of teams (currently only 2 supported)
     * @param maxPlayersPerTeam maximum players per team (0 = no limit, currently ignored)
     * @param gameMode the game mode to use for ELO lookup
     * @return the balance result
     */
    public CoreBalanceResult balanceTeams(List<CorePlayer> players, int numTeams, int maxPlayersPerTeam, CoreGameMode gameMode) {
        // Currently only supports 2 teams - future expansion possible
        if (numTeams != 2) {
            System.err.println("Warning: Only 2 teams supported, requested " + numTeams);
        }
        return balanceTeams(players, gameMode);
    }

    /**
     * Balances players into the specified number of teams using global ELO.
     * Note: Currently only 2 teams are supported; more teams will fall back to 2.
     * 
     * @param players list of players to balance
     * @param numTeams number of teams (currently only 2 supported)
     * @param maxPlayersPerTeam maximum players per team (0 = no limit, currently ignored)
     * @return the balance result
     */
    public CoreBalanceResult balanceTeams(List<CorePlayer> players, int numTeams, int maxPlayersPerTeam) {
        // Currently only supports 2 teams - future expansion possible
        if (numTeams != 2) {
            System.err.println("Warning: Only 2 teams supported, requested " + numTeams);
        }
        // Use global ELO when no mode specified
        return balanceTeamsUsingGlobalElo(players);
    }

    /**
     * Balances players into 2 teams using their global (weighted average) ELO.
     */
    private CoreBalanceResult balanceTeamsUsingGlobalElo(List<CorePlayer> players) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Cannot balance empty player list");
        }
        if (players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players");
        }

        // Sort players by global ELO descending
        List<CorePlayer> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort((a, b) -> Double.compare(b.getGlobalElo(), a.getGlobalElo()));

        // Add controlled randomization - shuffle within ELO brackets using global ELO
        shuffleWithinEloBracketsGlobal(sortedPlayers, config.eloBracketSize);

        // Greedy balance using global ELO
        List<CorePlayer> team1Players = new ArrayList<>();
        List<CorePlayer> team2Players = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;

        int targetTeamSize = players.size() / 2;

        for (CorePlayer player : sortedPlayers) {
            double playerElo = player.getGlobalElo();

            if (team1Players.size() >= targetTeamSize + 1) {
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() >= targetTeamSize + 1) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else if (team1TotalElo <= team2TotalElo) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else {
                team2Players.add(player);
                team2TotalElo += playerElo;
            }
        }

        // Build the result
        CoreTeam team1 = new CoreTeam();
        CoreTeam team2 = new CoreTeam();
        for (CorePlayer p : team1Players) team1.addPlayer(p, p.getGlobalElo());
        for (CorePlayer p : team2Players) team2.addPlayer(p, p.getGlobalElo());

        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);
        return new CoreBalanceResult(Arrays.asList(team1, team2), eloDifference, 1);
    }

    /**
     * Shuffles within ELO brackets using global ELO.
     */
    private void shuffleWithinEloBracketsGlobal(List<CorePlayer> players, double bracketSize) {
        int start = 0;
        while (start < players.size()) {
            double bracketStart = players.get(start).getGlobalElo();
            int end = start;
            while (end < players.size() &&
                    Math.abs(players.get(end).getGlobalElo() - bracketStart) < bracketSize) {
                end++;
            }
            if (end - start > 1) {
                List<CorePlayer> bracket = players.subList(start, end);
                Collections.shuffle(bracket, random);
            }
            start = end;
        }
    }

    /**
     * Attempts to balance teams within the specified ELO difference threshold.
     * Uses greedy approach with validation and enforces equal team sizes.
     */
    private CoreBalanceResult tryBalanceTeams(List<CorePlayer> players, double maxDifference, CoreGameMode mode) {
        List<CorePlayer> team1Players = new ArrayList<>();
        List<CorePlayer> team2Players = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;

        int targetTeamSize = players.size() / 2;

        for (CorePlayer player : players) {
            double playerElo = player.getElo(mode);

            // Enforce equal team sizes (or differ by 1 if odd number)
            if (team1Players.size() >= targetTeamSize + 1) {
                // Team 1 is full, must assign to team 2
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() >= targetTeamSize + 1) {
                // Team 2 is full, must assign to team 1
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else if (team1Players.size() == targetTeamSize && team2Players.size() < targetTeamSize) {
                // Team 1 reached target, continue filling team 2
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() == targetTeamSize && team1Players.size() < targetTeamSize) {
                // Team 2 reached target, continue filling team 1
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else {
                // Both teams have space, assign to weaker team (by total ELO)
                if (team1TotalElo <= team2TotalElo) {
                    team1Players.add(player);
                    team1TotalElo += playerElo;
                } else {
                    team2Players.add(player);
                    team2TotalElo += playerElo;
                }
            }
        }

        // Verify final balance meets the threshold (using total ELO difference)
        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);

        if (eloDifference <= maxDifference * targetTeamSize) {
            // Build the result
            CoreTeam team1 = new CoreTeam();
            CoreTeam team2 = new CoreTeam();
            for (CorePlayer p : team1Players) team1.addPlayer(p, p.getElo(mode));
            for (CorePlayer p : team2Players) team2.addPlayer(p, p.getElo(mode));

            return new CoreBalanceResult(Arrays.asList(team1, team2), eloDifference, 1);
        }

        return null; // Failed to meet threshold
    }

    /**
     * Fallback greedy balancing without threshold constraints.
     * Ensures equal team sizes.
     */
    private CoreBalanceResult greedyBalance(List<CorePlayer> players, CoreGameMode mode) {
        List<CorePlayer> team1Players = new ArrayList<>();
        List<CorePlayer> team2Players = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;

        int targetTeamSize = players.size() / 2;

        for (CorePlayer player : players) {
            double playerElo = player.getElo(mode);

            // Enforce equal team sizes
            if (team1Players.size() >= targetTeamSize + 1) {
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() >= targetTeamSize + 1) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else if (team1TotalElo <= team2TotalElo) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else {
                team2Players.add(player);
                team2TotalElo += playerElo;
            }
        }

        // Build the result
        CoreTeam team1 = new CoreTeam();
        CoreTeam team2 = new CoreTeam();
        for (CorePlayer p : team1Players) team1.addPlayer(p, p.getElo(mode));
        for (CorePlayer p : team2Players) team2.addPlayer(p, p.getElo(mode));

        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);
        return new CoreBalanceResult(Arrays.asList(team1, team2), eloDifference, 1);
    }

    /**
     * Shuffles players within ELO brackets to add variability while maintaining fairness.
     * Players within the same bracket are randomly shuffled, but brackets maintain their order.
     * This prevents getting the same teams every time while keeping balance.
     */
    private void shuffleWithinEloBrackets(List<CorePlayer> players, double bracketSize, CoreGameMode mode) {
        int start = 0;

        while (start < players.size()) {
            // Find all players in the same ELO bracket
            double bracketStart = players.get(start).getElo(mode);
            int end = start;

            while (end < players.size() &&
                    Math.abs(players.get(end).getElo(mode) - bracketStart) < bracketSize) {
                end++;
            }

            // Shuffle players within this bracket
            if (end - start > 1) {
                List<CorePlayer> bracket = players.subList(start, end);
                Collections.shuffle(bracket, random);
            }

            start = end;
        }
    }

    // ========================================================================
    // WEIGHTED ELO BALANCING (using adjusted global ELO with Mega weight)
    // ========================================================================

    /**
     * Attempts to balance teams using weighted ELO within the specified threshold.
     */
    private CoreBalanceResult tryBalanceTeamsWeighted(List<CorePlayer> players, double maxDifference, double megaWeight) {
        List<CorePlayer> team1Players = new ArrayList<>();
        List<CorePlayer> team2Players = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;

        int targetTeamSize = players.size() / 2;

        for (CorePlayer player : players) {
            double playerElo = player.getBalancingElo(megaWeight);

            // Enforce equal team sizes (or differ by 1 if odd number)
            if (team1Players.size() >= targetTeamSize + 1) {
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() >= targetTeamSize + 1) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else if (team1Players.size() == targetTeamSize && team2Players.size() < targetTeamSize) {
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() == targetTeamSize && team1Players.size() < targetTeamSize) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else {
                // Both teams have space, assign to weaker team (by total ELO)
                if (team1TotalElo <= team2TotalElo) {
                    team1Players.add(player);
                    team1TotalElo += playerElo;
                } else {
                    team2Players.add(player);
                    team2TotalElo += playerElo;
                }
            }
        }

        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);

        if (eloDifference <= maxDifference * targetTeamSize) {
            CoreTeam team1 = new CoreTeam();
            CoreTeam team2 = new CoreTeam();
            for (CorePlayer p : team1Players) team1.addPlayer(p, p.getBalancingElo(megaWeight));
            for (CorePlayer p : team2Players) team2.addPlayer(p, p.getBalancingElo(megaWeight));

            return new CoreBalanceResult(Arrays.asList(team1, team2), eloDifference, 1);
        }

        return null;
    }

    /**
     * Fallback greedy balancing using weighted ELO without threshold constraints.
     */
    private CoreBalanceResult greedyBalanceWeighted(List<CorePlayer> players, double megaWeight) {
        List<CorePlayer> team1Players = new ArrayList<>();
        List<CorePlayer> team2Players = new ArrayList<>();
        double team1TotalElo = 0;
        double team2TotalElo = 0;

        int targetTeamSize = players.size() / 2;

        for (CorePlayer player : players) {
            double playerElo = player.getBalancingElo(megaWeight);

            if (team1Players.size() >= targetTeamSize + 1) {
                team2Players.add(player);
                team2TotalElo += playerElo;
            } else if (team2Players.size() >= targetTeamSize + 1) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else if (team1TotalElo <= team2TotalElo) {
                team1Players.add(player);
                team1TotalElo += playerElo;
            } else {
                team2Players.add(player);
                team2TotalElo += playerElo;
            }
        }

        CoreTeam team1 = new CoreTeam();
        CoreTeam team2 = new CoreTeam();
        for (CorePlayer p : team1Players) team1.addPlayer(p, p.getBalancingElo(megaWeight));
        for (CorePlayer p : team2Players) team2.addPlayer(p, p.getBalancingElo(megaWeight));

        double eloDifference = Math.abs(team1TotalElo - team2TotalElo);
        return new CoreBalanceResult(Arrays.asList(team1, team2), eloDifference, 1);
    }

    /**
     * Shuffles within ELO brackets using weighted ELO.
     */
    private void shuffleWithinEloBracketsWeighted(List<CorePlayer> players, double bracketSize, double megaWeight) {
        int start = 0;
        while (start < players.size()) {
            double bracketStart = players.get(start).getBalancingElo(megaWeight);
            int end = start;
            while (end < players.size() &&
                    Math.abs(players.get(end).getBalancingElo(megaWeight) - bracketStart) < bracketSize) {
                end++;
            }
            if (end - start > 1) {
                List<CorePlayer> bracket = players.subList(start, end);
                Collections.shuffle(bracket, random);
            }
            start = end;
        }
    }
}

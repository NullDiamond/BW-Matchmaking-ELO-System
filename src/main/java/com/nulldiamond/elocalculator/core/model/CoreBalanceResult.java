package com.nulldiamond.elocalculator.core.model;

import java.util.*;

/**
 * Represents the result of team balancing, including teams and balance metrics.
 */
public class CoreBalanceResult {
    private final List<CoreTeam> teams;
    private final double eloDifference;
    private final int attempts;

    public CoreBalanceResult(List<CoreTeam> teams, double eloDifference, int attempts) {
        this.teams = new ArrayList<>(teams);
        this.eloDifference = eloDifference;
        this.attempts = attempts;
    }

    public List<CoreTeam> getTeams() {
        return Collections.unmodifiableList(teams);
    }

    /**
     * Gets the total ELO difference between teams.
     * For 2 teams, this is |team1_total - team2_total|.
     */
    public double getEloDifference() {
        return eloDifference;
    }

    /**
     * @deprecated Use getEloDifference() instead
     */
    @Deprecated
    public double getStandardDeviation() {
        return eloDifference;
    }

    public int getAttempts() {
        return attempts;
    }

    /**
     * Gets the average ELO across all teams.
     */
    public double getAverageTeamElo() {
        return teams.stream()
                .mapToDouble(CoreTeam::getTotalElo)
                .average()
                .orElse(0.0);
    }

    /**
     * Gets the average ELO difference per player.
     * Useful for assessing balance relative to team size.
     */
    public double getAverageEloDifferencePerPlayer() {
        int playersPerTeam = teams.isEmpty() ? 1 : teams.get(0).size();
        return eloDifference / Math.max(1, playersPerTeam);
    }

    /**
     * Checks if the balance meets the target ELO difference.
     * @param targetDifference the target maximum ELO difference
     * @return true if eloDifference <= targetDifference
     */
    public boolean meetsTarget(double targetDifference) {
        return eloDifference <= targetDifference;
    }

    /**
     * Checks if the balance is considered "good" (within 20 ELO per player).
     */
    public boolean isWellBalanced() {
        return getAverageEloDifferencePerPlayer() <= 20.0;
    }

    /**
     * Checks if the balance is considered "reasonable" (within 50 ELO per player).
     */
    public boolean isReasonablyBalanced() {
        return getAverageEloDifferencePerPlayer() <= 50.0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Balance Result (ELO Diff: %.1f, Attempts: %d)\n", eloDifference, attempts));
        for (int i = 0; i < teams.size(); i++) {
            CoreTeam team = teams.get(i);
            sb.append(String.format("  Team %d: %d players, %.1f total ELO, %.1f avg\n",
                    i + 1, team.size(), team.getTotalElo(), team.getAverageElo()));
        }
        return sb.toString();
    }

}

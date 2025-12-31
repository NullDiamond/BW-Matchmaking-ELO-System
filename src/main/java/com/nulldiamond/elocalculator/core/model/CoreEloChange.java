package com.nulldiamond.elocalculator.core.model;

/**
 * Represents an ELO change result for a player after a game.
 */
public class CoreEloChange {
    private final String playerId;
    private final CoreGameMode gameMode;
    private final double previousElo;
    private final double newElo;
    private final double change;
    private final double performanceScore;

    public CoreEloChange(String playerId, CoreGameMode gameMode, double previousElo, double change, double performanceScore) {
        this.playerId = playerId;
        this.gameMode = gameMode;
        this.previousElo = previousElo;
        this.change = change;
        this.newElo = previousElo + change;
        this.performanceScore = performanceScore;
    }

    public String getPlayerId() {
        return playerId;
    }

    public CoreGameMode getGameMode() {
        return gameMode;
    }

    public double getPreviousElo() {
        return previousElo;
    }

    public double getNewElo() {
        return newElo;
    }

    public double getChange() {
        return change;
    }

    public double getPerformanceScore() {
        return performanceScore;
    }

    @Override
    public String toString() {
        String sign = change >= 0 ? "+" : "";
        return String.format("%s [%s]: %.1f -> %.1f (%s%.1f) [perf: %.2f]",
                playerId, gameMode, previousElo, newElo, sign, change, performanceScore);
    }
}

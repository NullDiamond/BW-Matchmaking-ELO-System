package com.example.elocalculator.model.leaderboard;

import com.example.elocalculator.model.player.PlayerIdentifier;

/**
 * Represents an entry in a mode-specific leaderboard.
 * Extends PlayerIdentifier for player info and GameStatistics for game stats.
 */
public class LeaderboardEntry extends PlayerIdentifier {
    private int rank;
    private double elo;
    private int games;
    private double avgPerformance;
    private double avgNormalizedPerformance;
    private int victories;
    private int bedBreaks;
    private int kills;
    private int deaths;
    private int finalKills;
    private int invalidGames;
    private boolean legacyRankedPlayer;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getElo() {
        return elo;
    }

    public void setElo(double elo) {
        this.elo = elo;
    }

    public int getGames() {
        return games;
    }

    public void setGames(int games) {
        this.games = games;
    }

    public double getAvgPerformance() {
        return avgPerformance;
    }

    public void setAvgPerformance(double avgPerformance) {
        this.avgPerformance = avgPerformance;
    }

    public double getAvgNormalizedPerformance() {
        return avgNormalizedPerformance;
    }

    public void setAvgNormalizedPerformance(double avgNormalizedPerformance) {
        this.avgNormalizedPerformance = avgNormalizedPerformance;
    }

    public int getVictories() {
        return victories;
    }

    public void setVictories(int victories) {
        this.victories = victories;
    }

    public int getBedBreaks() {
        return bedBreaks;
    }

    public void setBedBreaks(int bedBreaks) {
        this.bedBreaks = bedBreaks;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public void setFinalKills(int finalKills) {
        this.finalKills = finalKills;
    }

    public int getInvalidGames() {
        return invalidGames;
    }

    public void setInvalidGames(int invalidGames) {
        this.invalidGames = invalidGames;
    }

    public boolean isLegacyRankedPlayer() {
        return legacyRankedPlayer;
    }

    public void setLegacyRankedPlayer(boolean legacyRankedPlayer) {
        this.legacyRankedPlayer = legacyRankedPlayer;
    }
}

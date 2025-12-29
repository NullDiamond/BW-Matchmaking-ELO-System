package com.sbmm.elocalculator.model.result;

/**
 * Contains results for a player in a specific game mode.
 */
public class ModeResult {
    private int kills = 0;
    private int deaths = 0;
    private int bedBreaks = 0;
    private int finalKills = 0;
    private int victories = 0;
    private double elo;
    private int games;
    private double avgPerformance;
    private double avgNormalizedPerformance;
    private int invalidGames;
    private int invalidWins;

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

    public int getBedBreaks() {
        return bedBreaks;
    }

    public void setBedBreaks(int bedBreaks) {
        this.bedBreaks = bedBreaks;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public void setFinalKills(int finalKills) {
        this.finalKills = finalKills;
    }

    public int getVictories() {
        return victories;
    }

    public void setVictories(int victories) {
        this.victories = victories;
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

    public int getInvalidGames() {
        return invalidGames;
    }

    public void setInvalidGames(int invalidGames) {
        this.invalidGames = invalidGames;
    }

    public int getInvalidWins() {
        return invalidWins;
    }

    public void setInvalidWins(int invalidWins) {
        this.invalidWins = invalidWins;
    }
}




package com.sbmm.elocalculator.model.elo;

/**
 * Represents an ELO rating record with the rating value and number of games played.
 * Used across multiple model classes to reduce code duplication.
 */
public class EloRecord {
    private double elo;
    private int games;

    public EloRecord() {}

    public EloRecord(double elo, int games) {
        this.elo = elo;
        this.games = games;
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
}





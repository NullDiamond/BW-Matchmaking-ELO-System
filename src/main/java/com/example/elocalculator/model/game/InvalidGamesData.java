package com.example.elocalculator.model.game;

/**
 * Represents invalid games data for a specific mode.
 */
public class InvalidGamesData {
    private int total = 0;
    private int wins = 0;
    private java.util.List<String> gameIds = new java.util.ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public java.util.List<String> getGameIds() {
        return gameIds;
    }

    public void setGameIds(java.util.List<String> gameIds) {
        this.gameIds = gameIds;
    }

    public void incrementTotal() {
        this.total++;
    }

    public void incrementWins() {
        this.wins++;
    }

    public void addGameId(String gameId) {
        this.gameIds.add(gameId);
    }
}

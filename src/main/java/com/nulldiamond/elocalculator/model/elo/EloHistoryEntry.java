package com.nulldiamond.elocalculator.model.elo;

import java.time.Instant;

/**
 * Represents a single ELO rating change in a player's history.
 */
public class EloHistoryEntry {
    private String gameId;
    private String gameMode;
    private long timestamp;
    private double previousElo;
    private double newElo;
    private double eloChange;
    private boolean won;
    private boolean isTie;

    /**
     * Default constructor for JSON deserialization.
     */
    public EloHistoryEntry() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    /**
     * Constructor with all fields.
     * @param gameId the game ID
     * @param gameMode the game mode (solo, duo, trio, fours, mega, global)
     * @param previousElo the ELO before the match
     * @param newElo the ELO after the match
     * @param won whether the player won the match
     */
    public EloHistoryEntry(String gameId, String gameMode, double previousElo, double newElo, boolean won) {
        this.gameId = gameId;
        this.gameMode = gameMode;
        this.timestamp = Instant.now().toEpochMilli();
        this.previousElo = previousElo;
        this.newElo = newElo;
        this.eloChange = newElo - previousElo;
        this.won = won;
        this.isTie = false;
    }

    /**
     * Constructor with all fields including tie status.
     * @param gameId the game ID
     * @param gameMode the game mode (solo, duo, trio, fours, mega, global)
     * @param previousElo the ELO before the match
     * @param newElo the ELO after the match
     * @param won whether the player won the match
     * @param isTie whether the match was a tie
     */
    public EloHistoryEntry(String gameId, String gameMode, double previousElo, double newElo, boolean won, boolean isTie) {
        this.gameId = gameId;
        this.gameMode = gameMode;
        this.timestamp = Instant.now().toEpochMilli();
        this.previousElo = previousElo;
        this.newElo = newElo;
        this.eloChange = newElo - previousElo;
        this.won = won;
        this.isTie = isTie;
    }

    /**
     * Constructor with all fields including tie status and actual match timestamp.
     * @param gameId the game ID
     * @param gameMode the game mode (solo, duo, trio, fours, mega, global)
     * @param timestamp the actual unix timestamp of the match in milliseconds
     * @param previousElo the ELO before the match
     * @param newElo the ELO after the match
     * @param won whether the player won the match
     * @param isTie whether the match was a tie
     */
    public EloHistoryEntry(String gameId, String gameMode, long timestamp, double previousElo, double newElo, boolean won, boolean isTie) {
        this.gameId = gameId;
        this.gameMode = gameMode;
        this.timestamp = timestamp;
        this.previousElo = previousElo;
        this.newElo = newElo;
        this.eloChange = newElo - previousElo;
        this.won = won;
        this.isTie = isTie;
    }

    // Getters and setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getPreviousElo() {
        return previousElo;
    }

    public void setPreviousElo(double previousElo) {
        this.previousElo = previousElo;
    }

    public double getNewElo() {
        return newElo;
    }

    public void setNewElo(double newElo) {
        this.newElo = newElo;
        this.eloChange = newElo - previousElo;
    }

    public double getEloChange() {
        return eloChange;
    }

    public void setEloChange(double eloChange) {
        this.eloChange = eloChange;
    }

    public boolean isWon() {
        return won;
    }

    public void setWon(boolean won) {
        this.won = won;
    }

    public boolean isTie() {
        return isTie;
    }

    public void setTie(boolean tie) {
        this.isTie = tie;
    }

    @Override
    public String toString() {
        String result = isTie ? "TIE" : (won ? "WIN" : "LOSS");
        return String.format("[%s] %s - %s: %.2f -> %.2f (%+.2f) %s",
                gameId, gameMode, 
                java.time.Instant.ofEpochMilli(timestamp).toString(),
                previousElo, newElo, eloChange,
                result);
    }
}





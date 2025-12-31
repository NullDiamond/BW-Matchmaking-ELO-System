package com.nulldiamond.elocalculator.model.result;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents a player's entry in the invalid games report.
 * Fields are ordered for readability in JSON output.
 */
public class PlayerInvalidGamesEntry {
    // Player identification first
    @SerializedName(value = "rank")
    private int rank;
    
    @SerializedName(value = "uuid")
    private String uuid;
    
    @SerializedName(value = "name")
    private String name;
    
    // Statistics
    @SerializedName(value = "invalidGames")
    private int invalidGames;
    
    @SerializedName(value = "invalidWins")
    private int invalidWins;
    
    @SerializedName(value = "validGames")
    private int validGames;
    
    @SerializedName(value = "invalidPercentage")
    private double invalidPercentage;
    
    // Game IDs last (can be long)
    @SerializedName(value = "gameIds")
    private List<String> gameIds;

    // Getters and setters for player identification
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
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

    public int getValidGames() {
        return validGames;
    }

    public void setValidGames(int validGames) {
        this.validGames = validGames;
    }

    public double getInvalidPercentage() {
        return invalidPercentage;
    }

    public void setInvalidPercentage(double invalidPercentage) {
        this.invalidPercentage = invalidPercentage;
    }

    public List<String> getGameIds() {
        return gameIds;
    }

    public void setGameIds(List<String> gameIds) {
        this.gameIds = gameIds;
    }
}





package com.nulldiamond.elocalculator.core.model;

/**
 * Represents a player's statistics for a single game in the CORE system.
 */
public class CorePlayerStats {
    private int kills;
    private int deaths;
    private int bedBreaks;
    private int finalKills;

    public CorePlayerStats() {
        this.kills = 0;
        this.deaths = 0;
        this.bedBreaks = 0;
        this.finalKills = 0;
    }

    public CorePlayerStats(int kills, int deaths, int bedBreaks, int finalKills) {
        this.kills = kills;
        this.deaths = deaths;
        this.bedBreaks = bedBreaks;
        this.finalKills = finalKills;
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

    @Override
    public String toString() {
        return String.format("K:%d D:%d BB:%d FK:%d", kills, deaths, bedBreaks, finalKills);
    }
}

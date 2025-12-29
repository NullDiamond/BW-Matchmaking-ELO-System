package com.sbmm.elocalculator.model.game;

import com.google.gson.annotations.SerializedName;

/**
 * Base class representing game statistics.
 * Used across multiple model classes to reduce code duplication.
 */
public class GameStatistics {
    public int kills = 0;
    public int deaths = 0;
    @SerializedName("bed_breaks")
    public int bedBreaks = 0;
    @SerializedName("final_kills")
    public int finalKills = 0;
    public int victories = 0;

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
}





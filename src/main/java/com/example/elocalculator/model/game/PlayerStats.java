package com.example.elocalculator.model.game;

/**
 * Represents the statistics for a player in a single game.
 * Note: Uses snake_case naming (bed_breaks, final_kills) for JSON serialization compatibility.
 */
public class PlayerStats extends GameStatistics {
    
    // Override getters/setters to support snake_case JSON field names
    public int getBed_breaks() {
        return getBedBreaks();
    }

    public void setBed_breaks(int bed_breaks) {
        setBedBreaks(bed_breaks);
    }

    public int getFinal_kills() {
        return getFinalKills();
    }

    public void setFinal_kills(int final_kills) {
        setFinalKills(final_kills);
    }
}

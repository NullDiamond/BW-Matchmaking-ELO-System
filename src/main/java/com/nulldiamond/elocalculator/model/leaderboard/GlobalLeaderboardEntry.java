package com.nulldiamond.elocalculator.model.leaderboard;

import com.nulldiamond.elocalculator.model.player.PlayerIdentifier;
import com.nulldiamond.elocalculator.model.elo.EloRecord;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an entry in the global leaderboard.
 * Extends PlayerIdentifier to inherit player identification fields.
 */
public class GlobalLeaderboardEntry extends PlayerIdentifier {
    private int rank;
    private double globalElo;
    private int totalGames;
    private boolean legacyRankedPlayer;
    public final Map<String, EloRecord> modes = new HashMap<>();

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getGlobalElo() {
        return globalElo;
    }

    public void setGlobalElo(double globalElo) {
        this.globalElo = globalElo;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }

    public boolean isLegacyRankedPlayer() {
        return legacyRankedPlayer;
    }

    public void setLegacyRankedPlayer(boolean legacyRankedPlayer) {
        this.legacyRankedPlayer = legacyRankedPlayer;
    }

    public Map<String, EloRecord> getModes() {
        return modes;
    }
}




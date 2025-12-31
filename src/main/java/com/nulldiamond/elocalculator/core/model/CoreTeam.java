package com.nulldiamond.elocalculator.core.model;

import java.util.*;

/**
 * Represents a balanced team for the team balancer output.
 */
public class CoreTeam {
    private final List<CorePlayer> players;
    private double totalElo;

    public CoreTeam() {
        this.players = new ArrayList<>();
        this.totalElo = 0.0;
    }

    /**
     * Adds a player to this team with a specific ELO value.
     * Used when balancing by a specific game mode.
     * @param player the player to add
     * @param elo the ELO value to use for this player
     */
    public void addPlayer(CorePlayer player, double elo) {
        players.add(player);
        totalElo += elo;
    }

    /**
     * Adds a player to this team using their default (SOLO) ELO.
     * @param player the player to add
     */
    public void addPlayer(CorePlayer player) {
        addPlayer(player, player.getElo());
    }

    /**
     * Gets all player IDs in this team.
     * @return list of player IDs
     */
    public List<String> getPlayerIds() {
        List<String> ids = new ArrayList<>();
        for (CorePlayer p : players) {
            ids.add(p.getId());
        }
        return ids;
    }

    public List<CorePlayer> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public double getTotalElo() {
        return totalElo;
    }

    public double getAverageElo() {
        return players.isEmpty() ? 0.0 : totalElo / players.size();
    }

    public int size() {
        return players.size();
    }

    @Override
    public String toString() {
        return String.format("Team(%d players, %.1f total ELO)", players.size(), totalElo);
    }
}

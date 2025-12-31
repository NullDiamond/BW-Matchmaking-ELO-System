package com.nulldiamond.elocalculator.model.player;

/**
 * Player information for team balancing.
 * Extends PlayerIdentifier and adds ELO and games information.
 */
public class PlayerInfo extends PlayerIdentifier {
    private final double elo;
    private final int games;

    public PlayerInfo(String uuid, String name, double elo, int games) {
        super(uuid, name);
        this.elo = elo;
        this.games = games;
    }

    public double getElo() {
        return elo;
    }

    public int getGames() {
        return games;
    }
}





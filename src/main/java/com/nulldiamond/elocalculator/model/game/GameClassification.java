package com.nulldiamond.elocalculator.model.game;

/**
 * Represents a classified game with its data and winner information.
 */
public class GameClassification {
    private final String gameId;
    private final GameData gameData;
    private final String winnerTeam;

    public GameClassification(String gameId, GameData gameData, String winnerTeam) {
        this.gameId = gameId;
        this.gameData = gameData;
        this.winnerTeam = winnerTeam;
    }

    public String getGameId() {
        return gameId;
    }

    public GameData getGameData() {
        return gameData;
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }
}




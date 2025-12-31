package com.nulldiamond.elocalculator.core.model;

import java.util.*;

/**
 * Represents a game in the CORE ELO system.
 * Contains teams, winner, game mode, and player statistics.
 */
public class CoreGame {
    private final String gameId;
    private final long timestamp;
    private final Map<String, List<String>> teams; // teamName -> list of player IDs
    private final Map<String, CorePlayerStats> playerStats; // playerID -> stats
    private String winnerTeam; // null for tie
    private CoreGameMode gameMode;

    /**
     * Creates a new game with the specified ID and game mode.
     * @param gameId unique game identifier
     * @param gameMode the game mode
     */
    public CoreGame(String gameId, CoreGameMode gameMode) {
        this.gameId = gameId;
        this.timestamp = System.currentTimeMillis();
        this.teams = new LinkedHashMap<>();
        this.playerStats = new HashMap<>();
        this.winnerTeam = null;
        this.gameMode = gameMode;
    }

    /**
     * Creates a new game with the specified ID (defaults to SOLO mode).
     * @param gameId unique game identifier
     */
    public CoreGame(String gameId) {
        this(gameId, CoreGameMode.SOLO);
    }

    /**
     * Creates a new game with auto-generated ID (defaults to SOLO mode).
     */
    public CoreGame() {
        this(UUID.randomUUID().toString(), CoreGameMode.SOLO);
    }

    public String getGameId() {
        return gameId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, List<String>> getTeams() {
        return Collections.unmodifiableMap(teams);
    }

    public Map<String, CorePlayerStats> getPlayerStats() {
        return Collections.unmodifiableMap(playerStats);
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(String winnerTeam) {
        this.winnerTeam = winnerTeam;
    }

    public CoreGameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(CoreGameMode gameMode) {
        this.gameMode = gameMode;
    }

    /**
     * Checks if this is a mega mode game.
     * @return true if mega mode
     */
    public boolean isMegaMode() {
        return gameMode == CoreGameMode.MEGA;
    }

    /**
     * Sets mega mode (convenience method).
     * @param megaMode if true, sets mode to MEGA
     */
    public void setMegaMode(boolean megaMode) {
        this.gameMode = megaMode ? CoreGameMode.MEGA : CoreGameMode.SOLO;
    }

    /**
     * Adds a team with its players.
     * @param teamName the team name/identifier
     * @param playerIds list of player IDs on this team
     */
    public void addTeam(String teamName, List<String> playerIds) {
        teams.put(teamName, new ArrayList<>(playerIds));
    }

    /**
     * Sets the statistics for a player in this game.
     * @param playerId the player's ID
     * @param stats the player's game statistics
     */
    public void setPlayerStats(String playerId, CorePlayerStats stats) {
        playerStats.put(playerId, stats);
    }

    /**
     * Gets all player IDs in this game.
     * @return set of all player IDs
     */
    public Set<String> getAllPlayerIds() {
        Set<String> allPlayers = new HashSet<>();
        for (List<String> teamPlayers : teams.values()) {
            allPlayers.addAll(teamPlayers);
        }
        return allPlayers;
    }

    /**
     * Gets the team name for a player.
     * @param playerId the player's ID
     * @return the team name, or null if player not found
     */
    public String getPlayerTeam(String playerId) {
        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Checks if the specified team won this game.
     * @param teamName the team to check
     * @return true if this team won
     */
    public boolean isWinner(String teamName) {
        return teamName != null && teamName.equals(winnerTeam);
    }

    /**
     * Checks if this game was a tie.
     * @return true if the game was a tie
     */
    public boolean isTie() {
        return "Tie".equals(winnerTeam);
    }

    /**
     * Creates a new Builder for constructing a CoreGame.
     * @param gameId unique game identifier
     * @param gameMode the game mode
     * @return a new Builder instance
     */
    public static Builder builder(String gameId, CoreGameMode gameMode) {
        return new Builder(gameId, gameMode);
    }

    /**
     * Builder class for constructing CoreGame instances.
     */
    public static class Builder {
        private final CoreGame game;

        private Builder(String gameId, CoreGameMode gameMode) {
            this.game = new CoreGame(gameId, gameMode);
        }

        /**
         * Adds a team with its players.
         * @param teamName the team name
         * @param playerIds list of player IDs
         * @return this builder
         */
        public Builder withTeam(String teamName, List<String> playerIds) {
            game.addTeam(teamName, playerIds);
            return this;
        }

        /**
         * Sets the winning team(s).
         * @param winnerTeams list of winning team names (first is used)
         * @return this builder
         */
        public Builder withWinners(List<String> winnerTeams) {
            if (winnerTeams != null && !winnerTeams.isEmpty()) {
                game.setWinnerTeam(winnerTeams.get(0));
            }
            return this;
        }

        /**
         * Sets the winning team.
         * @param winnerTeam the winning team name
         * @return this builder
         */
        public Builder withWinner(String winnerTeam) {
            game.setWinnerTeam(winnerTeam);
            return this;
        }

        /**
         * Sets this game as a tie.
         * @return this builder
         */
        public Builder withTie() {
            game.setWinnerTeam("Tie");
            return this;
        }

        /**
         * Adds player statistics.
         * @param playerId the player's ID
         * @param stats the player's game statistics
         * @return this builder
         */
        public Builder withPlayerStats(String playerId, CorePlayerStats stats) {
            game.setPlayerStats(playerId, stats);
            return this;
        }

        /**
         * Builds and returns the CoreGame.
         * @return the constructed CoreGame
         */
        public CoreGame build() {
            return game;
        }
    }

    @Override

    public String toString() {
        return String.format("Game[%s] - Winner: %s, Teams: %d, Players: %d",
                gameId.substring(0, Math.min(8, gameId.length())),
                winnerTeam != null ? winnerTeam : "Tie",
                teams.size(),
                getAllPlayerIds().size());
    }
}

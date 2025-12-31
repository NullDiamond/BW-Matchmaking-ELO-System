package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.*;

import java.util.*;

/**
 * Simple, clean API for the CORE ELO system.
 * 
 * This class provides a entry over the CoreEloSystem with the most commonly
 * used operations exposed in a straightforward manner.
 * 
 * Features:
 * - Add games and calculate ELO changes
 * - Undo recently added games
 * - Balance teams for matchmaking
 * - Per-mode ELO support (SOLO, DUO, TRIO, FOURS, MEGA)
 * - Global leaderboards
 * 
 * All data is stored in memory - no file I/O.
 * 
 * Example usage:
 * <pre>
 * CoreAPI api = new CoreAPI();
 * 
 * // Register players
 * api.registerPlayer("player1", "Alice");
 * api.registerPlayer("player2", "Bob");
 * 
 * // Balance teams for a FOURS game
 * CoreBalanceResult result = api.balanceTeams(
 *     Arrays.asList("player1", "player2", "player3", "player4"),
 *     CoreGameMode.FOURS
 * );
 * 
 * // Add a game result
 * CoreGame game = CoreGame.builder("game-123", CoreGameMode.FOURS)
 *     .withTeam("team1", Arrays.asList("player1", "player2"))
 *     .withTeam("team2", Arrays.asList("player3", "player4"))
 *     .withWinners(Arrays.asList("team1"))
 *     .build();
 * api.addGame(game);
 * 
 * // Undo if needed
 * api.undoLastGame();
 * </pre>
 */
public class CoreAPI {
    private final CoreEloSystem system;

    /**
     * Creates a new CoreAPI with default configuration.
     */
    public CoreAPI() {
        this.system = new CoreEloSystem();
    }

    /**
     * Creates a new CoreAPI with custom configuration.
     */
    public CoreAPI(CoreConfig config) {
        this.system = new CoreEloSystem(config);
    }

    /**
     * Creates a new CoreAPI wrapping an existing CoreEloSystem.
     */
    public CoreAPI(CoreEloSystem system) {
        this.system = system;
    }

    // ========================================================================
    // PLAYER MANAGEMENT
    // ========================================================================

    /**
     * Registers a new player with initial ELO.
     * 
     * @param id unique player identifier
     * @param name player display name
     * @return the registered player
     * @throws IllegalArgumentException if player already exists
     */
    public CorePlayer registerPlayer(String id, String name) {
        return system.registerPlayer(id, name);
    }

    /**
     * Registers a new player with custom initial ELO.
     * 
     * @param id unique player identifier
     * @param name player display name
     * @param initialElo custom starting ELO
     * @return the registered player
     */
    public CorePlayer registerPlayer(String id, String name, double initialElo) {
        return system.registerPlayer(id, name, initialElo);
    }

    /**
     * Gets or creates a player. Safe to call multiple times.
     * 
     * @param id unique player identifier
     * @param name player display name (used if creating)
     * @return the player
     */
    public CorePlayer getOrCreatePlayer(String id, String name) {
        return system.getOrCreatePlayer(id, name);
    }

    /**
     * Gets a player by ID.
     * 
     * @param id player identifier
     * @return the player, or null if not found
     */
    public CorePlayer getPlayer(String id) {
        return system.getPlayer(id);
    }

    /**
     * Gets a player's ELO for a specific game mode.
     * 
     * @param playerId player identifier
     * @param mode game mode
     * @return the player's ELO, or null if player not found
     */
    public Double getPlayerElo(String playerId, CoreGameMode mode) {
        CorePlayer player = system.getPlayer(playerId);
        return player != null ? player.getElo(mode) : null;
    }

    /**
     * Gets a player's global (weighted average) ELO.
     * 
     * @param playerId player identifier
     * @return the player's global ELO, or null if player not found
     */
    public Double getPlayerGlobalElo(String playerId) {
        CorePlayer player = system.getPlayer(playerId);
        return player != null ? player.getGlobalElo() : null;
    }

    /**
     * Gets all registered players.
     * 
     * @return unmodifiable collection of all players
     */
    public Collection<CorePlayer> getAllPlayers() {
        return system.getAllPlayers();
    }

    /**
     * Gets the number of registered players.
     */
    public int getPlayerCount() {
        return system.getPlayerCount();
    }

    // ========================================================================
    // LEGACY PLAYERS
    // ========================================================================

    /**
     * Sets the legacy player IDs.
     * Legacy players get boosted initial ELO (config.legacyPlayerElo) when first created.
     * This should be called before adding any games.
     * 
     * @param playerIds collection of player IDs to mark as legacy
     */
    public void setLegacyPlayers(Collection<String> playerIds) {
        system.setLegacyPlayers(playerIds);
    }

    /**
     * Adds a player ID to the legacy players set.
     * 
     * @param playerId the player ID to add
     */
    public void addLegacyPlayer(String playerId) {
        system.addLegacyPlayer(playerId);
    }

    /**
     * Checks if a player is a legacy player.
     * 
     * @param playerId the player ID to check
     * @return true if the player is in the legacy set
     */
    public boolean isLegacyPlayer(String playerId) {
        return system.isLegacyPlayer(playerId);
    }

    /**
     * Gets all legacy player IDs.
     * 
     * @return unmodifiable set of legacy player IDs
     */
    public Set<String> getLegacyPlayers() {
        return system.getLegacyPlayers();
    }

    // ========================================================================
    // GAME MANAGEMENT
    // ========================================================================

    /**
     * Adds and processes a game, updating player ELOs.
     * 
     * @param game the completed game
     * @return the result with all ELO changes
     */
    public CoreGameResult addGame(CoreGame game) {
        return system.addGame(game);
    }

    /**
     * Adds and processes a game with validation.
     * If valid, ELO changes are applied.
     * If invalid, invalid game counters are incremented but ELO is not changed.
     * 
     * @param game the completed game
     * @return AddGameResult indicating whether valid and the appropriate result
     */
    public CoreEloSystem.AddGameResult addGameWithValidation(CoreGame game) {
        return system.addGameWithValidation(game);
    }

    /**
     * Validates a game without processing it.
     * 
     * @param game the game to validate
     * @return validation result (isValid(), getReason())
     */
    public CoreGameValidator.ValidationResult validateGame(CoreGame game) {
        return system.validateGame(game);
    }

    /**
     * Checks if a game would be valid if processed.
     * 
     * @param game the game to check
     * @return true if the game is valid
     */
    public boolean isValidGame(CoreGame game) {
        return system.isValidGame(game);
    }

    /**
     * Quick method to add a 2-team game result.
     * 
     * @param gameId unique game identifier
     * @param mode game mode
     * @param team1Players player IDs for team 1
     * @param team2Players player IDs for team 2
     * @param team1Won true if team 1 won, false if team 2 won
     * @return the result with all ELO changes
     */
    public CoreGameResult addGame(String gameId, CoreGameMode mode,
                                   List<String> team1Players, List<String> team2Players,
                                   boolean team1Won) {
        List<String> winners = team1Won ? Arrays.asList("team1") : Arrays.asList("team2");
        CoreGame game = CoreGame.builder(gameId, mode)
                .withTeam("team1", team1Players)
                .withTeam("team2", team2Players)
                .withWinners(winners)
                .build();
        return system.addGame(game);
    }

    /**
     * Undoes the most recently added game.
     * 
     * @return the undone game result, or null if nothing to undo
     */
    public CoreGameResult undoLastGame() {
        return system.deleteRecentGame();
    }

    /**
     * Undoes a specific game by ID.
     * 
     * @param gameId the game ID to undo
     * @return the undone game result, or null if not found
     */
    public CoreGameResult undoGame(String gameId) {
        return system.deleteRecentGame(gameId);
    }

    /**
     * Gets the list of recent games available for undo.
     */
    public List<CoreGameResult> getRecentGames() {
        return system.getRecentGames();
    }

    /**
     * Gets the number of games available to undo.
     */
    public int getUndoableGamesCount() {
        return system.getRecentGamesCount();
    }

    /**
     * Gets the list of recent invalid games.
     */
    public List<CoreInvalidGameResult> getRecentInvalidGames() {
        return system.getRecentInvalidGames();
    }

    /**
     * Gets the number of recent invalid games.
     */
    public int getInvalidGamesCount() {
        return system.getRecentInvalidGamesCount();
    }

    /**
     * Gets a player's invalid game count for a specific mode.
     * 
     * @param playerId player identifier
     * @param mode game mode
     * @return the invalid game count, or 0 if player not found
     */
    public int getPlayerInvalidGamesCount(String playerId, CoreGameMode mode) {
        CorePlayer player = system.getPlayer(playerId);
        return player != null ? player.getInvalidGamesCount(mode) : 0;
    }

    /**
     * Gets a player's total invalid game count across all modes.
     * 
     * @param playerId player identifier
     * @return the total invalid game count, or 0 if player not found
     */
    public int getPlayerTotalInvalidGamesCount(String playerId) {
        CorePlayer player = system.getPlayer(playerId);
        return player != null ? player.getTotalInvalidGamesCount() : 0;
    }

    // ========================================================================
    // TEAM BALANCING
    // ========================================================================

    /**
     * Balances players into 2 teams for a specific game mode.
     * 
     * @param playerIds list of player IDs to balance
     * @param mode game mode (determines which ELO to use)
     * @return the balance result with team assignments
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds, CoreGameMode mode) {
        return system.balanceTeams(playerIds, mode);
    }

    /**
     * Balances players into 2 teams using their global ELO.
     * 
     * @param playerIds list of player IDs to balance
     * @return the balance result with team assignments
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds) {
        return system.balanceTeams(playerIds);
    }

    /**
     * Balances players into 2 teams using weighted ELO.
     * This uses adjusted global ELO which weighs each mode by games played,
     * with Mega mode having extra weight (configurable via megaWeight).
     * 
     * This is the recommended method for Mega mode team balancing as it accounts
     * for each player's experience across all modes.
     * 
     * @param playerIds list of player IDs to balance
     * @return the balance result with team assignments
     */
    public CoreBalanceResult balanceTeamsWeighted(List<String> playerIds) {
        return system.balanceTeamsWeighted(playerIds);
    }

    /**
     * Gets a player's adjusted global ELO with Mega weighting.
     * This is the ELO used for weighted team balancing.
     * 
     * @param playerId player identifier
     * @return the adjusted global ELO, or null if player not found
     */
    public Double getPlayerAdjustedElo(String playerId) {
        CorePlayer player = system.getPlayer(playerId);
        return player != null ? player.getBalancingElo(system.getConfig().megaWeight) : null;
    }

    /**
     * Balances players into the specified number of teams for a game mode.
     * Note: Currently only 2 teams are supported.
     * 
     * @param playerIds list of player IDs to balance
     * @param numTeams number of teams to create
     * @param maxPlayersPerTeam max players per team (0 = no limit)
     * @param mode game mode
     * @return the balance result
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds, int numTeams, 
                                           int maxPlayersPerTeam, CoreGameMode mode) {
        return system.balanceTeams(playerIds, numTeams, maxPlayersPerTeam, mode);
    }

    /**
     * Gets a simple string representation of balanced teams.
     * Useful for display purposes.
     * 
     * @param result the balance result
     * @return formatted string showing team assignments
     */
    public String formatBalanceResult(CoreBalanceResult result) {
        StringBuilder sb = new StringBuilder();
        List<CoreTeam> teams = result.getTeams();
        
        for (int i = 0; i < teams.size(); i++) {
            CoreTeam team = teams.get(i);
            sb.append(String.format("Team %d (ELO: %.0f):\n", i + 1, team.getTotalElo()));
            
            for (CorePlayer player : team.getPlayers()) {
                sb.append(String.format("  - %s\n", player.getName()));
            }
        }
        
        sb.append(String.format("\nELO Difference: %.0f\n", result.getEloDifference()));
        sb.append(String.format("Balanced: %s\n", result.isWellBalanced() ? "Yes" : "No"));
        
        return sb.toString();
    }

    // ========================================================================
    // LEADERBOARD
    // ========================================================================

    /**
     * Gets the top players by ELO for a specific game mode.
     * 
     * @param mode game mode
     * @param limit maximum players to return
     * @return list of top players
     */
    public List<CorePlayer> getLeaderboard(CoreGameMode mode, int limit) {
        return system.getLeaderboard(mode, limit);
    }

    /**
     * Gets all players sorted by ELO for a specific game mode.
     * 
     * @param mode game mode
     * @return sorted list of all players
     */
    public List<CorePlayer> getLeaderboard(CoreGameMode mode) {
        return system.getLeaderboard(mode);
    }

    /**
     * Gets the top players by global ELO.
     * 
     * @param limit maximum players to return
     * @return list of top players
     */
    public List<CorePlayer> getLeaderboard(int limit) {
        return system.getLeaderboard(limit);
    }

    /**
     * Gets all players sorted by global ELO.
     * 
     * @return sorted list of all players
     */
    public List<CorePlayer> getLeaderboard() {
        return system.getLeaderboard();
    }

    /**
     * Gets a formatted leaderboard string for a game mode.
     * 
     * @param mode game mode
     * @param limit maximum players to show
     * @return formatted leaderboard string
     */
    public String formatLeaderboard(CoreGameMode mode, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s Leaderboard ===\n", mode.getDisplayName()));
        
        List<CorePlayer> leaders = system.getLeaderboard(mode, limit);
        for (int i = 0; i < leaders.size(); i++) {
            CorePlayer p = leaders.get(i);
            sb.append(String.format("%d. %s - %.0f ELO (%d games)\n",
                    i + 1, p.getName(), p.getElo(mode), p.getGamesPlayed(mode)));
        }
        
        return sb.toString();
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Gets the underlying CoreEloSystem for advanced operations.
     */
    public CoreEloSystem getSystem() {
        return system;
    }

    /**
     * Gets the current configuration.
     */
    public CoreConfig getConfig() {
        return system.getConfig();
    }

    /**
     * Resets all data (players, games).
     */
    public void reset() {
        system.reset();
    }

    /**
     * Creates a game builder for constructing games.
     * 
     * @param gameId unique game identifier
     * @param mode game mode
     * @return a new game builder
     */
    public CoreGame.Builder gameBuilder(String gameId, CoreGameMode mode) {
        return CoreGame.builder(gameId, mode);
    }

    /**
     * Quick check if the system has any players.
     */
    public boolean hasPlayers() {
        return system.getPlayerCount() > 0;
    }

    /**
     * Quick check if there are games available to undo.
     */
    public boolean canUndo() {
        return system.getRecentGamesCount() > 0;
    }

    /**
     * Prints a summary of the current system state.
     */
    public void printStatus() {
        System.out.println("=== Core ELO System Status ===");
        System.out.println("Players: " + system.getPlayerCount());
        System.out.println("Undoable Games: " + system.getRecentGamesCount());
        
        if (system.getPlayerCount() > 0) {
            System.out.println("\nTop 5 by Global ELO:");
            List<CorePlayer> top = system.getLeaderboard(5);
            for (int i = 0; i < top.size(); i++) {
                CorePlayer p = top.get(i);
                System.out.printf("  %d. %s - %.0f\n", i + 1, p.getName(), p.getGlobalElo());
            }
        }
    }
}

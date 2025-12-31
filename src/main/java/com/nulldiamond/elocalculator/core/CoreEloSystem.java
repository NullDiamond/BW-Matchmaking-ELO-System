package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.*;

import java.util.*;

/**
 * Main entry point for the CORE ELO system.
 * Manages players, games, and provides essential operations:
 * - Add a game (and calculate ELO changes)
 * - Delete a recently added game (undo)
 * - Balance teams given a list of players
 * 
 * All data is stored in memory - no file I/O.
 * Supports per-mode ELO ratings (SOLO, DUO, TRIO, FOURS, MEGA).
 */
public class CoreEloSystem {
    private final CoreConfig config;
    private final CoreEloCalculator calculator;
    private final CoreTeamBalancer balancer;
    private final CoreGameValidator validator;
    
    // In-memory storage
    private final Map<String, CorePlayer> players;
    private final LinkedList<CoreGameResult> recentGames;
    private final LinkedList<CoreInvalidGameResult> recentInvalidGames;
    
    // Legacy players (get boosted initial ELO)
    private final Set<String> legacyPlayers;

    /**
     * Creates a new CORE ELO system with the specified configuration.
     */
    public CoreEloSystem(CoreConfig config) {
        this.config = config;
        this.calculator = new CoreEloCalculator(config);
        this.balancer = new CoreTeamBalancer(config);
        this.validator = new CoreGameValidator(config);
        this.players = new HashMap<>();
        this.recentGames = new LinkedList<>();
        this.recentInvalidGames = new LinkedList<>();
        this.legacyPlayers = new HashSet<>();
    }

    /**
     * Creates a new CORE ELO system with default configuration.
     */
    public CoreEloSystem() {
        this(new CoreConfig());
    }

    // ========================================================================
    // PLAYER MANAGEMENT
    // ========================================================================

    /**
     * Gets or creates a player by ID.
     * If the player is in the legacy players set, they get boosted initial ELO.
     * @param id unique player identifier
     * @param name player display name (used if creating new player)
     * @return the player object
     */
    public CorePlayer getOrCreatePlayer(String id, String name) {
        return players.computeIfAbsent(id, k -> {
            double initialElo = legacyPlayers.contains(id) ? config.legacyPlayerElo : config.initialElo;
            return new CorePlayer(id, name, initialElo);
        });
    }

    /**
     * Gets a player by ID, or null if not found.
     */
    public CorePlayer getPlayer(String id) {
        return players.get(id);
    }

    /**
     * Registers a new player with initial ELO for all modes.
     * @param id unique player identifier
     * @param name player display name
     * @return the new player
     */
    public CorePlayer registerPlayer(String id, String name) {
        if (players.containsKey(id)) {
            throw new IllegalArgumentException("Player already exists: " + id);
        }
        CorePlayer player = new CorePlayer(id, name, config.initialElo);
        players.put(id, player);
        return player;
    }

    /**
     * Registers a player with custom initial ELO for all modes.
     */
    public CorePlayer registerPlayer(String id, String name, double initialElo) {
        if (players.containsKey(id)) {
            throw new IllegalArgumentException("Player already exists: " + id);
        }
        CorePlayer player = new CorePlayer(id, name, initialElo);
        players.put(id, player);
        return player;
    }

    /**
     * Gets all registered players.
     */
    public Collection<CorePlayer> getAllPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    /**
     * Gets the number of registered players.
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Sets the legacy player IDs.
     * Legacy players get boosted initial ELO (config.legacyPlayerElo) when first created.
     * This should be called before adding any games.
     * @param playerIds collection of player IDs to mark as legacy
     */
    public void setLegacyPlayers(Collection<String> playerIds) {
        legacyPlayers.clear();
        if (playerIds != null) {
            legacyPlayers.addAll(playerIds);
        }
    }

    /**
     * Adds a player ID to the legacy players set.
     * @param playerId the player ID to add
     */
    public void addLegacyPlayer(String playerId) {
        legacyPlayers.add(playerId);
    }

    /**
     * Checks if a player is a legacy player.
     * @param playerId the player ID to check
     * @return true if the player is in the legacy set
     */
    public boolean isLegacyPlayer(String playerId) {
        return legacyPlayers.contains(playerId);
    }

    /**
     * Gets all legacy player IDs.
     * @return unmodifiable set of legacy player IDs
     */
    public Set<String> getLegacyPlayers() {
        return Collections.unmodifiableSet(legacyPlayers);
    }

    // ========================================================================
    // GAME MANAGEMENT
    // ========================================================================

    /**
     * Result of adding a game, which may be either valid or invalid.
     */
    public static class AddGameResult {
        private final boolean valid;
        private final CoreGameResult gameResult;
        private final CoreInvalidGameResult invalidGameResult;

        private AddGameResult(CoreGameResult gameResult) {
            this.valid = true;
            this.gameResult = gameResult;
            this.invalidGameResult = null;
        }

        private AddGameResult(CoreInvalidGameResult invalidGameResult) {
            this.valid = false;
            this.gameResult = null;
            this.invalidGameResult = invalidGameResult;
        }

        public static AddGameResult valid(CoreGameResult result) {
            return new AddGameResult(result);
        }

        public static AddGameResult invalid(CoreInvalidGameResult result) {
            return new AddGameResult(result);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isInvalid() {
            return !valid;
        }

        public CoreGameResult getGameResult() {
            return gameResult;
        }

        public CoreInvalidGameResult getInvalidGameResult() {
            return invalidGameResult;
        }
    }

    /**
     * Adds and processes a game with validation.
     * If the game is valid, ELO changes are calculated and applied.
     * If the game is invalid, no ELO changes occur but invalid game counters are incremented.
     * 
     * @param game the game to process
     * @return AddGameResult indicating whether the game was valid and its result
     */
    public AddGameResult addGameWithValidation(CoreGame game) {
        CoreGameValidator.ValidationResult validation = validator.validate(game);

        // Ensure all players exist first
        for (String playerId : game.getAllPlayerIds()) {
            if (!players.containsKey(playerId)) {
                players.put(playerId, new CorePlayer(playerId, 
                    "Player-" + playerId.substring(0, Math.min(8, playerId.length())), 
                    config.initialElo));
            }
        }

        if (validation.isInvalid()) {
            // Game is invalid - increment invalid game counters, don't change ELO
            CoreGameMode mode = game.getGameMode();
            
            for (String playerId : game.getAllPlayerIds()) {
                CorePlayer player = players.get(playerId);
                if (player != null) {
                    player.incrementInvalidGamesCount(mode);
                }
            }

            CoreInvalidGameResult invalidResult = new CoreInvalidGameResult(game, validation);
            recentInvalidGames.addFirst(invalidResult);

            // Trim invalid games list
            while (recentInvalidGames.size() > config.recentGamesLimit) {
                recentInvalidGames.removeLast();
            }

            return AddGameResult.invalid(invalidResult);
        }

        // Game is valid - process normally
        return AddGameResult.valid(addGame(game));
    }

    /**
     * Adds and processes a game, calculating ELO changes for all players.
     * This method does NOT validate the game - it always processes.
     * Use addGameWithValidation() for validation-aware processing.
     * 
     * Players not yet registered will be auto-created with initial ELO.
     * The game's mode determines which ELO rating is updated for each player.
     * 
     * @param game the game to process
     * @return the game result with all ELO changes
     */
    public CoreGameResult addGame(CoreGame game) {
        CoreGameMode mode = game.getGameMode();
        
        // Ensure all players exist
        for (String playerId : game.getAllPlayerIds()) {
            if (!players.containsKey(playerId)) {
                // Auto-create player with unknown name
                players.put(playerId, new CorePlayer(playerId, "Player-" + playerId.substring(0, Math.min(8, playerId.length())), config.initialElo));
            }
        }

        // Calculate ELO changes
        Map<String, CoreEloChange> eloChanges = calculator.calculateEloChanges(game, players);

        // Apply ELO changes to players (mode-specific)
        for (Map.Entry<String, CoreEloChange> entry : eloChanges.entrySet()) {
            CorePlayer player = players.get(entry.getKey());
            if (player != null) {
                player.applyEloChange(mode, entry.getValue().getChange());
                player.incrementGamesPlayed(mode);
            }
        }

        // Store game result for undo
        CoreGameResult result = new CoreGameResult(game, eloChanges);
        recentGames.addFirst(result);

        // Trim to recent games limit
        while (recentGames.size() > config.recentGamesLimit) {
            recentGames.removeLast();
        }

        return result;
    }

    /**
     * Deletes (undoes) the most recent game, reverting ELO changes.
     * @return the removed game result, or null if no games to undo
     */
    public CoreGameResult deleteRecentGame() {
        if (recentGames.isEmpty()) {
            return null;
        }

        CoreGameResult result = recentGames.removeFirst();
        CoreGameMode mode = result.getGame().getGameMode();
        
        // Revert ELO changes
        for (Map.Entry<String, CoreEloChange> entry : result.getEloChanges().entrySet()) {
            CorePlayer player = players.get(entry.getKey());
            if (player != null) {
                // Subtract the change to revert
                player.applyEloChange(mode, -entry.getValue().getChange());
                player.setGamesPlayed(mode, Math.max(0, player.getGamesPlayed(mode) - 1));
            }
        }

        return result;
    }

    /**
     * Deletes a specific recent game by ID, reverting ELO changes.
     * @param gameId the game ID to delete
     * @return the removed game result, or null if not found
     */
    public CoreGameResult deleteRecentGame(String gameId) {
        Iterator<CoreGameResult> iterator = recentGames.iterator();
        while (iterator.hasNext()) {
            CoreGameResult result = iterator.next();
            if (result.getGameId().equals(gameId)) {
                iterator.remove();
                
                CoreGameMode mode = result.getGame().getGameMode();
                
                // Revert ELO changes
                for (Map.Entry<String, CoreEloChange> entry : result.getEloChanges().entrySet()) {
                    CorePlayer player = players.get(entry.getKey());
                    if (player != null) {
                        player.applyEloChange(mode, -entry.getValue().getChange());
                        player.setGamesPlayed(mode, Math.max(0, player.getGamesPlayed(mode) - 1));
                    }
                }
                
                return result;
            }
        }
        return null;
    }

    /**
     * Gets the list of recent games that can be deleted/undone.
     */
    public List<CoreGameResult> getRecentGames() {
        return Collections.unmodifiableList(recentGames);
    }

    /**
     * Gets the number of recent games available for undo.
     */
    public int getRecentGamesCount() {
        return recentGames.size();
    }

    /**
     * Gets the list of recent invalid games.
     */
    public List<CoreInvalidGameResult> getRecentInvalidGames() {
        return Collections.unmodifiableList(recentInvalidGames);
    }

    /**
     * Gets the number of recent invalid games.
     */
    public int getRecentInvalidGamesCount() {
        return recentInvalidGames.size();
    }

    /**
     * Validates a game without processing it.
     * @param game the game to validate
     * @return the validation result
     */
    public CoreGameValidator.ValidationResult validateGame(CoreGame game) {
        return validator.validate(game);
    }

    /**
     * Checks if a game would be valid if processed.
     */
    public boolean isValidGame(CoreGame game) {
        return validator.isValid(game);
    }

    // ========================================================================
    // TEAM BALANCING
    // ========================================================================

    /**
     * Balances the given players into the specified number of teams for a specific game mode.
     * @param playerIds list of player IDs to balance
     * @param numTeams number of teams to create
     * @param maxPlayersPerTeam maximum players per team (0 = no limit)
     * @param mode the game mode (determines which ELO to use for balancing)
     * @return the balance result
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds, int numTeams, int maxPlayersPerTeam, CoreGameMode mode) {
        List<CorePlayer> playersToBalance = new ArrayList<>();
        
        for (String playerId : playerIds) {
            CorePlayer player = players.get(playerId);
            if (player != null) {
                playersToBalance.add(player);
            } else {
                // Create temporary player with initial ELO for balancing
                playersToBalance.add(new CorePlayer(playerId, "Unknown-" + playerId.substring(0, Math.min(8, playerId.length())), config.initialElo));
            }
        }

        return balancer.balanceTeams(playersToBalance, numTeams, maxPlayersPerTeam, mode);
    }

    /**
     * Balances the given players into the specified number of teams using global ELO.
     * @param playerIds list of player IDs to balance
     * @param numTeams number of teams to create
     * @param maxPlayersPerTeam maximum players per team (0 = no limit)
     * @return the balance result
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds, int numTeams, int maxPlayersPerTeam) {
        List<CorePlayer> playersToBalance = new ArrayList<>();
        
        for (String playerId : playerIds) {
            CorePlayer player = players.get(playerId);
            if (player != null) {
                playersToBalance.add(player);
            } else {
                // Create temporary player with initial ELO for balancing
                playersToBalance.add(new CorePlayer(playerId, "Unknown-" + playerId.substring(0, Math.min(8, playerId.length())), config.initialElo));
            }
        }

        return balancer.balanceTeams(playersToBalance, numTeams, maxPlayersPerTeam);
    }

    /**
     * Balances the given players into 2 teams for a specific game mode.
     * @param playerIds list of player IDs to balance
     * @param mode the game mode
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds, CoreGameMode mode) {
        return balanceTeams(playerIds, 2, 0, mode);
    }

    /**
     * Balances the given players into 2 teams (convenience method using global ELO).
     * @param playerIds list of player IDs to balance
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeams(List<String> playerIds) {
        return balanceTeams(playerIds, 2, 0);
    }

    /**
     * Balances the given CorePlayer objects into teams for a specific game mode.
     * @param playersToBalance list of players to balance
     * @param numTeams number of teams to create
     * @param maxPlayersPerTeam maximum players per team (0 = no limit)
     * @param mode the game mode
     * @return the balance result
     */
    public CoreBalanceResult balancePlayers(List<CorePlayer> playersToBalance, int numTeams, int maxPlayersPerTeam, CoreGameMode mode) {
        return balancer.balanceTeams(playersToBalance, numTeams, maxPlayersPerTeam, mode);
    }

    /**
     * Balances the given CorePlayer objects into teams using global ELO.
     * @param playersToBalance list of players to balance
     * @param numTeams number of teams to create
     * @param maxPlayersPerTeam maximum players per team (0 = no limit)
     * @return the balance result
     */
    public CoreBalanceResult balancePlayers(List<CorePlayer> playersToBalance, int numTeams, int maxPlayersPerTeam) {
        return balancer.balanceTeams(playersToBalance, numTeams, maxPlayersPerTeam);
    }

    /**
     * Balances the given CorePlayer objects into 2 teams for a specific game mode.
     */
    public CoreBalanceResult balancePlayers(List<CorePlayer> playersToBalance, CoreGameMode mode) {
        return balancer.balanceTeams(playersToBalance, 2, 0, mode);
    }

    /**
     * Balances the given CorePlayer objects into 2 teams using global ELO.
     */
    public CoreBalanceResult balancePlayers(List<CorePlayer> playersToBalance) {
        return balancer.balanceTeams(playersToBalance, 2, 0);
    }

    /**
     * Balances the given players into 2 teams using weighted ELO.
     * This uses adjusted global ELO which weighs each mode by games played,
     * with Mega mode having extra weight (configurable via megaWeight).
     * 
     * This is the recommended method for Mega mode team balancing.
     * 
     * @param playerIds list of player IDs to balance
     * @return the balance result with 2 teams
     */
    public CoreBalanceResult balanceTeamsWeighted(List<String> playerIds) {
        List<CorePlayer> playersToBalance = new ArrayList<>();
        
        for (String playerId : playerIds) {
            CorePlayer player = players.get(playerId);
            if (player != null) {
                playersToBalance.add(player);
            } else {
                playersToBalance.add(new CorePlayer(playerId, 
                    "Unknown-" + playerId.substring(0, Math.min(8, playerId.length())), 
                    config.initialElo));
            }
        }

        return balancer.balanceTeamsWeighted(playersToBalance);
    }

    /**
     * Balances the given CorePlayer objects into 2 teams using weighted ELO.
     * Uses adjusted global ELO with Mega weight multiplier.
     */
    public CoreBalanceResult balancePlayersWeighted(List<CorePlayer> playersToBalance) {
        return balancer.balanceTeamsWeighted(playersToBalance);
    }

    // ========================================================================
    // LEADERBOARD
    // ========================================================================

    /**
     * Gets players sorted by ELO rating for a specific game mode (highest first).
     * @param mode the game mode
     * @param limit maximum number of players to return (0 = all)
     * @return sorted list of players
     */
    public List<CorePlayer> getLeaderboard(CoreGameMode mode, int limit) {
        List<CorePlayer> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> Double.compare(b.getElo(mode), a.getElo(mode)));
        
        if (limit > 0 && limit < sorted.size()) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    /**
     * Gets all players sorted by ELO rating for a specific game mode.
     */
    public List<CorePlayer> getLeaderboard(CoreGameMode mode) {
        return getLeaderboard(mode, 0);
    }

    /**
     * Gets players sorted by global (weighted average) ELO rating (highest first).
     * @param limit maximum number of players to return (0 = all)
     * @return sorted list of players
     */
    public List<CorePlayer> getLeaderboard(int limit) {
        List<CorePlayer> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> Double.compare(b.getGlobalElo(), a.getGlobalElo()));
        
        if (limit > 0 && limit < sorted.size()) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    /**
     * Gets all players sorted by global ELO rating.
     */
    public List<CorePlayer> getLeaderboard() {
        return getLeaderboard(0);
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Gets the current configuration.
     */
    public CoreConfig getConfig() {
        return config;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Clears all data (players, games).
     */
    public void reset() {
        players.clear();
        recentGames.clear();
        recentInvalidGames.clear();
    }
}

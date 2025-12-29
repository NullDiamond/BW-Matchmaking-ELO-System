package com.sbmm.elocalculator.managers;

import com.sbmm.elocalculator.model.game.*;
import com.sbmm.elocalculator.model.elo.*;
import com.sbmm.elocalculator.model.result.*;
import com.sbmm.elocalculator.config.Config;
import static com.sbmm.elocalculator.managers.GameModeUtils.*;

import java.util.*;

/**
 * Unified processor for game-related operations shared between live matches and historical processing.
 * Centralizes core ELO calculation, player initialization, and statistics update logic.
 */
public class GameProcessor {

    private final EloCalculator eloCalculator;

    /**
     * Constructor.
     * @param eloCalculator the ELO calculator for computation
     */
    public GameProcessor(EloCalculator eloCalculator) {
        this.eloCalculator = eloCalculator;
    }

    // ============================================
    // Match Validation
    // ============================================

    /**
     * Validates a match for processing.
     * @param gameData the game data to validate
     * @return true if the match is valid for processing
     */
    public boolean isValidMatch(GameData gameData) {
        if (gameData == null) {
            return false;
        }

        // Check winner
        if (gameData.getWinner() == null || gameData.getWinner().isEmpty()) {
            return false;
        }

        // Check lobby
        if (isStaffOrUnknownLobby(gameData.getLobby_id())) {
            return false;
        }

        // Check game activity
        if (!isGameActive(gameData)) {
            return false;
        }

        // Validate winner team
        if (validateWinnerTeam(gameData) == null) {
            return false;
        }

        return true;
    }

    /**
     * Gets the determined winner team for a game.
     * @param gameData the game data
     * @return the winner team name, or null if invalid
     */
    public String getWinnerTeam(GameData gameData) {
        return validateWinnerTeam(gameData);
    }

    /**
     * Determines the game mode for a match.
     * @param gameData the game data
     * @return the determined game mode
     */
    public String determineMode(GameData gameData) {
        return determineBestMode(gameData);
    }

    // ============================================
    // Player Initialization
    // ============================================

    /**
     * Initializes a new player with default ELO values.
     * @param uuid the player's UUID
     * @param playerName the player's name
     * @param mode the game mode
     * @param legacyTopPlayers set of legacy top players (for initial ELO boost)
     * @return initialized PlayerEloData
     */
    public PlayerEloData initializeNewPlayer(String uuid, String playerName, String mode, Set<String> legacyTopPlayers) {
        PlayerEloData data = new PlayerEloData();
        double initialElo = (legacyTopPlayers != null && legacyTopPlayers.contains(uuid)) 
            ? Config.LEGACY_TOP_INITIAL_ELO 
            : Config.INITIAL_ELO;
        
        data.getElo().put(mode, initialElo);
        data.getGamesPlayed().put(mode, 0);
        
        return data;
    }

    /**
     * Initializes ELO data for all modes for a player.
     * @param uuid the player's UUID
     * @param legacyTopPlayers set of legacy top players
     * @return initialized PlayerEloData with all modes
     */
    public PlayerEloData initializeFullPlayerData(String uuid, Set<String> legacyTopPlayers) {
        PlayerEloData data = new PlayerEloData();
        double initialElo = (legacyTopPlayers != null && legacyTopPlayers.contains(uuid)) 
            ? Config.LEGACY_TOP_INITIAL_ELO 
            : Config.INITIAL_ELO;

        for (String modeName : getAllModeNames()) {
            data.getElo().put(modeName, initialElo);
            data.getGamesPlayed().put(modeName, 0);
        }

        return data;
    }

    /**
     * Ensures a player has data for a specific mode.
     * @param data the player's ELO data
     * @param mode the mode to initialize
     * @param legacyTopPlayer whether the player is a legacy top player
     */
    public void ensureModeInitialized(PlayerEloData data, String mode, boolean legacyTopPlayer) {
        if (!data.getElo().containsKey(mode) || data.getElo().get(mode) == null) {
            double initialElo = legacyTopPlayer ? Config.LEGACY_TOP_INITIAL_ELO : Config.INITIAL_ELO;
            data.getElo().put(mode, initialElo);
            data.getGamesPlayed().put(mode, 0);
        }
    }

    // ============================================
    // ELO Calculation and Application
    // ============================================

    /**
     * Calculates ELO changes for a game.
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param eloRatings current ELO ratings
     * @param mode the game mode
     * @param kFactor the K-factor to use
     * @param gameId the game ID
     * @return map of player UUIDs to their ELO changes
     */
    public Map<String, EloChange> calculateEloChanges(GameData gameData, String winnerTeam,
                                                      Map<String, PlayerEloData> eloRatings,
                                                      String mode, double kFactor, String gameId) {
        return eloCalculator.calculateMultiTeamEloChanges(gameData, winnerTeam, eloRatings, mode, kFactor, gameId);
    }

    /**
     * Applies ELO changes to player data.
     * @param uuid the player's UUID
     * @param data the player's ELO data
     * @param change the ELO change to apply
     * @param mode the game mode
     * @return the new ELO value after change
     */
    public double applyEloChange(String uuid, PlayerEloData data, double change, String mode) {
        double currentElo = data.getElo().get(mode);
        double newElo = currentElo + change;
        data.getElo().put(mode, newElo);
        return newElo;
    }

    /**
     * Increments games played for a player in a mode.
     * @param data the player's ELO data
     * @param mode the game mode
     */
    public void incrementGamesPlayed(PlayerEloData data, String mode) {
        data.getGamesPlayed().put(mode, data.getGamesPlayed().get(mode) + 1);
    }

    /**
     * Rounds all ELO values in the ratings map to 1 decimal place.
     * @param eloRatings the ELO ratings to round
     */
    public void roundEloRatings(Map<String, PlayerEloData> eloRatings) {
        for (PlayerEloData data : eloRatings.values()) {
            for (String mode : getAllModeNames()) {
                Double elo = data.getElo().get(mode);
                if (elo != null) {
                    data.getElo().put(mode, roundElo(elo));
                }
            }
        }
    }

    /**
     * Rounds an ELO value to 1 decimal place.
     * @param elo the ELO value
     * @return the rounded value
     */
    public static double roundElo(double elo) {
        return Math.round(elo * 10.0) / 10.0;
    }

    // ============================================
    // Statistics Updates
    // ============================================

    /**
     * Updates performance statistics for a player.
     * @param data the player's ELO data
     * @param change the ELO change with performance scores
     * @param mode the game mode
     */
    public void updatePerformanceStats(PlayerEloData data, EloChange change, String mode) {
        PerformanceTracker pt = data.getPerformanceStats().get(mode);
        pt.setTotalScore(pt.getTotalScore() + change.getRawPerformanceScore());
        pt.setTotalNormalizedScore(pt.getTotalNormalizedScore() + change.getNormalizedPerformanceScore());
        pt.setCount(pt.getCount() + 1);
    }

    /**
     * Updates detailed game statistics for a player.
     * @param data the player's ELO data
     * @param gameData the game data
     * @param winnerTeam the winning team
     * @param uuid the player's UUID
     * @param mode the game mode
     */
    public void updateDetailedStats(PlayerEloData data, GameData gameData, String winnerTeam, String uuid, String mode) {
        if (gameData.getPlayer_stats() != null && gameData.getPlayer_stats().containsKey(uuid)) {
            PlayerStats stats = gameData.getPlayer_stats().get(uuid);
            GameStatistics ds = data.getDetailedStats().get(mode);
            
            ds.setBedBreaks(ds.getBedBreaks() + stats.getBed_breaks());
            ds.setKills(ds.getKills() + stats.getKills());
            ds.setDeaths(ds.getDeaths() + stats.getDeaths());
            ds.setFinalKills(ds.getFinalKills() + stats.getFinal_kills());

            // Check if player was on winning team
            if (isPlayerOnWinningTeam(uuid, gameData, winnerTeam)) {
                ds.setVictories(ds.getVictories() + 1);
            }
        }
    }

    /**
     * Checks if a player was on the winning team.
     * @param uuid the player's UUID
     * @param gameData the game data
     * @param winnerTeam the winning team name
     * @return true if the player was on the winning team
     */
    public boolean isPlayerOnWinningTeam(String uuid, GameData gameData, String winnerTeam) {
        if (winnerTeam == null || "Tie".equals(winnerTeam)) {
            return false;
        }
        
        String playerTeam = findPlayerTeam(gameData, uuid);
        return winnerTeam.equals(playerTeam);
    }

    /**
     * Finds which team a player is on.
     * @param gameData the game data
     * @param uuid the player's UUID
     * @return the team name, or null if not found
     */
    public String findPlayerTeam(GameData gameData, String uuid) {
        if (gameData.getTeamsAsUuids() == null) {
            return null;
        }
        
        return gameData.getTeamsAsUuids().entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue().contains(uuid))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    // ============================================
    // History Entry Creation
    // ============================================

    /**
     * Creates an ELO history entry.
     * @param gameId the game ID
     * @param mode the game mode
     * @param timestamp the match timestamp
     * @param previousElo the ELO before the match
     * @param newElo the ELO after the match
     * @param won whether the player won
     * @param isTie whether the match was a tie
     * @return the created history entry
     */
    public EloHistoryEntry createHistoryEntry(String gameId, String mode, long timestamp,
                                              double previousElo, double newElo, boolean won, boolean isTie) {
        // Round ELO values for consistency
        double roundedPrevious = roundElo(previousElo);
        double roundedNew = roundElo(newElo);
        return new EloHistoryEntry(gameId, mode, timestamp, roundedPrevious, roundedNew, won, isTie);
    }

    // ============================================
    // Player Name Extraction
    // ============================================

    /**
     * Gets a player's name from match data.
     * @param gameData the game data
     * @param uuid the player's UUID
     * @return the player's name, or "Unknown" if not found
     */
    public String getPlayerNameFromMatch(GameData gameData, String uuid) {
        // Try to find the player name from teams data using PlayerIdentifier
        if (gameData.getTeams() != null) {
            for (List<com.sbmm.elocalculator.model.player.PlayerIdentifier> teamPlayers : gameData.getTeams().values()) {
                if (teamPlayers != null) {
                    for (com.sbmm.elocalculator.model.player.PlayerIdentifier player : teamPlayers) {
                        if (player != null && uuid.equals(player.getUuid())) {
                            String name = player.getName();
                            if (name != null && !name.isEmpty()) {
                                // Strip "___" prefix if present (common in live match data)
                                if (name.startsWith("___")) {
                                    name = name.substring(3);
                                }
                                return name;
                            }
                        }
                    }
                }
            }
        }
        return "Unknown";
    }

    /**
     * Gets all player UUIDs from a game.
     * @param gameData the game data
     * @return set of player UUIDs
     */
    public Set<String> getMatchPlayers(GameData gameData) {
        Set<String> players = new HashSet<>();
        if (gameData.getTeamsAsUuids() != null) {
            for (List<String> teamPlayers : gameData.getTeamsAsUuids().values()) {
                if (teamPlayers != null) {
                    players.addAll(teamPlayers);
                }
            }
        }
        return players;
    }

    // ============================================
    // Result Formatting
    // ============================================

    /**
     * Creates a ModeResult from PlayerEloData for a specific mode.
     * @param data the player's ELO data
     * @param mode the game mode
     * @return the formatted ModeResult
     */
    public ModeResult createModeResult(PlayerEloData data, String mode) {
        ModeResult mr = new ModeResult();
        mr.setElo(roundElo(data.getElo().get(mode)));
        mr.setGames(data.getGamesPlayed().get(mode));

        PerformanceTracker pt = data.getPerformanceStats().get(mode);
        mr.setAvgPerformance(pt.getCount() > 0 
            ? Math.round((pt.getTotalScore() / pt.getCount()) * 1000.0) / 1000.0 
            : 1.0);
        mr.setAvgNormalizedPerformance(pt.getCount() > 0 
            ? Math.round((pt.getTotalNormalizedScore() / pt.getCount()) * 1000.0) / 1000.0 
            : 1.0);

        GameStatistics gs = data.getDetailedStats().get(mode);
        mr.setVictories(gs.getVictories());
        mr.setBedBreaks(gs.getBedBreaks());
        mr.setKills(gs.getKills());
        mr.setDeaths(gs.getDeaths());
        mr.setFinalKills(gs.getFinalKills());

        return mr;
    }

    /**
     * Calculates global ELO from mode results.
     * @param modes map of mode names to their results
     * @return the calculated global ELO
     */
    public double calculateGlobalElo(Map<String, ModeResult> modes) {
        double totalWeightedElo = 0;
        int totalGames = 0;

        for (Map.Entry<String, ModeResult> entry : modes.entrySet()) {
            String modeName = entry.getKey();
            ModeResult mr = entry.getValue();
            
            // Skip mega mode in global ELO calculation
            if (isMegaMode(modeName)) continue;
            
            if (mr.getGames() > 0) {
                totalWeightedElo += mr.getElo() * mr.getGames();
                totalGames += mr.getGames();
            }
        }

        return totalGames > 0 
            ? roundElo(totalWeightedElo / totalGames) 
            : Config.INITIAL_ELO;
    }
}




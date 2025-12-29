package com.sbmm.elocalculator.managers;

import com.sbmm.elocalculator.config.Config;
import com.sbmm.elocalculator.model.game.GameMode;
import com.sbmm.elocalculator.model.result.ModeResult;
import com.sbmm.elocalculator.model.result.PlayerResult;

/**
 * Utility class for common ELO calculation operations.
 * Provides centralized methods for ELO rounding and global ELO calculation
 * to eliminate code duplication across manager classes.
 */
public final class EloUtils {

    private EloUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Rounds an ELO value to one decimal place.
     * Uses standard mathematical rounding (0.5 rounds up).
     * 
     * @param elo the raw ELO value
     * @return the ELO rounded to one decimal place
     */
    public static double roundElo(double elo) {
        return Math.round(elo * 10.0) / 10.0;
    }

    /**
     * Calculates the global ELO for a player based on their mode-specific ELOs.
     * Global ELO is a weighted average of Solo, Duo, Trio, and Fours modes,
     * weighted by the number of games played in each mode.
     * 
     * Mega mode is excluded from global ELO calculation as it uses a different
     * rating system with seeding from other modes.
     * 
     * @param playerResult the player's result containing mode-specific data
     * @return the calculated global ELO, or Config.INITIAL_ELO if no qualifying games
     */
    public static double calculateGlobalElo(PlayerResult playerResult) {
        double totalWeightedElo = 0.0;
        int totalGames = 0;

        // Only include Solo, Duo, Trio, and Fours in global ELO (not Mega)
        for (GameMode mode : GameMode.standardModes()) {
            String modeName = mode.getModeName();
            if (playerResult.getModes().containsKey(modeName)) {
                ModeResult modeResult = playerResult.getModes().get(modeName);
                totalWeightedElo += modeResult.getElo() * modeResult.getGames();
                totalGames += modeResult.getGames();
            }
        }

        if (totalGames > 0) {
            return roundElo(totalWeightedElo / totalGames);
        }
        return Config.INITIAL_ELO;
    }

    /**
     * Calculates the adjusted global ELO with optional Mega mode weighting.
     * This calculation includes Mega mode with a configurable weight multiplier,
     * making it suitable for mega-specific leaderboards and seeding.
     * 
     * @param playerResult the player's result containing mode-specific data
     * @param megaWeight the weight multiplier for Mega mode games (typically 4.0)
     * @return the calculated adjusted global ELO, or Config.INITIAL_ELO if no qualifying games
     */
    public static double calculateAdjustedGlobalElo(PlayerResult playerResult, double megaWeight) {
        double totalWeightedElo = 0.0;
        double totalWeight = 0.0;

        // Include all modes with regular weighting
        for (GameMode mode : GameMode.standardModes()) {
            String modeName = mode.getModeName();
            if (playerResult.getModes().containsKey(modeName)) {
                ModeResult modeResult = playerResult.getModes().get(modeName);
                totalWeightedElo += modeResult.getElo() * modeResult.getGames();
                totalWeight += modeResult.getGames();
            }
        }

        // Include Mega mode with higher weight
        if (playerResult.getModes().containsKey(GameMode.MEGA.getModeName())) {
            ModeResult megaResult = playerResult.getModes().get(GameMode.MEGA.getModeName());
            totalWeightedElo += megaResult.getElo() * megaResult.getGames() * megaWeight;
            totalWeight += megaResult.getGames() * megaWeight;
        }

        if (totalWeight > 0) {
            return roundElo(totalWeightedElo / totalWeight);
        }
        return Config.INITIAL_ELO;
    }

    /**
     * Calculates an adjusted ELO for Mega-only players.
     * For players who have only played Mega mode, returns their Mega ELO.
     * For players with other modes, returns the adjusted global ELO.
     * 
     * @param playerResult the player's result containing mode-specific data
     * @param megaWeight the weight multiplier for Mega mode games
     * @return the calculated Mega-adjusted ELO
     */
    public static double calculateMegaAdjustedElo(PlayerResult playerResult, double megaWeight) {
        // Check if player has any non-Mega modes
        boolean hasNonMegaModes = false;
        for (GameMode mode : GameMode.standardModes()) {
            if (playerResult.getModes().containsKey(mode.getModeName())) {
                hasNonMegaModes = true;
                break;
            }
        }

        // If only Mega mode, return Mega ELO directly
        if (!hasNonMegaModes && playerResult.getModes().containsKey(GameMode.MEGA.getModeName())) {
            return playerResult.getModes().get(GameMode.MEGA.getModeName()).getElo();
        }

        // Otherwise, calculate adjusted global ELO
        return calculateAdjustedGlobalElo(playerResult, megaWeight);
    }
}




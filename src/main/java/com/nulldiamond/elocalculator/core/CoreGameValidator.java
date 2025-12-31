package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.CoreGame;
import com.nulldiamond.elocalculator.core.model.CorePlayerStats;

import java.util.Map;
import java.util.Optional;

/**
 * Validates games before ELO processing.
 * 
 * Games can be invalid for several reasons:
 * - No beds broken and insufficient deaths (indicates incomplete/abandoned game)
 * - Other validation rules can be added
 * 
 * Invalid games:
 * - Do NOT affect player ELO ratings
 * - DO increment player invalid game counters
 * - Are tracked separately for analysis
 */
public class CoreGameValidator {

    private final CoreConfig config;

    public CoreGameValidator(CoreConfig config) {
        this.config = config;
    }

    /**
     * Result of game validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isInvalid() {
            return !valid;
        }

        public Optional<String> getReason() {
            return Optional.ofNullable(reason);
        }

        @Override
        public String toString() {
            if (valid) {
                return "VALID";
            }
            return "INVALID: " + reason;
        }
    }

    /**
     * Validates a game to determine if it should affect ELO.
     * 
     * @param game the game to validate
     * @return validation result with status and reason if invalid
     */
    public ValidationResult validate(CoreGame game) {
        if (game == null) {
            return ValidationResult.invalid("Game is null");
        }

        if (game.getAllPlayerIds().isEmpty()) {
            return ValidationResult.invalid("No players in game");
        }

        // Check for "no bed" rule
        // If no beds were broken in the entire game and total deaths are below threshold,
        // the game is considered invalid (likely incomplete, abandoned, or test game)
        ValidationResult noBedResult = validateNoBedRule(game);
        if (noBedResult.isInvalid()) {
            return noBedResult;
        }

        // Additional validation rules can be added here:
        // - Minimum player count
        // - Maximum game duration
        // - etc.

        return ValidationResult.valid();
    }

    /**
     * Checks if a game meets the "no bed" minimum deaths requirement.
     * 
     * Logic: If zero beds were broken across all teams, the game likely didn't
     * progress to normal gameplay. However, if there were sufficient deaths,
     * players may have been eliminated via kills (especially in combat-heavy modes).
     * 
     * @param game the game to check
     * @return validation result
     */
    private ValidationResult validateNoBedRule(CoreGame game) {
        int totalBedBreaks = 0;
        int totalDeaths = 0;

        Map<String, CorePlayerStats> allStats = game.getPlayerStats();
        for (CorePlayerStats stats : allStats.values()) {
            if (stats != null) {
                totalBedBreaks += stats.getBedBreaks();
                totalDeaths += stats.getDeaths();
            }
        }

        // If no beds broken, require minimum deaths to be valid
        if (totalBedBreaks == 0 && totalDeaths < config.noBedMinimumDeaths) {
            return ValidationResult.invalid(String.format(
                "No bed breaks and only %d deaths (minimum %d required)",
                totalDeaths, config.noBedMinimumDeaths
            ));
        }

        return ValidationResult.valid();
    }

    /**
     * Convenience method to check if game is valid.
     */
    public boolean isValid(CoreGame game) {
        return validate(game).isValid();
    }

    /**
     * Convenience method to check if game is invalid.
     */
    public boolean isInvalid(CoreGame game) {
        return validate(game).isInvalid();
    }

    /**
     * Calculates total bed breaks in a game.
     */
    public static int getTotalBedBreaks(CoreGame game) {
        int total = 0;
        for (CorePlayerStats stats : game.getPlayerStats().values()) {
            if (stats != null) {
                total += stats.getBedBreaks();
            }
        }
        return total;
    }

    /**
     * Calculates total deaths in a game.
     */
    public static int getTotalDeaths(CoreGame game) {
        int total = 0;
        for (CorePlayerStats stats : game.getPlayerStats().values()) {
            if (stats != null) {
                total += stats.getDeaths();
            }
        }
        return total;
    }

    /**
     * Calculates total kills in a game.
     */
    public static int getTotalKills(CoreGame game) {
        int total = 0;
        for (CorePlayerStats stats : game.getPlayerStats().values()) {
            if (stats != null) {
                total += stats.getKills();
            }
        }
        return total;
    }
}

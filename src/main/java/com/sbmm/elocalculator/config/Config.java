package com.sbmm.elocalculator.config;

/**
 * Configuration class containing all customizable settings for the ELO calculator.
 * These values can be adjusted to fine-tune the rating system behavior.
 */
public final class Config {

    // Prevent instantiation
    private Config() {}

    // ============================================================================
    // DEBUG AND LOGGING SETTINGS
    // ============================================================================

    /**
     * Enable debug output for development and testing.
     * When enabled, detailed ELO transition information is printed.
     * Should be false for production/release builds.
     * Default: false
     */
    public static boolean DEBUG_MODE = false;

    // ============================================================================
    // ELO SYSTEM BASICS
    // ============================================================================

    /**
     * The initial ELO rating assigned to new players.
     * Default: 1200.0
     */
    public static final double INITIAL_ELO = 1200.0;

    /**
     * The initial ELO rating assigned to legacy top players.
     * Default: 1800.0
     */
    public static final double LEGACY_TOP_INITIAL_ELO = 1800.0;

    /**
     * The ELO bracket size for team balancing shuffle.
     * Players within this ELO range are considered interchangeable for shuffle.
     * Default: 100.0
     */
    public static final double ELO_BRACKET_SIZE = 100.0;

    /**
     * The base K-factor used in ELO calculations for standard modes (solo, duo, trio, fours).
     * Higher values mean ratings change more dramatically with each game.
     * Default: 40.0
     */
    public static final double K_FACTOR = 40.0;

    /**
     * The K-factor used in ELO calculations for Mega mode.
     * Mega games typically have higher K-factors due to larger team sizes and longer games.
     * Default: 60.0
     */
    public static final double K_FACTOR_MEGA = 60.0;

    /**
     * The K-factor used in ELO calculations for Solo mode.
     * Default: 40.0
     */
    public static final double K_FACTOR_SOLO = 40.0;

    /**
     * The K-factor used in ELO calculations for Duo mode.
     * Default: 40.0
     */
    public static final double K_FACTOR_DUO = 40.0;

    /**
     * The K-factor used in ELO calculations for Trio mode.
     * Default: 40.0
     */
    public static final double K_FACTOR_TRIO = 40.0;

    /**
     * The K-factor used in ELO calculations for Fours mode.
     * Default: 40.0
     */
    public static final double K_FACTOR_FOURS = 40.0;

    /**
     * Adjustment factor for team size in ELO calculations.
     * Reduces the impact of larger teams to prevent rating inflation.
     * Default: 0.15
     */
    public static final double TEAM_SIZE_ADJUSTMENT = 0.2;

    // ============================================================================
    // ITERATION SETTINGS
    // ============================================================================

    /**
     * Number of iterations to run the ELO calculation algorithm for standard modes.
     * Multiple iterations help stabilize ratings by accounting for performance changes.
     * Default: 2
     */
    public static final int NUM_ITERATIONS = 1;

    /**
     * Number of iterations specifically for Mega mode calculations.
     * Can be different from standard modes due to Mega's unique characteristics.
     * Default: 1
     */
    public static int NUM_ITERATIONS_MEGA = 1;

    /**
     * Weight given to global ELO when seeding Mega mode ratings (0.0 to 1.0).
     * The remaining weight comes from INITIAL_ELO.
     * Default: 0.5
     */
    public static final double MEGA_GLOBAL_WEIGHT = 4.0; // Weight for Mega games in adjusted global ELO used in mega

    // ============================================================================
    // PERFORMANCE MODIFIERS (STANDARD MODES)
    // ============================================================================

    /**
     * Weight given to bed break performance.
     * Each bed break adds this value to the player's performance score.
     * Higher values make bed breaking more important for rating changes.
     * Default: 0.15
     */
    public static final double WEIGHT_BED_BREAKS = 0.15;

    /**
     * Weight given to K/D ratio performance relative to team average.
     * Higher values make individual combat performance more important.
     * 
     * Impact examples (assuming team average K/D = 1.0):
     * - 0.5 K/D: Significant penalty (performs at 50% of team average)
     * - 1.0 K/D: Neutral (matches team average)
     * - 2.0 K/D: Substantial bonus (performs at 200% of team average)
     * - 3.0 K/D: Large bonus (performs at 300% of team average)
     * 
     * The weight controls how much these K/D differences affect ELO changes.
     * Reduced from 0.08 to 0.05 to account for double influence (direct + normalization).
     * Default: 0.05
     */
    public static final double WEIGHT_KD = 0.05;

    /**
     * Maximum K/D ratio considered for performance calculations.
     * Prevents extreme outliers from dominating the system.
     * Default: 4.0
     */
    public static final double KD_CAP = 4.0;

    /**
     * Minimum K/D ratio considered for performance calculations.
     * Ensures some minimum performance impact even for very poor performers.
     * Default: 0.25
     */
    public static final double KD_MIN = 0.25;

    /**
     * Weight given to final kill performance.
     * Each final kill (capped at FINAL_KILL_CAP) adds this value to the player's performance score.
     * Final kills are typically rare and high-impact events.
     * Default: 0.06
     */
    public static final double FINAL_KILL_WEIGHT = 0.06;

    /**
     * Weight given to activity level (kills + deaths + bed breaks + final kills) relative to team average.
     * Encourages active participation in games.
     * This number is multiplied by the player activity surplus ratio compared to team average.
     * Default: 0.05
     */
    public static final double WEIGHT_ACTIVITY = 0.05;

    /**
     * Maximum final kills considered for performance calculations.
     * Prevents single players from dominating team performance metrics.
     * Default: 4
     */
    public static final int FINAL_KILL_CAP = 4;

    // ============================================================================
    // PERFORMANCE MODIFIERS (MEGA MODE)
    // ============================================================================

    /**
     * Weight given to bed break performance in Mega mode.
     * Higher than standard modes due to Mega's emphasis on bed control.
     * Default: 0.20
     */
    public static final double WEIGHT_BED_BREAKS_MEGA = 0.20;

    /**
     * Weight given to K/D ratio performance in Mega mode.
     * Lower than standard modes due to larger team sizes and double influence effect.
     * Reduced from 0.05 to 0.035 to account for double influence (direct + normalization).
     * Default: 0.035
     */
    public static final double WEIGHT_KD_MEGA = 0.035;

    /**
     * Weight given to final kill performance in Mega mode.
     * Typically lower than standard modes due to larger team sizes.
     * Default: 0.02
     */
    public static final double FINAL_KILL_WEIGHT_MEGA = 0.025;

    /**
     * Maximum final kills considered for performance calculations in Mega mode.
     * Default: 4
     */
    public static final int FINAL_KILL_CAP_MEGA = 4;

    // ============================================================================
    // GAME FILTERING CRITERIA
    // ============================================================================

    /**
     * Minimum deaths required in games with no bed breaks for the game to be included in ELO calculations.
     * Games with 0 bed breaks and fewer deaths than this threshold are likely disconnect games
     * and are excluded from rating calculations.
     * Default: 2
     */
    public static final int NO_BED_MINIMUM_DEATHS = 2;

    // ============================================================================
    // PERFORMANCE MULTIPLIER LIMITS
    // ============================================================================

    /**
     * Minimum performance multiplier that can be applied to ELO changes.
     * Prevents players from losing too much ELO even with very poor performance.
     * Default: 0.5
     */
    public static final double MIN_PERFORMANCE_MULTIPLIER = 0.5;

    /**
     * Maximum performance multiplier that can be applied to ELO changes.
     * Prevents players from gaining too much ELO even with exceptional performance.
     * Default: 2.0
     */
    public static final double MAX_PERFORMANCE_MULTIPLIER = 2.0;

    // ============================================================================
    // PERFORMANCE SCORE CALCULATION LIMITS
    // ============================================================================

    /**
     * Maximum ratio of bed breaks to team average considered for performance calculations.
     * Prevents extreme outliers from dominating the system.
     * Default: 5.0
     */
    public static final double BED_BREAK_RATIO_CAP = 5.0;

    /**
     * Maximum ratio of final kills to team average considered for performance calculations.
     * Prevents extreme outliers from dominating the system.
     * Default: 5.0
     */
    public static final double FINAL_KILL_RATIO_CAP = 5.0;

    /**
     * Fixed bonus multiplier when a player has bed breaks but teammates have none.
     * Default: 2.0
     */
    public static final double BED_BREAK_BONUS_MULTIPLIER = 2.0;

    /**
     * Fixed bonus multiplier when a player has final kills but teammates have none.
     * Default: 2.0
     */
    public static final double FINAL_KILL_BONUS_MULTIPLIER = 2.0;

    /**
     * Minimum performance score that can be calculated.
     * Prevents division by very small numbers in ELO calculations.
     * Default: 0.1
     */
    public static final double MIN_PERFORMANCE_SCORE = 0.1;

    /**
     * Maximum performance score that can be calculated.
     * Prevents extreme performance scores from dominating the system.
     * Default: 3.0
     */
    public static final double MAX_PERFORMANCE_SCORE = 3.0;

    // ============================================================================
    // ELO CALCULATION CONSTANTS
    // ============================================================================

    /**
     * The divisor used in the ELO expected score calculation.
     * Standard ELO formula uses 400, but this can be adjusted for different rating systems.
     * Default: 400.0
     */
    public static final double ELO_DIVISOR = 400.0;

    /**
     * Threshold for zero-sum correction in multi-team ELO calculations.
     * Values below this threshold are considered zero for floating-point precision.
     * Default: 0.0001
     */
    public static final double ZERO_SUM_THRESHOLD = 0.0001;

    /**
     * Number of top players to display in leaderboards.
     * Default: 100
     */
    public static final int TOP_PLAYERS_DISPLAY = 100;
}



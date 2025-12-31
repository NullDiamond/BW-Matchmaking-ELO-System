package com.nulldiamond.elocalculator.core;

/**
 * Configuration for the CORE ELO system.
 * Contains only essential settings - all values can be modified at runtime.
 */
public class CoreConfig {

    // ============================================================================
    // ELO SYSTEM BASICS
    // ============================================================================

    /** Initial ELO rating for new players */
    public double initialElo = 1200.0;

    /** Base K-factor for standard modes */
    public double kFactor = 40.0;

    /** K-factor for mega mode */
    public double kFactorMega = 60.0;

    // ============================================================================
    // PERFORMANCE WEIGHTS (STANDARD MODES)
    // ============================================================================

    /** Weight for bed breaks in performance calculation */
    public double weightBedBreaks = 0.15;

    /** Weight for K/D ratio in performance calculation */
    public double weightKd = 0.05;

    /** Weight for final kills in performance calculation */
    public double weightFinalKills = 0.06;

    /** Maximum K/D ratio considered */
    public double kdCap = 4.0;

    /** Minimum K/D ratio considered */
    public double kdMin = 0.25;

    /** Maximum final kills considered */
    public int finalKillCap = 4;

    // ============================================================================
    // PERFORMANCE WEIGHTS (MEGA MODE)
    // ============================================================================

    /** Weight for bed breaks in mega mode */
    public double weightBedBreaksMega = 0.20;

    /** Weight for K/D ratio in mega mode */
    public double weightKdMega = 0.035;

    /** Weight for final kills in mega mode */
    public double weightFinalKillsMega = 0.025;

    /** Maximum final kills in mega mode */
    public int finalKillCapMega = 4;

    // ============================================================================
    // PERFORMANCE LIMITS
    // ============================================================================

    /** Minimum performance multiplier */
    public double minPerformanceMultiplier = 0.5;

    /** Maximum performance multiplier */
    public double maxPerformanceMultiplier = 2.0;

    // ============================================================================
    // TEAM BALANCER SETTINGS
    // ============================================================================

    /** ELO bracket size for shuffle variability */
    public double eloBracketSize = 100.0;

    /** Initial target ELO difference for balanced teams */
    public double targetEloDifference = 20.0;

    /** Threshold increment per balancing attempt */
    public double thresholdIncrement = 10.0;

    /** Maximum balancing attempts before giving up */
    public int maxBalanceAttempts = 10;

    /** 
     * Weight multiplier for Mega mode games when calculating adjusted ELO.
     * Higher values give more importance to Mega ELO in team balancing.
     * Default of 2.0 means 1 Mega game counts as 2 regular games for ELO weighting.
     */
    public double megaWeight = 2.0;

    // ============================================================================
    // GAME VALIDATION SETTINGS
    // ============================================================================

    /** 
     * Minimum total deaths required for a game with no bed breaks to be valid.
     * Games with 0 bed breaks and fewer than this many deaths are considered invalid.
     */
    public int noBedMinimumDeaths = 2;

    // ============================================================================
    // LEGACY PLAYER SETTINGS
    // ============================================================================

    /** 
     * Initial ELO for legacy top players.
     * These are players who were known to be top players before the ELO system was introduced.
     */
    public double legacyPlayerElo = 1800.0;

    // ============================================================================
    // GAME HISTORY
    // ============================================================================

    /** Number of recent games to keep for undo functionality */
    public int recentGamesLimit = 5;

    /**
     * Creates a default configuration.
     */
    public CoreConfig() {}

    /**
     * Creates a copy of this configuration.
     */
    public CoreConfig copy() {
        CoreConfig copy = new CoreConfig();
        copy.initialElo = this.initialElo;
        copy.kFactor = this.kFactor;
        copy.kFactorMega = this.kFactorMega;
        copy.weightBedBreaks = this.weightBedBreaks;
        copy.weightKd = this.weightKd;
        copy.weightFinalKills = this.weightFinalKills;
        copy.kdCap = this.kdCap;
        copy.kdMin = this.kdMin;
        copy.finalKillCap = this.finalKillCap;
        copy.weightBedBreaksMega = this.weightBedBreaksMega;
        copy.weightKdMega = this.weightKdMega;
        copy.weightFinalKillsMega = this.weightFinalKillsMega;
        copy.finalKillCapMega = this.finalKillCapMega;
        copy.minPerformanceMultiplier = this.minPerformanceMultiplier;
        copy.maxPerformanceMultiplier = this.maxPerformanceMultiplier;
        copy.eloBracketSize = this.eloBracketSize;
        copy.targetEloDifference = this.targetEloDifference;
        copy.thresholdIncrement = this.thresholdIncrement;
        copy.maxBalanceAttempts = this.maxBalanceAttempts;
        copy.megaWeight = this.megaWeight;
        copy.noBedMinimumDeaths = this.noBedMinimumDeaths;
        copy.recentGamesLimit = this.recentGamesLimit;
        copy.legacyPlayerElo = this.legacyPlayerElo;
        return copy;
    }
}

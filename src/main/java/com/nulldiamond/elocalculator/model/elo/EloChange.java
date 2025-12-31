package com.nulldiamond.elocalculator.model.elo;

/**
 * Represents an ELO rating change and associated performance scores.
 */
public class EloChange {
    private final double change;
    private final double rawPerformanceScore;
    private final double normalizedPerformanceScore;

    public EloChange(double change, double rawPerformanceScore, double normalizedPerformanceScore) {
        this.change = change;
        this.rawPerformanceScore = rawPerformanceScore;
        this.normalizedPerformanceScore = normalizedPerformanceScore;
    }

    public double getChange() {
        return change;
    }

    public double getRawPerformanceScore() {
        return rawPerformanceScore;
    }

    public double getNormalizedPerformanceScore() {
        return normalizedPerformanceScore;
    }
}




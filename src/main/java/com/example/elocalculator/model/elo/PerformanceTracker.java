package com.example.elocalculator.model.elo;

/**
 * Tracks performance statistics for a player in a specific game mode.
 */
public class PerformanceTracker {
    private double totalScore = 0.0;
    private double totalNormalizedScore = 0.0;
    private int count = 0;

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public double getTotalNormalizedScore() {
        return totalNormalizedScore;
    }

    public void setTotalNormalizedScore(double totalNormalizedScore) {
        this.totalNormalizedScore = totalNormalizedScore;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}

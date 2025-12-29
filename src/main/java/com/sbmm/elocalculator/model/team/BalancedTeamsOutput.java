package com.sbmm.elocalculator.model.team;

import java.util.List;

/**
 * Output format for balanced teams.
 */
public class BalancedTeamsOutput {
    private List<String> team1;
    private List<String> team2;
    private double team1AvgElo;
    private double team2AvgElo;

    public List<String> getTeam1() {
        return team1;
    }

    public void setTeam1(List<String> team1) {
        this.team1 = team1;
    }

    public List<String> getTeam2() {
        return team2;
    }

    public void setTeam2(List<String> team2) {
        this.team2 = team2;
    }

    public double getTeam1AvgElo() {
        return team1AvgElo;
    }

    public void setTeam1AvgElo(double team1AvgElo) {
        this.team1AvgElo = team1AvgElo;
    }

    public double getTeam2AvgElo() {
        return team2AvgElo;
    }

    public void setTeam2AvgElo(double team2AvgElo) {
        this.team2AvgElo = team2AvgElo;
    }
}





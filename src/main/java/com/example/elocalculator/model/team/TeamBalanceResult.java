package com.example.elocalculator.model.team;

import com.example.elocalculator.model.player.PlayerInfo;
import java.util.List;

/**
 * Result of team balancing operation.
 */
public class TeamBalanceResult {
    private final List<PlayerInfo> team1;
    private final List<PlayerInfo> team2;

    public TeamBalanceResult(List<PlayerInfo> team1, List<PlayerInfo> team2) {
        this.team1 = team1;
        this.team2 = team2;
    }

    public List<PlayerInfo> getTeam1() {
        return team1;
    }

    public List<PlayerInfo> getTeam2() {
        return team2;
    }
}


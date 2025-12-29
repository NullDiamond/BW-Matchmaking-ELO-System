package com.sbmm.elocalculator.model.game;

import com.sbmm.elocalculator.model.player.PlayerIdentifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the data for a single game, containing all game information including player details.
 */
public class GameData {
    private String winner;
    private String lobby_id;
    private Long unix_time;
    private Integer total_players;
    private Integer duration;
    private String map;
    private Map<String, List<PlayerIdentifier>> teams;
    private Map<String, PlayerStats> player_stats;

    public GameData() {}

    // Getters and setters
    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getLobby_id() {
        return lobby_id;
    }

    public void setLobby_id(String lobby_id) {
        this.lobby_id = lobby_id;
    }

    public Long getUnix_time() {
        return unix_time;
    }

    public void setUnix_time(Long unix_time) {
        this.unix_time = unix_time;
    }

    public Integer getTotal_players() {
        return total_players;
    }

    public void setTotal_players(Integer total_players) {
        this.total_players = total_players;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public Map<String, List<PlayerIdentifier>> getTeams() {
        return teams;
    }

    public void setTeams(Map<String, List<PlayerIdentifier>> teams) {
        this.teams = teams;
    }

    /**
     * Gets teams as UUID strings only (for ELO calculation compatibility).
     * @return Map of team names to lists of player UUIDs
     */
    public Map<String, List<String>> getTeamsAsUuids() {
        if (teams == null) return null;
        return teams.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .map(PlayerIdentifier::getUuid)
                    .collect(Collectors.toList())
            ));
    }

    public Map<String, PlayerStats> getPlayer_stats() {
        return player_stats;
    }

    public void setPlayer_stats(Map<String, PlayerStats> player_stats) {
        this.player_stats = player_stats;
    }
}




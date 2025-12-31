package com.nulldiamond.elocalculator.core.model;

import java.util.*;

/**
 * Represents the result of processing a game, including all ELO changes.
 * Used for tracking and potentially undoing games.
 */
public class CoreGameResult {
    private final CoreGame game;
    private final Map<String, CoreEloChange> eloChanges;
    private final long processedAt;

    public CoreGameResult(CoreGame game, Map<String, CoreEloChange> eloChanges) {
        this.game = game;
        this.eloChanges = new HashMap<>(eloChanges);
        this.processedAt = System.currentTimeMillis();
    }

    public CoreGame getGame() {
        return game;
    }

    public Map<String, CoreEloChange> getEloChanges() {
        return Collections.unmodifiableMap(eloChanges);
    }

    public long getProcessedAt() {
        return processedAt;
    }

    public String getGameId() {
        return game.getGameId();
    }

    /**
     * Gets the ELO change for a specific player.
     * @param playerId the player's ID
     * @return the ELO change, or null if player not in game
     */
    public CoreEloChange getEloChange(String playerId) {
        return eloChanges.get(playerId);
    }

    @Override
    public String toString() {
        return String.format("GameResult[%s] - %d players affected",
                game.getGameId().substring(0, Math.min(8, game.getGameId().length())),
                eloChanges.size());
    }
}

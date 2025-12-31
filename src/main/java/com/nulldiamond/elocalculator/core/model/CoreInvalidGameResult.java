package com.nulldiamond.elocalculator.core.model;

import com.nulldiamond.elocalculator.core.CoreGameValidator;

import java.util.*;

/**
 * Represents an invalid game result - a game that was processed but
 * did not affect ELO ratings due to validation failure.
 */
public class CoreInvalidGameResult {
    private final CoreGame game;
    private final CoreGameValidator.ValidationResult validationResult;
    private final List<String> affectedPlayerIds;

    public CoreInvalidGameResult(CoreGame game, CoreGameValidator.ValidationResult validationResult) {
        this.game = game;
        this.validationResult = validationResult;
        this.affectedPlayerIds = new ArrayList<>(game.getAllPlayerIds());
    }

    public CoreGame getGame() {
        return game;
    }

    public String getGameId() {
        return game.getGameId();
    }

    public CoreGameMode getGameMode() {
        return game.getGameMode();
    }

    public CoreGameValidator.ValidationResult getValidationResult() {
        return validationResult;
    }

    public String getInvalidReason() {
        return validationResult.getReason().orElse("Unknown");
    }

    public List<String> getAffectedPlayerIds() {
        return Collections.unmodifiableList(affectedPlayerIds);
    }

    @Override
    public String toString() {
        return String.format("InvalidGame[%s, mode=%s, reason=%s, players=%d]",
                game.getGameId(), game.getGameMode(), getInvalidReason(), affectedPlayerIds.size());
    }
}

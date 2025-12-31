package com.nulldiamond.elocalculator.model.player;

/**
 * Base class representing a player's identifying information (UUID and name).
 * Used across multiple model classes to reduce code duplication.
 */
public class PlayerIdentifier {
    private String uuid;
    private String name;

    public PlayerIdentifier() {}

    public PlayerIdentifier(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}





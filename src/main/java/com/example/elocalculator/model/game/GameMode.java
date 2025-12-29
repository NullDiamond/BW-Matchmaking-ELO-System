package com.example.elocalculator.model.game;

/**
 * Enumeration of different game modes in Bedwars.
 */
public enum GameMode {
    SOLO("solo"),
    DUO("duo"),
    TRIO("trio"),
    FOURS("fours"),
    MEGA("mega");

    private final String modeName;

    /**
     * Standard modes (Solo, Duo, Trio, Fours) used for global ELO calculation.
     * Mega is excluded as it uses a different rating system with seeding.
     */
    private static final GameMode[] STANDARD_MODES = {SOLO, DUO, TRIO, FOURS};

    GameMode(String modeName) {
        this.modeName = modeName;
    }

    public String getModeName() {
        return modeName;
    }

    /**
     * Returns the standard game modes (Solo, Duo, Trio, Fours).
     * These modes are used for global ELO calculations.
     * @return array of standard game modes
     */
    public static GameMode[] standardModes() {
        return STANDARD_MODES.clone();
    }

    /**
     * Checks if this mode is a standard mode (not Mega).
     * @return true if this is a standard mode
     */
    public boolean isStandardMode() {
        return this != MEGA;
    }

    /**
     * Converts a string to the corresponding GameMode.
     * @param modeName the mode name string
     * @return the GameMode enum value
     * @throws IllegalArgumentException if the mode name is unknown
     */
    public static GameMode fromString(String modeName) {
        for (GameMode mode : values()) {
            if (mode.modeName.equals(modeName)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown game mode: " + modeName);
    }
}

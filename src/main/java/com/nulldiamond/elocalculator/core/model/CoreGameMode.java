package com.nulldiamond.elocalculator.core.model;

/**
 * Enumeration of game modes for the CORE ELO system.
 */
public enum CoreGameMode {
    SOLO("solo"),
    DUO("duo"),
    TRIO("trio"),
    FOURS("fours"),
    MEGA("mega");

    private final String modeName;

    /**
     * Standard modes used for global ELO calculation.
     * Mega is excluded as it uses a different rating system.
     */
    private static final CoreGameMode[] STANDARD_MODES = {SOLO, DUO, TRIO, FOURS};

    CoreGameMode(String modeName) {
        this.modeName = modeName;
    }

    public String getModeName() {
        return modeName;
    }

    /**
     * Returns the standard game modes (Solo, Duo, Trio, Fours).
     * @return array of standard game modes
     */
    public static CoreGameMode[] standardModes() {
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
     * Converts a string to the corresponding CoreGameMode.
     * @param modeName the mode name string
     * @return the CoreGameMode enum value
     * @throws IllegalArgumentException if the mode name is unknown
     */
    public static CoreGameMode fromString(String modeName) {
        for (CoreGameMode mode : values()) {
            if (mode.modeName.equalsIgnoreCase(modeName)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown game mode: " + modeName);
    }

    /**
     * Gets a display-friendly name for this game mode.
     * @return capitalized mode name
     */
    public String getDisplayName() {
        return modeName.substring(0, 1).toUpperCase() + modeName.substring(1);
    }

    /**
     * Checks if this mode is a Mega mode.
     * @return true if this is MEGA mode
     */
    public boolean isMega() {
        return this == MEGA;
    }

    @Override
    public String toString() {
        return modeName;
    }
}

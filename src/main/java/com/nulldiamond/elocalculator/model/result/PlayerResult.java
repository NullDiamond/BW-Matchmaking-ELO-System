package com.nulldiamond.elocalculator.model.result;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains the final results for a single player.
 */
public class PlayerResult {
    private String uuid;
    private String name;
    private Map<String, ModeResult> modes;
    private double globalElo;
    private double adjustedGlobalElo;

    public PlayerResult() {
        this.modes = new HashMap<>();
    }

    public PlayerResult(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.modes = new HashMap<>();
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

    public Map<String, ModeResult> getModes() {
        return modes;
    }

    public void setModes(Map<String, ModeResult> modes) {
        this.modes = modes;
    }

    public double getGlobalElo() {
        return globalElo;
    }

    public void setGlobalElo(double globalElo) {
        this.globalElo = globalElo;
    }

    public double getAdjustedGlobalElo() {
        return adjustedGlobalElo;
    }

    public void setAdjustedGlobalElo(double adjustedGlobalElo) {
        this.adjustedGlobalElo = adjustedGlobalElo;
    }
}




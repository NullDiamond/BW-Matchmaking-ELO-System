package com.nulldiamond.elocalculator.managers;

import com.nulldiamond.elocalculator.config.Config;
import com.nulldiamond.elocalculator.model.game.GameMode;
import com.nulldiamond.elocalculator.model.result.PlayerResult;
import com.nulldiamond.elocalculator.model.result.ModeResult;
import com.nulldiamond.elocalculator.model.leaderboard.LeaderboardEntry;
import com.nulldiamond.elocalculator.model.leaderboard.GlobalLeaderboardEntry;
import com.nulldiamond.elocalculator.model.elo.EloRecord;
import com.nulldiamond.elocalculator.model.game.InvalidGamesData;
import com.nulldiamond.elocalculator.model.result.PlayerInvalidGamesEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manager class responsible for output operations: saving results, printing statistics, and generating leaderboards.
 */
public class OutputManager {

    private final Gson gson;
    private final String dataDirectory;

    public OutputManager(String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LeaderboardEntry.class, new LeaderboardEntryAdapter())
            .registerTypeAdapter(GlobalLeaderboardEntry.class, new GlobalLeaderboardEntryAdapter())
            .create();
    }

    /**
     * Custom TypeAdapter for LeaderboardEntry to control field order.
     */
    private static class LeaderboardEntryAdapter extends TypeAdapter<LeaderboardEntry> {
        @Override
        public void write(JsonWriter out, LeaderboardEntry entry) throws IOException {
            out.beginObject();
            out.name("rank").value(entry.getRank());
            out.name("uuid").value(entry.getUuid());
            out.name("name").value(entry.getName());
            out.name("elo").value(entry.getElo());
            out.name("games").value(entry.getGames());
            out.name("avgPerformance").value(entry.getAvgPerformance());
            out.name("avgNormalizedPerformance").value(entry.getAvgNormalizedPerformance());
            out.name("victories").value(entry.getVictories());
            out.name("bedBreaks").value(entry.getBedBreaks());
            out.name("kills").value(entry.getKills());
            out.name("deaths").value(entry.getDeaths());
            out.name("finalKills").value(entry.getFinalKills());
            out.name("invalidGames").value(entry.getInvalidGames());
            out.name("legacyRankedPlayer").value(entry.isLegacyRankedPlayer());
            out.endObject();
        }

        @Override
        public LeaderboardEntry read(JsonReader in) throws IOException {
            // Not needed for this use case
            throw new UnsupportedOperationException("Deserialization not supported");
        }
    }

    /**
     * Custom TypeAdapter for GlobalLeaderboardEntry to control field order.
     */
    private static class GlobalLeaderboardEntryAdapter extends TypeAdapter<GlobalLeaderboardEntry> {
        @Override
        public void write(JsonWriter out, GlobalLeaderboardEntry entry) throws IOException {
            out.beginObject();
            out.name("rank").value(entry.getRank());
            out.name("uuid").value(entry.getUuid());
            out.name("name").value(entry.getName());
            out.name("globalElo").value(entry.getGlobalElo());
            out.name("totalGames").value(entry.getTotalGames());
            out.name("legacyRankedPlayer").value(entry.isLegacyRankedPlayer());
            out.name("modes");
            out.beginObject();
            for (Map.Entry<String, EloRecord> mode : entry.getModes().entrySet()) {
                out.name(mode.getKey());
                out.beginObject();
                out.name("elo").value(mode.getValue().getElo());
                out.name("games").value(mode.getValue().getGames());
                out.endObject();
            }
            out.endObject();
            out.endObject();
        }

        @Override
        public GlobalLeaderboardEntry read(JsonReader in) throws IOException {
            // Not needed for this use case
            throw new UnsupportedOperationException("Deserialization not supported");
        }
    }

    /**
     * Saves the calculation results to a JSON file.
     * @param results the player results
     * @throws IOException if file cannot be written
     */
    public void saveResults(Map<String, PlayerResult> results) throws IOException {
        String output = gson.toJson(results);
        Files.write(Paths.get(dataDirectory, "output/zero_sum_elo_java.json"), output.getBytes());
        System.out.println("\nSaved results to data/output/zero_sum_elo_java.json");
    }

    /**
     * Saves leaderboards for each game mode and the global leaderboard.
     * @param results the player results
     * @throws IOException if files cannot be written
     */
    public void saveLeaderboards(Map<String, PlayerResult> results) throws IOException {
        File dir = new File(dataDirectory, "output/leaderboards_java");
        dir.mkdirs();

        // Load legacy top players for marking in leaderboards
        DataManager dataManager = new DataManager(dataDirectory);
        Set<String> legacyTopPlayers = dataManager.loadLegacyTopPlayers();

        // Calculate global ELO for all players before generating leaderboards
        calculateGlobalElos(results);

        // Save leaderboard for each mode
        for (GameMode mode : GameMode.values()) {
            String modeName = mode.getModeName();
            List<LeaderboardEntry> leaderboard = new ArrayList<>();

            for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
                String uuid = entry.getKey();
                PlayerResult pr = entry.getValue();

                if (pr.getModes().containsKey(modeName)) {
                    ModeResult mr = pr.getModes().get(modeName);
                    LeaderboardEntry le = new LeaderboardEntry();
                    le.setUuid(uuid);
                    le.setName(pr.getName());
                    le.setElo(mr.getElo());
                    le.setGames(mr.getGames());
                    le.setAvgPerformance(mr.getAvgPerformance());
                    le.setAvgNormalizedPerformance(mr.getAvgNormalizedPerformance());
                    le.setVictories(mr.getVictories());
                    le.setBedBreaks(mr.getBedBreaks());
                    le.setKills(mr.getKills());
                    le.setDeaths(mr.getDeaths());
                    le.setFinalKills(mr.getFinalKills());
                    le.setInvalidGames(mr.getInvalidGames());
                    le.setLegacyRankedPlayer(legacyTopPlayers.contains(uuid));
                    leaderboard.add(le);
                }
            }

            leaderboard.sort(Comparator.comparingDouble((LeaderboardEntry e) -> e.getElo()).reversed());

            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }

            String output = gson.toJson(leaderboard);
            Files.write(Paths.get(dataDirectory, "output/leaderboards_java/" + modeName + "_leaderboard.json"),
                       output.getBytes());
            System.out.println("Saved " + modeName + " leaderboard with " + leaderboard.size() +
                             " players to data/output/leaderboards_java/" + modeName + "_leaderboard.json");
        }

        // Save global leaderboard
        List<GlobalLeaderboardEntry> globalLeaderboard = createGlobalLeaderboard(results, legacyTopPlayers);

        globalLeaderboard.sort(Comparator.comparingDouble((GlobalLeaderboardEntry e) -> e.getGlobalElo()).reversed());

        for (int i = 0; i < globalLeaderboard.size(); i++) {
            globalLeaderboard.get(i).setRank(i + 1);
        }

        String output = gson.toJson(globalLeaderboard);
        Files.write(Paths.get(dataDirectory, "output/leaderboards_java/global_leaderboard.json"), output.getBytes());
        System.out.println("Saved global leaderboard with " + globalLeaderboard.size() +
                         " players to data/output/leaderboards_java/global_leaderboard.json");

        // Save adjusted global leaderboard for Mega seeding
        List<GlobalLeaderboardEntry> adjustedGlobalLeaderboard = createAdjustedGlobalLeaderboard(results, legacyTopPlayers);

        adjustedGlobalLeaderboard.sort(Comparator.comparingDouble((GlobalLeaderboardEntry e) -> e.getGlobalElo()).reversed());

        for (int i = 0; i < adjustedGlobalLeaderboard.size(); i++) {
            adjustedGlobalLeaderboard.get(i).setRank(i + 1);
        }

        String adjustedOutput = gson.toJson(adjustedGlobalLeaderboard);
        Files.write(Paths.get(dataDirectory, "output/leaderboards_java/starting_adjusted_mega_elo.json"), adjustedOutput.getBytes());
        System.out.println("Saved adjusted global leaderboard with " + adjustedGlobalLeaderboard.size() +
                         " players to data/output/leaderboards_java/starting_adjusted_mega_elo.json");

        // Save Mega-only adjusted leaderboard
        List<GlobalLeaderboardEntry> megaOnlyAdjustedLeaderboard = createMegaOnlyAdjustedLeaderboard(results, legacyTopPlayers);

        megaOnlyAdjustedLeaderboard.sort(Comparator.comparingDouble((GlobalLeaderboardEntry e) -> e.getGlobalElo()).reversed());

        for (int i = 0; i < megaOnlyAdjustedLeaderboard.size(); i++) {
            megaOnlyAdjustedLeaderboard.get(i).setRank(i + 1);
        }

        String megaOnlyAdjustedOutput = gson.toJson(megaOnlyAdjustedLeaderboard);
        Files.write(Paths.get(dataDirectory, "output/leaderboards_java/mega_only_adjusted_elo.json"), megaOnlyAdjustedOutput.getBytes());
        System.out.println("Saved Mega-only adjusted leaderboard with " + megaOnlyAdjustedLeaderboard.size() +
                         " players to data/output/leaderboards_java/mega_only_adjusted_elo.json");
    }

    /**
     * Calculates global ELO and adjusted global ELO for all players.
     * This ensures that players added through live matches have proper global stats.
     * @param results the player results to update
     */
    private void calculateGlobalElos(Map<String, PlayerResult> results) {
        for (PlayerResult pr : results.values()) {
            // Calculate global ELO using EloUtils
            pr.setGlobalElo(EloUtils.calculateGlobalElo(pr));

            // Calculate adjusted global ELO with more weight to Mega games
            pr.setAdjustedGlobalElo(EloUtils.calculateAdjustedGlobalElo(pr, Config.MEGA_GLOBAL_WEIGHT));
        }
    }

    /**
     * Creates the adjusted global leaderboard from player results (with more weight to Mega).
     * @param results the player results
     * @param legacyTopPlayers the set of legacy top player UUIDs
     * @return the adjusted global leaderboard entries
     */
    private List<GlobalLeaderboardEntry> createAdjustedGlobalLeaderboard(Map<String, PlayerResult> results, Set<String> legacyTopPlayers) {
        List<GlobalLeaderboardEntry> adjustedGlobalLeaderboard = new ArrayList<>();

        for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
            String uuid = entry.getKey();
            PlayerResult pr = entry.getValue();

            PlayerParticipation participation = calculatePlayerParticipation(pr);
            if (participation.hasStandardModes || pr.getModes().containsKey("mega")) {
                GlobalLeaderboardEntry gle = createAdjustedGlobalLeaderboardEntry(uuid, pr, participation.totalGames, legacyTopPlayers);
                adjustedGlobalLeaderboard.add(gle);
            }
        }

        return adjustedGlobalLeaderboard;
    }

    /**
     * Creates the Mega-only adjusted global leaderboard from player results (only players with Mega games).
     * @param results the player results
     * @param legacyTopPlayers the set of legacy top player UUIDs
     * @return the Mega-only adjusted global leaderboard entries
     */
    private List<GlobalLeaderboardEntry> createMegaOnlyAdjustedLeaderboard(Map<String, PlayerResult> results, Set<String> legacyTopPlayers) {
        List<GlobalLeaderboardEntry> megaOnlyAdjustedLeaderboard = new ArrayList<>();

        for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
            String uuid = entry.getKey();
            PlayerResult pr = entry.getValue();

            // Only include players who have played Mega games
            if (pr.getModes().containsKey("mega")) {
                PlayerParticipation participation = calculatePlayerParticipation(pr);
                GlobalLeaderboardEntry gle = createAdjustedGlobalLeaderboardEntry(uuid, pr, participation.totalGames, legacyTopPlayers);
                megaOnlyAdjustedLeaderboard.add(gle);
            }
        }

        return megaOnlyAdjustedLeaderboard;
    }

    /**
     * Creates the global leaderboard from player results.
     * @param results the player results
     * @param legacyTopPlayers the set of legacy top player UUIDs
     * @return the global leaderboard entries
     */
    private List<GlobalLeaderboardEntry> createGlobalLeaderboard(Map<String, PlayerResult> results, Set<String> legacyTopPlayers) {
        List<GlobalLeaderboardEntry> globalLeaderboard = new ArrayList<>();

        for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
            String uuid = entry.getKey();
            PlayerResult pr = entry.getValue();

            PlayerParticipation participation = calculatePlayerParticipation(pr);
            if (participation.hasStandardModes || pr.getModes().containsKey("mega")) {
                GlobalLeaderboardEntry gle = createGlobalLeaderboardEntry(uuid, pr, participation.totalGames, legacyTopPlayers);
                globalLeaderboard.add(gle);
            }
        }

        return globalLeaderboard;
    }

    /**
     * Calculates player participation in standard modes.
     * @param pr the player result
     * @return participation information
     */
    private PlayerParticipation calculatePlayerParticipation(PlayerResult pr) {
        boolean hasStandardModes = false;
        int totalGames = 0;

        for (GameMode mode : GameMode.standardModes()) {
            String modeName = mode.getModeName();
            if (pr.getModes().containsKey(modeName)) {
                hasStandardModes = true;
                totalGames += pr.getModes().get(modeName).getGames();
            }
        }

        // Also count MEGA games in totalGames
        if (pr.getModes().containsKey("mega")) {
            totalGames += pr.getModes().get("mega").getGames();
        }

        return new PlayerParticipation(hasStandardModes, totalGames);
    }

    /**
     * Creates an adjusted global leaderboard entry for a player.
     * @param uuid the player UUID
     * @param pr the player result
     * @param totalGames the total games played
     * @param legacyTopPlayers the set of legacy top player UUIDs
     * @return the adjusted global leaderboard entry
     */
    private GlobalLeaderboardEntry createAdjustedGlobalLeaderboardEntry(String uuid, PlayerResult pr, int totalGames, Set<String> legacyTopPlayers) {
        GlobalLeaderboardEntry gle = new GlobalLeaderboardEntry();
        gle.setUuid(uuid);
        gle.setName(pr.getName());
        gle.setGlobalElo(pr.getAdjustedGlobalElo()); // Use adjusted ELO
        gle.setTotalGames(totalGames);
        gle.setLegacyRankedPlayer(legacyTopPlayers.contains(uuid));

        for (GameMode mode : GameMode.values()) {
            String modeName = mode.getModeName();
            if (pr.getModes().containsKey(modeName)) {
                EloRecord er = new EloRecord();
                er.setElo(pr.getModes().get(modeName).getElo());
                er.setGames(pr.getModes().get(modeName).getGames());
                gle.getModes().put(modeName, er);
            }
        }

        return gle;
    }

    /**
     * Creates a global leaderboard entry for a player.
     * @param uuid the player UUID
     * @param pr the player result
     * @param totalGames the total games played
     * @param legacyTopPlayers the set of legacy top player UUIDs
     * @return the global leaderboard entry
     */
    private GlobalLeaderboardEntry createGlobalLeaderboardEntry(String uuid, PlayerResult pr, int totalGames, Set<String> legacyTopPlayers) {
        GlobalLeaderboardEntry gle = new GlobalLeaderboardEntry();
        gle.setUuid(uuid);
        gle.setName(pr.getName());
        gle.setGlobalElo(pr.getGlobalElo()); // Use regular global ELO
        gle.setTotalGames(totalGames);
        gle.setLegacyRankedPlayer(legacyTopPlayers.contains(uuid));

        for (GameMode mode : GameMode.values()) {
            String modeName = mode.getModeName();
            if (pr.getModes().containsKey(modeName)) {
                EloRecord er = new EloRecord();
                er.setElo(pr.getModes().get(modeName).getElo());
                er.setGames(pr.getModes().get(modeName).getGames());
                gle.getModes().put(modeName, er);
            }
        }

        return gle;
    }

    /**
     * Helper class for player participation data.
     */
    private static class PlayerParticipation {
        final boolean hasStandardModes;
        final int totalGames;

        PlayerParticipation(boolean hasStandardModes, int totalGames) {
            this.hasStandardModes = hasStandardModes;
            this.totalGames = totalGames;
        }
    }

    /**
     * Prints statistics for all game modes.
     * @param results the player results
     */
    public void printStatistics(Map<String, PlayerResult> results) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ELO CALCULATION STATISTICS (Zero-Sum Performance)");
        System.out.println("=".repeat(70));

        for (GameMode mode : GameMode.values()) {
            String modeName = mode.getModeName();
            List<Double> elos = new ArrayList<>();
            List<Double> performances = new ArrayList<>();

            for (PlayerResult pr : results.values()) {
                if (pr.getModes().containsKey(modeName)) {
                    ModeResult mr = pr.getModes().get(modeName);
                    elos.add(mr.getElo());
                    performances.add(mr.getAvgPerformance());
                }
            }

            if (!elos.isEmpty()) {
                double minElo = elos.stream().min(Double::compare).orElse(0.0);
                double maxElo = elos.stream().max(Double::compare).orElse(0.0);
                double avgElo = elos.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double minPerf = performances.stream().min(Double::compare).orElse(0.0);
                double maxPerf = performances.stream().max(Double::compare).orElse(0.0);
                double avgPerf = performances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                System.out.println("\n" + modeName.toUpperCase() + ":");
                System.out.println("  Players: " + elos.size());
                System.out.printf("  ELO Range: %.1f - %.1f%n", minElo, maxElo);
                System.out.printf("  Average ELO: %.1f%n", avgElo);
                System.out.printf("  Average Performance Score: %.3f%n", avgPerf);
                System.out.printf("  Performance Range: %.3f - %.3f%n", minPerf, maxPerf);
            }
        }
    }

    /**
     * Prints the top players for a specific game mode.
     * @param results the player results
     * @param mode the game mode
     * @param n the number of top players to display
     */
    public void printTopPlayers(Map<String, PlayerResult> results, String mode, int n) {
        List<Map.Entry<String, PlayerResult>> modePlayers = new ArrayList<>();

        for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
            if (entry.getValue().getModes().containsKey(mode)) {
                modePlayers.add(entry);
            }
        }

        modePlayers.sort((a, b) -> Double.compare(
            b.getValue().getModes().get(mode).getElo(),
            a.getValue().getModes().get(mode).getElo()
        ));

        System.out.println("\n" + "=".repeat(120));
        System.out.println("TOP " + n + " PLAYERS - " + mode.toUpperCase());
        System.out.println("=".repeat(120));
        System.out.printf("%-6s %-20s %-8s %-7s %-6s %-6s %-6s %-7s %-7s %-7s%n",
            "Rank", "Player", "ELO", "Games", "Perf", "Wins", "Beds", "Kills", "Deaths", "Finals");
        System.out.println("-".repeat(120));

        for (int i = 0; i < Math.min(n, modePlayers.size()); i++) {
            Map.Entry<String, PlayerResult> entry = modePlayers.get(i);
            PlayerResult pr = entry.getValue();
            ModeResult mr = pr.getModes().get(mode);

            System.out.printf("%-6d %-20s %-8.1f %-7d %-6.3f %-6d %-6d %-7d %-7d %-7d%n",
                i + 1, pr.getName(), mr.getElo(), mr.getGames(), mr.getAvgPerformance(),
                mr.getVictories(), mr.getBedBreaks(), mr.getKills(), mr.getDeaths(), mr.getFinalKills());
        }
    }

    /**
     * Prints the top players in the global leaderboard.
     * @param results the player results
     * @param n the number of top players to display
     */
    public void printTopGlobalPlayers(Map<String, PlayerResult> results, int n) {
        List<Map.Entry<String, PlayerResult>> globalPlayers = new ArrayList<>();

        for (Map.Entry<String, PlayerResult> entry : results.entrySet()) {
            PlayerResult pr = entry.getValue();
            boolean hasStandardModes = false;
            for (GameMode mode : GameMode.standardModes()) {
                if (pr.getModes().containsKey(mode.getModeName())) {
                    hasStandardModes = true;
                    break;
                }
            }
            if (hasStandardModes) {
                globalPlayers.add(entry);
            }
        }

        globalPlayers.sort((a, b) -> Double.compare(b.getValue().getGlobalElo(), a.getValue().getGlobalElo()));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("TOP " + n + " PLAYERS - GLOBAL ELO (Solo/Duo/Trio/Fours)");
        System.out.println("=".repeat(80));
        System.out.printf("%-6s %-20s %-12s %-12s%n", "Rank", "Player", "Global ELO", "Total Games");
        System.out.println("-".repeat(80));

        for (int i = 0; i < Math.min(n, globalPlayers.size()); i++) {
            Map.Entry<String, PlayerResult> entry = globalPlayers.get(i);
            PlayerResult pr = entry.getValue();

            int totalGames = 0;
            for (GameMode mode : GameMode.standardModes()) {
                String modeName = mode.getModeName();
                if (pr.getModes().containsKey(modeName)) {
                    totalGames += pr.getModes().get(modeName).getGames();
                }
            }

            System.out.printf("%-6d %-20s %-12.1f %-12d%n",
                i + 1, pr.getName(), pr.getGlobalElo(), totalGames);
        }
    }

    /**
     * Saves invalid games data as JSON, ranked by total invalid games.
     * @param invalidGamesByPlayer the invalid games data by player and mode
     * @param playerNames the player name mappings
     * @param results the player results for calculating total games
     * @throws IOException if file cannot be written
     */
    public void saveInvalidGames(Map<String, Map<String, InvalidGamesData>> invalidGamesByPlayer,
                                   Map<String, String> playerNames,
                                   Map<String, PlayerResult> results) throws IOException {
        List<PlayerInvalidGamesEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Map<String, InvalidGamesData>> playerEntry : invalidGamesByPlayer.entrySet()) {
            String uuid = playerEntry.getKey();
            String playerName = playerNames.getOrDefault(uuid, "Unknown");
            Map<String, InvalidGamesData> modeData = playerEntry.getValue();

            int totalInvalidGames = 0;
            int totalInvalidWins = 0;
            List<String> allGameIds = new ArrayList<>();

            for (InvalidGamesData modeStats : modeData.values()) {
                totalInvalidGames += modeStats.getTotal();
                totalInvalidWins += modeStats.getWins();
                allGameIds.addAll(modeStats.getGameIds());
            }

            if (totalInvalidGames > 0) {
                PlayerInvalidGamesEntry entry = new PlayerInvalidGamesEntry();
                entry.setUuid(uuid);
                entry.setName(playerName);
                entry.setInvalidGames(totalInvalidGames);
                entry.setInvalidWins(totalInvalidWins);
                entry.setGameIds(allGameIds);

                // Calculate total legitimate games played
                PlayerResult pr = results.get(uuid);
                int totalGames = 0;
                if (pr != null) {
                    for (ModeResult mr : pr.getModes().values()) {
                        totalGames += mr.getGames();
                    }
                }
                entry.setValidGames(totalGames);
                entry.setInvalidPercentage(totalGames > 0 ?
                    Math.round((double) totalInvalidGames / (totalInvalidGames + totalGames) * 1000.0) / 10.0 : 100.0);

                entries.add(entry);
            }
        }

        entries.sort(Comparator.comparingInt((PlayerInvalidGamesEntry e) -> e.getInvalidGames()).reversed());

        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        String output = gson.toJson(entries);
        Files.write(Paths.get(dataDirectory, "temp/invalid_games_java.json"), output.getBytes());
        System.out.println("Saved invalid games data to data/temp/invalid_games_java.json (" +
                          entries.size() + " players)");
    }
}




package com.nulldiamond.elocalculator.core;

import com.nulldiamond.elocalculator.core.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EXAMPLE: Demonstrates the CORE ELO System API.
 * 
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  THIS IS AN EXAMPLE FILE - NOT PART OF THE CORE API                          ║
 * ║                                                                              ║
 * ║  The JSON file loading below is just for demonstration purposes.             ║
 * ║  In your application, you would provide game data from your own source:      ║
 * ║  - Database queries                                                          ║
 * ║  - API responses                                                             ║
 * ║  - Live game events                                                          ║
 * ║  - WebSocket messages                                                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * 
 * What this example does:
 * 1. Loads sample games from a JSON file (YOUR DATA SOURCE GOES HERE)
 * 2. Processes them using CoreAPI (THE ACTUAL API USAGE)
 * 3. Displays leaderboards and balances teams (EXAMPLE OUTPUT)
 * 
 * See CORE_API.md in the project root for full API documentation.
 */
public class CoreRealExample {

    // ════════════════════════════════════════════════════════════════════════════
    // EXAMPLE-SPECIFIC: File paths for sample data
    // In your application, replace this with your own data source
    // ════════════════════════════════════════════════════════════════════════════
    private static final String DATA_DIR = "data";
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("CORE ELO SYSTEM - EXAMPLE WITH SAMPLE DATA");
        System.out.println("=".repeat(80));
        System.out.println("Note: JSON loading is example-only. See CORE_API.md for real usage.");

        // ════════════════════════════════════════════════════════════════════════
        // STEP 1: Initialize the API
        // ════════════════════════════════════════════════════════════════════════
        CoreConfig config = new CoreConfig();
        CoreAPI api = new CoreAPI(config);

        // ════════════════════════════════════════════════════════════════════════
        // STEP 2: Set legacy players BEFORE adding games (optional)
        // Legacy players are previous top players who start with higher ELO, and we just use a Set of their UUIDs
        // ════════════════════════════════════════════════════════════════════════
        Set<String> legacyPlayers = loadLegacyPlayersFromJson(); // <-- Example: loads from JSON
        api.setLegacyPlayers(legacyPlayers);                     // <-- API call
        System.out.println("\nLoaded " + legacyPlayers.size() + " legacy players");
        if (!legacyPlayers.isEmpty()) {
            System.out.println("Legacy players start with " + config.legacyPlayerElo + " ELO instead of " + config.initialElo);
        }

        // ════════════════════════════════════════════════════════════════════════
        // STEP 3: Load game data (EXAMPLE - YOUR DATA SOURCE GOES HERE)
        // In your app, this would be: database query, API call, etc.
        // ════════════════════════════════════════════════════════════════════════
        System.out.println("\n[EXAMPLE] Loading games from scraped_games.json...");
        Map<String, JsonObject> games = loadGamesFromJson(); // <-- Example-specific
        System.out.println("[EXAMPLE] Loaded " + games.size() + " games from JSON file");

        // Sort by timestamp (chronological order is important for ELO), you may skip this if the data is already sorted
        List<Map.Entry<String, JsonObject>> sortedGames = games.entrySet().stream()
                .sorted((a, b) -> {
                    long timeA = a.getValue().has("unix_time") ? a.getValue().get("unix_time").getAsLong() : 0;
                    long timeB = b.getValue().has("unix_time") ? b.getValue().get("unix_time").getAsLong() : 0;
                    return Long.compare(timeA, timeB);
                })
                .collect(Collectors.toList());

        // ════════════════════════════════════════════════════════════════════════
        // STEP 4: Process games through the API
        // For each game: convert game info to CoreGame, then call api.addGame()
        // ════════════════════════════════════════════════════════════════════════
        System.out.println("\nProcessing games chronologically...\n");

        int processed = 0;
        int skipped = 0;
        Map<String, Integer> gamesByMode = new HashMap<>();

        for (Map.Entry<String, JsonObject> entry : sortedGames) {
            String gameId = entry.getKey();
            JsonObject gameJson = entry.getValue();

            try {
                // Convert JSON to CoreGame (example-specific conversion)
                CoreGame coreGame = convertJsonToCoreGame(gameId, gameJson, api);
                if (coreGame != null) {
                    // ═══════════════════════════════════════════════════════════
                    // THIS IS THE API CALL: api.addGame(coreGame)
                    // ═══════════════════════════════════════════════════════════
                    api.addGame(coreGame);
                    processed++;

                    String modeName = coreGame.getGameMode().name();
                    gamesByMode.merge(modeName, 1, Integer::sum);

                    if (processed % 500 == 0) {
                        System.out.println("  Processed " + processed + " games...");
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                skipped++;
                // Silent skip for invalid games
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROCESSING COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println("Total games processed: " + processed);
        System.out.println("Games skipped (invalid): " + skipped);
        System.out.println("Total players tracked: " + api.getPlayerCount());
        System.out.println("\nGames by mode:");
        for (Map.Entry<String, Integer> modeEntry : gamesByMode.entrySet()) {
            System.out.printf("  %s: %d games%n", modeEntry.getKey(), modeEntry.getValue());
        }

        // ════════════════════════════════════════════════════════════════════════
        // STEP 5: Query results (EXAMPLE OUTPUT)
        // Use api.getAllPlayers(), api.getPlayerElo(), etc.
        // ════════════════════════════════════════════════════════════════════════
        printLeaderboards(api);

        // ════════════════════════════════════════════════════════════════════════
        // STEP 6: Team balancing (EXAMPLE OF BALANCING API)
        // Use api.balanceTeams() or api.balanceTeamsWeighted()
        // ════════════════════════════════════════════════════════════════════════
        balanceTop40MegaPlayers(api);
    }

    // ╔══════════════════════════════════════════════════════════════════════════╗
    // ║  OUTPUT METHODS                                                          ║
    // ║  These just display results - customize for your application.            ║
    // ╚══════════════════════════════════════════════════════════════════════════╝

    /**
     * Prints leaderboards for all modes.
     */
    private static void printLeaderboards(CoreAPI api) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TOP 20 PLAYERS BY MODE");
        System.out.println("=".repeat(80));

        for (CoreGameMode mode : CoreGameMode.values()) {
            List<CorePlayer> leaderboard = getLeaderboard(api, mode, 20);
            if (leaderboard.isEmpty()) continue;

            System.out.println("\n" + "-".repeat(60));
            System.out.printf("TOP 20 - %s%n", mode.name());
            System.out.println("-".repeat(60));
            System.out.printf("%-4s %-20s %10s %8s%n", "Rank", "Player", "ELO", "Games");
            System.out.println("-".repeat(60));

            int rank = 1;
            for (CorePlayer player : leaderboard) {
                System.out.printf("%-4d %-20s %10.1f %8d%n",
                        rank++,
                        truncate(player.getName(), 20),
                        player.getElo(mode),
                        player.getGamesPlayed(mode));
            }
        }

        // Global ELO leaderboard
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TOP 20 - GLOBAL ELO");
        System.out.println("-".repeat(60));
        System.out.printf("%-4s %-20s %10s %8s%n", "Rank", "Player", "Global ELO", "Games");
        System.out.println("-".repeat(60));

        List<CorePlayer> globalLeaderboard = api.getAllPlayers().stream()
                .filter(p -> p.getTotalGamesPlayed() > 0)
                .sorted((a, b) -> Double.compare(b.getGlobalElo(), a.getGlobalElo()))
                .limit(20)
                .collect(Collectors.toList());

        int rank = 1;
        for (CorePlayer player : globalLeaderboard) {
            System.out.printf("%-4d %-20s %10.1f %8d%n",
                    rank++,
                    truncate(player.getName(), 20),
                    player.getGlobalElo(),
                    player.getTotalGamesPlayed());
        }
    }

    /**
     * Gets the leaderboard for a specific mode.
     * Shows how to query and sort players using the API.
     */
    private static List<CorePlayer> getLeaderboard(CoreAPI api, CoreGameMode mode, int limit) {
        return api.getAllPlayers().stream()
                .filter(p -> p.getGamesPlayed(mode) > 0)
                .sorted((a, b) -> Double.compare(b.getElo(mode), a.getElo(mode)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Demonstrates team balancing with the top 40 Mega players.
     * Shows both mode-specific and weighted balancing methods.
     */
    private static void balanceTop40MegaPlayers(CoreAPI api) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEAM BALANCING - TOP 40 MEGA PLAYERS");
        System.out.println("=".repeat(80));

        // Get top 40 players by Mega ELO who have played Mega
        List<CorePlayer> megaPlayers = api.getAllPlayers().stream()
                .filter(p -> p.getGamesPlayed(CoreGameMode.MEGA) > 0)
                .sorted((a, b) -> Double.compare(b.getElo(CoreGameMode.MEGA), a.getElo(CoreGameMode.MEGA)))
                .limit(40)
                .collect(Collectors.toList());

        if (megaPlayers.size() < 2) {
            System.out.println("Not enough Mega players for balancing!");
            return;
        }

        System.out.println("\nTop 40 Mega Players:");
        System.out.printf("%-4s %-20s %10s %10s %8s%n", "Rank", "Player", "Mega ELO", "Adj. ELO", "Games");
        System.out.println("-".repeat(60));

        CoreConfig config = new CoreConfig();
        int rank = 1;
        for (CorePlayer p : megaPlayers) {
            System.out.printf("%-4d %-20s %10.1f %10.1f %8d%n",
                    rank++,
                    truncate(p.getName(), 20),
                    p.getElo(CoreGameMode.MEGA),
                    p.getBalancingElo(config.megaWeight),
                    p.getGamesPlayed(CoreGameMode.MEGA));
        }

        // Balance using standard Mega ELO
        List<String> playerIds = megaPlayers.stream()
                .map(CorePlayer::getId)
                .collect(Collectors.toList());

        // ═══════════════════════════════════════════════════════════════════════
        // CORE API: balanceTeams() - uses mode-specific ELO
        // ═══════════════════════════════════════════════════════════════════════
        System.out.println("\n--- Balancing using MEGA mode ELO ---");
        CoreBalanceResult megaResult = api.balanceTeams(playerIds, CoreGameMode.MEGA);
        printBalanceResult(megaResult, "Mega ELO");

        // ═══════════════════════════════════════════════════════════════════════
        // CORE API: balanceTeamsWeighted() - combines all modes with mega weight
        // Better for players with experience across multiple modes
        // ═══════════════════════════════════════════════════════════════════════
        System.out.println("\n--- Balancing using WEIGHTED ELO (megaWeight=" + config.megaWeight + ") ---");
        CoreBalanceResult weightedResult = api.balanceTeamsWeighted(playerIds);
        printBalanceResult(weightedResult, "Weighted ELO");
    }

    /**
     * Prints a balance result.
     */
    private static void printBalanceResult(CoreBalanceResult result, String method) {
        List<CoreTeam> teams = result.getTeams();

        for (int i = 0; i < teams.size(); i++) {
            CoreTeam team = teams.get(i);
            System.out.printf("\nTeam %d (Total ELO: %.1f, Avg: %.1f):%n",
                    i + 1, team.getTotalElo(), team.getAverageElo());
            System.out.printf("  %-20s%n", "Player");
            System.out.println("  " + "-".repeat(20));

            for (CorePlayer player : team.getPlayers()) {
                System.out.printf("  %-20s%n", truncate(player.getName(), 20));
            }
        }

        System.out.printf("\nELO Difference (%s): %.1f%n", method, result.getEloDifference());

        // Calculate win chances using ELO formula (using average ELO, not total)
        if (teams.size() == 2) {
            CoreTeam team1 = teams.get(0);
            CoreTeam team2 = teams.get(1);
            double avg1 = team1.getAverageElo();
            double avg2 = team2.getAverageElo();
            double avgDiff = Math.abs(avg1 - avg2);
            System.out.printf("Average ELO difference per player: %.1f%n", avgDiff);
            
            // Win probability uses average ELO (treats teams as single entities with their average rating)
            double diff = avg2 - avg1;
            double prob1 = 1.0 / (1.0 + Math.pow(10, diff / 400.0));
            double prob2 = 1.0 - prob1;
            System.out.printf("Win chance Team 1: %.1f%%%n", prob1 * 100);
            System.out.printf("Win chance Team 2: %.1f%%%n", prob2 * 100);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "Unknown";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    // ╔══════════════════════════════════════════════════════════════════════════╗
    // ║  EXAMPLE-SPECIFIC METHODS                                                ║
    // ║  These load data from JSON files. Replace with your own data source.     ║
    // ╚══════════════════════════════════════════════════════════════════════════╝

    /**
     * EXAMPLE: Loads legacy player UUIDs from a JSON file.
     * In your app, this might come from a database or config service.
     */
    private static Set<String> loadLegacyPlayersFromJson() throws IOException {
        String path = Paths.get(DATA_DIR, "config", "legacy_top_players.json").toString();
        if (!Files.exists(Paths.get(path))) {
            return new HashSet<>();
        }
        String content = new String(Files.readAllBytes(Paths.get(path)));
        List<String> uuids = gson.fromJson(content, new TypeToken<List<String>>(){}.getType());
        return uuids != null ? new HashSet<>(uuids) : new HashSet<>();
    }

    /**
     * EXAMPLE: Loads all games from a JSON file.
     * In your app, this would be replaced with your data source (API, DB, etc.)
     */
    private static Map<String, JsonObject> loadGamesFromJson() throws IOException {
        String path = Paths.get(DATA_DIR, "input", "scraped_games.json").toString();
        String content = new String(Files.readAllBytes(Paths.get(path)));
        return gson.fromJson(content, new TypeToken<Map<String, JsonObject>>(){}.getType());
    }

    /**
     * EXAMPLE: Converts a JSON game object to a CoreGame.
     * 
     * This is specific to the JSON format used in scraped_games.json.
     * In your application, you would convert from YOUR data format to CoreGame.
     * 
     * The key steps are:
     * 1. Create a CoreGame with ID and mode
     * 2. Add teams with player UUIDs
     * 3. Set the winner team
     * 4. Optionally add player stats
     */
    private static CoreGame convertJsonToCoreGame(String gameId, JsonObject json, CoreAPI api) {
        // Determine game mode from team sizes
        JsonObject teamsJson = json.getAsJsonObject("teams");
        if (teamsJson == null || teamsJson.size() == 0) {
            return null;
        }

        int maxTeamSize = 0;
        Map<String, List<String>> teams = new HashMap<>();
        Map<String, String> playerNames = new HashMap<>();

        for (String teamName : teamsJson.keySet()) {
            List<String> playerIds = new ArrayList<>();
            for (JsonElement playerElem : teamsJson.getAsJsonArray(teamName)) {
                JsonObject player = playerElem.getAsJsonObject();
                String uuid = player.get("uuid").getAsString();
                String name = player.has("name") ? player.get("name").getAsString() : uuid;
                playerIds.add(uuid);
                playerNames.put(uuid, name);
            }
            teams.put(teamName, playerIds);
            maxTeamSize = Math.max(maxTeamSize, playerIds.size());
        }

        // Determine game mode based on max team size
        CoreGameMode mode;
        if (maxTeamSize >= 8) mode = CoreGameMode.MEGA;
        else if (maxTeamSize == 1) mode = CoreGameMode.SOLO;
        else if (maxTeamSize == 2) mode = CoreGameMode.DUO;
        else if (maxTeamSize == 3) mode = CoreGameMode.TRIO;
        else mode = CoreGameMode.FOURS;

        // ═══════════════════════════════════════════════════════════════════════
        // CORE API USAGE: Creating a CoreGame
        // ═══════════════════════════════════════════════════════════════════════
        CoreGame game = new CoreGame(gameId, mode);

        // Add teams to the game
        for (Map.Entry<String, List<String>> teamEntry : teams.entrySet()) {
            game.addTeam(teamEntry.getKey(), teamEntry.getValue());
        }

        // Set winner team
        String winner = json.has("winner") ? json.get("winner").getAsString() : null;
        if (winner != null && !winner.isEmpty()) {
            game.setWinnerTeam(winner);
        }

        // Add player stats (optional - improves ELO accuracy)
        if (json.has("player_stats")) {
            JsonObject statsJson = json.getAsJsonObject("player_stats");
            for (String playerId : statsJson.keySet()) {
                JsonObject playerStatsJson = statsJson.getAsJsonObject(playerId);
                int kills = playerStatsJson.has("kills") ? playerStatsJson.get("kills").getAsInt() : 0;
                int deaths = playerStatsJson.has("deaths") ? playerStatsJson.get("deaths").getAsInt() : 0;
                int bedBreaks = playerStatsJson.has("bed_breaks") ? playerStatsJson.get("bed_breaks").getAsInt() : 0;
                int finalKills = playerStatsJson.has("final_kills") ? playerStatsJson.get("final_kills").getAsInt() : 0;

                game.setPlayerStats(playerId, new CorePlayerStats(kills, deaths, bedBreaks, finalKills));
            }
        }

        // Register players with display names (getOrCreatePlayer is safe to call multiple times)
        for (Map.Entry<String, String> nameEntry : playerNames.entrySet()) {
            api.getOrCreatePlayer(nameEntry.getKey(), nameEntry.getValue());
        }

        return game;
    }
}

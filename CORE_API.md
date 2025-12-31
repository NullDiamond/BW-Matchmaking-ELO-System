# CORE API Documentation

A lightweight, standalone ELO calculation system for Bedwars matchmaking.

## Overview

The **CORE module** is a clean, dependency-free subset of the full ELO system designed for:
- **Embedding** in other applications (Discord bots, web services, etc.)
- **Real-time matchmaking** without file I/O overhead
- **Team balancing** with multiple strategies

All data is stored **in-memory only** — no file reads or writes.

---

## Quick Start

```java
import com.nulldiamond.elocalculator.core.*;
import com.nulldiamond.elocalculator.core.model.*;

// 1. Create the API (with default or custom config)
CoreAPI api = new CoreAPI();

// 2. Register players
api.registerPlayer("uuid-1", "Alice");
api.registerPlayer("uuid-2", "Bob");
api.registerPlayer("uuid-3", "Charlie");
api.registerPlayer("uuid-4", "Dave");

// 3. Create and add a game
CoreGame game = new CoreGame("game-001", CoreGameMode.DUO);
game.addTeam("team1", Arrays.asList("uuid-1", "uuid-2"));
game.addTeam("team2", Arrays.asList("uuid-3", "uuid-4"));
game.setWinnerTeam("team1");
api.addGame(game);

// 4. Query results
Double aliceElo = api.getPlayerElo("uuid-1", CoreGameMode.DUO);
System.out.println("Alice's DUO ELO: " + aliceElo);

// 5. Undo if needed
api.undoLastGame();
```

---

## Core Classes

| Class | Purpose |
|-------|---------|
| `CoreAPI` | Main entry — use this for all operations |
| `CoreConfig` | Configuration (K-factors, initial ELO, weights) |
| `CoreGame` | Represents a single game with teams and stats |
| `CorePlayer` | Player data with per-mode ELO and statistics |
| `CoreGameMode` | Enum: SOLO, DUO, TRIO, FOURS, MEGA |
| `CoreBalanceResult` | Result of team balancing operation |

---

## API Reference

### Creating the API

```java
// Default configuration
CoreAPI api = new CoreAPI();

// Custom configuration
CoreConfig config = new CoreConfig();
config.kFactor = 50.0;           // Increase rating volatility
config.initialElo = 1000.0;      // Start players at 1000
config.legacyPlayerElo = 1500.0; // Legacy players start higher
CoreAPI api = new CoreAPI(config);
```

### Player Management

```java
// Register new player
CorePlayer player = api.registerPlayer("uuid", "PlayerName");

// Register with custom initial ELO
CorePlayer player = api.registerPlayer("uuid", "PlayerName", 1500.0);

// Get or create (safe to call multiple times)
CorePlayer player = api.getOrCreatePlayer("uuid", "PlayerName");

// Query player
CorePlayer player = api.getPlayer("uuid");
Double elo = api.getPlayerElo("uuid", CoreGameMode.SOLO);
Double globalElo = api.getPlayerGlobalElo("uuid");

// Get all players
Collection<CorePlayer> allPlayers = api.getAllPlayers();
int count = api.getPlayerCount();
```

### Legacy Players

Legacy players are previous top players who start with boosted ELO instead of the default.

```java
// Set legacy players (call BEFORE adding games)
Set<String> legacyUuids = Set.of("uuid-1", "uuid-2", "uuid-3");
api.setLegacyPlayers(legacyUuids);

// Add individual legacy player
api.addLegacyPlayer("uuid-4");

// Check if player is legacy
boolean isLegacy = api.isLegacyPlayer("uuid-1");
```

### Adding Games

```java
// Create a game
CoreGame game = new CoreGame("unique-game-id", CoreGameMode.FOURS);

// Add teams (team name -> list of player UUIDs)
game.addTeam("Red", Arrays.asList("uuid-1", "uuid-2", "uuid-3", "uuid-4"));
game.addTeam("Blue", Arrays.asList("uuid-5", "uuid-6", "uuid-7", "uuid-8"));

// Set winner
game.setWinnerTeam("Red");

// Optional: Add player stats for performance-based ELO
game.setPlayerStats("uuid-1", new CorePlayerStats(
    5,   // kills
    2,   // deaths
    1,   // bed breaks
    2    // final kills
));

// Add the game (calculates ELO changes)
api.addGame(game);
```

### Undoing Games

```java
// Undo the most recent game
boolean success = api.undoLastGame();

// Get undo history size
int undoCount = api.getUndoHistorySize();
```

### Team Balancing

```java
List<String> playerIds = Arrays.asList(
    "uuid-1", "uuid-2", "uuid-3", "uuid-4",
    "uuid-5", "uuid-6", "uuid-7", "uuid-8"
);

// Balance using mode-specific ELO (e.g., MEGA ELO for mega games)
CoreBalanceResult result = api.balanceTeams(playerIds, CoreGameMode.MEGA);

// Balance using weighted ELO (combines all modes with mega weight)
CoreBalanceResult result = api.balanceTeamsWeighted(playerIds);

// Balance into specific number of teams
CoreBalanceResult result = api.balanceTeams(playerIds, CoreGameMode.FOURS, 2);

// Access results
List<CoreTeam> teams = result.getTeams();
double eloDiff = result.getEloDifference();

for (CoreTeam team : teams) {
    System.out.println("Team avg ELO: " + team.getAverageElo());
    for (CorePlayer player : team.getPlayers()) {
        System.out.println("  - " + player.getName());
    }
}
```

---

## Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `initialElo` | 1200.0 | Starting ELO for new players |
| `legacyPlayerElo` | 1800.0 | Starting ELO for legacy players |
| `kFactor` | 40.0 | Standard K-factor (rating change sensitivity) |
| `kFactorMega` | 60.0 | K-factor for Mega mode |
| `megaWeight` | 0.7 | Weight of Mega ELO in weighted calculations |
| `bedBreakWeight` | 0.15 | Performance weight for bed breaks |
| `kdWeight` | 0.05 | Performance weight for K/D ratio |
| `finalKillWeight` | 0.06 | Performance weight for final kills |

---

## Game Modes

| Mode | Max Team Size | Typical Teams |
|------|---------------|---------------|
| SOLO | 1 | 8 players (8 teams of 1) |
| DUO | 2 | 8 teams of 2 |
| TRIO | 3 | 4 teams of 3 |
| FOURS | 4 | 4 teams of 4 |
| MEGA | 8+ | 2 teams of 8+ |

The system automatically handles different team sizes and adjusts calculations accordingly.

---

## Example: Processing Game Data from External Source

The `CoreRealExample.java` class shows how to load games from JSON files, but in real usage you would typically receive game data from an external source (API, database, live events, etc.):

```java
// Pseudocode for real-world integration
public class MyMatchmakingService {
    private final CoreAPI api = new CoreAPI();
    
    public void onGameComplete(MyGameResult gameResult) {
        // Convert your data format to CoreGame
        CoreGame game = new CoreGame(
            gameResult.getId(),
            determineGameMode(gameResult.getTeamSize())
        );
        
        // Add teams from your data
        for (MyTeam team : gameResult.getTeams()) {
            List<String> playerIds = team.getPlayers().stream()
                .map(MyPlayer::getUuid)
                .collect(Collectors.toList());
            game.addTeam(team.getName(), playerIds);
        }
        
        game.setWinnerTeam(gameResult.getWinnerTeamName());
        
        // Add to ELO system
        api.addGame(game);
    }
    
    public List<List<String>> balanceLobby(List<String> playerIds) {
        CoreBalanceResult result = api.balanceTeamsWeighted(playerIds);
        return result.getTeams().stream()
            .map(team -> team.getPlayers().stream()
                .map(CorePlayer::getId)
                .collect(Collectors.toList()))
            .collect(Collectors.toList());
    }
}
```

---

## File Structure

```
src/main/java/com/nulldiamond/elocalculator/core/
├── CoreAPI.java              # Main entry (use this)
├── CoreConfig.java           # Configuration settings
├── CoreEloSystem.java        # Internal ELO calculations
├── CoreRealExample.java      # Example with sample JSON data
└── model/
    ├── CoreBalanceResult.java
    ├── CoreGame.java
    ├── CoreGameMode.java
    ├── CorePlayer.java
    ├── CorePlayerStats.java
    └── CoreTeam.java
```

---

## Running the Example

The `CoreRealExample` class demonstrates the API using the sample data in `data/input/scraped_games.json`:

```bash
# Compile the project
mvn clean compile

# Run the example
mvn exec:java -Dexec.mainClass="com.nulldiamond.elocalculator.core.CoreRealExample"
```

This will:
1. Load games from the sample JSON file
2. Process all games through the ELO system
3. Display leaderboards for each mode
4. Balance the top 20 Mega players into teams

**Note:** The JSON file loading in `CoreRealExample` is just for demonstration. In production, you would feed game data directly to `CoreAPI` from your data source.

---

## Integration Tips

1. **Create one `CoreAPI` instance** and reuse it for the lifetime of your application
2. **Call `setLegacyPlayers()` before adding games** if you have existing top players
3. **Use `getOrCreatePlayer()`** when processing games — it safely handles both new and existing players
4. **Use `balanceTeamsWeighted()`** for Mega games to account for experience across all modes
5. **Undo support** only tracks the most recent games — design your system accordingly

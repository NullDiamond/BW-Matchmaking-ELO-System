# Bedwars ELO Calculator v0.1

A sophisticated ELO rating system for Minecraft Bedwars that implements zero-sum performance-based calculations across multiple game modes.

## Features

- **Multi-Mode Support**: Separate ELO ratings for Solo, Duo, Trio, Fours, and Mega game modes
- **Zero-Sum Performance System**: Individual performance modifiers that maintain mathematical balance
- **Advanced Metrics**: Tracks bed breaks, K/D ratios, final kills, and victory rates
- **Iterative Calculations**: Multiple calculation passes for rating stability
- **Comprehensive Statistics**: Detailed leaderboards and performance analytics

## Quick Start

### Prerequisites
- **Java 21** or higher
- **Maven 3.6+**

### Setup
1. **Clone the repository:**
   ```bash
   git clone https://github.com/NullDiamond/BW-Matchmaking-ELO-System.git
   cd BW-Matchmaking-ELO-System
   ```

2. **Compile the project:**
   ```bash
   mvn clean compile
   ```

3. **Run with sample data:**
   ```bash
   mvn exec:java
   ```

4. **View results:**
   - Check `data/output/zero_sum_elo_java.json` for calculated ratings
   - View leaderboards in `data/output/leaderboards_java/`

### First Run
The application will automatically process the sample games and generate leaderboards. Use the interactive menu to explore features like player history, match analysis, and team balancing.

**Note:** The `data/` directory contains sample data files for testing. For real usage, you'll need to provide your own game data files. The `.gitignore` file excludes real player data to protect privacy.

---

## Core API (Embeddable Library)

The **CORE module** is a lightweight, standalone ELO system designed for embedding in game servers, while the full version is designed for heavier and more verbose testing

### Core Features
- **In-memory only** — No file I/O, no external dependencies
- **Real-time matchmaking** — Fast team balancing and ELO calculations
- **Multi-mode support** — Separate ELO for SOLO, DUO, TRIO, FOURS, MEGA
- **Undo support** — Roll back recent games if needed

### Core Package Structure

```
src/main/java/com/nulldiamond/elocalculator/core/
├── CoreAPI.java              # Main entry point (use this!)
├── CoreConfig.java           # Configuration settings
├── CoreEloSystem.java        # Internal system orchestrator
├── CoreEloCalculator.java    # ELO calculation algorithms
├── CoreTeamBalancer.java     # Team balancing algorithms  
├── CoreGameValidator.java    # Game validation logic
├── CoreRealExample.java      # Example with sample data
└── model/                    # Data models
    ├── CoreGame.java             # Game with teams and stats
    ├── CorePlayer.java           # Player with per-mode ELO
    ├── CoreGameMode.java         # SOLO, DUO, TRIO, FOURS, MEGA
    ├── CorePlayerStats.java      # Kills, deaths, bed breaks
    ├── CoreBalanceResult.java    # Team balancing result
    ├── CoreTeam.java             # Balanced team
    └── CoreEloChange.java        # ELO change record
```

### Core Quick Start

```java
import com.nulldiamond.elocalculator.core.*;
import com.nulldiamond.elocalculator.core.model.*;

// 1. Create the API
CoreAPI api = new CoreAPI();

// 2. Register players
api.registerPlayer("uuid-1", "Alice");
api.registerPlayer("uuid-2", "Bob");

// 3. Create and add a game
CoreGame game = new CoreGame("game-001", CoreGameMode.DUO);
game.addTeam("team1", Arrays.asList("uuid-1", "uuid-2"));
game.addTeam("team2", Arrays.asList("uuid-3", "uuid-4"));
game.setWinnerTeam("team1");
api.addGame(game);

// 4. Query results
Double aliceElo = api.getPlayerElo("uuid-1", CoreGameMode.DUO);

// 5. Balance teams for matchmaking
CoreBalanceResult result = api.balanceTeams(playerIds, CoreGameMode.MEGA);
```

### Running the Core Example

```bash
mvn exec:java -Dexec.mainClass="com.nulldiamond.elocalculator.core.CoreRealExample"
```

For full Core API documentation, see [CORE_API.md](CORE_API.md).

---

## Algorithm Overview

### Core Principles
- **Zero-Sum Design**: All ELO changes in a game sum to zero, ensuring rating conservation
- **Performance-Based**: Individual player performance (bed breaks, K/D, final kills) modifies base win/loss changes
- **Team-Aware**: Accounts for team composition and size in rating calculations
- **Mode-Specific**: Different parameters optimized for each game mode's characteristics

### Key Components
1. **Base ELO Change**: Calculated using standard ELO formula based on team ratings
2. **Performance Score**: Individual player performance based on bed breaks, K/D ratio, and final kills
3. **Zero-Sum Adjustment**: Ensures total rating changes balance to zero
4. **Team Size Scaling**: Prevents rating inflation in larger team modes

## Configuration

All system parameters are centralized in `Config.java` with detailed documentation:

### ELO System Basics
- `INITIAL_ELO`: Starting rating for new players (default: 1200.0)
- `K_FACTOR`: Rating change sensitivity for standard modes (default: 40.0)
- `K_FACTOR_MEGA`: Higher sensitivity for Mega mode (default: 60.0)

### Performance Weights
- `WEIGHT_BED_BREAKS`: Impact of bed destruction (default: 0.15)
- `WEIGHT_KD`: Impact of kill/death ratio (default: 0.05)
- `FINAL_KILL_WEIGHT`: Impact of final eliminations (default: 0.06)

### Calculation Settings
- `NUM_ITERATIONS`: Calculation passes for stability (default: 1)
- `ACTIVITY_THRESHOLD`: Minimum game activity required (default: 2.0)

## Project Structure

The project consists of two main parts:

### 1. Core API (`core/`) - Embeddable Library
Lightweight, in-memory ELO system for embedding in other applications. See [Core API section](#core-api-embeddable-library) above.

### 2. Full Application (`managers/`, `model/`, etc.) - Complete System
Full-featured ELO calculator with file I/O, interactive menu, history tracking, and more.

```
src/main/java/com/nulldiamond/elocalculator/
├── Main.java                      # Application entry point
├── InteractiveMenuHandler.java    # Interactive menu system
├── config/
│   └── Config.java                # All system constants
├── core/                          # 🔷 CORE API (embeddable)
│   ├── CoreAPI.java               # Main entry point
│   ├── CoreConfig.java            # Configuration
│   ├── CoreEloCalculator.java     # ELO algorithms
│   ├── CoreTeamBalancer.java      # Team balancing
│   └── model/                     # Data models
├── managers/                      # Business logic layer
│   ├── DataManager.java           # Data loading/saving operations
│   ├── EloCalculationManager.java # Entry for ELO calculations
│   ├── EloCalculator.java         # Core ELO algorithm implementation
│   ├── EloHistoryManager.java     # Player ELO history tracking
│   ├── EloUtils.java              # ELO calculation utilities
│   ├── GameModeUtils.java         # Game mode detection utilities
│   ├── GameProcessor.java         # Game data processing
│   ├── HistoricalDataProcessor.java # Batch historical processing
│   ├── MatchAnalyzer.java         # Detailed match analysis
│   ├── MatchManager.java          # Live match processing
│   ├── OutputManager.java         # Results output and display
│   └── TeamBalancer.java          # Team balancing algorithm
└── model/                         # Data transfer objects
    ├── elo/                        # ELO-related models
    │   ├── EloChange.java
    │   ├── EloHistoryEntry.java
    │   ├── EloRecord.java
    │   ├── PerformanceTracker.java
    │   ├── PlayerEloData.java
    │   └── PlayerEloHistory.java
    ├── game/                       # Game-related models
    │   ├── GameClassification.java
    │   ├── GameData.java
    │   ├── GameMode.java
    │   ├── GameStatistics.java
    │   ├── InvalidGamesData.java
    │   └── PlayerStats.java
    ├── leaderboard/                # Leaderboard models
    │   ├── GlobalLeaderboardEntry.java
    │   └── LeaderboardEntry.java
    ├── player/                     # Player models
    │   ├── PlayerIdentifier.java
    │   ├── PlayerInfo.java
    │   └── PlayerList.java
    ├── result/                     # Result models
    │   ├── ModeResult.java
    │   ├── PlayerInvalidGamesEntry.java
    │   └── PlayerResult.java
    └── team/                       # Team balancing models
        ├── BalancedTeamsOutput.java
        └── TeamBalanceResult.java
```

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Input data files in `data/input/` directory:
  - `scraped_games.json`: Main game data with player statistics
  - `sample_games.json`: Sample game data with player statistics (anonymized)
  - `sample_players.json`: Sample player UUID to name mappings (fake data)

## Building and Running

### Using Maven (Recommended)
```bash
# Build the project
mvn clean compile
```

### Interactive Menu (Recommended for Users)

Simply run without arguments to access the interactive menu:

```bash
mvn exec:java
```

The menu provides easy access to all features:
- **Process All Historical Data** - Calculate ELO for all games
- **View Player ELO History** - See detailed match-by-match progression
- **View Player ELO Summary** - View condensed statistics per mode
- **Analyze Specific Match** - See detailed ELO calculation breakdown for historical scraped games
- **Add Live Match** - Process new matches and update leaderboard

### Command Line Options (For Scripting/Automation)

For automation or quick one-off commands:

```bash
# View a player's ELO history
mvn exec:java -Dexec.args="--history PlayerName"

# View a player's ELO summary
mvn exec:java -Dexec.args="--summary PlayerName"

# Analyze a specific match in detail
mvn exec:java -Dexec.args="--match game_id"

# Process a live match
mvn exec:java -Dexec.args="--live {json_data}"
```

### Manual Compilation
```bash
# Compile all classes
javac -cp "path/to/gson.jar" src/main/java/com/sbmm/elocalculator/*.java

# Run the application
java -cp "src/main/java:path/to/gson.jar" com.sbmm.elocalculator.Main

# View player history
java -cp "src/main/java:path/to/gson.jar" com.sbmm.elocalculator.Main --history "PlayerName"

# View player summary
java -cp "src/main/java:path/to/gson.jar" com.sbmm.elocalculator.Main --summary "PlayerName"
```

## Command Line Options

The application supports two modes of operation:

1. **Interactive Mode** (default when no arguments): User-friendly menu interface
2. **Command-Line Mode**: Direct command execution with arguments

### Available Commands

- **No arguments**: Launch interactive menu
- `--history <PlayerName>`: Display detailed match-by-match ELO history
- `--summary <PlayerName>`: Display statistical summary of ELO progression
- `--match <GameID>`: Analyze detailed ELO calculations for a specific match
- `--live <json>`: Process a single live match and update the live leaderboard

### ELO History Feature

The system automatically tracks every ELO change for every player. After running the main calculation, you can view any player's complete ELO progression:

```bash
# View detailed history
mvn exec:java -Dexec.args="--history Alice"

# View summary statistics
mvn exec:java -Dexec.args="--summary Bob"
```

For complete documentation on the ELO history feature, see [ELO_HISTORY_FEATURE.md](ELO_HISTORY_FEATURE.md).

### Match Analysis Feature

Want to understand why a specific match gave a certain ELO change? Use the match analysis tool to see all the math behind the calculations:

```bash
# Analyze a specific match
mvn exec:java -Dexec.args="--match OPlynv3f"
```

This will show you:
- Initial ELO ratings for all players
- Team average ELOs
- Expected win probabilities (calculated from ELO differences)
- Performance scores for each player
- Step-by-step ELO change calculations
- Final ELO changes and zero-sum verification

Perfect for understanding:
- Why you gained/lost a certain amount of ELO
- How your performance affected the rating change
- Impact of playing against stronger/weaker opponents
- Multi-team game normalization

## Input Data Format

### sample_games.json
```json
{
  "SAMPLE_GAME_001": {
    "winner": "Blue",
    "map": "SampleMap",
    "lobby_id": "BEDF123456",
    "total_players": 8,
    "duration": 1200,
    "unix_time": 1842379443796,
    "teams": {
      "Blue": ["sample-uuid-blue-1", "sample-uuid-blue-2", "sample-uuid-blue-3", "sample-uuid-blue-4"],
      "Red": ["sample-uuid-red-1", "sample-uuid-red-2", "sample-uuid-red-3", "sample-uuid-red-4"]
    },
    "player_stats": {
      "sample-uuid-blue-1": {
        "kills": 15,
        "deaths": 8,
        "bed_breaks": 2,
        "final_kills": 1
      }
    }
  }
}
```

### sample_players.json
```json
{
  "sample-uuid-blue-1": "PlayerBlue1",
  "sample-uuid-blue-2": "PlayerBlue2",
  "sample-uuid-red-1": "PlayerRed1"
}
```

## Output

The calculator generates several output files in the `data/output/` directory:

- `zero_sum_elo_java.json`: Complete player ELO data
- `leaderboards_java/`: Individual mode leaderboards
  - `solo_leaderboard.json`
  - `duo_leaderboard.json`
  - `trio_leaderboard.json`
  - `fours_leaderboard.json`
  - `mega_leaderboard.json`
  - `global_leaderboard.json`

### Temporary/Processing Files (data/temp/)

- `elo_history.json`: Complete match-by-match ELO history for all players
- `processed_game_ids.json`: List of processed game IDs
- `recent_snapshots.json`: Recent ELO snapshots for rollback
- `last_5_games.json`: Last 5 games played
- `live_adjusted_mega_elo.json`: Live adjusted mega ELO leaderboard
- `live_mega_leaderboard.json`: Live mega leaderboard
- `invalid_games_java.json`: Games excluded from ELO calculations

### Configuration Files (data/config/)

- `player_uuid_map.json`: Player UUID to name mappings
- `legacy_top_players.json`: List of legacy top player UUIDs (start with 1800 ELO)
- `blacklisted_game_ids.json`: Game IDs to exclude from processing

### Data Files (data/)

- `lobby_discrepancies_java.json`: Games with lobby size mismatches

## Algorithm Details

### Performance Score Calculation
For each player in a game:
1. Calculate team averages for kills, deaths, bed breaks, final kills
2. Compare individual stats to team averages
3. Apply weighted modifiers based on configuration
4. Cap extreme values to prevent outliers

### Zero-Sum Adjustment
1. Calculate base ELO changes using team ratings
2. Apply performance multipliers
3. Adjust all changes proportionally to ensure sum = 0
4. Fine-tune to eliminate any remaining imbalance

### Mega Mode Seeding
- After initial iterations, Mega ratings are seeded using weighted average of other modes
- Allows new Mega players to start with reasonable ratings
- Subsequent Mega games refine these ratings

## Customization

To modify the rating system behavior:

1. Edit values in `Config.java`
2. Recompile and run
3. Analyze output statistics
4. Iterate on configuration

Recommended approach:
- Start with default values
- Adjust performance weights based on game mode characteristics
- Tune K-factors for desired rating volatility
- Modify iteration counts for convergence speed vs. stability

## License

This project is open source. Please attribute the original work when using or modifying the code.

## Contributing

When contributing:
1. Follow Java naming conventions
2. Add documentation for new configuration options
3. Test changes with sample data
4. Update this README for significant changes

## Version History


- **v0.1**: Initial implementation
- **v0.2**: Implementation of Core mode


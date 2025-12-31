/**
 * CORE ELO System - A lightweight, in-memory ELO rating system.
 * 
 * <h2>Package Structure</h2>
 * <pre>
 * core/
 * ├── CoreAPI.java           - Main entry point (entry for all operations)
 * ├── CoreConfig.java        - Configuration (K-factors, initial ELO, weights)
 * ├── CoreEloSystem.java     - Internal system orchestrator
 * ├── CoreEloCalculator.java - ELO calculation algorithms
 * ├── CoreTeamBalancer.java  - Team balancing algorithms
 * ├── CoreGameValidator.java - Game validation logic
 * ├── CoreRealExample.java   - Example usage with sample data
 * └── model/                 - Data models
 *     ├── CoreGame.java          - Game representation
 *     ├── CorePlayer.java        - Player with per-mode ELO
 *     ├── CoreGameMode.java      - Game modes (SOLO, DUO, TRIO, FOURS, MEGA)
 *     ├── CorePlayerStats.java   - Player performance stats
 *     ├── CoreBalanceResult.java - Team balancing result
 *     ├── CoreTeam.java          - Balanced team representation
 *     ├── CoreEloChange.java     - ELO change record
 *     ├── CoreGameResult.java    - Game processing result
 *     └── CoreInvalidGameResult.java - Invalid game details
 * </pre>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Add games</b> - Process games and calculate ELO changes automatically</li>
 *   <li><b>Delete recent games</b> - Undo the last N games (configurable)</li>
 *   <li><b>Balance teams</b> - Create balanced teams from a list of players</li>
 *   <li><b>Multi-mode ELO</b> - Separate ratings for SOLO, DUO, TRIO, FOURS, MEGA</li>
 *   <li><b>Global ELO</b> - Weighted average across all modes</li>
 *   <li><b>Legacy players</b> - Boosted initial ELO for known top players</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>No file I/O</b> - All data stored in memory</li>
 *   <li><b>No external dependencies</b> - Pure Java, no Gson or other libraries required</li>
 *   <li><b>Configurable</b> - All settings can be adjusted via {@link CoreConfig}</li>
 *   <li><b>Standalone</b> - Can be used independently or integrated with the full system</li>
 *   <li><b>Thread-safe</b> - Safe for concurrent access</li>
 * </ul>
 * 
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create the API (recommended entry point)
 * CoreAPI api = new CoreAPI();
 * 
 * // Register players
 * api.registerPlayer("uuid1", "Player1");
 * api.registerPlayer("uuid2", "Player2");
 * api.registerPlayer("uuid3", "Player3");
 * api.registerPlayer("uuid4", "Player4");
 * 
 * // Balance teams
 * List<String> playerIds = Arrays.asList("uuid1", "uuid2", "uuid3", "uuid4");
 * CoreBalanceResult balance = api.balanceTeams(playerIds, CoreGameMode.DUO);
 * 
 * // Create and add a game
 * CoreGame game = new CoreGame("game-001", CoreGameMode.DUO);
 * game.addTeam("Team1", Arrays.asList("uuid1", "uuid2"));
 * game.addTeam("Team2", Arrays.asList("uuid3", "uuid4"));
 * game.setWinnerTeam("Team1");
 * api.addGame(game);
 * 
 * // Query results
 * Double elo = api.getPlayerElo("uuid1", CoreGameMode.DUO);
 * 
 * // Undo the game if needed
 * api.undoLastGame();
 * }</pre>
 * 
 * <h2>Team Balancing</h2>
 * The team balancer uses standard deviation as its balance metric, providing
 * a statistically sound measure of team balance that scales naturally with
 * team count and player skill levels.
 * 
 * @see CoreEloSystem
 * @see CoreConfig
 * @see CoreTeamBalancer
 */
package com.nulldiamond.elocalculator.core;
